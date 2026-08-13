package org.encinet.mik.module.social.platform.matrix.sync;

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
                JsonObject state = object(room, "state");
                JsonObject timeline = object(room, "timeline");
                rooms.add(new RoomBatch(roomId,
                        events(state == null ? null : array(state, "events")),
                        events(timeline == null ? null : array(timeline, "events"))));
            }
        }
        return new Batch(nextBatch, rooms);
    }

    private static List<Event> events(JsonArray values) {
        if (values == null) {
            return List.of();
        }
        List<Event> events = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject event = value.getAsJsonObject();
            String type = string(event, "type");
            if (type.isBlank()) {
                continue;
            }
            JsonObject content = object(event, "content");
            events.add(new Event(string(event, "event_id"), type,
                    string(event, "sender"), string(event, "state_key"),
                    content == null ? new JsonObject() : content.deepCopy()));
        }
        return List.copyOf(events);
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
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    public record Batch(String nextBatch, List<RoomBatch> rooms) {
        public Batch {
            rooms = List.copyOf(rooms);
        }
    }

    public record RoomBatch(
            String roomId,
            List<Event> stateEvents,
            List<Event> timelineEvents
    ) {
        public RoomBatch {
            stateEvents = List.copyOf(stateEvents);
            timelineEvents = List.copyOf(timelineEvents);
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
