package org.encinet.mik.feature.server.monitor;

import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonitorSchedulerTest {
    private static final long SLICE = Duration.ofSeconds(15).toNanos();

    @Test
    void weightsBalanceActualCoverageWithoutStarvingLowWeightPlayers() {
        MonitorScheduler scheduler = new MonitorScheduler();
        List<MonitorScheduler.Candidate> candidates = new ArrayList<>();
        for (PlayerRole role : PlayerRole.values()) {
            candidates.add(candidate(role));
            candidates.add(candidate(role));
        }
        Map<UUID, Integer> counts = new HashMap<>();
        Map<UUID, Integer> lastSeen = new HashMap<>();
        UUID observer = UUID.randomUUID();
        UUID previous = null;
        for (int slice = 0; slice < 3000; slice++) {
            long now = slice * SLICE;
            scheduler.refresh(candidates, now);
            UUID target = scheduler.select(observer, candidates, now);
            assertNotEquals(previous, target);
            counts.merge(target, 1, Integer::sum);
            Integer last = lastSeen.put(target, slice);
            assertTrue(slice - (last == null ? 0 : last) <= 40, "even custodians must be covered regularly");
            MonitorScheduler.Candidate selected = candidates.stream().filter(c -> c.id().equals(target)).findFirst().orElseThrow();
            scheduler.watching(observer, selected, true, now);
            previous = target;
        }
        for (MonitorScheduler.Candidate candidate : candidates) {
            int expected = switch (candidate.role()) {
                case DEFAULT -> 800;
                case MEMBER -> 400;
                case MODERATOR -> 200;
                case CUSTODIAN -> 100;
            };
            assertEquals(expected, counts.get(candidate.id()), 2);
        }
    }

    @Test
    void manualWatchingCountsTimeOnceAndFailedCamerasDoNotConsumeCoverage() {
        var a = candidate(PlayerRole.MEMBER);
        var b = candidate(PlayerRole.MEMBER);
        List<MonitorScheduler.Candidate> candidates = List.of(a, b);
        UUID manual = UUID.randomUUID();
        UUID duplicate = UUID.randomUUID();
        UUID automatic = UUID.randomUUID();
        MonitorScheduler scheduler = new MonitorScheduler();
        scheduler.refresh(candidates, 0);
        scheduler.watching(manual, a, true, 0);
        scheduler.watching(duplicate, a, true, 0);
        scheduler.release(manual, SLICE);
        scheduler.release(duplicate, SLICE);
        assertEquals(b.id(), scheduler.select(automatic, candidates, SLICE));
        scheduler.watching(automatic, b, false, SLICE);
        scheduler.release(automatic, SLICE * 10);
        assertEquals(b.id(), scheduler.select(automatic, candidates, SLICE * 10));
        scheduler.watching(automatic, b, true, SLICE * 10);
        scheduler.release(automatic, SLICE * 11);
        // Both players have now received exactly one slice; duplicate cameras did not double A's service.
        assertEquals(a.id(), scheduler.select(automatic, candidates, SLICE * 11));
    }

    @Test
    void occupiedTargetsAreReservedEvenBeforeTheCameraSucceeds() {
        MonitorScheduler scheduler = new MonitorScheduler();
        var a = candidate(PlayerRole.MEMBER);
        var b = candidate(PlayerRole.MEMBER);
        var candidates = List.of(a, b);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        scheduler.refresh(candidates, 0);
        assertEquals(a.id(), scheduler.select(first, candidates, 0));
        assertEquals(b.id(), scheduler.select(second, candidates, 0));
        assertNull(scheduler.select(third, candidates, 0));
        scheduler.release(first, 0);
        assertEquals(a.id(), scheduler.select(third, candidates, 0));
        scheduler.clear();
        assertEquals(a.id(), scheduler.select(first, List.of(a), 0));
    }

    @Test
    void roleChangesApplyToSubsequentWatchingTime() {
        MonitorScheduler scheduler = new MonitorScheduler();
        var a = candidate(PlayerRole.DEFAULT);
        var b = candidate(PlayerRole.MEMBER);
        UUID observer = UUID.randomUUID();
        scheduler.refresh(List.of(a, b), 0);
        scheduler.watching(observer, a, true, 0);
        var promoted = new MonitorScheduler.Candidate(a.id(), PlayerRole.CUSTODIAN);
        scheduler.watching(observer, promoted, true, SLICE * 8);
        scheduler.watching(observer, b, true, SLICE * 9);
        scheduler.release(observer, SLICE * 15);
        // A received 8/8 + 1/1 units, B received 6/4: B is due next.
        assertEquals(b.id(), scheduler.select(observer, List.of(promoted, b), SLICE * 15));
    }

    @Test
    void shortAbsencesPreserveServiceButUnusedHistoryExpires() {
        MonitorScheduler scheduler = new MonitorScheduler();
        var a = candidate(PlayerRole.MEMBER);
        var b = candidate(PlayerRole.MEMBER);
        UUID observer = UUID.randomUUID();
        scheduler.refresh(List.of(a, b), 0);
        scheduler.watching(observer, a, true, 0);
        scheduler.release(observer, SLICE);
        scheduler.refresh(List.of(b), SLICE);
        scheduler.refresh(List.of(a, b), SLICE * 2);
        assertEquals(b.id(), scheduler.select(observer, List.of(a, b), SLICE * 2));
        scheduler.release(observer, SLICE * 2);
        scheduler.refresh(List.of(b), SLICE * 2);
        long later = Duration.ofMinutes(31).toNanos();
        scheduler.refresh(List.of(b), later);
        scheduler.refresh(List.of(a, b), later);
        assertEquals(a.id(), scheduler.select(observer, List.of(a, b), later));
    }

    @Test
    void historyAlsoExpiresWhenNoMonitorWasRunning() {
        MonitorScheduler scheduler = new MonitorScheduler();
        var a = candidate(PlayerRole.MEMBER);
        var b = candidate(PlayerRole.MEMBER);
        UUID observer = UUID.randomUUID();
        scheduler.refresh(List.of(a, b), 0);
        scheduler.watching(observer, a, true, 0);
        scheduler.release(observer, SLICE);
        long later = Duration.ofMinutes(31).toNanos();
        scheduler.refresh(List.of(a, b), later);
        assertEquals(a.id(), scheduler.select(observer, List.of(a, b), later));
    }

    @Test
    void returningPlayersAndNewPlayersJoinAtCurrentServiceInsteadOfBankingOfflineDebt() {
        MonitorScheduler scheduler = new MonitorScheduler();
        var a = candidate(PlayerRole.MEMBER);
        var b = candidate(PlayerRole.MEMBER);
        UUID observer = UUID.randomUUID();
        scheduler.refresh(List.of(a, b), 0);
        scheduler.refresh(List.of(a), 0);
        scheduler.watching(observer, a, true, 0);
        scheduler.refresh(List.of(a), SLICE * 100);
        scheduler.release(observer, SLICE * 100);
        var newcomer = candidate(PlayerRole.MEMBER);
        var candidates = List.of(a, b, newcomer);
        scheduler.refresh(candidates, SLICE * 100);
        for (int i = 0; i < 12; i++) {
            long now = SLICE * (100 + i);
            scheduler.refresh(candidates, now);
            UUID selected = scheduler.select(observer, candidates, now);
            assertEquals(candidates.get(i % 3).id(), selected);
            scheduler.watching(observer, candidates.get(i % 3), true, now);
        }
    }

    private static MonitorScheduler.Candidate candidate(PlayerRole role) {
        return new MonitorScheduler.Candidate(UUID.randomUUID(), role);
    }
}
