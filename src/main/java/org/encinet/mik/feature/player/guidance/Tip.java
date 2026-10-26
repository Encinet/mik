package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** One immutable, registered scenario and its typed module-facing operations. */
public final class Tip<C> {
    private final Binding binding;
    private final Policy policy;
    private final Resolver<C> resolver;
    private final BiPredicate<Player, C> condition;
    private final ClickEvent action;
    private final List<EventBinding<?, C>> triggers;
    private final boolean ambient;

    private Tip(Builder<C> builder) {
        binding = builder.binding;
        policy = new Policy(builder.id, builder.group, builder.priority, builder.interval,
                builder.delay, builder.lifetime);
        resolver = Objects.requireNonNull(builder.resolver, "Tip resolver is required");
        condition = builder.condition;
        action = builder.action;
        triggers = List.copyOf(builder.triggers);
        triggers.forEach(source -> checkTiming(source.trigger().delayOr(policy.delay()), policy.lifetime()));
        ambient = builder.ambient;
    }

    public String id() { return policy.id(); }

    public void offer(Player player, C context) {
        offerRandomly(player, context, 1.0);
    }

    /** For scenarios without event-specific context. */
    public void offer(Player player) { offer(player, null); }

    /** The chance belongs to this trigger; relevance, expiry and interval still apply after selection. */
    public void offerRandomly(Player player, C context, double chance) {
        checkChance(chance);
        binding.offer(this, Objects.requireNonNull(player, "player"), context, chance, false, 1, policy.delay());
    }

    public void offerRandomly(Player player, double chance) { offerRandomly(player, null, chance); }

    void observe(Player player, C context, TipTrigger<?> trigger, int occurrences) {
        binding.offer(this, player, context, trigger.chance(), trigger.adaptive(), occurrences,
                trigger.delayOr(policy.delay()));
    }

    public void cancel(UUID playerId) {
        binding.cancel(policy, Objects.requireNonNull(playerId, "playerId"));
    }

    Policy policy() { return policy; }
    List<EventBinding<?, C>> triggers() { return triggers; }
    boolean ambient() { return ambient; }
    boolean applicable(Player player, C context) { return condition.test(player, context); }
    Optional<TipContent> resolve(Player player, C context, Language language) {
        if (!condition.test(player, context)) return Optional.empty();
        return resolver.resolve(player, context, language).map(content -> action == null ? content
                : new TipContent(content.situationKey(), content.message().clickEvent(action)));
    }

    /** Recheck the scene and localize its content on the main thread when delivery is considered. */
    @FunctionalInterface
    public interface Resolver<C> {
        Optional<TipContent> resolve(Player player, C context, Language language);
    }

    /** Declare once during module construction; subsequent calls only supply the player and context. */
    public static final class Builder<C> {
        private final Binding binding;
        private final String id;
        private String group;
        private int priority;
        private Duration interval = Duration.ofDays(1);
        private Duration delay = Duration.ZERO;
        private Duration lifetime = Duration.ofSeconds(20);
        private Resolver<C> resolver;
        private BiPredicate<Player, C> condition = (player, context) -> true;
        private ClickEvent action;
        private final List<EventBinding<?, C>> triggers = new ArrayList<>();
        private boolean ambient;

        Builder(Binding binding, String id) {
            this.binding = binding;
            this.id = id;
            this.group = id;
        }

        public Builder<C> group(String value) { group = value; return this; }
        public Builder<C> priority(int value) { priority = value; return this; }
        public Builder<C> interval(Duration value) { interval = value; return this; }
        public Builder<C> delay(Duration value) { delay = value; return this; }
        public Builder<C> lifetime(Duration value) { lifetime = value; return this; }
        public Builder<C> resolve(Resolver<C> value) { resolver = value; return this; }

        /** Join the shared everyday random pool; delivery uses current player state and null context. */
        public Builder<C> ambient() { ambient = true; return this; }

        /** Check current feature state immediately before delivery, rather than during the triggering event. */
        public Builder<C> when(Predicate<Player> value) {
            Objects.requireNonNull(value, "condition");
            return when((player, context) -> value.test(player));
        }

        public Builder<C> when(BiPredicate<Player, C> value) {
            condition = condition.and(Objects.requireNonNull(value, "condition"));
            return this;
        }

        public Builder<C> suggestCommand(String command) {
            action = ClickEvent.suggestCommand(Objects.requireNonNull(command, "command"));
            return this;
        }

        public Builder<C> on(TipTrigger<?> trigger) {
            return on(trigger, event -> null);
        }

        /** Capture event data on its thread; async events must not read live Bukkit state. Never retain the Event. */
        public <E extends Event> Builder<C> on(TipTrigger<E> trigger, Function<? super E, ? extends C> context) {
            triggers.add(new EventBinding<>(Objects.requireNonNull(trigger, "trigger"),
                    Objects.requireNonNull(context, "context")));
            return this;
        }

        Builder<C> message(Message message) {
            Objects.requireNonNull(message, "message");
            resolver = (player, context, language) -> Optional.of(new TipContent(id, binding.text(language, message)));
            return this;
        }

        public Tip<C> build() {
            Tip<C> tip = new Tip<>(this);
            binding.register(tip);
            return tip;
        }
    }

    /** Runtime binding is internal; consumers only see their module scope and registered Tip. */
    interface Binding {
        void register(Tip<?> tip);
        Component text(Language language, Message message);
        <C> void offer(Tip<C> tip, Player player, C context, double chance, boolean adaptive, int occurrences,
                       Duration delay);
        void cancel(Policy policy, UUID playerId);
    }

    record EventBinding<E extends Event, C>(TipTrigger<E> trigger, Function<? super E, ? extends C> context) {}

    static void checkChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("Tip chance must be in 0..1");
        }
    }

    record Policy(String id, String group, int priority, Duration interval,
                  Duration delay, Duration lifetime) {
        Policy {
            if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("Invalid tip id: " + id);
            }
            if (group == null || !group.matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("Invalid tip group: " + group);
            }
            Objects.requireNonNull(interval, "interval");
            if (interval.isNegative() || interval.compareTo(Duration.ofDays(365)) > 0) {
                throw new IllegalArgumentException("Tip interval must be in 0..365 days");
            }
            checkTiming(delay, lifetime);
        }

    }

    private static void checkTiming(Duration delay, Duration lifetime) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(lifetime, "lifetime");
        if (delay.isNegative() || lifetime.isNegative() || lifetime.isZero()
                || delay.compareTo(lifetime) >= 0 || lifetime.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Tip delay must be less than its lifetime (maximum 5 minutes)");
        }
    }
}
