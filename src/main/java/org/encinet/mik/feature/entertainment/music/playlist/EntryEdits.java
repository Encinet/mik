package org.encinet.mik.feature.entertainment.music.playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Stable identities express both the moved entries and their destination. */
public final class EntryEdits {
    private EntryEdits() {}
    public sealed interface Destination permits Before, End { }
    public record Before(UUID entry) implements Destination {
        public Before { Objects.requireNonNull(entry); }
    }
    public enum End implements Destination { INSTANCE }
    public static Destination before(UUID entry) { return new Before(entry); }
    public static Destination end() { return End.INSTANCE; }

    public static <T> List<T> move(
            List<T> entries, Set<UUID> selected, Destination destination, Function<T, UUID> id) {
        Objects.requireNonNull(destination);
        if (selected.isEmpty())
            return List.copyOf(entries);
        int position = entries.size();
        if (destination instanceof Before before) {
            if (selected.contains(before.entry())) throw new IllegalArgumentException("Destination belongs to selection");
            position = -1;
            for (int i = 0; i < entries.size(); i++) if (id.apply(entries.get(i)).equals(before.entry())) { position = i; break; }
            if (position < 0) throw new IllegalStateException("Destination changed");
        }
        List<T> moved = entries.stream().filter(e -> selected.contains(id.apply(e))).toList();
        if (moved.size() != selected.size())
            throw new IllegalStateException("Selection changed");
        int before = (int) entries.subList(0, position)
                             .stream()
                             .filter(e -> selected.contains(id.apply(e)))
                             .count();
        List<T> result =
                new ArrayList<>(entries.stream().filter(e -> !selected.contains(id.apply(e))).toList());
        result.addAll(position - before, moved);
        return List.copyOf(result);
    }
}
