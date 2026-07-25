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
        return create(message, ChatChannel.PUBLIC, null, null);
    }

    public String createStaff(String message) {
        return create(message, ChatChannel.STAFF, null, null);
    }

    public String createPrivate(String message, UUID senderId, UUID targetId) {
        return create(message, ChatChannel.PRIVATE, senderId, targetId);
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

    private String create(String message, ChatChannel channel, UUID senderId, UUID targetId) {
        long now = System.currentTimeMillis();
        actions.entrySet().removeIf(entry -> entry.getValue().expiredAtMillis() <= now);

        UUID actionId = UUID.randomUUID();
        actions.put(actionId, new RepeatAction(message, channel, senderId, targetId, now + ACTION_LIFETIME_MILLIS));
        return actionId.toString();
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
