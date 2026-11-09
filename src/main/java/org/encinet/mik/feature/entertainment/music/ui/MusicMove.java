package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;

/** A placement step freezes the owning list, its version and occurrence identities across pages. */
record MusicMove(UUID owner, long version, List<UUID> order, Set<UUID> selected) {
    MusicMove {
        Objects.requireNonNull(owner);
        order = List.copyOf(order);
        selected = Set.copyOf(selected);
        var identities = new HashSet<>(order);
        if (selected.isEmpty() || !identities.containsAll(selected) || identities.size() != order.size())
            throw new IllegalArgumentException("Invalid move selection");
    }
    boolean current(UUID id, long currentVersion) { return owner.equals(id) && version == currentVersion; }
    boolean canPlace(EntryEdits.Destination destination) {
        if (destination instanceof EntryEdits.Before before && (selected.contains(before.entry()) || !order.contains(before.entry()))) return false;
        return !EntryEdits.move(order, selected, destination, id -> id).equals(order);
    }
    boolean hasDestination(Set<UUID> candidates) {
        return canPlace(EntryEdits.end()) || order.stream().filter(candidates::contains)
                .anyMatch(id -> canPlace(EntryEdits.before(id)));
    }
}
