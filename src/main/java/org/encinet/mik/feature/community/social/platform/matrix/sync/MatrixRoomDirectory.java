package org.encinet.mik.feature.community.social.platform.matrix.sync;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** One room policy for plaintext, encrypted delivery, Space exclusions and disconnected generations. */
public final class MatrixRoomDirectory implements AutoCloseable {
    public enum Security { UNAVAILABLE, PLAINTEXT, ENCRYPTED }
    private final Set<String> joined = new HashSet<>();
    private final Set<String> encrypted = new HashSet<>();
    private final Set<String> spaces = new HashSet<>();
    private boolean synchronizedWithServer;
    private boolean closed;
    private CompletableFuture<Void> cryptoSync = CompletableFuture.completedFuture(null);

    public synchronized void apply(MatrixSyncProtocol.Batch batch) {
        if (closed) return;
        joined.removeAll(batch.leftRooms());
        for (MatrixSyncProtocol.RoomBatch room : batch.rooms()) {
            joined.add(room.roomId());
            if (room.stateUpdates()
                    .anyMatch(event -> "m.room.encryption".equals(event.type()))) {
                // Encryption cannot be disabled, including by redacting its state event.
                encrypted.add(room.roomId());
            }
            if (room.stateUpdates()
                    .anyMatch(event -> "m.room.create".equals(event.type())
                            && "m.space".equals(MatrixSyncProtocol.string(event.content(), "type")))) {
                spaces.add(room.roomId());
            }
        }
        synchronizedWithServer = true;
    }

    public synchronized boolean allowsPlaintext(String roomId) {
        return security(roomId) == Security.PLAINTEXT;
    }

    public synchronized Security security(String roomId) {
        if (closed || !synchronizedWithServer || !joined.contains(roomId) || spaces.contains(roomId)) return Security.UNAVAILABLE;
        return encrypted.contains(roomId) ? Security.ENCRYPTED : Security.PLAINTEXT;
    }

    public synchronized boolean allowsMessages(String roomId) {
        return security(roomId) != Security.UNAVAILABLE;
    }

    public synchronized boolean joinedMessageRoom(String roomId) {
        return joined.contains(roomId) && !spaces.contains(roomId);
    }

    public synchronized boolean encryptedMessageRoom(String roomId) {
        return joinedMessageRoom(roomId) && encrypted.contains(roomId);
    }

    public synchronized void beginCryptoSync() {
        if (!closed && cryptoSync.isDone()) cryptoSync = new CompletableFuture<>();
    }

    public synchronized CompletableFuture<Security> access(String roomId) {
        return cryptoSync.thenApply(_ -> security(roomId));
    }

    public void endCryptoSync() {
        CompletableFuture<Void> completed;
        synchronized (this) { completed = cryptoSync; }
        completed.complete(null);
    }

    public void suspend() {
        synchronized (this) { synchronizedWithServer = false; }
        endCryptoSync();
    }

    public void clear() {
        synchronized (this) {
            joined.clear();
            encrypted.clear();
            spaces.clear();
            synchronizedWithServer = false;
        }
        endCryptoSync();
    }

    @Override
    public void close() {
        synchronized (this) { closed = true; }
        clear();
    }
}
