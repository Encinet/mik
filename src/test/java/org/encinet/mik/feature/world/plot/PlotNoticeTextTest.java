package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotNoticeTextTest {
    @Test
    void acceptsTheFullLimitIncludingSupplementaryUnicodeCharacters() {
        for (String character : List.of("x", "地", "🌿")) {
            String text = character.repeat(PlotNoticeText.MAX_LENGTH);
            assertEquals(text, PlotNoticeText.normalize(text));
            PlotProblem problem = assertThrows(PlotProblem.class,
                    () -> PlotNoticeText.normalize(text + character));
            assertEquals(Message.PLOT_ERROR_NOTICE_TEXT, problem.message());
            assertArrayEquals(new Object[] { PlotNoticeText.MAX_LENGTH }, problem.args());
        }
    }

    @Test
    void removesLineAndPerLineLimitsWithoutChangingNormalizationOrClearing() {
        for (String text : List.of("x".repeat(1000), "one\ntwo\nthree",
                "Long line ".repeat(100) + "\n" + "Another line\n".repeat(100).strip()))
            assertEquals(text, PlotNoticeText.normalize(text));
        assertEquals("First\nSecond\nThird", PlotNoticeText.normalize("  First\r\nSecond\rThird  "));
        assertEquals("", PlotNoticeText.normalize("  \r\n  "));
        assertThrows(PlotProblem.class, () -> PlotNoticeText.normalize(null));
        for (String control : List.of("\t", "\u0000", "\u0007", "\u007f", "\u0085"))
            assertThrows(PlotProblem.class, () -> PlotNoticeText.normalize("before" + control + "after"));
    }

    @Test
    void previewsCollapseWhitespaceAndStayBoundedWithoutSplittingUnicode() {
        assertEquals("", PlotNoticeText.preview(""));
        assertEquals("First Second Third", PlotNoticeText.preview(" First\n\nSecond  Third "));
        assertEquals("x".repeat(PlotNoticeText.PREVIEW_LENGTH),
                PlotNoticeText.preview("x".repeat(PlotNoticeText.PREVIEW_LENGTH)));
        for (String character : List.of("x", "地", "🌿")) {
            String preview = PlotNoticeText.preview(character.repeat(PlotNoticeText.MAX_LENGTH));
            assertEquals(character.repeat(PlotNoticeText.PREVIEW_LENGTH - 1) + "…", preview);
            assertEquals(PlotNoticeText.PREVIEW_LENGTH, preview.codePointCount(0, preview.length()));
        }
    }

    @Test
    void boardPreviewDoesNotReplaceTheStoredBody() {
        String body = "Full announcement 🌿\n".repeat(100).strip();
        PlotNoticeBoard board = new PlotNoticeBoard(UUID.randomUUID(), "Garden",
                UUID.randomUUID(), "Owner", body, "");
        assertEquals(body, board.body());
        assertEquals(PlotNoticeText.preview(body), board.preview());
        assertTrue(board.preview().endsWith("…"));
    }
}
