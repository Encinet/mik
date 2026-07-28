package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxQueueServiceTest {

    @Test
    void identifiesTracksByStableIdAcrossCatalogReloads() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();
        MusicTrack original = track("album/song.mp3", "Song");
        MusicTrack reloaded = track("album/song.mp3", "Song (reloaded metadata)");

        data.addToQueue(original);
        data.addToQueue(reloaded);

        assertEquals(1, data.queueSize());
        assertTrue(data.contains(reloaded));
        assertEquals(0, data.indexOf(reloaded));

        data.removeFromQueue(reloaded);

        assertTrue(data.queueEmpty());
        assertFalse(data.contains(original));
    }

    @Test
    void movesTracksWithoutChangingTheirIdentity() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();
        MusicTrack first = track("first.mp3", "First");
        MusicTrack second = track("second.mp3", "Second");
        data.addToQueue(first);
        data.addToQueue(second);

        data.moveInQueue(1, 0);

        assertEquals("second.mp3", data.queue().getFirst().id());
        assertEquals(1, data.indexOf(first));
    }

    @Test
    void removesStateWhenJukeboxIsDestroyed() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 12.75, 64, -3.25);
        manager.state(location).addToQueue(track("queued.mp3", "Queued"));

        manager.removeState(new Location(null, 12, 64, -4));

        assertTrue(manager.state(location).queueEmpty());
    }

    @Test
    void readOnlyLookupDoesNotCreateJukeboxState() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 3, 64, 5);

        assertNull(manager.findState(location));

        manager.state(location).cyclePlaybackMode();
        assertEquals(JukeboxPlaybackMode.REPEAT_ONE,
                manager.findState(location).playbackMode());
    }

    @Test
    void exposesAnImmutableQueueSnapshot() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();
        data.addToQueue(track("queued.mp3", "Queued"));

        assertThrows(UnsupportedOperationException.class,
                () -> data.queue().clear());
        assertEquals(1, data.queueSize());
    }

    @Test
    void cyclesAllSupportedPlaybackModes() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();

        assertEquals(JukeboxPlaybackMode.REPEAT_ALL, data.playbackMode());
        assertEquals(JukeboxPlaybackMode.REPEAT_ONE, data.cyclePlaybackMode());
        assertEquals(JukeboxPlaybackMode.SHUFFLE, data.cyclePlaybackMode());
        assertEquals(JukeboxPlaybackMode.LIBRARY_SHUFFLE, data.cyclePlaybackMode());
        assertEquals(JukeboxPlaybackMode.REPEAT_ALL, data.cyclePlaybackMode());
    }

    @Test
    void repeatAllAdvancesAndWrapsThePlaylist() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 1, 64, 2);
        MusicTrack first = track("first.mp3", "First");
        MusicTrack second = track("second.mp3", "Second");
        manager.state(location).addToQueue(first);
        manager.state(location).addToQueue(second);

        assertEquals(first, manager.nextTrack(location, null, false));
        assertEquals(second, manager.nextTrack(location, first, true));
        assertEquals(first, manager.nextTrack(location, second, true));
    }

    @Test
    void repeatOneOnlyRepeatsAfterNaturalCompletion() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 1, 64, 2);
        MusicTrack first = track("first.mp3", "First");
        MusicTrack second = track("second.mp3", "Second");
        manager.state(location).addToQueue(first);
        manager.state(location).addToQueue(second);
        manager.state(location).setPlaybackMode(JukeboxPlaybackMode.REPEAT_ONE);

        assertEquals(first, manager.nextTrack(location, first, true));
        assertEquals(second, manager.nextTrack(location, first, false));
    }

    @Test
    void shuffleUsesOnlyPlaylistTracksAndAvoidsImmediateRepeats() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 1, 64, 2);
        MusicTrack current = track("current.mp3", "Current");
        MusicTrack other = track("other.mp3", "Other");
        manager.state(location).addToQueue(current);
        manager.state(location).addToQueue(other);
        manager.state(location).setPlaybackMode(JukeboxPlaybackMode.SHUFFLE);

        assertEquals(other, manager.nextTrack(location, current, true));

        manager.state(location).removeFromQueue(other);
        assertEquals(current, manager.nextTrack(location, current, true));
    }

    @Test
    void libraryShuffleUsesTheDynamicLibraryWithoutRepeatingTheCurrentTrack() {
        Location location = new Location(null, 1, 64, 2);
        MusicTrack current = track("current.mp3", "Current");
        MusicTrack other = track("other.mp3", "Other");
        MusicTrack addedLater = track("later.mp3", "Later");
        AtomicReference<List<MusicTrack>> library = new AtomicReference<>(
                List.of(current, other));
        JukeboxQueueService manager = new JukeboxQueueService(
                new MusicTrackSelector(), library::get);
        manager.state(location).setPlaybackMode(JukeboxPlaybackMode.LIBRARY_SHUFFLE);

        assertTrue(manager.state(location).queueEmpty());
        assertEquals(other, manager.nextTrack(location, current, true));

        library.set(List.of(current, addedLater));
        assertEquals(addedLater, manager.nextTrack(location, current, false));
    }

    @Test
    void libraryShuffleDoesNotRepeatWhenOnlyTheCurrentTrackIsAvailable() {
        Location location = new Location(null, 1, 64, 2);
        MusicTrack current = track("current.mp3", "Current");
        JukeboxQueueService manager = new JukeboxQueueService(
                new MusicTrackSelector(), () -> List.of(current));
        manager.state(location).setPlaybackMode(JukeboxPlaybackMode.LIBRARY_SHUFFLE);

        assertNull(manager.nextTrack(location, current, true));
    }

    @Test
    void resolvesCurrentLibraryTrackOutsideThePlaylist() {
        Location location = new Location(null, 1, 64, 2);
        MusicTrack libraryTrack = track("library.mp3", "Library");
        JukeboxQueueService manager = new JukeboxQueueService(
                new MusicTrackSelector(), () -> List.of(libraryTrack));

        assertEquals(libraryTrack, manager.trackById(location, libraryTrack.id()));
    }

    @Test
    void rejectsNullQueueEntries() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();

        assertThrows(NullPointerException.class, () -> data.addToQueue(null));
        assertTrue(data.queueEmpty());
    }

    private static MusicTrack track(String id, String name) {
        return new MusicTrack(id,
                new TrackDetails(name, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }

    private static JukeboxQueueService manager() {
        return new JukeboxQueueService(new MusicTrackSelector(), List::of);
    }
}
