package org.encinet.mik.feature.community.social.platform.matrix.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Pure parsing of the small /sync subset needed by the Matrix adapter. */
public final class MatrixSyncProtocol {
    private MatrixSyncProtocol() {
    }

    public static Batch parse(JsonObject response) {
        String nextBatch = string(response, "next_batch");
        if (nextBatch.isBlank()) {
            throw new IllegalArgumentException("Matrix /sync omitted next_batch");
        }
        List<RoomBatch> rooms = new ArrayList<>();
        JsonObject roomGroups = object(response, "rooms");
        JsonObject joined = roomGroups == null ? null : object(roomGroups, "join");
        if (joined != null) {
            for (String roomId : joined.keySet()) {
                JsonObject room = object(joined, roomId);
                if (room == null || roomId.isBlank()) {
                    continue;
                }
                boolean stateAfter = room.has("state_after");
                JsonObject state = object(room, stateAfter ? "state_after" : "state");
                JsonObject timeline = object(room, "timeline");
                rooms.add(new RoomBatch(roomId,
                        stateAfter,
                        events(state == null ? null : array(state, "events")),
                        events(timeline == null ? null : array(timeline, "events"))));
            }
        }
        JsonObject left = roomGroups == null ? null : object(roomGroups, "leave");
        return new Batch(nextBatch, rooms, left == null ? List.of() : List.copyOf(left.keySet()));
    }

    private static List<Event> events(JsonArray values) {
        if (values == null) {
            return List.of();
        }
        List<Event> events = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Matrix event must be an object");
            }
            JsonObject event = value.getAsJsonObject();
            String type = string(event, "type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("Matrix event omitted type");
            }
            events.add(event(event));
        }
        return List.copyOf(events);
    }

    public static Event event(JsonObject value) {
        JsonObject content = object(value, "content");
        return new Event(string(value, "event_id"), string(value, "type"),
                string(value, "sender"), string(value, "state_key"),
                content == null ? new JsonObject() : content.deepCopy());
    }

    static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static JsonObject object(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null) return null;
        if (value.isJsonObject()) return value.getAsJsonObject();
        throw new IllegalArgumentException("Matrix " + name + " must be an object");
    }

    static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null) return null;
        if (value.isJsonArray()) return value.getAsJsonArray();
        throw new IllegalArgumentException("Matrix " + name + " must be an array");
    }

    public record Batch(String nextBatch, List<RoomBatch> rooms, List<String> leftRooms) {
        public Batch {
            rooms = List.copyOf(rooms);
            leftRooms = List.copyOf(leftRooms);
        }
    }

    public record RoomBatch(
            String roomId,
            boolean stateAfter,
            List<Event> stateEvents,
            List<Event> timelineEvents
    ) {
        public RoomBatch {
            stateEvents = List.copyOf(stateEvents);
            timelineEvents = List.copyOf(timelineEvents);
        }

        public java.util.stream.Stream<Event> stateUpdates() {
            return stateAfter ? stateEvents.stream()
                    : java.util.stream.Stream.concat(stateEvents.stream(), timelineEvents.stream());
        }
    }

    public record Event(
            String eventId,
            String type,
            String sender,
            String stateKey,
            JsonObject content
    ) {
    }
}
