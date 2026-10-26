package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.Language;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/** Bounded, main-thread scheduling; feature callbacks run only when delivery is considered. */
final class TipScheduler {
    private static final int MAX_PENDING = 8;
    private static final int MAX_BATCH = 3;
    private static final long AMBIENT_MIN_INTERVAL = Duration.ofMinutes(15).toMillis();
    private static final long AMBIENT_MAX_INTERVAL = Duration.ofMinutes(30).toMillis();
    private final Map<UUID, TipState> states = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private final Map<UUID, Map<String, Pending>> pending = new HashMap<>();
    private final Map<String, Object> registrations = new HashMap<>();
    private final Map<String, Ambient> ambient = new LinkedHashMap<>();
    private final Map<UUID, Long> nextAmbientAt = new HashMap<>();
    private final Consumer<RuntimeException> errors;
    private final DoubleSupplier random;
    private long sequence;

    TipScheduler(Consumer<RuntimeException> errors) {
        this(errors, () -> ThreadLocalRandom.current().nextDouble());
    }

    TipScheduler(Consumer<RuntimeException> errors, DoubleSupplier random) {
        this.errors = errors;
        this.random = Objects.requireNonNull(random, "random");
    }

    void register(Object owner, Tip.Policy definition) {
        register(owner, definition, null);
    }

    void register(Object owner, Tip.Policy definition,
                  BiFunction<UUID, Language, Optional<TipContent>> ambientContent) {
        if (registrations.putIfAbsent(definition.id(), owner) != null) {
            throw new IllegalArgumentException("Tip id is already registered: " + definition.id());
        }
        if (ambientContent != null) ambient.put(definition.id(), new Ambient(owner, definition, ambientContent));
    }

    void restore(UUID player, TipState.Snapshot snapshot) {
        states.put(player, new TipState(snapshot));
        dirty.remove(player);
    }

    boolean loaded(UUID player) { return states.containsKey(player); }
    TipState.Snapshot snapshot(UUID player) {
        TipState state = states.get(player);
        return state == null ? new TipState.Snapshot(Map.of()) : state.snapshot();
    }
    boolean changed(UUID player) { return dirty.contains(player); }
    void saved(UUID player) { dirty.remove(player); }

    void offer(Object owner, UUID player, Tip.Policy definition, long now,
               Function<Language, Optional<TipContent>> content) {
        offer(owner, player, definition, now, 1.0, content);
    }

    void offer(Object owner, UUID player, Tip.Policy definition, long now, double chance,
               Function<Language, Optional<TipContent>> content) {
        offer(owner, player, definition, now, chance, false, 1, content);
    }

    void offer(Object owner, UUID player, Tip.Policy definition, long now, double chance,
               boolean adaptive, int occurrences, Function<Language, Optional<TipContent>> content) {
        if (adaptive && chance > 0) {
            TipState state = states.computeIfAbsent(player, ignored -> new TipState());
            TipState.Entry history = state.history.getOrDefault(definition.id(), TipState.Entry.empty());
            chance *= history.probability(now, occurrences);
            state.history.put(definition.id(), history.matched(now, occurrences));
            dirty.add(player);
        }
        if (chance == 0 || chance < 1 && random.getAsDouble() >= chance) return;
        Map<String, Pending> offers = pending.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        offers.values().removeIf(it -> now >= it.expiresAt);
        Pending previous = offers.get(definition.id());
        if (previous != null && previous.owner != owner) {
            throw new IllegalArgumentException("Tip id owned by another publisher: " + definition.id());
        }
        // Repeated observations refresh the predicate without postponing delivery or extending expiry.
        offers.put(definition.id(), new Pending(owner, definition,
                previous == null ? now + definition.delay().toMillis() : previous.readyAt,
                previous == null ? now + definition.lifetime().toMillis() : previous.expiresAt,
                previous == null ? sequence++ : previous.sequence, adaptive, content));
        if (offers.size() > MAX_PENDING) {
            Pending discarded = offers.values().stream().max(ORDER).orElseThrow();
            offers.remove(discarded.definition.id());
        }
    }

    List<Delivery> collect(UUID player, long now, long globalCooldown, boolean available, Language language) {
        var batch = collectPending(player, now, globalCooldown, available, language);
        return batch.isEmpty() ? collectAmbient(player, now, globalCooldown, available, language) : batch;
    }

    private List<Delivery> collectPending(UUID player, long now, long globalCooldown,
                                          boolean available, Language language) {
        Map<String, Pending> offers = pending.get(player);
        if (offers == null) return List.of();
        offers.values().removeIf(it -> now >= it.expiresAt);
        List<Pending> ready = offers.values().stream().filter(it -> now >= it.readyAt).sorted(ORDER).toList();
        if (offers.isEmpty()) pending.remove(player);
        if (!available || ready.isEmpty()) return List.of();

        TipState state = states.get(player);
        if (state != null && recent(state.lastSentAt, now, globalCooldown)) {
            discardReady(player, offers, ready);
            return List.of();
        }

        List<Evaluated> candidates = new ArrayList<>();
        for (Pending offer : ready) {
            if (offers.get(offer.definition.id()) != offer) continue;
            var delivery = resolve(player, offer.definition, now, language, offer.adaptive, offer.content);
            if (delivery.isPresent()) candidates.add(new Evaluated(offer, delivery.get()));
            else discard(offers, offer);
        }

        // A source can close or complete a scene during another source's callback.
        candidates.removeIf(it -> offers.get(it.offer.definition.id()) != it.offer);
        if (candidates.isEmpty()) {
            discardReady(player, offers, ready);
            return List.of();
        }
        String group = candidates.getFirst().offer.definition.group();
        // Pick the group from valid candidates before waiting for its slightly later join sources.
        if (offers.values().stream().anyMatch(it -> it.definition.group().equals(group)
                && it.readyAt > now && it.readyAt - now <= 1000)) return List.of();

        discardReady(player, offers, ready);
        return candidates.stream().filter(it -> it.offer.definition.group().equals(group)).limit(MAX_BATCH)
                .map(Evaluated::delivery).toList();
    }

    private List<Delivery> collectAmbient(UUID player, long now, long globalCooldown,
                                         boolean available, Language language) {
        if (ambient.isEmpty()) return List.of();
        Long due = nextAmbientAt.get(player);
        // Start each connection with a quiet interval; reset it if the wall clock moves backwards.
        if (due == null || due > now && due - now > AMBIENT_MAX_INTERVAL) {
            scheduleAmbient(player, now);
            return List.of();
        }
        if (now < due) return List.of();
        // An unavailable opportunity is skipped, never queued for a burst on return from AFK.
        scheduleAmbient(player, now);
        TipState state = states.get(player);
        if (!available || pending.containsKey(player)
                || state != null && recent(state.lastSentAt, now, globalCooldown)) return List.of();

        List<Ambient> remaining = new ArrayList<>(ambient.values());
        while (!remaining.isEmpty()) {
            int index = (int) (random.getAsDouble() * remaining.size());
            Ambient candidate = remaining.get(index);
            remaining.set(index, remaining.getLast());
            remaining.removeLast();
            if (ambient.get(candidate.definition.id()) != candidate) continue;
            var delivery = resolve(player, candidate.definition, now, language, false,
                    recipientLanguage -> candidate.content.apply(player, recipientLanguage));
            // A callback may close, complete or replace its own registration.
            if (delivery.isPresent() && ambient.get(candidate.definition.id()) == candidate
                    && !pending.containsKey(player)) return List.of(delivery.get());
        }
        return List.of();
    }

    private void scheduleAmbient(UUID player, long now) {
        nextAmbientAt.put(player, now + AMBIENT_MIN_INTERVAL
                + (long) (random.getAsDouble() * (AMBIENT_MAX_INTERVAL - AMBIENT_MIN_INTERVAL)));
    }

    private Optional<Delivery> resolve(UUID player, Tip.Policy definition, long now, Language language,
                                       boolean adaptive, Function<Language, Optional<TipContent>> source) {
        try {
            var content = Objects.requireNonNull(source.apply(language), "Tip source must return Optional");
            if (content.isEmpty()) return Optional.empty();
            TipState state = states.get(player);
            TipContent tip = content.get();
            TipState.Entry history = state == null ? null : state.history.get(definition.id());
            if (!adaptive && history != null && !history.due(tip.situationKey(), now, definition.interval())) {
                return Optional.empty();
            }
            return Optional.of(new Delivery(definition, tip.situationKey(), tip.message()));
        } catch (RuntimeException error) {
            errors.accept(error);
            return Optional.empty();
        }
    }

    private void discardReady(UUID player, Map<String, Pending> offers, List<Pending> ready) {
        // Preserve any replacement published while callbacks were running.
        ready.forEach(it -> discard(offers, it));
        if (offers.isEmpty()) pending.remove(player, offers);
    }

    private static void discard(Map<String, Pending> offers, Pending offer) {
        if (offers.get(offer.definition.id()) == offer) offers.remove(offer.definition.id());
    }

    void delivered(UUID player, List<Delivery> batch, long now) {
        TipState state = states.computeIfAbsent(player, ignored -> new TipState());
        state.lastSentAt = now;
        batch.forEach(it -> state.history.compute(it.definition.id(), (id, previous) ->
                (previous == null ? TipState.Entry.empty() : previous).delivered(it.situationKey, now)));
        dirty.add(player);
    }

    void cancel(UUID player, Tip.Policy definition) {
        Map<String, Pending> offers = pending.get(player);
        if (offers == null) return;
        offers.remove(definition.id());
        if (offers.isEmpty()) pending.remove(player);
    }

    void close(Object owner) {
        pending.values().forEach(offers -> offers.values().removeIf(it -> it.owner == owner));
        pending.values().removeIf(Map::isEmpty);
        registrations.values().removeIf(registered -> registered == owner);
        ambient.values().removeIf(it -> it.owner == owner);
        if (ambient.isEmpty()) nextAmbientAt.clear();
    }

    void forget(UUID player) {
        pending.remove(player);
        nextAmbientAt.remove(player);
        states.remove(player);
        dirty.remove(player);
    }

    void clear() {
        pending.clear();
        registrations.clear();
        ambient.clear();
        nextAmbientAt.clear();
        states.clear();
        dirty.clear();
    }

    List<UUID> pendingPlayers() { return List.copyOf(pending.keySet()); }

    private static boolean recent(Long timestamp, long now, long duration) {
        return timestamp != null && duration > 0 && now >= timestamp && now - timestamp < duration;
    }

    private static final Comparator<Pending> ORDER = Comparator
            .comparingInt((Pending it) -> it.definition.priority()).reversed().thenComparingLong(it -> it.sequence);

    record Delivery(Tip.Policy definition, String situationKey, Component message) {}
    private record Evaluated(Pending offer, Delivery delivery) {}
    private record Pending(Object owner, Tip.Policy definition, long readyAt, long expiresAt,
                           long sequence, boolean adaptive, Function<Language, Optional<TipContent>> content) {}
    private record Ambient(Object owner, Tip.Policy definition,
                           BiFunction<UUID, Language, Optional<TipContent>> content) {}
}
