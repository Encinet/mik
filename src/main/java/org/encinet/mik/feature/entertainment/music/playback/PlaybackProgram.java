package org.encinet.mik.feature.entertainment.music.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;

/** Pure queue transitions. A session owns this value; devices never advance it independently. */
public record
        PlaybackProgram(long version, List<QueueEntry> base, List<QueueEntry> priority, QueueEntry current,
                int cursor, Mode mode, List<QueueEntry> history, List<UUID> shuffled, boolean finishCurrent) {
    public enum Mode {
        SEQUENTIAL,
        REPEAT_ALL,
        REPEAT_ONE,
        SHUFFLE
    }
    public enum Submission { INSERT, APPEND }
    public PlaybackProgram {
        base = List.copyOf(base);
        priority = List.copyOf(priority);
        history = List.copyOf(history);
        shuffled = List.copyOf(shuffled);
        Objects.requireNonNull(mode);
        if (base.size() + priority.size() > 5000)
            throw new IllegalArgumentException("Queue limit");
    }
    public static PlaybackProgram empty() {
        return new PlaybackProgram(
                0, List.of(), List.of(), null, -1, Mode.SEQUENTIAL, List.of(), List.of(), false);
    }
    public PlaybackProgram submit(List<QueueEntry> entries, Submission action) {
        if (entries.isEmpty())
            return this;
        List<QueueEntry> next = new ArrayList<>(base), requests = new ArrayList<>(priority);
        QueueEntry selected = current;
        List<QueueEntry> played = history;
        if (action == Submission.INSERT) {
            if (current != null)
                played = remember(current);
            selected = entries.getFirst();
            requests.addAll(0, entries.subList(1, entries.size()));
        } else
            next.addAll(entries);
        return new PlaybackProgram(
                version + 1, next, requests, selected, cursor, mode, played, shuffled, false);
    }
    public PlaybackProgram advance(boolean natural, RandomGenerator random) {
        if (finishCurrent)
            return copy(base, List.of(), null, cursor, mode, current == null ? history : remember(current),
                    List.of(), false);
        List<QueueEntry> played = current == null ? history : remember(current);
        if (!priority.isEmpty())
            return copy(base, priority.subList(1, priority.size()), priority.getFirst(), cursor, mode, played,
                    shuffled, false);
        if (natural && mode == Mode.REPEAT_ONE && cursor >= 0 && cursor < base.size())
            return copy(base, priority, base.get(cursor), cursor, mode, played, shuffled, false);
        if (base.isEmpty())
            return copy(base, priority, null, -1, mode, played, List.of(), false);
        int index = cursor + 1;
        List<UUID> bag = shuffled;
        if (mode == Mode.SHUFFLE) {
            List<UUID> remaining = new ArrayList<>(
                    shuffled.stream().filter(id -> base.stream().anyMatch(e -> e.id().equals(id))).toList());
            if (remaining.isEmpty()) {
                remaining.addAll(base.stream().map(QueueEntry::id).toList());
            }
            List<UUID> candidates = remaining.size() > 1 && current != null
                    ? remaining.stream().filter(id -> !id.equals(current.id())).toList()
                    : remaining;
            UUID picked = candidates.get(random.nextInt(candidates.size()));
            remaining.remove(picked);
            index = java.util.stream.IntStream.range(0, base.size())
                            .filter(i -> base.get(i).id().equals(picked))
                            .findFirst()
                            .orElseThrow();
            bag = remaining;
        } else if (index >= base.size())
            index = mode == Mode.SEQUENTIAL ? -1 : 0;
        return copy(base, priority, index < 0 ? null : base.get(index), index, mode, played, bag, false);
    }
    public PlaybackProgram previous(long positionMillis) {
        if (current == null || positionMillis > 3000 || history.isEmpty())
            return this;
        QueueEntry last = history.getLast();
        int index = java.util.stream.IntStream.range(0, base.size())
                            .filter(i -> base.get(i).id().equals(last.id()))
                            .findFirst()
                            .orElse(cursor);
        return copy(
                base, priority, last, index, mode, history.subList(0, history.size() - 1), shuffled, false);
    }
    public PlaybackProgram withMode(Mode value) {
        return copy(base, priority, current, cursor, value, history, List.of(), finishCurrent);
    }
    public PlaybackProgram clearPending() {
        return copy(List.of(), List.of(), current, -1, mode, history, List.of(), true);
    }
    public List<QueueEntry> pendingBase() {
        if (finishCurrent) return List.of();
        var result = new ArrayList<QueueEntry>();
        for (int i = 0; i < base.size(); i++) {
            var entry = base.get(i);
            if (current != null && current.id().equals(entry.id())) continue;
            if (mode == Mode.SEQUENTIAL && i <= cursor) continue;
            result.add(entry);
        }
        return List.copyOf(result);
    }
    /** A complete editable partition, or no movable selection. Shuffle candidates have no manual order. */
    public List<QueueEntry> movableEntries(Set<UUID> selected) {
        if (selected.isEmpty()) return List.of();
        if (contains(priority, selected)) return priority;
        return mode != Mode.SHUFFLE && contains(pendingBase(), selected) ? base : List.of();
    }
    public PlaybackProgram movePending(Set<UUID> selected, EntryEdits.Destination destination) {
        var entries = movableEntries(selected);
        if (entries.isEmpty()) throw new IllegalArgumentException("Selection has no playback order");
        boolean requests = entries == priority;
        if (destination instanceof EntryEdits.Before before
                && !contains(requests ? priority : pendingBase(), Set.of(before.entry())))
            throw new IllegalStateException("Destination is no longer pending in this partition");
        var moved = EntryEdits.move(entries, selected, destination, QueueEntry::id);
        if (moved.equals(entries)) return this;
        return requests ? edit(base, moved) : edit(moved, priority);
    }
    public PlaybackProgram removePending(Set<UUID> selected) {
        var pending = new ArrayList<>(priority);
        pending.addAll(pendingBase());
        if (!contains(pending, selected)) throw new IllegalStateException("Selection is no longer pending");
        if (selected.isEmpty()) return this;
        return edit(base.stream().filter(entry -> !selected.contains(entry.id())).toList(),
                priority.stream().filter(entry -> !selected.contains(entry.id())).toList());
    }
    private static boolean contains(List<QueueEntry> entries, Set<UUID> selected) {
        return entries.stream().filter(entry -> selected.contains(entry.id())).count() == selected.size();
    }
    public PlaybackProgram edit(List<QueueEntry> entries, List<QueueEntry> requests) {
        int index = current == null ? -1
                                    : java.util.stream.IntStream.range(0, entries.size())
                                              .filter(i -> entries.get(i).id().equals(current.id()))
                                              .findFirst()
                                              .orElse(Math.min(cursor, entries.size() - 1));
        return copy(entries, requests, current, index, mode, history, List.of(), finishCurrent);
    }
    private List<QueueEntry> remember(QueueEntry entry) {
        List<QueueEntry> result =
                new ArrayList<>(history.subList(Math.max(0, history.size() - 99), history.size()));
        result.add(entry);
        return result;
    }
    private PlaybackProgram copy(List<QueueEntry> b, List<QueueEntry> p, QueueEntry c, int i, Mode m,
            List<QueueEntry> h, List<UUID> s, boolean f) {
        return new PlaybackProgram(version + 1, b, p, c, i, m, h, s, f);
    }
}
