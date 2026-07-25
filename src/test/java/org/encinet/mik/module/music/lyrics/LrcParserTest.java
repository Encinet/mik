package org.encinet.mik.module.music.lyrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LrcParserTest {

    private final LrcParser parser = new LrcParser();

    @Test
    void parsesFractionsMultipleTimestampsMetadataAndOffset() {
        Lyrics lyrics = parser.parse(new LyricSourceText("""
                [ar:Artist]
                [offset:+250]
                [00:01.5][00:02.050]First
                [01:02:03.004]Long
                [00:02.05]Again
                """, null, null), Duration.ofHours(2));

        assertEquals(List.of(1_750L, 2_300L, 3_723_254L), lyrics.lines().stream()
                .map(LyricLine::timestampMillis).toList());
        assertEquals("First / Again", lyrics.lines().get(1).text());
    }

    @Test
    void alignsTimestampedTranslationsAndRomanizationByPosition() {
        Lyrics lyrics = parser.parse(new LyricSourceText("""
                [00:01.00]One
                [00:05.00]Two
                """, """
                [00:01.05]一
                [00:05.00]二
                """, """
                Ichi
                Ni
                """), Duration.ofSeconds(10));

        assertEquals("一", lyrics.lines().getFirst().translation());
        assertEquals("二", lyrics.lines().get(1).translation());
        assertEquals("Ichi", lyrics.lines().getFirst().romanization());
        assertEquals("Ni", lyrics.lines().get(1).romanization());
    }

    @Test
    void distributesPlainEmbeddedLyricsAcrossKnownDuration() {
        Lyrics lyrics = parser.parse(new LyricSourceText("First\n\nSecond\nThird", null, null),
                Duration.ofSeconds(12));

        assertEquals(List.of(0L, 4_000L, 8_000L), lyrics.lines().stream()
                .map(LyricLine::timestampMillis).toList());
        assertEquals("Second", lyrics.lineAt(7_999).orElseThrow().text());
        assertEquals("Third", lyrics.lineAt(8_000).orElseThrow().text());
    }

    @Test
    void rejectsOversizedInputAndIgnoresMalformedLines() {
        String oversized = "x".repeat(LrcParser.MAX_LYRIC_BYTES + 1);
        assertNull(parser.parse(new LyricSourceText(oversized, null, null), null));

        Lyrics lyrics = parser.parse(new LyricSourceText("""
                [bad]ignored as plain
                [00:99.00]also plain
                [00:01.00]Valid
                """, null, null), null);
        assertEquals(List.of("Valid"), lyrics.lines().stream().map(LyricLine::text).toList());
    }

    @Test
    void returnsNoLineBeforeFirstTimestamp() {
        Lyrics lyrics = parser.parse(new LyricSourceText("[00:05.00]Later", null, null), null);

        assertEquals(java.util.Optional.empty(), lyrics.lineAt(4_999));
        assertEquals("Later", lyrics.lineAt(5_000).orElseThrow().text());
    }

    @Test
    void preservesBlankTimestampAsADisplayClearEvent() {
        Lyrics lyrics = parser.parse(new LyricSourceText("""
                [00:01.00]Line
                [00:05.00]
                [00:08.00]Next
                """, null, null), null);

        assertEquals("Line", lyrics.lineAt(4_999).orElseThrow().text());
        assertEquals("", lyrics.lineAt(5_000).orElseThrow().text());
        assertEquals("Next", lyrics.lineAt(8_000).orElseThrow().text());
    }

    @Test
    void usesTranslationWhenOriginalExceedsTheInputLimit() {
        Lyrics lyrics = parser.parse(new LyricSourceText(
                "x".repeat(LrcParser.MAX_LYRIC_BYTES + 1),
                "[00:01.00]Fallback", null), null);

        assertEquals("Fallback", lyrics.lineAt(1_000).orElseThrow().text());
    }
}
