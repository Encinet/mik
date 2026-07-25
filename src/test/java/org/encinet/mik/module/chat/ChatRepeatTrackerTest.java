package org.encinet.mik.module.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRepeatTrackerTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void marksTheSecondConsecutivePublicMessageWhenAnotherPlayerSaysTheSameThing() {
        ChatRepeatTracker tracker = new ChatRepeatTracker();

        assertFalse(tracker.recordPublic(ALICE, "hello"));
        assertTrue(tracker.recordPublic(BOB, "hello"));
    }

    @Test
    void requiresAnExactlyMatchingMessageFromAnotherPlayer() {
        ChatRepeatTracker tracker = new ChatRepeatTracker();

        assertFalse(tracker.recordPublic(ALICE, "hello"));
        assertFalse(tracker.recordPublic(ALICE, "hello"));
        assertFalse(tracker.recordPublic(BOB, "Hello"));
        assertFalse(tracker.recordPublic(ALICE, "hello"));
    }

    @Test
    void anInterveningMessageBreaksTheRepeatChain() {
        ChatRepeatTracker tracker = new ChatRepeatTracker();

        assertFalse(tracker.recordPublic(ALICE, "same"));
        assertFalse(tracker.recordPublic(BOB, "different"));
        assertFalse(tracker.recordPublic(ALICE, "same"));
    }

    @Test
    void keepsPublicStaffAndPrivateConversationsIndependent() {
        ChatRepeatTracker tracker = new ChatRepeatTracker();

        assertFalse(tracker.recordPublic(ALICE, "same"));
        assertFalse(tracker.recordStaff(BOB, "same"));
        assertFalse(tracker.recordPrivate(ALICE, BOB, "same"));
        assertTrue(tracker.recordPrivate(BOB, ALICE, "same"));
    }
}
