package org.encinet.mik.module.chat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class ChatRepeatTracker {

    private final Map<SharedChannel, LastMessage> sharedMessages = new EnumMap<>(SharedChannel.class);
    private final Map<PrivateConversation, LastMessage> privateMessages = new HashMap<>();

    public synchronized boolean wouldRepeatPublic(UUID senderId, String message) {
        return wouldRepeat(sharedMessages, SharedChannel.PUBLIC, senderId, message);
    }

    public synchronized boolean wouldRepeatStaff(UUID senderId, String message) {
        return wouldRepeat(sharedMessages, SharedChannel.STAFF, senderId, message);
    }

    public synchronized boolean wouldRepeatPrivate(
            UUID senderId,
            UUID targetId,
            String message
    ) {
        return wouldRepeat(privateMessages,
                PrivateConversation.of(senderId, targetId), senderId, message);
    }

    public synchronized boolean recordPublic(UUID senderId, String message) {
        return record(sharedMessages, SharedChannel.PUBLIC, senderId, message);
    }

    public synchronized boolean recordStaff(UUID senderId, String message) {
        return record(sharedMessages, SharedChannel.STAFF, senderId, message);
    }

    public synchronized boolean recordPrivate(UUID senderId, UUID targetId, String message) {
        return record(privateMessages, PrivateConversation.of(senderId, targetId), senderId, message);
    }

    public synchronized void forget(UUID playerId) {
        sharedMessages.entrySet().removeIf(entry -> entry.getValue().senderId().equals(playerId));
        privateMessages.entrySet().removeIf(entry -> entry.getKey().includes(playerId));
    }

    private <K> boolean record(Map<K, LastMessage> messages, K channel, UUID senderId, String message) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(message, "message");

        boolean repeated = wouldRepeat(messages, channel, senderId, message);
        messages.put(channel, new LastMessage(senderId, message));
        return repeated;
    }

    private <K> boolean wouldRepeat(
            Map<K, LastMessage> messages,
            K channel,
            UUID senderId,
            String message
    ) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(message, "message");
        LastMessage previous = messages.get(channel);
        return previous != null
                && !previous.senderId().equals(senderId)
                && previous.message().equals(message);
    }

    private enum SharedChannel {
        PUBLIC,
        STAFF
    }

    private record LastMessage(UUID senderId, String message) {
    }

    private record PrivateConversation(UUID firstPlayerId, UUID secondPlayerId) {

        static PrivateConversation of(UUID firstPlayerId, UUID secondPlayerId) {
            if (firstPlayerId.compareTo(secondPlayerId) <= 0) {
                return new PrivateConversation(firstPlayerId, secondPlayerId);
            }
            return new PrivateConversation(secondPlayerId, firstPlayerId);
        }

        boolean includes(UUID playerId) {
            return firstPlayerId.equals(playerId) || secondPlayerId.equals(playerId);
        }
    }
}
