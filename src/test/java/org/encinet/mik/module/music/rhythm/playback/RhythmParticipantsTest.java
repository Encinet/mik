package org.encinet.mik.module.music.rhythm.playback;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmParticipantsTest {

    @Test
    void lastActualParticipantReturnsTransportToWaiting() {
        RhythmParticipants participants = new RhythmParticipants();
        AtomicInteger returnsToWaiting = new AtomicInteger();
        RhythmParticipants.Participation first = participants.acquire(
                UUID.randomUUID(), returnsToWaiting::incrementAndGet);
        RhythmParticipants.Participation second = participants.acquire(
                UUID.randomUUID(), returnsToWaiting::incrementAndGet);

        assertTrue(first.first());
        assertFalse(second.first());
        assertEquals(2, participants.size());

        first.close();
        assertEquals(0, returnsToWaiting.get());
        second.close();
        second.close();

        assertTrue(participants.isEmpty());
        assertEquals(1, returnsToWaiting.get());
    }

    @Test
    void staleParticipationCannotRemoveNewGameForSamePlayer() {
        RhythmParticipants participants = new RhythmParticipants();
        AtomicInteger returnsToWaiting = new AtomicInteger();
        UUID playerId = UUID.randomUUID();
        RhythmParticipants.Participation stale = participants.acquire(
                playerId, returnsToWaiting::incrementAndGet);
        RhythmParticipants.Participation current = participants.acquire(
                playerId, returnsToWaiting::incrementAndGet);

        stale.close();
        assertEquals(1, participants.size());
        assertEquals(0, returnsToWaiting.get());

        current.close();
        assertTrue(participants.isEmpty());
        assertEquals(1, returnsToWaiting.get());
    }
}
