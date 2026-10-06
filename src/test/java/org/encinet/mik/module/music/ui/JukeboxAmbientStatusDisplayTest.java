package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.encinet.mik.module.menu.runtime.WorldTextDisplayService;
import org.encinet.mik.module.music.jukebox.PlaybackStatus;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackSnapshot;
import org.encinet.mik.module.music.jukebox.JukeboxExperienceMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxAmbientStatusDisplayTest {
    @Test
    void labelUsesTheFaceClosestToTheViewer() {
        Location center = new Location(null, 10.5, 64, 10.5);
        assertEquals(new JukeboxAmbientStatusDisplay.Face(1, 0, -90.0F),
                JukeboxAmbientStatusDisplay.faceToward(center,
                        new Location(null, 14, 64, 10.5)));
        assertEquals(new JukeboxAmbientStatusDisplay.Face(-1, 0, 90.0F),
                JukeboxAmbientStatusDisplay.faceToward(center,
                        new Location(null, 7, 64, 10.5)));
        assertEquals(new JukeboxAmbientStatusDisplay.Face(0, 1, 0.0F),
                JukeboxAmbientStatusDisplay.faceToward(center,
                        new Location(null, 10.5, 64, 14)));
        assertEquals(new JukeboxAmbientStatusDisplay.Face(0, -1, 180.0F),
                JukeboxAmbientStatusDisplay.faceToward(center,
                        new Location(null, 10.5, 64, 7)));
    }

    @Test
    void clearLabelFitsOnTheJukeboxFace() {
        World world = world(target -> false, (x, z) -> true, new AtomicInteger());
        JukeboxAmbientStatusDisplay.Label label = JukeboxAmbientStatusDisplay.visibleLabel(
                new Location(world, 0.5, 64, 0.5),
                new Location(world, 3, 65.6, 0.5));

        assertNotNull(label);
        assertEquals(new JukeboxAmbientStatusDisplay.Face(1, 0, -90.0F), label.face());
        assertEquals(1.01, label.location().getX(), 0.0001);
        assertEquals(64.175, label.location().getY(), 0.0001);
        assertTrue(WorldTextDisplayService.PANEL_WIDTH < 1.0);
    }

    @Test
    void labelSitsJustOutsideEachJukeboxSide() {
        World world = world(target -> false, (x, z) -> true, new AtomicInteger());
        Location center = new Location(world, 0.5, 64, 0.5);

        assertEquals(-0.01, JukeboxAmbientStatusDisplay.visibleLabel(center,
                new Location(world, -2, 65.6, 0.5)).location().getX(), 0.0001);
        assertEquals(1.01, JukeboxAmbientStatusDisplay.visibleLabel(center,
                new Location(world, 3, 65.6, 0.5)).location().getX(), 0.0001);
        assertEquals(-0.01, JukeboxAmbientStatusDisplay.visibleLabel(center,
                new Location(world, 0.5, 65.6, -2)).location().getZ(), 0.0001);
        assertEquals(1.01, JukeboxAmbientStatusDisplay.visibleLabel(center,
                new Location(world, 0.5, 65.6, 3)).location().getZ(), 0.0001);
    }

    @Test
    void diagonalViewerGetsTheOtherVisibleFaceWhenTheNearestFaceIsBlocked() {
        World world = world(target -> Math.abs(target.getX() - 1.01) < 0.0001,
                (x, z) -> true, new AtomicInteger());

        JukeboxAmbientStatusDisplay.Label label = JukeboxAmbientStatusDisplay.visibleLabel(
                new Location(world, 0.5, 64, 0.5),
                new Location(world, 2.5, 65.6, 2.0));

        assertNotNull(label);
        assertEquals(new JukeboxAmbientStatusDisplay.Face(0, 1, 0.0F), label.face());
    }

    @Test
    void anyBlockedTextCornerHidesTheLabel() {
        World world = world(target -> target.getY() > 64.8,
                (x, z) -> true, new AtomicInteger());

        Location center = new Location(world, 0.5, 64, 0.5);
        Location eye = new Location(world, 3, 65.6, 0.5);
        assertNotNull(JukeboxAmbientStatusDisplay.visibleLabel(center, eye, 0.42));
        assertNull(JukeboxAmbientStatusDisplay.visibleLabel(center, eye, 0.65));
        assertNull(JukeboxAmbientStatusDisplay.visibleLabel(center, eye, 0.84));
    }

    @Test
    void blockedHorizontalCornerHidesTheNarrowLabel() {
        World world = world(target -> target.getZ() > 0.94,
                (x, z) -> true, new AtomicInteger());

        assertNull(JukeboxAmbientStatusDisplay.visibleLabel(
                new Location(world, 0.5, 64, 0.5),
                new Location(world, 3, 65.6, 0.5)));
    }

    @Test
    void visibilityCheckDoesNotTraceThroughUnloadedChunks() {
        AtomicInteger traces = new AtomicInteger();
        World world = world(target -> false, (x, z) -> x != 1, traces);

        assertNull(JukeboxAmbientStatusDisplay.visibleLabel(
                new Location(world, 15.5, 64, 0.5),
                new Location(world, 17.5, 65.6, 0.5)));
        assertEquals(0, traces.get());
    }

    @Test
    void titleScrollsAtWholeGraphemesAndKeepsControlCharactersOffTheDisplay() {
        assertEquals("Rain and Snow", JukeboxAmbientStatusDisplay.titleWindow(
                "  Rain\n\r and  Snow \u0000 ", 0));

        String raw = "A".repeat(23) + " 👩‍🎤 " + "B".repeat(25);
        String[] lines = JukeboxAmbientStatusDisplay.titleWindow(raw, 0).split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("A"));
        assertTrue(JukeboxAmbientStatusDisplay.titleWindow(raw, 10).contains("👩‍🎤"));
        assertTrue(JukeboxAmbientStatusDisplay.titleWindow(raw, 15).contains("B"));
        assertBounded(lines, 82);
    }

    @Test
    void longCjkAndEmojiTitlesRemainWithinTheFaceAndReachTheEnd() {
        String title = "曲".repeat(18) + "终";
        assertEquals("曲".repeat(9) + "\n" + "曲".repeat(9),
                JukeboxAmbientStatusDisplay.titleWindow(title, 0));
        assertEquals("曲".repeat(9) + "\n终",
                JukeboxAmbientStatusDisplay.titleWindow(title, 5));
        assertBounded(JukeboxAmbientStatusDisplay.titleWindow("🎵".repeat(40), 10)
                .split("\n"), 82);
        assertTrue(JukeboxAmbientStatusDisplay.scrollLine("👩‍🎤".repeat(8), 8, 82)
                .contains("👩‍🎤"));
    }

    @Test
    void overlongLinePausesAtBothEndsAndScrollsWholeEmoji() {
        String line = "A".repeat(5) + "B".repeat(5) + "C".repeat(5);
        assertEquals("A".repeat(5), JukeboxAmbientStatusDisplay.scrollLine(line, 0, 30));
        assertEquals("A".repeat(4) + "B",
                JukeboxAmbientStatusDisplay.scrollLine(line, 3, 30));
        assertEquals("C".repeat(5), JukeboxAmbientStatusDisplay.scrollLine(line, 12, 30));
        for (long second = 0; second < 16; second++) {
            String frame = JukeboxAmbientStatusDisplay.scrollLine("👩‍🎤".repeat(8),
                    second, 82);
            assertTrue(frame.matches("(?:👩‍🎤)+"));
        }
    }

    @Test
    void progressUsesActualDurationAndClampsOutOfRangePositions() {
        Duration duration = Duration.ofMinutes(2);
        assertEquals("▱".repeat(9), progress(PlaybackStatus.PLAYING, -1, duration, 0));
        assertEquals("▰".repeat(4) + "▱".repeat(5),
                progress(PlaybackStatus.PLAYING, 60_000, duration, 0));
        assertEquals("▰".repeat(9),
                progress(PlaybackStatus.PLAYING, 180_000, duration, 0));
    }

    @Test
    void loadingAndUnknownDurationUseMovingPulse() {
        String first = progress(PlaybackStatus.LOADING, 60_000, Duration.ofMinutes(2), 0);
        String next = progress(PlaybackStatus.LOADING, 60_000, Duration.ofMinutes(2), 1);
        assertNotEquals(first, next);
        assertEquals(3, first.codePoints().filter(codePoint -> codePoint == '▰').count());
        assertEquals(first, progress(PlaybackStatus.PLAYING, 60_000, null, 0));
    }

    @Test
    void progressUsesTheActiveModeAccent() {
        var rhythmBar = JukeboxAmbientStatusDisplay.progressBar(
                PlaybackStatus.PLAYING, 60_000, Duration.ofMinutes(2), 0,
                MusicMenuPalette.RHYTHM);
        assertEquals(MusicMenuPalette.RHYTHM, rhythmBar.color());
        assertNotEquals(MusicMenuPalette.MUSIC, rhythmBar.color());
    }

    @Test
    void playbackCardShowsMetadataClockAndProgressInReadingOrder() {
        var card = JukeboxAmbientStatusDisplay.playbackCard(
                "Station Lights", "♪ Play",
                List.of("Artist: Lantern", "Album: Night"),
                new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 45_000),
                Duration.ofMinutes(3), true, false,
                MusicMenuPalette.MUSIC, MusicMenuPalette.MUSIC, 0);

        assertEquals("Station Lights\nArtist: Lantern\nAlbum: Night\n"
                        + "♪ Play\n▶ 0:45 / 3:00\n"
                        + "▰".repeat(2) + "▱".repeat(7),
                PlainTextComponentSerializer.plainText().serialize(card));
        assertEquals(0.56, JukeboxAmbientStatusDisplay.textHeight(card), 0.0001);
        assertEquals(TextDecoration.State.NOT_SET,
                card.decoration(TextDecoration.BOLD));
    }

    @Test
    void ambientSurfaceMatchesTheSelectedMusicMode() {
        int music = MusicMenuPalette.ambientBackground(JukeboxExperienceMode.MUSIC);
        int rhythm = MusicMenuPalette.ambientBackground(JukeboxExperienceMode.RHYTHM);

        assertNotEquals(music, rhythm);
        assertEquals(0xD0, music >>> 24);
        assertEquals(0xD0, rhythm >>> 24);
    }

    @Test
    void unknownDurationStillShowsElapsedTimeWithoutInventingATotal() {
        assertEquals("1:05", JukeboxAmbientStatusDisplay.playbackTimeLabel(65_000, null));
        assertEquals("1:00 / 1:00", JukeboxAmbientStatusDisplay.playbackTimeLabel(
                90_000, Duration.ofMinutes(1)));

        var vanilla = JukeboxAmbientStatusDisplay.playbackCard(
                "Music Disc", "♪ Music mode · Playing", List.of(),
                new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 0),
                null, false, false, MusicMenuPalette.MUSIC, MusicMenuPalette.MUSIC, 0);
        assertTrue(!PlainTextComponentSerializer.plainText().serialize(vanilla).contains("▶"));
    }

    @Test
    void longLocalizedCardKeepsEveryFrameInsideOneBlockFace() {
        String title = "长曲名👩‍🎤".repeat(12);
        List<String> metadata = List.of("歌手：" + "某位歌手".repeat(8),
                "原作者：" + "另一个名字".repeat(8), "专辑：" + "多年合集".repeat(8));
        for (long second = 0; second < 90; second++) {
            var card = JukeboxAmbientStatusDisplay.playbackCard(title,
                    "✦ 音游模式 · 正在等待玩家", metadata,
                    new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, second * 1000),
                    Duration.ofMinutes(4), true, false,
                    MusicMenuPalette.RHYTHM, MusicMenuPalette.RHYTHM, second);
            String[] lines = PlainTextComponentSerializer.plainText().serialize(card)
                    .split("\n", -1);
            assertTrue(lines.length <= 7);
            assertBounded(lines, 82);
            assertTrue(JukeboxAmbientStatusDisplay.textHeight(card) < 0.7);
        }
    }

    @Test
    void metadataRotatesWithoutRemovingTheArtistOrProgress() {
        List<String> metadata = List.of("Artist", "Original author", "Album");
        String first = cardAt(metadata, 0);
        String next = cardAt(metadata, 8);
        assertTrue(first.contains("Artist\nOriginal author"));
        assertTrue(next.contains("Artist\nAlbum"));
        assertTrue(first.endsWith("▱".repeat(9)));
        assertTrue(next.endsWith("▱".repeat(9)));
    }

    private static String cardAt(List<String> metadata, long second) {
        var card = JukeboxAmbientStatusDisplay.playbackCard("Song", "♪ Play", metadata,
                new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 0), null,
                false, true, MusicMenuPalette.MUSIC, MusicMenuPalette.MUSIC, second);
        return PlainTextComponentSerializer.plainText().serialize(card);
    }

    private static void assertBounded(String[] lines, int maxPixels) {
        for (String line : lines) {
            assertTrue(JukeboxAmbientStatusDisplay.glyphWidth(line) <= maxPixels,
                    () -> "Text exceeds side label: " + line);
        }
    }

    private static String progress(PlaybackStatus status, long positionMillis,
                                   Duration duration, long second) {
        return PlainTextComponentSerializer.plainText().serialize(
                JukeboxAmbientStatusDisplay.progressBar(status, positionMillis,
                        duration, second));
    }

    private static World world(Predicate<Location> blocked,
                               BiPredicate<Integer, Integer> loaded,
                               AtomicInteger traces) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isChunkLoaded" -> loaded.test((int) args[0], (int) args[1]);
                    case "rayTraceBlocks" -> {
                        traces.incrementAndGet();
                        Location eye = (Location) args[0];
                        Vector direction = (Vector) args[1];
                        double distance = (double) args[2];
                        Location target = eye.clone().add(direction.clone().multiply(distance));
                        yield blocked.test(target)
                                ? new RayTraceResult(eye.toVector().add(
                                        direction.clone().multiply(distance * 0.5))) : null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "getName" -> "jukebox-visibility-test";
                    default -> null;
                });
    }
}
