package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TipSchedulerTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final Object SOURCE = new Object();
    private static final long NOW = 1_800_000_000_000L;
    private final TipScheduler scheduler = new TipScheduler(Assertions::fail, ignored -> TipAudience.MEMBER);

    @Test
    void readyAdviceDoesNotWaitForAFutureSourceInTheSameGroup() {
        var version = definition("client-version", "client", 60);
        var mods = definition("client-mods", "client", 30, Duration.ofMillis(500), Duration.ofSeconds(30));
        scheduler.offer(SOURCE, PLAYER, version, NOW,
                language -> content("target-version"));
        scheduler.offer(SOURCE, PLAYER, mods, NOW,
                language -> content("voice,map"));

        assertEquals(List.of(version), collect(NOW).stream().map(TipScheduler.Delivery::definition).toList());
        assertEquals(List.of(mods), collect(NOW + 500).stream().map(TipScheduler.Delivery::definition).toList());
    }

    @Test
    void irrelevantHigherPriorityOfferCannotSplitAClientGroup() {
        scheduler.offer(SOURCE, PLAYER, definition("stale-home", "home", 90, Duration.ZERO, Duration.ofSeconds(30)), NOW,
                language -> Optional.empty());
        var version = definition("client-version", "client", 60);
        var mods = definition("client-mods", "client", 30);
        scheduler.offer(SOURCE, PLAYER, version, NOW,
                language -> content("target-version"));
        scheduler.offer(SOURCE, PLAYER, mods, NOW + 5,
                language -> content("recommended-mods"));

        assertEquals(List.of(version, mods), collect(NOW + 5).stream()
                .map(TipScheduler.Delivery::definition).toList());
    }

    @Test
    void newObservationAfterExpiryStartsANewDeliveryWindow() {
        var tip = definition("controls", "controls", 50, Duration.ofSeconds(2), Duration.ofSeconds(5));
        scheduler.offer(SOURCE, PLAYER, tip, NOW,
                language -> content("old"));
        scheduler.offer(SOURCE, PLAYER, tip, NOW + 6000,
                language -> content("new"));

        assertTrue(collect(NOW + 6000).isEmpty());
        assertEquals("new", collect(NOW + 8000).getFirst().situationKey());
    }

    @Test
    void invalidSourceResultDoesNotPreventAnotherSourceFromDelivering() {
        List<RuntimeException> failures = new java.util.ArrayList<>();
        TipScheduler isolated = new TipScheduler(failures::add, ignored -> TipAudience.MEMBER);
        isolated.offer(SOURCE, PLAYER, definition("invalid-source", "invalid", 90), NOW,
                language -> null);
        var valid = definition("valid-source", "valid", 50);
        isolated.offer(new Object(), PLAYER, valid, NOW,
                language -> content("v1"));

        assertEquals(valid, isolated.collect(PLAYER, NOW, 45_000, true, Language.EN_US).getFirst().definition());
        assertEquals(1, failures.size());
    }

    @Test
    void failedEvaluationAndLocalizationDoNotChooseTheGroupOrConsumeCooldown() {
        List<RuntimeException> failures = new java.util.ArrayList<>();
        TipScheduler isolated = new TipScheduler(failures::add, ignored -> TipAudience.MEMBER);
        isolated.offer(SOURCE, PLAYER, definition("throws", "broken", 100), NOW,
                language -> { throw new IllegalStateException("Failed to localize " + language); });
        isolated.offer(SOURCE, PLAYER, definition("null", "broken", 90), NOW,
                language -> Optional.of(new TipContent("v1", null)));
        var valid = definition("valid", "useful", 50);
        isolated.offer(SOURCE, PLAYER, valid, NOW, language -> content("v1"));

        var batch = isolated.collect(PLAYER, NOW, 45_000, true, Language.FR_FR);
        assertEquals(List.of(valid), batch.stream().map(TipScheduler.Delivery::definition).toList());
        assertEquals(2, failures.size());
        assertTrue(isolated.snapshot(PLAYER).history().isEmpty());
        isolated.delivered(PLAYER, batch, NOW);
        assertEquals(java.util.Set.of("valid"), isolated.snapshot(PLAYER).history().keySet());
    }

    @Test
    void sourceClosedDuringEvaluationCannotDeliverItsReturnedContent() {
        scheduler.offer(SOURCE, PLAYER, definition("closed", "group", 90), NOW,
                language -> {
                    scheduler.close(SOURCE);
                    return content("stale");
                });

        assertTrue(collect(NOW).isEmpty());
        assertTrue(scheduler.snapshot(PLAYER).history().isEmpty());
    }

    @Test
    void laterCallbackCanCancelAnAlreadyEvaluatedSource() {
        var cancelled = definition("cancelled", "group", 90);
        var kept = definition("kept", "group", 80);
        offer(cancelled, "v1", NOW);
        scheduler.offer(new Object(), PLAYER, kept, NOW,
                language -> {
            scheduler.close(SOURCE);
            return content("v1");
        });

        assertEquals(List.of(kept), collect(NOW).stream().map(TipScheduler.Delivery::definition).toList());
    }

    @Test
    void replacementDuringEvaluationSurvivesDiscardingTheOldObservation() {
        var tip = definition("refreshed", "group", 50);
        Function<Language, Optional<TipContent>> source = new Function<>() {
            private boolean refresh = true;

            @Override
            public Optional<TipContent> apply(Language language) {
                if (refresh) {
                    refresh = false;
                    scheduler.offer(SOURCE, PLAYER, tip, NOW,
                this);
                }
                return content("current");
            }
        };
        scheduler.offer(SOURCE, PLAYER, tip, NOW,
                source);

        assertTrue(collect(NOW).isEmpty());
        assertEquals(tip, collect(NOW).getFirst().definition());
    }

    @Test
    void clockRollbackDoesNotSuppressGuidanceUntilAnOldFutureTimestamp() {
        var tip = definition("version", "client", 30);
        offer(tip, "v1", NOW + Duration.ofDays(1).toMillis());
        scheduler.delivered(PLAYER, collect(NOW + Duration.ofDays(1).toMillis()),
                NOW + Duration.ofDays(1).toMillis());
        scheduler.restore(PLAYER, scheduler.snapshot(PLAYER));
        offer(tip, "v1", NOW);

        assertEquals(tip, collect(NOW).getFirst().definition());
    }

    @Test
    void latePositiveInstallationEvidenceCancelsRecommendationBeforeSending() {
        AtomicBoolean installed = new AtomicBoolean();
        scheduler.offer(SOURCE, PLAYER, definition("voice", "client", 30, Duration.ofSeconds(10), Duration.ofSeconds(30)), NOW,
                language -> installed.get() ? Optional.empty() : content("voice-undetected"));
        installed.set(true);

        assertTrue(collect(NOW + 10_000).isEmpty());
        assertTrue(scheduler.snapshot(PLAYER).history().isEmpty(), "evaluating an invalid offer must not record delivery");
    }

    @Test
    void repeatedObservationsRefreshContentWithoutRestartingTheDelay() {
        var tip = definition("controls", "controls", 50, Duration.ofSeconds(2), Duration.ofSeconds(20));
        scheduler.offer(SOURCE, PLAYER, tip, NOW,
                language -> content("old"));
        scheduler.offer(SOURCE, PLAYER, tip, NOW + 1000,
                language -> content("current"));

        assertEquals("current", collect(NOW + 2000).getFirst().situationKey());
    }

    @Test
    void unavailablePlayersDiscardReadyAdviceWithoutWaitingToResume() {
        AtomicInteger evaluations = new AtomicInteger();
        var tip = definition("context", "context", 50);
        scheduler.offer(SOURCE, PLAYER, tip, NOW,
                language -> {
            evaluations.incrementAndGet();
            return content("current");
        });
        assertTrue(scheduler.collect(PLAYER, NOW + 1000, 45_000, false, Language.EN_US).isEmpty());
        assertTrue(collect(NOW + 1001).isEmpty());
        assertEquals(0, evaluations.get());
        assertTrue(scheduler.pendingPlayers().isEmpty());
    }

    @Test
    void unchangedSituationRespectsCooldownButNewTargetCanBeSuggested() {
        var tip = definition("version", "client", 30);
        offer(tip, "target-a", NOW);
        scheduler.delivered(PLAYER, collect(NOW), NOW);
        offer(tip, "target-a", NOW + 60_000);
        assertTrue(collect(NOW + 60_000).isEmpty());

        offer(tip, "target-b", NOW + 60_000);
        assertEquals("target-b", collect(NOW + 60_000).getFirst().situationKey());
    }

    @Test
    void globalCooldownDiscardsReadyOffersInsteadOfSendingThemLater() {
        var first = definition("first", "group", 20);
        offer(first, "v1", NOW);
        scheduler.delivered(PLAYER, collect(NOW), NOW);
        offer(definition("second", "other", 20), "v1", NOW + 1000);
        assertTrue(collect(NOW + 1000).isEmpty());
        assertTrue(collect(NOW + 45_000).isEmpty());
    }

    @Test
    void historylessAdviceUsesOnlyTheCurrentSessionsGlobalCooldown() {
        var tip = new Tip.Policy("connection-check", "connection", 30, Duration.ofDays(7),
                Duration.ZERO, Duration.ofSeconds(20), false);
        scheduler.register(SOURCE, tip);
        offer(tip, "same", NOW);
        scheduler.delivered(PLAYER, collect(NOW), NOW);
        assertTrue(scheduler.snapshot(PLAYER).history().isEmpty());
        assertFalse(scheduler.changed(PLAYER), "a connection check alone needs no PDC write");

        offer(tip, "same", NOW + 1000);
        assertTrue(collect(NOW + 1000).isEmpty());
        offer(tip, "same", NOW + 45_000);
        var repeated = collect(NOW + 45_000);
        assertEquals(1, repeated.size(), "the scenario interval does not apply without history");
        scheduler.delivered(PLAYER, repeated, NOW + 45_000);

        var saved = scheduler.snapshot(PLAYER);
        scheduler.forget(PLAYER);
        scheduler.restore(PLAYER, saved);
        offer(tip, "same", NOW + 45_001);
        assertEquals(1, collect(NOW + 45_001).size(), "the previous connection check cannot suppress a new session");
    }

    @Test
    void historylessRegistrationDiscardsOldRecordsRegardlessOfLoadingOrder() {
        var version = new Tip.Policy("connection-version", "connection", 60, Duration.ofDays(7),
                Duration.ZERO, Duration.ofSeconds(20), false);
        var mods = new Tip.Policy("connection-mods", "connection", 30, Duration.ofDays(7),
                Duration.ZERO, Duration.ofSeconds(20), false);
        var retained = new TipState.Entry("other", NOW - 100_000, 2, 1, NOW - 100_000);
        var snapshot = new TipState.Snapshot(java.util.Map.of(
                version.id(), new TipState.Entry("old-version", NOW, 6, 0, 0),
                mods.id(), new TipState.Entry("old-list", NOW - 1000, 6, 0, 0),
                "other", retained));
        for (boolean restoreFirst : new boolean[]{true, false}) {
            TipScheduler restarted = new TipScheduler(error -> fail(error), ignored -> TipAudience.MEMBER);
            if (restoreFirst) restarted.restore(PLAYER, snapshot);
            restarted.register(SOURCE, version);
            restarted.register(SOURCE, mods);
            if (!restoreFirst) restarted.restore(PLAYER, snapshot);

            assertEquals(java.util.Map.of("other", retained), restarted.snapshot(PLAYER).history());
            assertTrue(restarted.changed(PLAYER), "old records must be removed on the next PDC save");
            restarted.offer(SOURCE, PLAYER, version, NOW, language -> content("same"));
            restarted.offer(SOURCE, PLAYER, mods, NOW, language -> content("same"));
            var batch = restarted.collect(PLAYER, NOW, 45_000, true, Language.EN_US);
            assertEquals(2, batch.size(), "discarded history must not impose a stale global cooldown");
            restarted.delivered(PLAYER, batch, NOW);
            assertEquals(java.util.Map.of("other", retained), restarted.snapshot(PLAYER).history());
        }
    }

    @Test
    void mixedBatchOnlyPersistsTheScenariosThatRememberHistory() {
        var remembered = definition("remembered", "group", 60);
        var transientTip = new Tip.Policy("transient", "group", 30, Duration.ofDays(1),
                Duration.ZERO, Duration.ofSeconds(20), false);
        offer(remembered, "same", NOW);
        offer(transientTip, "same", NOW);
        var batch = collect(NOW);
        assertEquals(2, batch.size());
        scheduler.delivered(PLAYER, batch, NOW);
        assertEquals(java.util.Set.of("remembered"), scheduler.snapshot(PLAYER).history().keySet());
        assertTrue(scheduler.changed(PLAYER));
    }

    @Test
    void collectionWithoutActualDeliveryDoesNotConsumeCooldown() {
        var tip = definition("retry", "group", 20);
        offer(tip, "v1", NOW);
        assertEquals(1, collect(NOW).size());
        offer(tip, "v1", NOW + 1000);
        assertEquals(1, collect(NOW + 1000).size());
        assertTrue(scheduler.snapshot(PLAYER).history().isEmpty());
    }

    @Test
    void closingSourceDropsOnlyItsCallbacks() {
        Object anotherSource = new Object();
        var cancelled = definition("closed", "group", 90);
        scheduler.offer(SOURCE, PLAYER, cancelled, NOW,
                language -> { fail("closed feature was queried"); return Optional.empty(); });
        var kept = definition("kept", "group", 20);
        scheduler.offer(anotherSource, PLAYER, kept, NOW,
                language -> content("v1"));
        scheduler.close(SOURCE);

        assertEquals(kept, collect(NOW).getFirst().definition());
    }

    @Test
    void quitCancelsPendingOffersWithoutSuppressingTheNextSession() {
        var tip = definition("optional", "group", 20);
        offer(tip, "v1", NOW);
        scheduler.forget(PLAYER);
        assertTrue(collect(NOW).isEmpty());
        offer(tip, "v1", NOW);
        assertEquals(1, collect(NOW).size());
    }

    @Test
    void queueIsBoundedAndKeepsHigherPriorityOffers() {
        for (int i = 0; i < 20; i++) {
            offer(definition("tip-" + i, "group", i), "v1", NOW);
        }
        var batch = collect(NOW);
        assertEquals(List.of("tip-19", "tip-18", "tip-17"),
                batch.stream().map(it -> it.definition().id()).toList());
        assertTrue(scheduler.pendingPlayers().isEmpty());
    }

    @Test
    void rejectsInvalidLifetimeAndDuplicateOwnership() {
        var tip = definition("shared-id", "group", 20);
        assertThrows(IllegalArgumentException.class, () -> definition("invalid", "group", 20,
                Duration.ofSeconds(5), Duration.ofSeconds(5)));
        offer(tip, "v1", NOW);
        assertThrows(IllegalArgumentException.class, () -> scheduler.offer(new Object(), PLAYER, tip, NOW,
                language -> content("v1")));
    }

    @Test
    void registrationRejectsDuplicateIdsBeforeAnyOfferAndReleasesThemOnClose() {
        var tip = definition("registered", "group", 20);
        Object anotherSource = new Object();
        scheduler.register(SOURCE, tip);
        assertThrows(IllegalArgumentException.class, () -> scheduler.register(SOURCE, tip));
        assertThrows(IllegalArgumentException.class, () -> scheduler.register(anotherSource, tip));
        scheduler.close(SOURCE);
        assertDoesNotThrow(() -> scheduler.register(anotherSource, tip));
    }

    @Test
    void persistentCooldownsArePlayerSpecific() {
        var tip = definition("recommendation", "group", 20);
        offer(tip, "v1", NOW);
        scheduler.delivered(PLAYER, collect(NOW), NOW);
        var snapshot = scheduler.snapshot(PLAYER);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.history().clear());
        scheduler.restore(PLAYER, snapshot);
        UUID another = UUID.randomUUID();
        scheduler.offer(SOURCE, another, tip, NOW,
                language -> content("v1"));
        assertEquals(1, scheduler.collect(another, NOW, 45_000, true, Language.EN_US).size());
    }

    @Test
    void adaptiveSamplingStartsCertainCountsMissesAndRecoversWithTime() {
        TipScheduler adaptive = new TipScheduler(error -> fail(error), () -> 0.9, ignored -> TipAudience.MEMBER);
        var rule = definition("chat", "chat", 0);
        Function<Language, Optional<TipContent>> text = language -> content("chat");
        adaptive.offer(SOURCE, PLAYER, rule, NOW, 1, true, 1, Duration.ZERO, text);
        var first = adaptive.collect(PLAYER, NOW, 45_000, true, Language.EN_US);
        assertEquals(1, first.size(), "the first eligible match has no probability penalty");
        adaptive.delivered(PLAYER, first, NOW);
        adaptive.offer(SOURCE, PLAYER, rule, NOW + 60_000, 1, true, 10, Duration.ZERO, text);
        assertTrue(adaptive.collect(PLAYER, NOW + 60_000, 45_000, true, Language.EN_US).isEmpty());
        var afterMiss = adaptive.snapshot(PLAYER).history().get("chat");
        assertTrue(afterMiss.pressure() > 10, "a missed dense burst must still contribute all matches");
        assertEquals(1, afterMiss.presentations(), "a miss must not advance reminder spacing");

        long later = NOW + Duration.ofHours(8).toMillis();
        adaptive.offer(SOURCE, PLAYER, rule, later, 1, true, 1, Duration.ZERO, text);
        assertEquals(1, adaptive.collect(PLAYER, later, 45_000, true, Language.EN_US).size(),
                "a fresh chat request recovers without waiting for the background reminder interval");
    }

    @Test
    void equalCountsSpreadOverTimeProduceLessPressureThanADenseBurst() {
        TipState.Entry burst = TipState.Entry.empty().matched(NOW, 10);
        TipState.Entry sparse = TipState.Entry.empty();
        for (int i = 0; i < 10; i++) sparse = sparse.matched(NOW + Duration.ofHours(i).toMillis(), 1);
        assertTrue(burst.probability(NOW, 1) < sparse.probability(NOW + Duration.ofHours(9).toMillis(), 1));
        assertEquals(0.25, TipState.Entry.empty().matched(NOW, 1).probability(NOW, 1), 1e-9);
        assertTrue(burst.probability(NOW, 10) < burst.probability(NOW, 1),
                "a repeated batch must also penalize its current extra messages");
        assertEquals(1, TipState.Entry.empty().probability(NOW, 32),
                "coalescing the first burst must preserve its initial certain opportunity");
        assertEquals(1, burst.probability(NOW + Duration.ofHours(8).toMillis(), 1));
    }

    @Test
    void backgroundSpacingIsFiniteAndANewSituationRestartsIt() {
        TipState.Entry stamp = TipState.Entry.empty();
        long now = NOW;
        for (int days : new int[]{1, 2, 4, 8, 16, 30, 30}) {
            stamp = stamp.delivered("same", now);
            long interval = Duration.ofDays(days).toMillis();
            assertFalse(stamp.due("same", now + interval - 1, Duration.ofDays(1)));
            assertTrue(stamp.due("same", now + interval, Duration.ofDays(1)));
            now += interval;
        }
        assertTrue(stamp.due("changed", now, Duration.ofDays(1)));
        assertEquals(1, stamp.delivered("changed", now).presentations());
        assertEquals(1, stamp.delivered("same", now + TipState.RETENTION).presentations());
        assertEquals(Duration.ofDays(60).toMillis(), stamp.interval(Duration.ofDays(60)),
                "a module's longer base interval is preserved");
    }

    private void offer(Tip.Policy tip, String situation, long now) {
        scheduler.offer(SOURCE, PLAYER, tip, now,
                language -> content(situation));
    }

    private List<TipScheduler.Delivery> collect(long now) {
        return scheduler.collect(PLAYER, now, 45_000, true, Language.EN_US);
    }

    private static Optional<TipContent> content(String situation) {
        return Optional.of(new TipContent(situation, Component.text(situation)));
    }

    private static Tip.Policy definition(String id, String group, int priority) {
        return definition(id, group, priority, Duration.ZERO, Duration.ofSeconds(20));
    }

    private static Tip.Policy definition(String id, String group, int priority,
                                         Duration delay, Duration lifetime) {
        return new Tip.Policy(id, group, priority, Duration.ofDays(7), delay, lifetime, true);
    }
}
