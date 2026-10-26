package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TipDeliveryQueueTest {
    private static final long NOW = 1_800_000_000_000L;
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<RuntimeException> errors = new ArrayList<>();
    private final Map<UUID, List<TipScheduler.Delivery>> sent = new HashMap<>();
    private final TipScheduler scheduler = new TipScheduler(errors::add, () -> 0.5, ignored -> TipAudience.MEMBER);
    private final TipDeliveryQueue queue = new TipDeliveryQueue(task -> {
        tasks.add(task);
        return () -> tasks.remove(task);
    }, this::deliver, errors::add);
    private boolean available = true;
    private Set<UUID> failing = Set.of();

    @AfterEach
    void close() {
        queue.close();
        scheduler.clear();
    }

    @Test
    void acceptedOffersMergeIntoOneMainTaskAndSendWithoutAdvancingTime() {
        Player first = player(UUID.randomUUID());
        Player second = player(UUID.randomUUID());
        for (int repeat = 0; repeat < 100; repeat++) offer(first, "first", 1);
        offer(first, "second", 1);
        offer(second, "first", 1);

        assertEquals(1, tasks.size());
        assertTrue(queue.pending(first));
        assertTrue(queue.pending(second));
        assertTrue(sent.isEmpty(), "delivery must wait until event callbacks finish on the main thread");
        runTask();
        assertEquals(List.of("first", "second"), sent.get(first.getUniqueId()).stream()
                .map(delivery -> delivery.definition().id()).toList());
        assertEquals(1, sent.get(second.getUniqueId()).size());
        assertEquals(NOW, scheduler.snapshot(first.getUniqueId()).history().get("first").sentAt());
        assertTrue(tasks.isEmpty());
        assertFalse(queue.pending(first));
        assertFalse(queue.pending(second));
        assertTrue(errors.isEmpty());
    }

    @Test
    void missedAndDisabledOpportunitiesDoNotScheduleDeliveryWork() {
        Player player = player(UUID.randomUUID());
        offer(player, "disabled", 0);
        offer(player, "missed", 0.1);

        assertTrue(tasks.isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    void aCompletedOperationIsObservedOnTheNextMainTaskWithoutASecondsLongTimer() {
        Player player = player(UUID.randomUUID());
        AtomicBoolean completed = new AtomicBoolean();
        offer(player, "result", 1, language -> {
            assertTrue(completed.get(), "the source must not be read inside the original event");
            return Optional.of(new TipContent("result", Component.text("ready")));
        });
        completed.set(true);

        runTask();
        assertEquals(1, sent.get(player.getUniqueId()).size());
    }

    @Test
    void afkAndDeadPlayersDoNotReceiveStaleAdviceWhenTheyBecomeAvailable() {
        Player player = player(UUID.randomUUID());
        available = false;
        offer(player, "context", 1);
        runTask();
        assertTrue(sent.isEmpty());
        available = true;
        assertTrue(scheduler.collectPending(player.getUniqueId(), NOW + 1000, 45_000, true, Language.EN_US).isEmpty());

        offer(player, "fresh-context", 1);
        runTask();
        assertEquals("fresh-context", sent.get(player.getUniqueId()).getFirst().definition().id());
    }

    @Test
    void disconnectsAndShutdownCancelQueuedWorkWithoutDroppingANewSession() {
        UUID id = UUID.randomUUID();
        Player oldSession = player(id);
        offer(oldSession, "context", 1);
        queue.forget(oldSession);
        scheduler.forget(id);
        assertTrue(tasks.isEmpty());

        Player replacement = player(id);
        offer(replacement, "context", 1);
        queue.forget(oldSession);
        assertEquals(1, tasks.size());
        runTask();
        assertEquals(1, sent.get(id).size());

        offer(replacement, "another-context", 1);
        Runnable late = tasks.getFirst();
        queue.close();
        assertTrue(tasks.isEmpty());
        queue.request(replacement);
        late.run();
        assertTrue(tasks.isEmpty());
        assertEquals(1, sent.get(id).size());
    }

    @Test
    void deliveryFailureForOnePlayerDoesNotStopOtherPlayers() {
        Player first = player(UUID.randomUUID());
        Player second = player(UUID.randomUUID());
        failing = Set.of(first.getUniqueId());
        offer(first, "context", 1);
        offer(second, "context", 1);

        runTask();
        assertEquals(1, errors.size());
        assertEquals(1, sent.get(second.getUniqueId()).size());
    }

    private void deliver(Player player) {
        UUID id = player.getUniqueId();
        if (failing.contains(id)) throw new IllegalStateException("delivery failed");
        var batch = scheduler.collectPending(id, NOW, 45_000, available, Language.EN_US);
        if (!batch.isEmpty()) {
            sent.put(id, batch);
            scheduler.delivered(id, batch, NOW);
        }
    }

    private void offer(Player player, String id, double chance) {
        offer(player, id, chance, language -> Optional.of(new TipContent(id, Component.text(id))));
    }

    private void offer(Player player, String id, double chance, Function<Language, Optional<TipContent>> source) {
        var policy = new Tip.Policy(id, "group", 0, Duration.ofDays(1), Duration.ZERO, Duration.ofSeconds(20));
        if (scheduler.offer(this, player.getUniqueId(), policy, NOW, chance, false, 1, Duration.ZERO, source)) {
            queue.request(player);
        }
    }

    private void runTask() { tasks.removeFirst().run(); }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new AssertionError(method.getName());
                });
    }
}
