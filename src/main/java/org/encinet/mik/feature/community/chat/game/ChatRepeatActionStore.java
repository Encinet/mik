package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.model.ChatChannel;
import net.kyori.adventure.key.Key;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ChatRepeatActionStore {

    private static final long ACTION_LIFETIME_MILLIS = Duration.ofMinutes(10).toMillis();

    private final Map<Key, RepeatAction> actions = new ConcurrentHashMap<>();

    public PendingAction preparePublic(String message) {
        return prepare(message, ChatChannel.PUBLIC, null, null);
    }

    public PendingAction prepareStaff(String message) {
        return prepare(message, ChatChannel.STAFF, null, null);
    }

    public PendingAction preparePrivate(
            String message,
            UUID senderId,
            UUID targetId
    ) {
        return prepare(message, ChatChannel.PRIVATE, senderId, targetId);
    }

    public void commit(PendingAction pending) {
        PendingAction checked = java.util.Objects.requireNonNull(pending, "pending");
        long now = System.currentTimeMillis();
        actions.entrySet().removeIf(entry ->
                entry.getValue().expiredAtMillis() <= now);
        actions.put(checked.identifier(), checked.action());
    }

    public Optional<RepeatAction> resolve(Key identifier) {
        RepeatAction action = actions.get(identifier);
        if (action == null) {
            return Optional.empty();
        }
        if (action.expiredAtMillis() > System.currentTimeMillis()) {
            return Optional.of(action);
        }
        actions.remove(identifier, action);
        return Optional.empty();
    }

    public void forgetPrivateActions(UUID playerId) {
        actions.entrySet().removeIf(entry -> entry.getValue().includesPrivateParticipant(playerId));
    }

    public void clear() {
        actions.clear();
    }

    private PendingAction prepare(
            String message,
            ChatChannel channel,
            UUID senderId,
            UUID targetId
    ) {
        long now = System.currentTimeMillis();
        Key identifier = Key.key("mik", "chat/repeat/" + UUID.randomUUID());
        return new PendingAction(identifier, new RepeatAction(
                message, channel, senderId, targetId,
                now + ACTION_LIFETIME_MILLIS));
    }

    record PendingAction(Key identifier, RepeatAction action) {
        PendingAction {
            java.util.Objects.requireNonNull(identifier, "identifier");
            java.util.Objects.requireNonNull(action, "action");
        }
    }

    record RepeatAction(String message, ChatChannel channel, UUID senderId, UUID targetId, long expiredAtMillis) {

        UUID privateTargetFor(UUID playerId) {
            if (channel != ChatChannel.PRIVATE) {
                return null;
            }
            if (senderId.equals(playerId)) {
                return targetId;
            }
            if (targetId.equals(playerId)) {
                return senderId;
            }
            return null;
        }

        boolean includesPrivateParticipant(UUID playerId) {
            return channel == ChatChannel.PRIVATE && (senderId.equals(playerId) || targetId.equals(playerId));
        }
    }
}
