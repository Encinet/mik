package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Device-scoped encryption and key management, independent of chat projection and Bukkit. */
public interface MatrixEncryption extends AutoCloseable {
    void initialize(String userId) throws InterruptedException;

    void receiveSync(JsonObject response, boolean fullState) throws InterruptedException;

    Optional<String> cursor();

    void commit(String nextBatch) throws InterruptedException;

    void resetCursor() throws InterruptedException;

    void disconnected();

    CompletableFuture<JsonObject> encrypt(String roomId, JsonObject content);

    CompletableFuture<JsonObject> decrypt(String roomId, String eventId);

    CompletableFuture<MatrixCryptoResult> manage(String action, List<String> arguments);

    @Override
    void close();
}
