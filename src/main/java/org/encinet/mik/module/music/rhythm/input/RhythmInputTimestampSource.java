package org.encinet.mik.module.music.rhythm.input;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.jspecify.annotations.NonNull;
import org.bukkit.util.Vector;

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
public final class RhythmInputTimestampSource implements AutoCloseable {
    static final long MAXIMUM_STAMP_AGE_NANOS = 750_000_000L;
    private static final long DUPLICATE_CLICK_BURST_NANOS = 12_000_000L;
    private static final int MAXIMUM_QUEUED_STAMPS = 48;

    private final Map<UUID, ConcurrentLinkedDeque<Stamp>> stamps =
            new ConcurrentHashMap<>();
    private final Map<UUID, ViewStamp> views = new ConcurrentHashMap<>();
    private final PacketListenerAbstract listener = new InputPacketListener();
    private EventManager eventManager;

    public void enable() {
        if (eventManager != null) return;
        eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(listener);
    }

    public TimedInput claimHotbar(
            UUID playerId, int slot, long fallbackNanos) {
        return claim(playerId, Kind.HOTBAR, slot, fallbackNanos);
    }

    public TimedInput claimPointer(UUID playerId, long fallbackNanos) {
        return claim(playerId, Kind.POINTER, -1, fallbackNanos);
    }

    public void forget(UUID playerId) {
        stamps.remove(playerId);
        views.remove(playerId);
    }

    public void rememberView(
            UUID playerId, float yaw, float pitch, long receivedAtNanos) {
        if (playerId == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
        views.put(playerId, new ViewStamp(yaw, pitch, receivedAtNanos));
    }

    @Override
    public void close() {
        if (eventManager != null) {
            eventManager.unregisterListener(listener);
            eventManager = null;
        }
        stamps.clear();
        views.clear();
    }

    private TimedInput claim(UUID playerId, Kind kind, int value,
                             long fallbackNanos) {
        ConcurrentLinkedDeque<Stamp> queue = stamps.get(playerId);
        if (queue == null) return TimedInput.coarse(fallbackNanos);
        queue.removeIf(stamp -> elapsed(fallbackNanos,
                stamp.receivedAtNanos()) > MAXIMUM_STAMP_AGE_NANOS);

        Stamp selected = null;
        for (var iterator = queue.descendingIterator(); iterator.hasNext(); ) {
            Stamp candidate = iterator.next();
            if (elapsed(fallbackNanos, candidate.receivedAtNanos()) < 0L
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
                && distance(stamp.receivedAtNanos(), selectedAt)
                <= DUPLICATE_CLICK_BURST_NANOS);
        if (queue.isEmpty()) stamps.remove(playerId, queue);
        return TimedInput.precise(selectedAt, selected.yaw(), selected.pitch());
    }

    private static long elapsed(long nowNanos, long earlierNanos) {
        return nowNanos - earlierNanos;
    }

    private static long distance(long firstNanos, long secondNanos) {
        long difference = firstNanos - secondNanos;
        return difference == Long.MIN_VALUE
                ? Long.MAX_VALUE : Math.abs(difference);
    }

    private void record(UUID playerId, Kind kind, int value, long receivedAtNanos) {
        if (playerId == null) return;
        ConcurrentLinkedDeque<Stamp> queue = stamps.computeIfAbsent(playerId,
                ignored -> new ConcurrentLinkedDeque<>());
        ViewStamp view = kind == Kind.POINTER ? views.get(playerId) : null;
        queue.addLast(new Stamp(kind, value, receivedAtNanos,
                view == null ? null : view.yaw(),
                view == null ? null : view.pitch()));
        while (queue.size() > MAXIMUM_QUEUED_STAMPS) queue.pollFirst();
    }

    /** Test seam that exercises queue matching without a live PacketEvents channel. */
    void recordForTest(UUID playerId, boolean pointer, int value,
                       long receivedAtNanos) {
        record(playerId, pointer ? Kind.POINTER : Kind.HOTBAR, value,
                receivedAtNanos);
    }

    void recordViewForTest(UUID playerId, float yaw, float pitch,
                           long receivedAtNanos) {
        rememberView(playerId, yaw, pitch, receivedAtNanos);
    }

    public record TimedInput(long receivedAtNanos, boolean coarse,
                             Float yaw, Float pitch) {
        static TimedInput precise(long receivedAtNanos, Float yaw, Float pitch) {
            return new TimedInput(receivedAtNanos, false, yaw, pitch);
        }

        static TimedInput coarse(long receivedAtNanos) {
            return new TimedInput(receivedAtNanos, true, null, null);
        }

        public boolean hasView() {
            return yaw != null && pitch != null;
        }

        public java.util.Optional<Vector> viewDirection() {
            return hasView() ? java.util.Optional.of(
                    RhythmWorldAim.viewDirection(yaw, pitch))
                    : java.util.Optional.empty();
        }
    }

    private enum Kind {
        HOTBAR,
        POINTER
    }

    private record Stamp(Kind kind, int value, long receivedAtNanos,
                         Float yaw, Float pitch) {
    }

    private record ViewStamp(float yaw, float pitch, long receivedAtNanos) {
    }

    private final class InputPacketListener extends PacketListenerAbstract {
        private InputPacketListener() {
            super(PacketListenerPriority.LOWEST);
        }

        @Override
        public void onPacketReceive(@NonNull PacketReceiveEvent event) {
            UUID playerId = event.getUser().getUUID();
            long receivedAt = System.nanoTime();
            if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
                WrapperPlayClientPlayerFlying movement =
                        new WrapperPlayClientPlayerFlying(event);
                if (movement.hasRotationChanged()) {
                    rememberView(playerId, movement.getLocation().getYaw(),
                            movement.getLocation().getPitch(), receivedAt);
                }
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                int slot = new WrapperPlayClientHeldItemChange(event).getSlot();
                record(playerId, Kind.HOTBAR, slot, receivedAt);
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.ANIMATION
                    || event.getPacketType() == PacketType.Play.Client.USE_ITEM
                    || event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                    || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                record(playerId, Kind.POINTER, -1, receivedAt);
            }
        }
    }
}
