package org.encinet.mik.feature.player.guidance;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Immutable event selection, independent of any tip's text, reminder interval or feature state. */
public final class TipTrigger<E extends Event> {
    private final Class<E> eventType;
    private final Function<? super E, ? extends Player> recipient;
    private final Predicate<E> filter;
    private final Predicate<E> verification;
    private final double chance;
    private final boolean adaptive;

    private TipTrigger(Class<E> eventType, Function<? super E, ? extends Player> recipient,
                       Predicate<E> filter, Predicate<E> verification, double chance, boolean adaptive) {
        this.eventType = eventType;
        this.recipient = recipient;
        this.filter = filter;
        this.verification = verification;
        this.chance = chance;
        this.adaptive = adaptive;
    }

    public static <E extends PlayerEvent> TipTrigger<E> event(Class<E> type) {
        return event(type, PlayerEvent::getPlayer);
    }

    /** Recipient extraction runs on the event thread; async events must use only their own event data. */
    public static <E extends Event> TipTrigger<E> event(Class<E> type,
                                                       Function<? super E, ? extends Player> recipient) {
        return new TipTrigger<>(Objects.requireNonNull(type, "eventType"),
                Objects.requireNonNull(recipient, "recipient"), event -> true, event -> true, 1, false);
    }

    /** Runs on the event thread. For async events, inspect event data rather than live Bukkit state. */
    public TipTrigger<E> filter(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate, "filter");
        return new TipTrigger<>(eventType, recipient, filter.and(predicate), verification, chance, adaptive);
    }

    /** Runs on the next main-thread dispatch, e.g. to confirm the player actually reached a teleport target. */
    public TipTrigger<E> verify(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate, "verification");
        return new TipTrigger<>(eventType, recipient, filter, verification.and(predicate), chance, adaptive);
    }

    public TipTrigger<E> chance(double value) {
        Tip.checkChance(value);
        return new TipTrigger<>(eventType, recipient, filter, verification, value, adaptive);
    }

    /** Reduce repeated opportunities using their recent density and the last actual display time. */
    public TipTrigger<E> adaptiveChance() {
        return new TipTrigger<>(eventType, recipient, filter, verification, chance, true);
    }

    Class<E> eventType() { return eventType; }
    Player recipient(E event) { return recipient.apply(event); }
    boolean matches(E event) { return filter.test(event); }
    boolean verify(E event) { return verification.test(event); }
    double chance() { return chance; }
    boolean adaptive() { return adaptive; }
}
