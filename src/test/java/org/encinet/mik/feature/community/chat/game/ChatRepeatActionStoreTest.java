package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.model.ChatChannel;
import net.kyori.adventure.key.Key;

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
    void resolvesARepeatActionByItsCustomClickIdentifier() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.PendingAction pending = store.preparePublic("same");
        store.commit(pending);
        ChatRepeatActionStore.RepeatAction action = store.resolve(pending.identifier()).orElseThrow();

        assertEquals("same", action.message());
        assertEquals(ChatChannel.PUBLIC, action.channel());
        assertFalse(store.resolve(Key.key("other", pending.identifier().value())).isPresent());
        assertTrue(store.resolve(Key.key("mik", "menu/" + UUID.randomUUID())).isEmpty());
        assertTrue(store.resolve(Key.key("mik", "chat/repeat/not-a-uuid")).isEmpty());
        assertTrue(store.resolve(Key.key("mik", "chat/repeat/" + UUID.randomUUID())).isEmpty());
    }

    @Test
    void allowsOnlyTheOriginalPrivateConversationParticipantsToRepeat() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.PendingAction pending = store.preparePrivate("same", ALICE, BOB);
        store.commit(pending);
        ChatRepeatActionStore.RepeatAction action = store.resolve(pending.identifier()).orElseThrow();

        assertEquals(BOB, action.privateTargetFor(ALICE));
        assertEquals(ALICE, action.privateTargetFor(BOB));
        assertEquals(null, action.privateTargetFor(EVE));
    }

    @Test
    void forgetRemovesPrivateActionsForAPlayer() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.PendingAction privateAction = store.preparePrivate("same", ALICE, BOB);
        ChatRepeatActionStore.PendingAction publicAction = store.preparePublic("same");
        store.commit(privateAction);
        store.commit(publicAction);

        store.forgetPrivateActions(ALICE);

        assertTrue(store.resolve(privateAction.identifier()).isEmpty());
        assertTrue(store.resolve(publicAction.identifier()).isPresent());
    }

    @Test
    void preparedActionIsInvisibleUntilCommitted() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        ChatRepeatActionStore.PendingAction pending = store.preparePublic("same");

        assertTrue(store.resolve(pending.identifier()).isEmpty());
        store.commit(pending);
        assertTrue(store.resolve(pending.identifier()).isPresent());
    }

    @Test
    void expiredAndClearedActionsCannotBeUsed() {
        ChatRepeatActionStore store = new ChatRepeatActionStore();
        var expired = new ChatRepeatActionStore.PendingAction(Key.key("mik", "chat/repeat/" + UUID.randomUUID()),
                new ChatRepeatActionStore.RepeatAction("old", ChatChannel.PUBLIC, null, null, 0));
        store.commit(expired);
        assertTrue(store.resolve(expired.identifier()).isEmpty());

        var pending = store.prepareStaff("staff");
        store.commit(pending);
        assertEquals(ChatChannel.STAFF, store.resolve(pending.identifier()).orElseThrow().channel());
        store.clear();
        assertTrue(store.resolve(pending.identifier()).isEmpty());
    }
}
