package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TipAmbientTest {
    private static final long MINUTE = 60_000;
    private static final long INTERVAL = 15 * MINUTE;
    private static final UUID PLAYER = UUID.randomUUID();
    private static final Object OWNER = new Object();
    private final AtomicReference<Double> random = new AtomicReference<>(0.0);
    private final TipScheduler scheduler = new TipScheduler(error -> fail(error), random::get,
            ignored -> TipAudience.MEMBER);

    @Test
    void newcomerAmbientOpportunitiesUseHalfTheWaitAcrossTheRandomRange() {
        for (double fraction : new double[]{0, 0.5, 0.999}) {
            UUID newcomer = UUID.randomUUID();
            TipScheduler boosted = new TipScheduler(error -> fail(error), () -> fraction,
                    player -> player.equals(newcomer) ? TipAudience.NEWCOMER : TipAudience.MEMBER);
            boosted.register(OWNER, scene("ambient"), (player, language) -> content("ambient"));
            assertTrue(boosted.collect(PLAYER, 0, 45_000, true, Language.EN_US).isEmpty());
            assertTrue(boosted.collect(newcomer, 0, 45_000, true, Language.EN_US).isEmpty());

            long regularWait = INTERVAL + (long) (fraction * INTERVAL);
            long newcomerWait = regularWait / 2;
            assertTrue(boosted.collect(newcomer, newcomerWait - 1, 45_000, true, Language.EN_US).isEmpty());
            assertEquals(1, boosted.collect(newcomer, newcomerWait, 45_000, true, Language.EN_US).size());
            assertTrue(boosted.collect(PLAYER, newcomerWait, 45_000, true, Language.EN_US).isEmpty());
            assertEquals(1, boosted.collect(PLAYER, regularWait, 45_000, true, Language.EN_US).size());
        }
    }

    @Test
    void changingMembershipRecomputesAnAlreadyScheduledAmbientWait() {
        AtomicReference<TipAudience> audience = new AtomicReference<>(TipAudience.MEMBER);
        TipScheduler boosted = new TipScheduler(error -> fail(error), () -> 0, ignored -> audience.get());
        boosted.register(OWNER, scene("ambient"), (player, language) -> content("ambient"));
        assertTrue(boosted.collect(PLAYER, 0, 45_000, true, Language.EN_US).isEmpty());
        assertTrue(boosted.collect(PLAYER, INTERVAL / 2, 45_000, true, Language.EN_US).isEmpty());
        audience.set(TipAudience.NEWCOMER);
        assertEquals(1, boosted.collect(PLAYER, INTERVAL / 2, 45_000, true, Language.EN_US).size());

        audience.set(TipAudience.MEMBER);
        assertTrue(boosted.collect(PLAYER, INTERVAL, 45_000, true, Language.EN_US).isEmpty());
        assertEquals(1, boosted.collect(PLAYER, INTERVAL * 3 / 2, 45_000, true, Language.EN_US).size());
    }

    @Test
    void eachPlayerHasAnIndependentRandomQuietInterval() {
        var elevator = scene("elevator");
        scheduler.register(OWNER, elevator, (player, language) -> content(language.id()));
        random.set(0.25);
        assertTrue(collect(PLAYER, 0).isEmpty());
        random.set(0.75);
        UUID another = UUID.randomUUID();
        assertTrue(collect(another, 0).isEmpty());

        long firstDue = INTERVAL + INTERVAL / 4;
        long secondDue = INTERVAL + INTERVAL * 3 / 4;
        assertTrue(collect(PLAYER, firstDue - 1).isEmpty());
        assertEquals("fr_fr", scheduler.collect(PLAYER, firstDue, 45_000, true, Language.FR_FR)
                .getFirst().situationKey());
        assertTrue(collect(another, firstDue).isEmpty());
        assertEquals(elevator, collect(another, secondDue).getFirst().definition());
        assertTrue(scheduler.snapshot(PLAYER).history().isEmpty(), "selection alone must not record a display");
    }

    @Test
    void randomSelectionTriesOtherEligibleScenesAndReturnsOnlyOne() {
        var first = scene("first");
        var second = scene("second");
        var irrelevant = scene("irrelevant");
        var cooling = scene("cooling");
        scheduler.register(OWNER, first, (player, language) -> content("first"));
        scheduler.register(OWNER, second, (player, language) -> content("second"));
        scheduler.register(OWNER, irrelevant, (player, language) -> Optional.empty());
        scheduler.register(OWNER, cooling, (player, language) -> content("cooling"));
        scheduler.delivered(PLAYER, List.of(new TipScheduler.Delivery(cooling, "cooling", Component.empty())), 0);

        // Start at the last registered scene, then work backwards through ineligible entries.
        random.set(0.999);
        assertTrue(collect(PLAYER, 0).isEmpty());
        var batch = collect(PLAYER, 30 * MINUTE);
        assertEquals(List.of(second), batch.stream().map(TipScheduler.Delivery::definition).toList());
        assertTrue(collect(PLAYER, 30 * MINUTE + 1).isEmpty(), "at most one scene per opportunity");
    }

    @Test
    void pendingContextualTipsTakePrecedenceEvenBeforeTheirDeliveryTime() {
        scheduler.register(OWNER, scene("ambient"), (player, language) -> {
            fail("daily tips must not interrupt pending contextual guidance");
            return Optional.empty();
        });
        assertTrue(collect(PLAYER, 0).isEmpty());
        var context = new Tip.Policy("context", "context", 90, Duration.ofDays(1),
                Duration.ofSeconds(2), Duration.ofSeconds(20));
        scheduler.offer(OWNER, PLAYER, context, INTERVAL, language -> content("context"));

        assertTrue(collect(PLAYER, INTERVAL).isEmpty());
        assertEquals(context, collect(PLAYER, INTERVAL + 2000).getFirst().definition());
        assertTrue(collect(PLAYER, INTERVAL + 3000).isEmpty(), "the skipped opportunity must not catch up");
    }

    @Test
    void unavailableAndRecentlyMessagedPlayersSkipTheOpportunityWithoutEvaluatingRules() {
        AtomicInteger evaluations = new AtomicInteger();
        scheduler.register(OWNER, scene("ambient"), (player, language) -> {
            evaluations.incrementAndGet();
            return content("ambient");
        });
        assertTrue(collect(PLAYER, 0).isEmpty());
        assertTrue(scheduler.collect(PLAYER, INTERVAL, 45_000, false, Language.EN_US).isEmpty());
        assertTrue(collect(PLAYER, INTERVAL + 1000).isEmpty());
        assertEquals(0, evaluations.get());

        var context = scene("context");
        scheduler.delivered(PLAYER, List.of(new TipScheduler.Delivery(context, "v1", Component.empty())),
                INTERVAL * 2 - 1000);
        assertTrue(collect(PLAYER, INTERVAL * 2).isEmpty());
        assertTrue(collect(PLAYER, INTERVAL * 2 + 45_000).isEmpty());
        assertEquals(0, evaluations.get());
        assertEquals(1, collect(PLAYER, INTERVAL * 3).size());
        assertEquals(1, evaluations.get());
    }

    @Test
    void ambientAndManualOffersShareFinitePersistedReminderHistory() {
        var daily = scene("daily");
        scheduler.register(OWNER, daily, (player, language) -> content("v1"));
        assertTrue(collect(PLAYER, 0).isEmpty());
        var batch = collect(PLAYER, INTERVAL);
        scheduler.delivered(PLAYER, batch, INTERVAL);
        scheduler.restore(PLAYER, scheduler.snapshot(PLAYER));
        scheduler.offer(OWNER, PLAYER, daily, INTERVAL + MINUTE, language -> content("v1"));

        assertTrue(collect(PLAYER, INTERVAL + MINUTE).isEmpty());
        assertTrue(collect(PLAYER, INTERVAL * 2).isEmpty());
        assertEquals(daily, collect(PLAYER, INTERVAL + Duration.ofDays(1).toMillis()).getFirst().definition());
    }

    @Test
    void closingOneOwnerAndFailingCallbacksDoNotBlockAnotherModule() {
        List<RuntimeException> failures = new ArrayList<>();
        TipScheduler isolated = new TipScheduler(failures::add, () -> 0, ignored -> TipAudience.MEMBER);
        Object closed = new Object();
        isolated.register(closed, scene("closed"), (player, language) -> {
            fail("closed module was queried");
            return Optional.empty();
        });
        isolated.register(OWNER, scene("broken"), (player, language) -> {
            throw new IllegalStateException("localization failed");
        });
        var kept = scene("kept");
        isolated.register(OWNER, kept, (player, language) -> content("kept"));
        isolated.close(closed);

        assertTrue(isolated.collect(PLAYER, 0, 45_000, true, Language.EN_US).isEmpty());
        assertEquals(kept, isolated.collect(PLAYER, INTERVAL, 45_000, true, Language.EN_US)
                .getFirst().definition());
        assertEquals(1, failures.size());
        isolated.close(OWNER);
        assertTrue(isolated.collect(PLAYER, INTERVAL * 2, 45_000, true, Language.EN_US).isEmpty());
    }

    @Test
    void callbacksCannotDeliverAfterClosingTheirOwnScope() {
        Object selfClosing = new Object();
        scheduler.register(selfClosing, scene("self-closing"), (player, language) -> {
            scheduler.close(selfClosing);
            return content("stale");
        });
        var kept = scene("kept");
        scheduler.register(OWNER, kept, (player, language) -> content("kept"));
        assertTrue(collect(PLAYER, 0).isEmpty());
        assertEquals(kept, collect(PLAYER, INTERVAL).getFirst().definition());
    }

    @Test
    void disconnectAndClockRollbackStartFreshQuietIntervals() {
        scheduler.register(OWNER, scene("ambient"), (player, language) -> content("ambient"));
        assertTrue(collect(PLAYER, 0).isEmpty());
        scheduler.forget(PLAYER);
        assertTrue(collect(PLAYER, INTERVAL).isEmpty());
        assertTrue(collect(PLAYER, INTERVAL * 2 - 1).isEmpty());
        assertEquals(1, collect(PLAYER, INTERVAL * 2).size());

        assertTrue(collect(PLAYER, 0).isEmpty(), "a clock rollback must reset the randomized wait");
        assertTrue(collect(PLAYER, INTERVAL - 1).isEmpty());
        assertEquals(1, collect(PLAYER, INTERVAL).size());
        scheduler.clear();
        assertTrue(collect(PLAYER, INTERVAL * 3).isEmpty());
    }

    private List<TipScheduler.Delivery> collect(UUID player, long now) {
        return scheduler.collect(player, now, 45_000, true, Language.EN_US);
    }

    private static Tip.Policy scene(String id) {
        return new Tip.Policy(id, id, 0, Duration.ofDays(1), Duration.ZERO, Duration.ofSeconds(20));
    }

    private static Optional<TipContent> content(String situation) {
        return Optional.of(new TipContent(situation, Component.text(situation)));
    }
}
