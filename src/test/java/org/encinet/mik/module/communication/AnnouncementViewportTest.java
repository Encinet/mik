package org.encinet.mik.module.communication;

import org.encinet.mik.module.communication.AnnouncementCatalog.Announcement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementViewportTest {
    @Test
    void restingPageIncludesDatePartAndAllFourteenBodyRows() {
        var board = new AnnouncementBoard(List.of(new Announcement(1, "正文 😀 é\n".repeat(20))));
        var fragments = AnnouncementViewport.fragments(board, 0, page -> "日期");
        assertEquals("日期 · 1/2", fragments.getFirst().text());
        assertEquals(15, fragments.size());
        assertEquals(IntStream.range(2, 16).boxed().toList(),
                fragments.stream().skip(1).map(AnnouncementViewport.Fragment::row).toList());
        assertTrue(fragments.stream().skip(1).allMatch(fragment -> fragment.text().equals("正文 😀 é")));
    }

    @Test
    void movingTextIsClippedToTheViewportWithoutSplittingGraphemes() {
        String text = "甲😀é👩🏽‍💻🇨🇳".repeat(50);
        var board = new AnnouncementBoard(List.of(new Announcement(1, text), new Announcement(2, text)));
        for (int step = 0; step <= 200; step++) {
            double position = board.maximumStart() * step / 200.0;
            var fragments = AnnouncementViewport.fragments(board, position, page -> "2026-10-06 12:00");
            assertTrue(fragments.size() <= AnnouncementViewport.ROWS * 2);
            assertTrue(fragments.stream().map(AnnouncementViewport.Fragment::page).distinct().count() <= 2);
            for (var fragment : fragments) {
                assertTrue(fragment.left() >= 2);
                assertTrue(fragment.left() + fragment.width() <= AnnouncementViewport.WIDTH - 2);
                assertTrue(fragment.row() >= 0 && fragment.row() < AnnouncementViewport.ROWS);
                assertEquals(AnnouncementReading.textWidth(fragment.text()), fragment.width());
                String original = fragment.row() == 0 ? "2026-10-06 12:00"
                        : board.page(fragment.page()).text().split("\n", -1)[fragment.row() - 2];
                var originalGlyphs = AnnouncementReading.GLYPHS.matcher(original).results()
                        .map(match -> match.group()).toList();
                var clippedGlyphs = AnnouncementReading.GLYPHS.matcher(fragment.text()).results()
                        .map(match -> match.group()).toList();
                assertTrue(originalGlyphs.containsAll(clippedGlyphs));
            }
        }
    }

    @Test
    void advanceMovesTheCurrentPageLeftAndBringsTheNextPageFromTheRight() {
        var board = new AnnouncementBoard(List.of(new Announcement(1, "甲".repeat(35)),
                new Announcement(2, "乙".repeat(35))));
        var fragments = AnnouncementViewport.fragments(board, 0.5, page -> "date");
        var current = fragments.stream().filter(fragment -> fragment.page() == 0 && fragment.row() == 2).findFirst().orElseThrow();
        var next = fragments.stream().filter(fragment -> fragment.page() == 1 && fragment.row() == 2).findFirst().orElseThrow();
        assertTrue(current.left() < AnnouncementViewport.WIDTH / 2.0);
        assertTrue(next.left() > AnnouncementViewport.WIDTH / 2.0);
        assertTrue(current.left() + current.width() < next.left());
    }

    @Test
    void fragmentCountIsIndependentOfCatalogSizeAndPositionsAreBounded() {
        var empty = new AnnouncementBoard(List.of());
        assertTrue(AnnouncementViewport.fragments(empty, 0, page -> "date").isEmpty());
        var board = new AnnouncementBoard(IntStream.range(0, 1000)
                .mapToObj(index -> new Announcement(index, "甲".repeat(600))).toList());
        assertTrue(AnnouncementViewport.fragments(board, 1234.5, page -> "date").size() <= 32);
        assertTrue(AnnouncementViewport.fragments(board, -100, page -> "date").stream().allMatch(fragment -> fragment.page() == 0));
        assertTrue(AnnouncementViewport.fragments(board, 1_000_000, page -> "date").stream()
                .allMatch(fragment -> fragment.page() == board.maximumStart()));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementViewport.fragments(board, Double.NaN, page -> "date"));
    }
}
