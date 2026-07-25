package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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

        manager.state(location).toggleAutoPlay();
        assertTrue(manager.findState(location).autoPlay());
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
    void autoPlayHasAnExplicitDisableOperation() {
        JukeboxQueueService.JukeboxState data = new JukeboxQueueService.JukeboxState();
        data.toggleAutoPlay();

        data.disableAutoPlay();

        assertFalse(data.autoPlay());
    }

    @Test
    void sequentialPlaybackUsesTheQueuedTrackSnapshot() {
        JukeboxQueueService manager = manager();
        Location location = new Location(null, 1, 64, 2);
        MusicTrack online = new MusicTrack("lx:kw:1",
                new TrackDetails("Online", "Artist", null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "1", java.util.List.of("320k"),
                        "{\"source\":\"kw\",\"meta\":{\"songId\":\"1\"}}"));
        manager.state(location).addToQueue(online);

        assertEquals(online, manager.nextTrack(location));
    }

    @Test
    void randomModeCanSelectAFullyCachedOnlineTrack() {
        MusicTrack cachedOnline = new MusicTrack("lx:kw:1",
                new TrackDetails("Online", "Artist", null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "1", List.of("320k"),
                        "{\"source\":\"kw\",\"meta\":{\"songId\":\"1\"}}"));
        JukeboxQueueService manager = new JukeboxQueueService(
                new MusicTrackPool(List::of, () -> List.of(cachedOnline)),
                new MusicTrackSelector());
        Location location = new Location(null, 1, 64, 2);
        manager.state(location).toggleRandomMode();

        assertEquals(cachedOnline, manager.nextTrack(location));
        assertEquals(1, manager.availableTrackCount());
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
        return new JukeboxQueueService(
                new MusicTrackPool(java.util.List::of, java.util.List::of),
                new MusicTrackSelector());
    }
}
