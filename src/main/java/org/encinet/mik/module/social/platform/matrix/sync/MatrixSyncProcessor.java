package org.encinet.mik.module.social.platform.matrix.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maintains bounded event/member context while converting /sync batches into text messages. */
public final class MatrixSyncProcessor {
    private static final int MAXIMUM_EVENT_SENDERS = 8_192;
    private static final int MAXIMUM_MENTIONS = 32;

    private final Map<String, String> eventSenders = boundedMap(MAXIMUM_EVENT_SENDERS);
    private final MatrixMemberDirectory members;

    public MatrixSyncProcessor() {
        this(new MatrixMemberDirectory());
    }

    public MatrixSyncProcessor(MatrixMemberDirectory members) {
        this.members = java.util.Objects.requireNonNull(members, "members");
    }

    public List<MatrixRoomMessage> process(MatrixSyncProtocol.Batch batch) {
        List<MatrixRoomMessage> messages = new ArrayList<>();
        for (MatrixSyncProtocol.RoomBatch room : batch.rooms()) {
            room.stateEvents().forEach(event -> members.applyMember(
                    room.roomId(), event));
            for (MatrixSyncProtocol.Event event : room.timelineEvents()) {
                members.applyMember(room.roomId(), event);
                message(room.roomId(), event).ifPresent(messages::add);
                rememberEventSender(room.roomId(), event);
            }
        }
        return List.copyOf(messages);
    }

    public void clear() {
        eventSenders.clear();
        members.clear();
    }

    private Optional<MatrixRoomMessage> message(
            String roomId,
            MatrixSyncProtocol.Event event
    ) {
        if (!"m.room.message".equals(event.type())
                || event.eventId().isBlank() || event.sender().isBlank()) {
            return Optional.empty();
        }
        JsonObject content = event.content();
        if (!"m.text".equals(MatrixSyncProtocol.string(content, "msgtype"))) {
            return Optional.empty();
        }
        JsonObject relation = MatrixSyncProtocol.object(content, "m.relates_to");
        if (relation != null
                && "m.replace".equals(MatrixSyncProtocol.string(relation, "rel_type"))) {
            return Optional.empty();
        }
        List<MatrixRoomMessage.MatrixMember> mentionedMembers = mentions(
                roomId, content);
        MatrixFormattedTextParser.ParsedText parsed =
                MatrixFormattedTextParser.parse(content, mentionedMembers);
        if (parsed.body().isBlank()) {
            return Optional.empty();
        }

        Optional<String> repliedEventId = repliedEventId(relation);
        Optional<MatrixRoomMessage.MatrixMember> repliedAuthor = repliedEventId
                .flatMap(eventId -> Optional.ofNullable(
                        eventSenders.get(eventKey(roomId, eventId))))
                .map(userId -> members.member(roomId, userId));
        Optional<String> threadRoot = Optional.empty();
        if (relation != null
                && "m.thread".equals(MatrixSyncProtocol.string(relation, "rel_type"))) {
            String root = MatrixSyncProtocol.string(relation, "event_id");
            if (!root.isBlank()) {
                threadRoot = Optional.of(root);
            }
        }
        List<MatrixRoomMessage.MatrixMention> mentionSpans = parsed.mentions()
                .stream()
                .map(mention -> new MatrixRoomMessage.MatrixMention(
                        mention.member(), mention.start(), mention.end()))
                .toList();
        return Optional.of(new MatrixRoomMessage(event.eventId(), roomId,
                members.member(roomId, event.sender()), parsed.body(), mentionSpans,
                repliedAuthor, threadRoot));
    }

    private List<MatrixRoomMessage.MatrixMember> mentions(
            String roomId,
            JsonObject content
    ) {
        JsonObject mentionData = MatrixSyncProtocol.object(content, "m.mentions");
        JsonArray userIds = mentionData == null
                ? null : MatrixSyncProtocol.array(mentionData, "user_ids");
        if (userIds == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (JsonElement value : userIds) {
            if (unique.size() >= MAXIMUM_MENTIONS) {
                break;
            }
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String userId = value.getAsString();
                if (!userId.isBlank()) {
                    unique.add(userId);
                }
            }
        }
        return unique.stream().map(userId -> members.member(roomId, userId)).toList();
    }

    private Optional<String> repliedEventId(JsonObject relation) {
        JsonObject reply = relation == null
                ? null : MatrixSyncProtocol.object(relation, "m.in_reply_to");
        String eventId = reply == null
                ? "" : MatrixSyncProtocol.string(reply, "event_id");
        return eventId.isBlank() ? Optional.empty() : Optional.of(eventId);
    }

    private void rememberEventSender(String roomId, MatrixSyncProtocol.Event event) {
        if (!event.eventId().isBlank() && !event.sender().isBlank()) {
            eventSenders.put(eventKey(roomId, event.eventId()), event.sender());
        }
    }

    private static String eventKey(String roomId, String eventId) {
        return roomId + '\u0000' + eventId;
    }

    private static <K, V> Map<K, V> boundedMap(int maximumSize) {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        };
    }
}
