package org.encinet.mik.module.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRepeatActionStoreTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void resolvesARepeatActionByItsOpaqueToken() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();

        ChatRepeatActionStore.RepeatAction action = store.resolve(store.createPublic("same")).orElseThrow();

        assertEquals("same", action.message());
        assertEquals(ChatChannel.PUBLIC, action.channel());
        assertFalse(store.resolve("not-a-token").isPresent());
    }

    @Test
    void allowsOnlyTheOriginalPrivateConversationParticipantsToRepeat() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.RepeatAction action = store.resolve(store.createPrivate("same", ALICE, BOB)).orElseThrow();

        assertEquals(BOB, action.privateTargetFor(ALICE));
        assertEquals(ALICE, action.privateTargetFor(BOB));
        assertEquals(null, action.privateTargetFor(EVE));
    }

    @Test
    void forgetRemovesPrivateActionsForAPlayer() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        String privateToken = store.createPrivate("same", ALICE, BOB);
        String publicToken = store.createPublic("same");

        store.forgetPrivateActions(ALICE);

        assertTrue(store.resolve(privateToken).isEmpty());
        assertTrue(store.resolve(publicToken).isPresent());
    }

    @Test
    void preparedActionIsInvisibleUntilCommitted() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.PendingAction pending = store.preparePublic("same");

        assertTrue(store.resolve(pending.token()).isEmpty());
        store.commit(pending);
        assertTrue(store.resolve(pending.token()).isPresent());
    }
}
