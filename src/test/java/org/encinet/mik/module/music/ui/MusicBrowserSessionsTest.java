package org.encinet.mik.module.music.ui;

import org.bukkit.inventory.Inventory;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicBrowserSessionsTest {

    @Test
    void rejectsResultFromAnOlderSearchGeneration() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory firstInventory = inventory();
        MusicBrowserSessions.Session first = sessions.beginSearch(playerId, "first");
        int firstGeneration = first.generation();
        sessions.attachInventory(first, firstInventory);

        MusicBrowserSessions.Session current = sessions.beginSearch(playerId, "second");
        Inventory currentInventory = inventory();
        sessions.attachInventory(current, currentInventory);

        assertFalse(sessions.completeSearch(playerId, first, firstGeneration,
                firstInventory, List.of(track("stale")), null, 0, MusicPlaybackStats.EMPTY));
        assertTrue(current.loading());
        assertTrue(current.tracks().isEmpty());
    }

    @Test
    void closingCurrentInventoryInvalidatesLateResultsAndOldClicks() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory inventory = inventory();
        MusicBrowserSessions.Session state = sessions.beginSearch(playerId, "song");
        int generation = state.generation();
        sessions.attachInventory(state, inventory);

        assertTrue(sessions.isCurrentInventory(playerId, inventory, generation,
                state.view(), state.page()));
        sessions.closeInventory(playerId, inventory);

        assertFalse(sessions.isCurrentInventory(playerId, inventory, generation,
                state.view(), state.page()));
        assertFalse(sessions.completeSearch(playerId, state, generation, inventory,
                List.of(track("late")), null, 0, MusicPlaybackStats.EMPTY));
    }

    @Test
    void publishesAnImmutableSearchSnapshotIntoTheCurrentSession() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory inventory = inventory();
        MusicBrowserSessions.Session state = sessions.beginSearch(playerId, "song");
        sessions.attachInventory(state, inventory);
        MusicTrack track = track("online");

        assertTrue(sessions.completeSearch(playerId, state, state.generation(), inventory,
                List.of(track), null, 2, MusicPlaybackStats.EMPTY));
        assertSame(track, state.tracks().getFirst());
        assertFalse(state.loading());
    }

    @Test
    void nbsSectionFiltersEveryViewAndPersistsUntilPlayerRemoval() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        MusicTrack audio = track("audio");
        MusicTrack nbs = nbsTrack("notes");

        MusicBrowserSessions.Session library = sessions.showLibrary(
                playerId, List.of(audio, nbs), 4, MusicPlaybackStats.EMPTY);
        sessions.cycleSection(playerId, library, MusicPlaybackStats.EMPTY);

        assertEquals(MusicBrowserSessions.Section.NBS, library.section());
        assertEquals(List.of(nbs), library.tracks());
        assertEquals(0, library.page());
        assertEquals(List.of(nbs), sessions.tracksInSection(
                playerId, List.of(audio, nbs)));

        Inventory searchInventory = inventory();
        MusicBrowserSessions.Session search = sessions.beginSearch(playerId, "song");
        sessions.attachInventory(search, searchInventory);
        assertTrue(sessions.completeSearch(playerId, search, search.generation(),
                searchInventory, List.of(audio, nbs), null, 0, MusicPlaybackStats.EMPTY));
        assertEquals(MusicBrowserSessions.Section.NBS, search.section());
        assertEquals(List.of(nbs), search.tracks());

        sessions.closeInventory(playerId, searchInventory);
        MusicBrowserSessions.Session reopened = sessions.showLibrary(
                playerId, List.of(audio, nbs), 0, MusicPlaybackStats.EMPTY);
        assertEquals(MusicBrowserSessions.Section.NBS, reopened.section());
        assertEquals(List.of(nbs), reopened.tracks());

        sessions.removePlayer(playerId);
        MusicBrowserSessions.Session afterQuit = sessions.showLibrary(
                playerId, List.of(audio, nbs), 0, MusicPlaybackStats.EMPTY);
        assertEquals(MusicBrowserSessions.Section.ALL, afterQuit.section());
        assertEquals(List.of(audio, nbs), afterQuit.tracks());
    }

    private static Inventory inventory() {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "test-inventory";
                    }
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) {
                        return null;
                    }
                    if (type == boolean.class) {
                        return false;
                    }
                    return 0;
                });
    }

    private static MusicTrack track(String id) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id + ".mp3")));
    }

    private static MusicTrack nbsTrack(String id) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "NBS", AudioProperties.EMPTY),
                new TrackTarget.NbsFile(Path.of(id + ".nbs")));
    }
}
