package org.encinet.mik.feature.entertainment.music.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.UnaryOperator;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;

/** Author-only durable editing. All entry points use these same checks. */
public final class PlaylistService {
    private final MusicStore store;
    private final Map<UUID, Deque<Undo>> undo = new ConcurrentHashMap<>(); // store executor writes; UI reads availability
    private record Undo(long at, Playlist before, Playlist after) {}
    public PlaylistService(MusicStore store) {
        this.store = store;
    }
    public List<Playlist> owned(UUID actor) {
        return store.snapshot()
                .playlists()
                .values()
                .stream()
                .filter(p -> p.author().equals(actor))
                .sorted(Comparator.comparing(Playlist::name).thenComparing(Playlist::id))
                .toList();
    }
    public List<Playlist> published() {
        return store.snapshot()
                .playlists()
                .values()
                .stream()
                .filter(Playlist::published)
                .sorted(Comparator.comparing(Playlist::name).thenComparing(Playlist::id))
                .toList();
    }
    public Playlist visible(UUID actor, UUID id) {
        Playlist list = store.snapshot().playlists().get(id);
        if (list == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        if (!list.visibleTo(actor))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        return list;
    }
    public CompletableFuture<Playlist> create(UUID actor, String name, List<MusicTrack> tracks) {
        return store.transact(e -> create(e, actor, name, tracks));
    }
    private Playlist create(MusicState.Editor e, UUID actor, String name, List<MusicTrack> tracks) {
        try { name = Playlist.text(name, 64); }
        catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
        if (e.playlists.values().stream().filter(p -> p.author().equals(actor)).count() >= 100)
            throw new MusicProblem(MusicProblem.Code.LIMIT);
        tracks.forEach(t -> e.tracks.put(t.id(), StoredTrack.of(t)));
        List<Playlist.Entry> entries =
                tracks.stream()
                        .map(MusicTrack::id)
                        .distinct()
                        .map(id -> new Playlist.Entry(UUID.randomUUID(), id, System.currentTimeMillis()))
                        .toList();
        if (entries.size() > Playlist.MAX_ENTRIES) throw new MusicProblem(MusicProblem.Code.LIMIT);
        Playlist list = new Playlist(UUID.randomUUID(), actor, name, "", "MUSIC_DISC_CAT", false, 0, entries);
        e.playlists.put(list.id(), list);
        return list;
    }
    public CompletableFuture<Playlist> copy(UUID actor, UUID id, String name) {
        return store.transact(e -> {
            Playlist source = e.playlists.get(id);
            if (source == null || !source.visibleTo(actor))
                throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
            return create(e, actor, name,
                    source.entries().stream().map(t -> e.tracks.get(t.trackId()).track()).toList());
        });
    }
    public CompletableFuture<Playlist> update(UUID actor, UUID id, long version, String name,
            String description, String icon, boolean published) {
        return edit(actor, id, version, p -> {
            try { return p.revise(name, description, icon, published, p.entries()); }
            catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
        });
    }
    public CompletableFuture<Playlist> add(UUID actor, UUID id, long version, List<MusicTrack> tracks) {
        return store.transact(e -> {
            Playlist before = editable(e, actor, id, version);
            List<Playlist.Entry> items = new ArrayList<>(before.entries());
            Set<String> existing = new HashSet<>(items.stream().map(Playlist.Entry::trackId).toList());
            for (MusicTrack t : tracks) {
                e.tracks.put(t.id(), StoredTrack.of(t));
                if (existing.add(t.id()))
                    items.add(new Playlist.Entry(UUID.randomUUID(), t.id(), System.currentTimeMillis()));
            }
            if (items.size() > Playlist.MAX_ENTRIES) throw new MusicProblem(MusicProblem.Code.LIMIT);
            return save(e, actor, before,
                    before.revise(
                            before.name(), before.description(), before.icon(), before.published(), items));
        });
    }
    public CompletableFuture<Playlist> remove(UUID actor, UUID id, long version, Set<UUID> ids) {
        return edit(actor, id, version, p -> {
            if (p.entries().stream().filter(t -> ids.contains(t.id())).count() != ids.size())
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            return p.revise(p.name(), p.description(), p.icon(), p.published(),
                    p.entries().stream().filter(t -> !ids.contains(t.id())).toList());
        });
    }
    public CompletableFuture<Playlist> move(UUID actor, UUID id, long version, Set<UUID> ids, EntryEdits.Destination destination) {
        var selected = Set.copyOf(ids);
        return edit(actor, id, version, p -> {
            try {
                var entries = EntryEdits.move(p.entries(), selected, destination, Playlist.Entry::id);
                return entries.equals(p.entries()) ? p : p.revise(p.name(), p.description(), p.icon(), p.published(), entries);
            } catch (IllegalStateException stale) { throw new MusicProblem(MusicProblem.Code.CONFLICT); }
            catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
        });
    }
    public CompletableFuture<Void> delete(UUID actor, UUID id, long version) {
        return store.transact(e -> {
            editable(e, actor, id, version);
            e.playlists.remove(id);
            e.favorites.values().removeIf(favorite -> favorite.playlist().equals(id));
            return null;
        });
    }
    private CompletableFuture<Playlist> edit(
            UUID actor, UUID id, long version, UnaryOperator<Playlist> mutation) {
        return store.transact(e -> {
            Playlist before = editable(e, actor, id, version);
            return save(e, actor, before, mutation.apply(before));
        });
    }
    private Playlist save(MusicState.Editor e, UUID actor, Playlist before, Playlist after) {
        if (before.equals(after)) return before;
        e.playlists.put(after.id(), after);
        Deque<Undo> journal = undo.computeIfAbsent(actor, _ -> new ConcurrentLinkedDeque<>());
        journal.addLast(new Undo(System.currentTimeMillis(), before, after));
        while (journal.size() > 20) journal.removeFirst();
        return after;
    }
    private Playlist editable(MusicState.Editor e, UUID actor, UUID id, long version) {
        Playlist p = e.playlists.get(id);
        if (p == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        if (!p.author().equals(actor))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        if (p.version() != version)
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        return p;
    }
    public CompletableFuture<Playlist> undo(UUID actor, UUID playlist) {
        return store.transact(e -> {
            Deque<Undo> journal = undo.get(actor);
            if (journal == null || journal.isEmpty())
                throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
            Undo item = journal.stream().filter(edit -> edit.after().id().equals(playlist))
                    .reduce((_, last) -> last).orElseThrow(() -> new MusicProblem(MusicProblem.Code.NOT_FOUND));
            if (System.currentTimeMillis() - item.at() > 600000) {
                journal.remove(item);
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            }
            Playlist current = e.playlists.get(item.after().id());
            if (current == null || !current.author().equals(actor))
                throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
            Playlist before = item.before(), after = item.after();
            // Allow additions at the tail, preserving them; never overwrite a reordered or removed baseline.
            if (current.entries().size() < after.entries().size()
                    || !current.entries().subList(0, after.entries().size()).equals(after.entries()))
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            List<Playlist.Entry> restoredEntries = new ArrayList<>(before.entries());
            Set<String> restoredTracks =
                    new HashSet<>(restoredEntries.stream().map(Playlist.Entry::trackId).toList());
            for (Playlist.Entry entry :
                    current.entries().subList(after.entries().size(), current.entries().size()))
                if (restoredTracks.add(entry.trackId()))
                    restoredEntries.add(entry);
            String name = undoField(before.name(), after.name(), current.name());
            String description = undoField(before.description(), after.description(), current.description());
            String icon = undoField(before.icon(), after.icon(), current.icon());
            boolean published = current.published();
            if (before.published() != after.published()) {
                if (current.published() != after.published())
                    throw new MusicProblem(MusicProblem.Code.CONFLICT);
                published = before.published();
            }
            Playlist restored = current.revise(name, description, icon, published, restoredEntries);
            e.playlists.put(restored.id(), restored);
            journal.remove(item);
            return restored;
        });
    }
    public boolean canUndo(UUID actor, UUID playlist) {
        Deque<Undo> journal = undo.get(actor);
        return journal != null && journal.stream().anyMatch(edit -> edit.after().id().equals(playlist)
                && System.currentTimeMillis() - edit.at() <= 600000);
    }
    private static String undoField(String before, String after, String current) {
        if (before.equals(after))
            return current;
        if (!current.equals(after))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        return before;
    }
    public CompletableFuture<Void> favorite(UUID actor, UUID id, boolean selected) {
        return store.transact(e -> {
            Playlist p = e.playlists.get(id);
            if (p == null || !p.visibleTo(actor))
                throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
            String key = MusicState.key(actor, id);
            if (selected)
                e.favorites.put(key, new MusicState.Favorite(actor, id, System.currentTimeMillis()));
            else
                e.favorites.remove(key);
            return null;
        });
    }
    public CompletableFuture<Playlist> imported(UUID actor, UUID destination, String name,
            List<MusicTrack> tracks, String source, String reference, int total, UUID request) {
        return store.transact(e -> {
            MusicState.ImportReceipt previous = e.imports.get(request);
            if (previous != null) {
                if (!previous.player().equals(actor))
                    throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
                return e.playlists.get(previous.playlist());
            }
            Playlist result;
            if (destination == null)
                result = create(e, actor, name, tracks);
            else {
                Playlist before = e.playlists.get(destination);
                if (before == null || !before.author().equals(actor))
                    throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
                List<Playlist.Entry> items = new ArrayList<>(before.entries());
                Set<String> ids = new HashSet<>(items.stream().map(Playlist.Entry::trackId).toList());
                for (MusicTrack track : tracks) {
                    e.tracks.put(track.id(), StoredTrack.of(track));
                    if (ids.add(track.id()))
                        items.add(new Playlist.Entry(
                                UUID.randomUUID(), track.id(), System.currentTimeMillis()));
                }
                if (items.size() > Playlist.MAX_ENTRIES) throw new MusicProblem(MusicProblem.Code.LIMIT);
                result = before.revise(
                        before.name(), before.description(), before.icon(), before.published(), items);
                e.playlists.put(destination, result);
            }
            e.imports.put(request,
                    new MusicState.ImportReceipt(request, actor, source, reference, result.id(),
                            tracks.size(), total, System.currentTimeMillis()));
            return result;
        });
    }
    public CompletableFuture<Void> clearRecents(UUID actor) {
        return store.transact(e -> {
            e.recents.values().removeIf(r -> r.player().equals(actor));
            return null;
        });
    }
    public CompletableFuture<Void> forgetRecent(UUID actor, String track) {
        return store.transact(e -> {
            e.recents.values().removeIf(r -> r.player().equals(actor) && r.track().equals(track));
            return null;
        });
    }
}
