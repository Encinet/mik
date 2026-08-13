package org.encinet.mik.module.chat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ChatRepeatActionStore {

    private static final long ACTION_LIFETIME_MILLIS = Duration.ofMinutes(10).toMillis();

    private final Map<UUID, RepeatAction> actions = new ConcurrentHashMap<>();

    public String createPublic(String message) {
        PendingAction pending = preparePublic(message);
        commit(pending);
        return pending.token();
    }

    public String createStaff(String message) {
        PendingAction pending = prepareStaff(message);
        commit(pending);
        return pending.token();
    }

    public String createPrivate(String message, UUID senderId, UUID targetId) {
        PendingAction pending = preparePrivate(message, senderId, targetId);
        commit(pending);
        return pending.token();
    }

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
        actions.put(checked.id(), checked.action());
    }

    public Optional<RepeatAction> resolve(String token) {
        UUID actionId;
        try {
            actionId = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        RepeatAction action = actions.get(actionId);
        if (action == null) {
            return Optional.empty();
        }
        if (action.expiredAtMillis() > System.currentTimeMillis()) {
            return Optional.of(action);
        }
        actions.remove(actionId, action);
        return Optional.empty();
    }

    public void forgetPrivateActions(UUID playerId) {
        actions.entrySet().removeIf(entry -> entry.getValue().includesPrivateParticipant(playerId));
    }

    private PendingAction prepare(
            String message,
            ChatChannel channel,
            UUID senderId,
            UUID targetId
    ) {
        long now = System.currentTimeMillis();
        UUID actionId = UUID.randomUUID();
        return new PendingAction(actionId, new RepeatAction(
                message, channel, senderId, targetId,
                now + ACTION_LIFETIME_MILLIS));
    }

    record PendingAction(UUID id, RepeatAction action) {
        PendingAction {
            java.util.Objects.requireNonNull(id, "id");
            java.util.Objects.requireNonNull(action, "action");
        }

        String token() {
            return id.toString();
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
