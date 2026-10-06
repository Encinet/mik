package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.music.jukebox.JukeboxExperienceMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxControlGuiTest {

    @Test
    void showsSixteenTracksOnEachQueuePage() {
        assertEquals(1, JukeboxControlGui.pageCount(0));
        assertEquals(1, JukeboxControlGui.pageCount(16));
        assertEquals(2, JukeboxControlGui.pageCount(17));
        assertEquals(2, JukeboxControlGui.pageCount(25));
        assertEquals(3, JukeboxControlGui.pageCount(33));
    }

    @Test
    void musicAndRhythmModesHaveDistinctReadableThemes() {
        assertEquals(FloatingMenuAppearance.MUSIC,
                MusicMenuPalette.appearance(JukeboxExperienceMode.MUSIC));
        assertEquals(FloatingMenuAppearance.RHYTHM,
                MusicMenuPalette.appearance(JukeboxExperienceMode.RHYTHM));
    }

    @Test
    void clampsEmptyAndInvalidQueueSizesToOnePage() {
        assertEquals(1, JukeboxControlGui.pageCount(-1));
        assertEquals(1, JukeboxControlGui.pageCount(0));
    }

    @Test
    void formatsPlaybackClockWithoutRoundingIntoTheNextSecond() {
        assertEquals("0:00", JukeboxControlGui.playbackTime(-1));
        assertEquals("0:01", JukeboxControlGui.playbackTime(1_999));
        assertEquals("1:01", JukeboxControlGui.playbackTime(61_000));
        assertEquals("1:01:01", JukeboxControlGui.playbackTime(3_661_999));
    }

    @Test
    void clampsVisualPlaybackProgressToTheAvailableBar() {
        assertEquals(0, JukeboxControlGui.completedProgressSegments(-1, 120_000));
        assertEquals(3, JukeboxControlGui.completedProgressSegments(30_000, 120_000));
        assertEquals(12, JukeboxControlGui.completedProgressSegments(120_000, 120_000));
        assertEquals(12, JukeboxControlGui.completedProgressSegments(180_000, 120_000));
        assertEquals(0, JukeboxControlGui.completedProgressSegments(1_000, 0));
    }

    @Test
    void rendersElapsedTimeAndKnownDurationAsOneCompactProgressLine() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
                JukeboxControlGui.playbackProgress(30_999, Duration.ofMinutes(2)));

        assertEquals("\n▶ [===---------] 0:30 / 2:00", rendered);
    }

    @Test
    void keepsElapsedTimeVisibleWhenTrackDurationIsUnknown() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
                JukeboxControlGui.playbackProgress(61_999, null));

        assertEquals("\n▶ 1:01", rendered);
    }
}
