package org.encinet.mik.module.governance.membership.tenure;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeratorTenurePolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Test
    void automaticRemovalUsesOldestFirstAndPreservesFourModerators() {
        List<ModeratorPresence> moderators = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> new ModeratorPresence(
                        new UUID(0, index + 1), "Moderator" + index,
                        NOW.minus(Duration.ofDays(200L - index))))
                .toList();

        List<ModeratorPresence> removals = ModeratorTenurePolicy.automaticRemovals(
                moderators, NOW);

        assertEquals(3, removals.size());
        assertEquals("Moderator0", removals.getFirst().playerName());
        assertEquals("Moderator2", removals.getLast().playerName());
        assertTrue(ModeratorTenurePolicy.automaticRemovals(
                moderators.subList(0, 4), NOW).isEmpty());
    }
}
