package org.encinet.mik.feature.entertainment.music.application;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.encinet.mik.feature.entertainment.music.catalog.AudioProperties;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PlaylistServiceTest {
    @TempDir Path directory;
    MusicStore store;
    PlaylistService service;
    UUID author = UUID.randomUUID(), visitor = UUID.randomUUID();
    @BeforeEach
    void open() {
        store = new MusicStore();
        store.open(directory.resolve("music.sqlite")).join();
        service = new PlaylistService(store);
    }
    @AfterEach
    void close() {
        store.close();
    }
    private MusicTrack track(String id) {
        return new MusicTrack(id, new TrackDetails(id, null, null, "mp3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(directory.resolve(id + ".mp3"), directory));
    }
    private Playlist create() {
        return service.create(author, "My playlist", List.of(track("a"), track("b"), track("c"))).join();
    }
    @Test void invalidEditsRemainInputErrorsAndLeaveTheSavedPlaylistUnchanged() {
        Playlist list = create();
        var invalidName = assertThrows(CompletionException.class, () -> service.create(author, " ", List.of(track("new"))).join());
        assertEquals(MusicProblem.Code.INVALID, MusicProblem.code(invalidName));
        var invalidDescription = assertThrows(CompletionException.class, () -> service.update(author, list.id(), list.version(), list.name(),
                "bad\u0001description", list.icon(), false).join());
        assertEquals(MusicProblem.Code.INVALID, MusicProblem.code(invalidDescription));
        var invalidDestination = assertThrows(CompletionException.class, () -> service.move(author, list.id(), list.version(),
                Set.of(list.entries().getFirst().id()), EntryEdits.before(list.entries().getFirst().id())).join());
        assertEquals(MusicProblem.Code.INVALID, MusicProblem.code(invalidDestination));
        var staleSelection = assertThrows(CompletionException.class, () -> service.move(author, list.id(), list.version(), Set.of(UUID.randomUUID()), EntryEdits.end()).join());
        assertEquals(MusicProblem.Code.CONFLICT, MusicProblem.code(staleSelection));
        assertEquals(list, service.visible(author, list.id()));
        assertEquals(List.of(list), service.owned(author));
        assertFalse(store.snapshot().tracks().containsKey("new"));
    }

    @Test void playlistCapacityIsReportedAsALimitAndFailedImportsPublishNoPartialState() {
        var tracks = java.util.stream.IntStream.range(0, Playlist.MAX_ENTRIES + 1).mapToObj(index -> track("capacity-" + index)).toList();
        var createError = assertThrows(CompletionException.class, () -> service.create(author, "Large playlist", tracks).join());
        assertEquals(MusicProblem.Code.LIMIT, MusicProblem.code(createError));
        assertTrue(store.snapshot().tracks().isEmpty());
        var list = create();
        var addError = assertThrows(CompletionException.class, () -> service.add(author, list.id(), list.version(), tracks).join());
        assertEquals(MusicProblem.Code.LIMIT, MusicProblem.code(addError));
        var importError = assertThrows(CompletionException.class, () -> service.imported(author, list.id(), "Import", tracks,
                "wy", "test", tracks.size(), UUID.randomUUID()).join());
        assertEquals(MusicProblem.Code.LIMIT, MusicProblem.code(importError));
        assertEquals(list, service.visible(author, list.id()));
        assertEquals(3, store.snapshot().tracks().size());
        assertTrue(store.snapshot().imports().isEmpty());
    }
    @Test
    void onlyAuthorEditsEvenWhenPlaylistIsPublic() {
        Playlist p = create();
        p = service.update(author, p.id(), p.version(), p.name(), "", "MUSIC_DISC_CAT", true).join();
        Playlist shared = p;
        assertEquals(shared, service.visible(visitor, shared.id()));
        CompletionException error = assertThrows(CompletionException.class,
                () -> service.add(visitor, shared.id(), shared.version(), List.of(track("d"))).join());
        assertEquals(MusicProblem.Code.FORBIDDEN, ((MusicProblem) error.getCause()).code());
    }
    @Test
    void playlistFavoritesRemainActorScopedPersistedAndRemovedWithThePlaylist() {
        Playlist list = create();
        list = service.update(author, list.id(), list.version(), list.name(), "", "MUSIC_DISC_CAT", true).join();
        service.favorite(visitor, list.id(), true).join();
        var key = MusicState.key(visitor, list.id());
        assertEquals(list.id(), new MusicQueries(store).snapshot(visitor).favorites().get(key).playlist());
        assertTrue(new MusicQueries(store).snapshot(author).favorites().isEmpty());
        store.close();
        store = new MusicStore();
        store.open(directory.resolve("music.sqlite")).join();
        service = new PlaylistService(store);
        assertEquals(list.id(), store.snapshot().favorites().get(key).playlist());
        service.delete(author, list.id(), list.version()).join();
        assertTrue(store.snapshot().favorites().isEmpty());
        assertTrue(new MusicQueries(store).snapshot(visitor).playlists().isEmpty());
    }
    @Test
    void copyRemainsIndependentAndVisibilityRevokesFavorites() {
        Playlist p = create();
        p = service.update(author, p.id(), p.version(), p.name(), "", "MUSIC_DISC_CAT", true).join();
        service.favorite(visitor, p.id(), true).join();
        Playlist copy = service.copy(visitor, p.id(), "Copy").join();
        service.update(author, p.id(), p.version(), "Private", "", "MUSIC_DISC_CAT", false).join();
        UUID id = p.id();
        assertThrows(MusicProblem.class, () -> service.visible(visitor, id));
        assertEquals(3, service.visible(visitor, copy.id()).entries().size());
    }
    @Test
    void stableIdsMoveAcrossPagesAndUndoRestoresOrder() {
        Playlist p = create();
        UUID last = p.entries().getLast().id();
        Playlist changed = service.move(author, p.id(), p.version(), Set.of(last), EntryEdits.before(p.entries().getFirst().id())).join();
        assertEquals(last, changed.entries().getFirst().id());
        assertEquals(p.entries(), service.undo(author, p.id()).join().entries());
    }
    @Test void staleOrForeignPlacementsCannotChangeAPlaylistAndNoOpsDoNotCreateUndoEdits() {
        Playlist p = create();
        var last = p.entries().getLast().id();
        var unchanged = service.move(author, p.id(), p.version(), Set.of(last), EntryEdits.end()).join();
        assertEquals(p, unchanged);
        assertFalse(service.canUndo(author, p.id()));
        var forbidden = assertThrows(CompletionException.class,
                () -> service.move(visitor, p.id(), p.version(), Set.of(last), EntryEdits.before(p.entries().getFirst().id())).join());
        assertEquals(MusicProblem.Code.FORBIDDEN, MusicProblem.code(forbidden));
        var missingAnchor = assertThrows(CompletionException.class,
                () -> service.move(author, p.id(), p.version(), Set.of(last), EntryEdits.before(UUID.randomUUID())).join());
        assertEquals(MusicProblem.Code.CONFLICT, MusicProblem.code(missingAnchor));
        service.add(author, p.id(), p.version(), List.of(track("new"))).join();
        var stale = assertThrows(CompletionException.class,
                () -> service.move(author, p.id(), p.version(), Set.of(last), EntryEdits.before(p.entries().getFirst().id())).join());
        assertEquals(MusicProblem.Code.CONFLICT, MusicProblem.code(stale));
        assertEquals(List.of("a", "b", "c", "new"), service.visible(author, p.id()).entries().stream().map(Playlist.Entry::trackId).toList());
    }
    @Test void undoTargetsTheDisplayedPlaylistRatherThanAnotherPlaylistsLatestEdit() {
        Playlist first = create();
        service.move(author, first.id(), first.version(), Set.of(first.entries().getLast().id()), EntryEdits.before(first.entries().getFirst().id())).join();
        Playlist other = service.create(author, "Other", List.of(track("z"))).join();
        Playlist changed = service.update(author, other.id(), other.version(), "Renamed", "", other.icon(), false).join();
        assertTrue(service.canUndo(author, first.id()));
        assertEquals(first.entries(), service.undo(author, first.id()).join().entries());
        assertEquals(changed, service.visible(author, other.id()));
        assertFalse(service.canUndo(author, first.id()));
        assertEquals("Other", service.undo(author, other.id()).join().name());
    }
    @Test
    void concurrentStaleEditCannotOverwriteCommittedChanges() {
        Playlist p = create();
        service.add(author, p.id(), p.version(), List.of(track("d"))).join();
        CompletionException error = assertThrows(CompletionException.class,
                ()
                        -> service.remove(author, p.id(), p.version(), Set.of(p.entries().getFirst().id()))
                                .join());
        assertEquals(MusicProblem.Code.CONFLICT, ((MusicProblem) error.getCause()).code());
        assertEquals(4, store.snapshot().playlists().get(p.id()).entries().size());
    }
    @Test
    void duplicateImportRequestIsIdempotentAndMergePreservesOrder() {
        UUID request = UUID.randomUUID();
        Playlist first = service.imported(author, null, "Imported", List.of(track("b"), track("a")), "wy",
                                        "123", 4, request)
                                 .join();
        Playlist repeated =
                service.imported(author, null, "Ignored", List.of(track("c")), "wy", "123", 4, request)
                        .join();
        assertEquals(first, repeated);
        Playlist merged = service.imported(author, first.id(), "Imported", List.of(track("a"), track("d")),
                                         "wy", "123", 2, UUID.randomUUID())
                                  .join();
        assertEquals(List.of("b", "a", "d"), merged.entries().stream().map(Playlist.Entry::trackId).toList());
        assertEquals(2, store.snapshot().imports().size());
        assertEquals(4, store.snapshot().imports().get(request).total());
    }
    @Test
    void transactionExceptionPublishesNothingAndSurvivesReopen() {
        Playlist p = create();
        MusicState before = store.snapshot();
        assertThrows(CompletionException.class,
                ()
                        -> store.transact(e -> {
                                    e.playlists.clear();
                                    throw new IllegalStateException("fail");
                                })
                                .join());
        assertSame(before, store.snapshot());
        store.close();
        store = new MusicStore();
        store.open(directory.resolve("music.sqlite")).join();
        assertEquals(p, store.snapshot().playlists().get(p.id()));
        assertEquals(track("a"), store.snapshot().tracks().get("a").track());
    }
    @Test
    void privateHistoryDeletionDoesNotAffectAnotherPlayer() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), repeat = UUID.randomUUID(), other = UUID.randomUUID();
        store.transact(e -> {
                 e.recents.put(a, new MusicState.Recent(a, author, "a", UUID.randomUUID(), 1));
                 e.recents.put(b, new MusicState.Recent(b, visitor, "a", UUID.randomUUID(), 2));
                 e.recents.put(repeat, new MusicState.Recent(repeat, author, "a", UUID.randomUUID(), 3));
                 e.recents.put(other, new MusicState.Recent(other, author, "b", UUID.randomUUID(), 4));
                 return null;
             }).join();
        service.forgetRecent(author, "a").join();
        assertEquals(Set.of(b, other), store.snapshot().recents().keySet());
        service.clearRecents(author).join();
        assertEquals(Set.of(b), store.snapshot().recents().keySet());
    }
    @Test
    void playlistDeDuplicationDoesNotDependOnTrackMetadata() {
        Playlist p = service.create(author, "test", List.of(track("a"), track("a"))).join();
        assertEquals(1, p.entries().size());
    }
    @Test
    void readViewHidesOtherPlayersPrivateListsTracksAndHistory() {
        Playlist p = create();
        MusicState visible = new MusicQueries(store).snapshot(visitor);
        assertFalse(visible.playlists().containsKey(p.id()));
        assertTrue(visible.tracks().isEmpty());
        assertEquals(p, new MusicQueries(store).snapshot(author).playlists().get(p.id()));
    }
    @Test
    void multipleUndosAndLaterAppendPreserveTheirOwnChanges() {
        Playlist p = create();
        Playlist moved =
                service.move(author, p.id(), p.version(), Set.of(p.entries().getLast().id()), EntryEdits.before(p.entries().getFirst().id())).join();
        service.remove(author, p.id(), moved.version(), Set.of(p.entries().getFirst().id())).join();
        service.undo(author, p.id()).join();
        Playlist restored = service.undo(author, p.id()).join();
        assertEquals(p.entries(), restored.entries());
    }
}
