package org.encinet.mik.feature.community.chat.mention;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable names shared by chat contexts until their source players change. */
public final class PlayerMentionIndex {
    private static final int MAXIMUM_NAME_LENGTH = 64;
    private static final PlayerMentionIndex EMPTY = new PlayerMentionIndex(List.of());

    private final List<Target> targets;
    private final Map<UUID, Target> byId;
    private final Map<String, List<Target>> byName;
    private final long lengths;

    private PlayerMentionIndex(List<Target> targets) {
        this.targets = targets;
        Map<UUID, Target> ids = new LinkedHashMap<>();
        Map<String, List<Target>> names = new HashMap<>();
        long lengths = 0;
        for (Target target : targets) {
            ids.put(target.id(), target);
            names.computeIfAbsent(nameKey(target.name()), _ -> new ArrayList<>()).add(target);
            lengths |= 1L << (target.name().length() - 1);
        }
        names.replaceAll((_, values) -> List.copyOf(values));
        byId = Map.copyOf(ids);
        byName = Map.copyOf(names);
        this.lengths = lengths;
    }

    public static PlayerMentionIndex empty() {
        return EMPTY;
    }

    public static PlayerMentionIndex of(Collection<Target> targets) {
        return EMPTY.withTargets(targets);
    }

    public PlayerMentionIndex withTargets(Collection<Target> targets) {
        List<Target> checked = List.copyOf(targets);
        return this.targets.equals(checked) ? this
                : checked.isEmpty() ? EMPTY : new PlayerMentionIndex(checked);
    }

    /** Incorporates live channel names without rebuilding an unchanged shared index. */
    public PlayerMentionIndex including(Collection<Target> current) {
        if (current.stream().allMatch(target -> target.equals(byId.get(target.id())))) {
            return this;
        }
        Map<UUID, Target> merged = new LinkedHashMap<>();
        targets.forEach(target -> merged.put(target.id(), target));
        current.forEach(target -> merged.put(target.id(), target));
        return of(merged.values());
    }

    Set<UUID> playerIds() {
        return byId.keySet();
    }

    long lengths() {
        return lengths;
    }

    Target lookup(String key, int length, Set<UUID> allowed, UUID excluded) {
        List<Target> candidates = byName.get(key);
        if (candidates == null) {
            return null;
        }
        for (Target target : candidates) {
            if (target.name().length() == length
                    && !target.id().equals(excluded)
                    && (allowed == null || allowed.contains(target.id()))) {
                return target;
            }
        }
        return null;
    }

    public static String nameKey(String name) {
        StringBuilder key = new StringBuilder(name.length());
        for (int index = 0; index < name.length();) {
            int codePoint = name.codePointAt(index);
            key.appendCodePoint(fold(codePoint));
            index += Character.charCount(codePoint);
        }
        return key.toString();
    }

    // The same simple Unicode case equivalence used by String.regionMatches(true, ...).
    static int fold(int codePoint) {
        return Character.toLowerCase(Character.toUpperCase(codePoint));
    }

    public record Target(UUID id, String name) {
        public Target {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name").strip();
            if (name.isEmpty() || name.length() > MAXIMUM_NAME_LENGTH
                    || name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("mentionable player name is invalid");
            }
        }
    }
}
