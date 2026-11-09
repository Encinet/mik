package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuInputLease;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** View-owned inputs. Chat ingress is thread-safe; ownership and delivery stay on the server thread. */
final class MenuInputSessions {
    enum Finish { COMPLETED, CANCELLED, INVALIDATED }
    enum Delivery { NONE, LATEST, ONCE }
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    private final Map<UUID, Ticket> chatTickets = new ConcurrentHashMap<>();
    private final BiConsumer<UUID, Finish> onFinish;

    MenuInputSessions(BiConsumer<UUID, Finish> onFinish) {
        this.onFinish = onFinish;
    }

    MenuInputLease begin(UUID playerId, BooleanSupplier ownerActive) {
        return begin(playerId, ownerActive, Delivery.NONE, null, null);
    }

    MenuInputLease begin(UUID playerId, BooleanSupplier ownerActive, Runnable ended) {
        return begin(playerId, ownerActive, Delivery.NONE, null, Objects.requireNonNull(ended));
    }

    MenuInputLease beginChat(UUID playerId, BooleanSupplier ownerActive, Consumer<String> text, Runnable ended) {
        return begin(playerId, ownerActive, Delivery.LATEST, Objects.requireNonNull(text), Objects.requireNonNull(ended));
    }

    /** Claims the first sent message at ingress; subsequent chat is ordinary chat, even before the next tick. */
    MenuInputLease beginChatOnce(UUID playerId, BooleanSupplier ownerActive, Consumer<String> text, Runnable ended) {
        return begin(playerId, ownerActive, Delivery.ONCE, Objects.requireNonNull(text), Objects.requireNonNull(ended));
    }

    private MenuInputLease begin(UUID playerId, BooleanSupplier ownerActive, Delivery delivery, Consumer<String> text, Runnable ended) {
        invalidate(playerId);
        Ticket ticket = new Ticket(playerId, ownerActive, delivery, text, ended);
        tickets.put(playerId, ticket);
        if (ticket.chat()) chatTickets.put(playerId, ticket);
        return ticket;
    }

    boolean blocking(UUID playerId) {
        Ticket ticket = tickets.get(playerId);
        return ticket != null && ticket.active() && !ticket.chat();
    }

    /** Called from chat threads; submissions and latest-value previews have bounded storage. */
    boolean offerText(UUID playerId, String raw) {
        Ticket ticket = chatTickets.get(playerId);
        // A submission claims only its first message. Other modes retain private capture
        // until the server thread publishes their end, discarding text if the view has gone.
        return ticket != null && ticket.offer(Objects.requireNonNull(raw));
    }

    /** An in-place preview update retains chat ownership; an old native response must become invalid. */
    void contentChanged(UUID playerId) {
        Ticket ticket = tickets.get(playerId);
        if (ticket != null && !ticket.chat()) ticket.finish(Finish.INVALIDATED);
    }

    void tick() { List.copyOf(tickets.values()).forEach(Ticket::poll); }
    void invalidate(UUID playerId) {
        Ticket ticket = tickets.get(playerId);
        if (ticket != null) ticket.finish(Finish.INVALIDATED);
    }
    void clear() {
        ShutdownSequence shutdown = new ShutdownSequence();
        for (UUID id : List.copyOf(tickets.keySet())) shutdown.attempt("input for " + id, () -> invalidate(id));
        shutdown.finish("menu input sessions");
    }

    private final class Ticket implements MenuInputLease {
        private final UUID playerId;
        private final BooleanSupplier ownerActive;
        private final Delivery delivery;
        private final Consumer<String> text;
        private final Runnable ended;
        private final AtomicReference<String> pending = new AtomicReference<>();
        private boolean claimed;
        private volatile boolean open = true;
        private Ticket(UUID playerId, BooleanSupplier ownerActive, Delivery delivery, Consumer<String> text, Runnable ended) {
            this.playerId = playerId;
            this.ownerActive = ownerActive;
            this.delivery = delivery;
            this.text = text;
            this.ended = ended;
        }
        private boolean chat() { return delivery != Delivery.NONE; }
        private synchronized boolean offer(String raw) {
            if (!open || delivery == Delivery.ONCE && claimed) return false;
            if (delivery == Delivery.ONCE) claimed = true;
            pending.set(raw);
            return true;
        }
        private void poll() {
            if (!active() || !chat()) return;
            String value = pending.getAndSet(null);
            if (value != null) {
                try { text.accept(value); }
                finally { if (delivery == Delivery.ONCE) finish(Finish.COMPLETED); }
            }
        }
        @Override public boolean active() {
            if (tickets.get(playerId) != this) return false;
            if (!ownerActive.getAsBoolean()) { finish(Finish.INVALIDATED); return false; }
            return true;
        }
        @Override public boolean complete() {
            if (!active()) return false;
            finish(Finish.COMPLETED);
            return true;
        }
        @Override public void close() { finish(Finish.CANCELLED); }
        private void finish(Finish reason) {
            if (!tickets.remove(playerId, this)) return;
            open = false;
            chatTickets.remove(playerId, this);
            pending.set(null);
            try {
                if (!chat()) onFinish.accept(playerId, reason);
            } finally {
                if (ended != null) ended.run();
            }
        }
    }
}
