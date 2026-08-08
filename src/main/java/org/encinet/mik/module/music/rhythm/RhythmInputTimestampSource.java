package org.encinet.mik.module.music.rhythm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Captures rhythm inputs on PacketEvents' network thread before Bukkit moves
 * their handling to a later server tick.
 *
 * <p>The Bukkit listeners remain authoritative for cancellation and game state.
 * They only claim a matching timestamp from this short-lived queue. A missing
 * stamp is explicitly reported as coarse timing so calibration can retain a
 * conservative uncertainty floor.</p>
 */
final class RhythmInputTimestampSource implements AutoCloseable {
    static final long MAXIMUM_STAMP_AGE_NANOS = 750_000_000L;
    private static final long DUPLICATE_CLICK_BURST_NANOS = 12_000_000L;
    private static final int MAXIMUM_QUEUED_STAMPS = 48;

    private final Map<UUID, ConcurrentLinkedDeque<Stamp>> stamps =
            new ConcurrentHashMap<>();
    private final PacketListenerAbstract listener = new InputPacketListener();
    private EventManager eventManager;

    void enable() {
        if (eventManager != null) return;
        eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(listener);
    }

    TimedInput claimHotbar(UUID playerId, int slot, long fallbackNanos) {
        return claim(playerId, Kind.HOTBAR, slot, fallbackNanos);
    }

    TimedInput claimRadial(UUID playerId, long fallbackNanos) {
        return claim(playerId, Kind.RADIAL, -1, fallbackNanos);
    }

    void forget(UUID playerId) {
        stamps.remove(playerId);
    }

    @Override
    public void close() {
        if (eventManager != null) {
            eventManager.unregisterListener(listener);
            eventManager = null;
        }
        stamps.clear();
    }

    private TimedInput claim(UUID playerId, Kind kind, int value,
                             long fallbackNanos) {
        ConcurrentLinkedDeque<Stamp> queue = stamps.get(playerId);
        if (queue == null) return TimedInput.coarse(fallbackNanos);
        long oldest = fallbackNanos - MAXIMUM_STAMP_AGE_NANOS;
        queue.removeIf(stamp -> stamp.receivedAtNanos() < oldest);

        Stamp selected = null;
        for (var iterator = queue.descendingIterator(); iterator.hasNext(); ) {
            Stamp candidate = iterator.next();
            if (candidate.receivedAtNanos() > fallbackNanos
                    || candidate.kind() != kind
                    || kind == Kind.HOTBAR && candidate.value() != value) {
                continue;
            }
            selected = candidate;
            break;
        }
        if (selected == null) return TimedInput.coarse(fallbackNanos);

        Stamp claimed = selected;
        long selectedAt = claimed.receivedAtNanos();
        queue.removeIf(stamp -> stamp == claimed
                || stamp.kind() == kind
                && Math.abs(stamp.receivedAtNanos() - selectedAt)
                <= DUPLICATE_CLICK_BURST_NANOS);
        if (queue.isEmpty()) stamps.remove(playerId, queue);
        return TimedInput.precise(selectedAt);
    }

    private void record(UUID playerId, Kind kind, int value, long receivedAtNanos) {
        if (playerId == null) return;
        ConcurrentLinkedDeque<Stamp> queue = stamps.computeIfAbsent(playerId,
                ignored -> new ConcurrentLinkedDeque<>());
        queue.addLast(new Stamp(kind, value, receivedAtNanos));
        while (queue.size() > MAXIMUM_QUEUED_STAMPS) queue.pollFirst();
    }

    /** Test seam that exercises queue matching without a live PacketEvents channel. */
    void recordForTest(UUID playerId, boolean radial, int value,
                       long receivedAtNanos) {
        record(playerId, radial ? Kind.RADIAL : Kind.HOTBAR, value,
                receivedAtNanos);
    }

    record TimedInput(long receivedAtNanos, boolean coarse) {
        static TimedInput precise(long receivedAtNanos) {
            return new TimedInput(receivedAtNanos, false);
        }

        static TimedInput coarse(long receivedAtNanos) {
            return new TimedInput(receivedAtNanos, true);
        }
    }

    private enum Kind {
        HOTBAR,
        RADIAL
    }

    private record Stamp(Kind kind, int value, long receivedAtNanos) {
    }

    private final class InputPacketListener extends PacketListenerAbstract {
        private InputPacketListener() {
            super(PacketListenerPriority.LOWEST);
        }

        @Override
        public void onPacketReceive(@NonNull PacketReceiveEvent event) {
            UUID playerId = event.getUser().getUUID();
            long receivedAt = System.nanoTime();
            if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                int slot = new WrapperPlayClientHeldItemChange(event).getSlot();
                record(playerId, Kind.HOTBAR, slot, receivedAt);
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.ANIMATION
                    || event.getPacketType() == PacketType.Play.Client.USE_ITEM
                    || event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                    || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                record(playerId, Kind.RADIAL, -1, receivedAt);
            }
        }
    }
}
