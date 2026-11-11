package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixApiErrors;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Bounded, ordered room delivery while missing keys arrive through subsequent /sync calls. */
public final class MatrixEncryptedInbox implements AutoCloseable {
    private static final int MAX_PENDING = 512;
    private static final int MAX_REMEMBERED = 8_192;
    private final MatrixEncryption encryption;
    private final BiFunction<String, MatrixSyncProtocol.Event, Optional<MatrixRoomMessage>> projector;
    private final Function<MatrixRoomMessage, SocialEventSink.Acceptance> delivery;
    private final BooleanSupplier ready;
    private final long retryDelay;
    private final Logger logger;
    private final Map<String, RoomQueue> rooms = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Map<String, Boolean> remembered = new LinkedHashMap<>();
    private boolean closed;

    public MatrixEncryptedInbox(MatrixEncryption encryption,
                                BiFunction<String, MatrixSyncProtocol.Event, Optional<MatrixRoomMessage>> projector,
                                Function<MatrixRoomMessage, SocialEventSink.Acceptance> delivery,
                                BooleanSupplier ready, long retryDelay, Logger logger) {
        this.encryption = encryption;
        this.projector = projector;
        this.delivery = delivery;
        this.ready = ready;
        this.retryDelay = retryDelay;
        this.logger = logger;
    }

    public synchronized boolean offer(String roomId, String eventId) {
        String key = key(roomId, eventId);
        if (closed || remembered.containsKey(key) || pending.contains(key)) return true;
        if (pending.size() >= MAX_PENDING) return false;
        pending.add(key);
        RoomQueue queue = rooms.computeIfAbsent(roomId, RoomQueue::new);
        queue.events.addLast(eventId);
        if (queue.worker == null) {
            queue.worker = Thread.ofVirtual().name("mik-matrix-decrypt").unstarted(() -> drain(queue));
            queue.worker.start();
        }
        return true;
    }

    public synchronized void retainRooms(Predicate<String> retained) {
        rooms.values().removeIf(queue -> {
            if (retained.test(queue.roomId)) return false;
            queue.worker.interrupt();
            pending.removeIf(value -> value.startsWith(queue.roomId + '\u0000'));
            return true;
        });
    }

    private void drain(RoomQueue queue) {
        while (true) {
            String eventId;
            synchronized (this) {
                if (closed || rooms.get(queue.roomId) != queue) return;
                eventId = queue.events.pollFirst();
                if (eventId == null) {
                    rooms.remove(queue.roomId);
                    return;
                }
            }
            CompletableFuture<JsonObject> decrypting = null;
            try {
                decrypting = encryption.decrypt(queue.roomId, eventId);
                JsonObject event = decrypting.get();
                while (!ready.getAsBoolean()) Thread.sleep(retryDelay);
                synchronized (this) {
                    if (closed || rooms.get(queue.roomId) != queue || Thread.currentThread().isInterrupted()) return;
                }
                MatrixSyncProtocol.Event decrypted = MatrixSyncProtocol.event(event);
                if (!eventId.equals(decrypted.eventId())) throw new IllegalStateException("Matrix decrypted event ID changed");
                Optional<MatrixRoomMessage> message = projector.apply(queue.roomId, decrypted);
                if (message.isPresent()) {
                    while (delivery.apply(message.get()) == SocialEventSink.Acceptance.RETRY_LATER) {
                        Thread.sleep(retryDelay);
                    }
                }
            } catch (InterruptedException error) {
                if (decrypting != null) decrypting.cancel(true);
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | RuntimeException error) {
                synchronized (this) {
                    if (!closed) logger.warning("Could not deliver encrypted Matrix event " + eventId + ": " + MatrixApiErrors.describe(error));
                }
            } finally {
                synchronized (this) {
                    String key = key(queue.roomId, eventId);
                    if (!closed && rooms.get(queue.roomId) == queue) {
                        pending.remove(key);
                        remembered.put(key, Boolean.TRUE);
                        if (remembered.size() > MAX_REMEMBERED) remembered.remove(remembered.keySet().iterator().next());
                    }
                }
            }
        }
    }

    private static String key(String roomId, String eventId) {
        return roomId + '\u0000' + eventId;
    }

    @Override
    public synchronized void close() {
        closed = true;
        rooms.values().forEach(queue -> queue.worker.interrupt());
        rooms.clear();
        pending.clear();
        remembered.clear();
    }

    private static final class RoomQueue {
        private final String roomId;
        private final ArrayDeque<String> events = new ArrayDeque<>();
        private Thread worker;

        private RoomQueue(String roomId) {
            this.roomId = roomId;
        }
    }
}
