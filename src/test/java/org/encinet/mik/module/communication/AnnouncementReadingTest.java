package org.encinet.mik.module.communication;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementReadingTest {
    @Test
    void longCopyRemainsCompleteAndReadableAcrossPages() {
        String content = "甲".repeat(800) + "\n\n最后一段 with  two spaces 😀";
        List<String> pages = AnnouncementReading.pages(content);

        assertTrue(pages.size() > 1);
        assertTrue(pages.stream().allMatch(page -> page.split("\n", -1).length <= 14));
        assertEquals(content.replace("\n", ""),
                String.join("", pages).replace("\n", ""));
        assertTrue(pages.getLast().contains("最后一段 with  two spaces 😀"));
    }

    @Test
    void longUnbrokenParagraphsKeepUnicodeAndWhitespaceWithoutTruncation() {
        String content = "😀é日本語".repeat(10000) + "  最后";
        List<String> columns = AnnouncementReading.pages(content);
        assertEquals(content, String.join("", columns).replace("\n", ""));
        assertTrue(columns.stream().allMatch(column -> column.split("\n", -1).length <= 14));
    }

    @Test
    void wrapsAtGraphemeBoundariesAndKeepsEachLineWithinItsPixelBudget() {
        String content = ("甲".repeat(32) + " 👩🏽‍💻 é 🇨🇳 ́").repeat(60);
        var lines = AnnouncementReading.lines(content);
        assertEquals(content, String.join("", lines));
        assertTrue(lines.stream().allMatch(line -> AnnouncementReading.textWidth(line) <= 320));
        var original = AnnouncementReading.GLYPHS.matcher(content).results().map(match -> match.group()).toList();
        var wrapped = lines.stream().flatMap(line -> AnnouncementReading.GLYPHS.matcher(line)
                .results().map(match -> match.group())).toList();
        assertEquals(original, wrapped);
    }
}
