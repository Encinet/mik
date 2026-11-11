package org.encinet.mik.feature.community.social.platform.matrix.sync;

import org.encinet.mik.feature.community.social.platform.matrix.message.MatrixContentDecoder;
import org.encinet.mik.feature.community.social.platform.matrix.message.MatrixContentProjector;
import org.encinet.mik.feature.community.social.platform.matrix.message.MatrixMessageContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maintains bounded event/member context while converting /sync batches into text messages. */
public final class MatrixSyncProcessor {
    private static final int MAXIMUM_EVENT_SENDERS = 8_192;

    private final Map<String, String> eventSenders = boundedMap(MAXIMUM_EVENT_SENDERS);
    private final MatrixMemberDirectory members;

    public MatrixSyncProcessor() {
        this(new MatrixMemberDirectory());
    }

    public MatrixSyncProcessor(MatrixMemberDirectory members) {
        this.members = java.util.Objects.requireNonNull(members, "members");
    }

    public List<MatrixRoomMessage> process(MatrixSyncProtocol.Batch batch, boolean completeState) {
        for (String roomId : batch.leftRooms()) {
            members.removeRoom(roomId);
            eventSenders.keySet().removeIf(key -> key.startsWith(roomId + '\u0000'));
        }
        List<MatrixRoomMessage> messages = new ArrayList<>();
        for (MatrixSyncProtocol.RoomBatch room : batch.rooms()) {
            if (completeState) members.replaceJoined(room.roomId(), Map.of());
            // state_after is the authoritative current view; state is the view before the timeline.
            room.stateEvents().forEach(event -> members.applyMember(room.roomId(), event));
            for (MatrixSyncProtocol.Event event : room.timelineEvents()) {
                if (!room.stateAfter()) members.applyMember(room.roomId(), event);
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

    public Optional<MatrixRoomMessage> message(
            String roomId,
            MatrixSyncProtocol.Event event
    ) {
        if (event.eventId().isBlank() || event.sender().isBlank()) {
            return Optional.empty();
        }
        MatrixMessageContent content = MatrixContentDecoder.decode(event.type(), event.content())
                .orElse(null);
        if (content == null || content.relation().replacement()) {
            return Optional.empty();
        }
        Map<String, String> displayNames = new LinkedHashMap<>();
        content.mentionedUserIds().forEach(userId -> displayNames.put(userId,
                members.member(roomId, userId).displayName()));
        MatrixContentProjector.Projection parsed = MatrixContentProjector.project(content, displayNames);
        if (parsed.body().isBlank()) {
            return Optional.empty();
        }

        Optional<String> repliedEventId = content.relation().explicitReplyEventId();
        Optional<MatrixRoomMessage.MatrixMember> repliedAuthor = repliedEventId
                .flatMap(eventId -> Optional.ofNullable(
                        eventSenders.get(eventKey(roomId, eventId))))
                .map(userId -> members.member(roomId, userId));
        List<MatrixRoomMessage.MatrixMention> mentionSpans = parsed.mentions()
                .stream()
                .map(mention -> new MatrixRoomMessage.MatrixMention(
                        members.member(roomId, mention.userId()), mention.start(), mention.end()))
                .toList();
        return Optional.of(new MatrixRoomMessage(event.eventId(), roomId,
                members.member(roomId, event.sender()), parsed.body(), parsed.commandBody(), mentionSpans,
                repliedAuthor, content.relation().threadRootEventId(), parsed.placeholders()));
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
