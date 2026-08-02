package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

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
        MusicBrowserSessions.Session first = new MusicBrowserSessions.Session();
        sessions.beginSearch(playerId, first, "first");
        int firstGeneration = first.generation();

        MusicBrowserSessions.Session current = sessions.beginSearch(playerId, first, "second");

        assertFalse(sessions.completeSearch(first, firstGeneration,
                List.of(track("stale")), null, 0, MusicPlaybackStats.EMPTY));
        assertTrue(current.loading());
        assertTrue(current.tracks().isEmpty());
    }

    @Test
    void publishesAnImmutableSearchSnapshotIntoTheCurrentSession() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        MusicBrowserSessions.Session state = new MusicBrowserSessions.Session();
        sessions.beginSearch(playerId, state, "song");
        MusicTrack track = track("online");

        assertTrue(sessions.completeSearch(state, state.generation(),
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

        MusicBrowserSessions.Session library = new MusicBrowserSessions.Session();
        sessions.showLibrary(playerId, library, List.of(audio, nbs), 4, MusicPlaybackStats.EMPTY);
        sessions.cycleSection(playerId, library, MusicPlaybackStats.EMPTY);

        assertEquals(MusicBrowserSessions.Section.NBS, library.section());
        assertEquals(List.of(nbs), library.tracks());
        assertEquals(0, library.page());
        assertEquals(List.of(nbs), sessions.tracksInSection(
                playerId, List.of(audio, nbs)));

        MusicBrowserSessions.Session search = new MusicBrowserSessions.Session();
        sessions.beginSearch(playerId, search, "song");
        assertTrue(sessions.completeSearch(search, search.generation(),
                List.of(audio, nbs), null, 0, MusicPlaybackStats.EMPTY));
        assertEquals(MusicBrowserSessions.Section.NBS, search.section());
        assertEquals(List.of(nbs), search.tracks());

        MusicBrowserSessions.Session reopened = new MusicBrowserSessions.Session();
        sessions.showLibrary(playerId, reopened, List.of(audio, nbs), 0, MusicPlaybackStats.EMPTY);
        assertEquals(MusicBrowserSessions.Section.NBS, reopened.section());
        assertEquals(List.of(nbs), reopened.tracks());

        sessions.removePlayer(playerId);
        MusicBrowserSessions.Session afterQuit = new MusicBrowserSessions.Session();
        sessions.showLibrary(playerId, afterQuit, List.of(audio, nbs), 0, MusicPlaybackStats.EMPTY);
        assertEquals(MusicBrowserSessions.Section.ALL, afterQuit.section());
        assertEquals(List.of(audio, nbs), afterQuit.tracks());
    }

    @Test
    void focusedTrackFollowsTheVisiblePageAndPreservesAVisibleChoice() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        MusicBrowserSessions.Session state = new MusicBrowserSessions.Session();
        MusicTrack first = track("first");
        MusicTrack second = track("second");
        MusicTrack third = track("third");
        sessions.showLibrary(UUID.randomUUID(), state,
                List.of(first, second, third), 0, MusicPlaybackStats.EMPTY);

        assertSame(first, sessions.focusedTrackOnPage(state, 0, 2));
        sessions.focusTrack(state, second.id());
        assertSame(second, sessions.focusedTrackOnPage(state, 0, 2));
        assertSame(third, sessions.focusedTrackOnPage(state, 2, 3));
        assertEquals(third.id(), state.focusedTrackId());
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
