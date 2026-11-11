package org.encinet.mik.feature.community.social.platform.matrix;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixEncryption;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Keeps transport and membership tests independent of a device; crypto has real SDK integration tests. */
public final class MatrixTestSupport {
    private MatrixTestSupport() { }

    public static JsonObject joinedRoom(Map<String, String> members) {
        var events = new JsonArray();
        members.forEach((userId, displayName) -> {
            var event = new JsonObject();
            event.addProperty("type", "m.room.member");
            event.addProperty("state_key", userId);
            var content = new JsonObject();
            content.addProperty("membership", "join");
            content.addProperty("displayname", displayName);
            event.add("content", content);
            events.add(event);
        });
        var state = new JsonObject();
        state.add("events", events);
        var room = new JsonObject();
        room.add("state_after", state);
        var timeline = new JsonObject();
        timeline.add("events", new JsonArray());
        room.add("timeline", timeline);
        return room;
    }

    public static MatrixEncryption plaintextDevice() {
        return new MatrixEncryption() {
            @Override public void initialize(String userId) { }
            @Override public void receiveSync(JsonObject response, boolean fullState) { }
            @Override public Optional<String> cursor() { return Optional.empty(); }
            @Override public void commit(String nextBatch) { }
            @Override public void resetCursor() { }
            @Override public void disconnected() { }
            @Override public CompletableFuture<JsonObject> encrypt(String roomId, JsonObject content) {
                return CompletableFuture.failedFuture(new IllegalStateException("This test device cannot encrypt"));
            }
            @Override public CompletableFuture<JsonObject> decrypt(String roomId, String eventId) {
                return CompletableFuture.failedFuture(new IllegalStateException("This test device cannot decrypt"));
            }
            @Override public CompletableFuture<org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult> manage(String action, List<String> arguments) {
                return CompletableFuture.failedFuture(new IllegalStateException("This test has no device management"));
            }
            @Override public void close() { }
        };
    }
}
