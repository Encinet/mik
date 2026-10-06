package org.encinet.mik.module.communication;

import org.encinet.mik.module.communication.AnnouncementCatalog.Announcement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementBoardTest {
    @Test
    void eachPageContainsOneCompleteShortAnnouncement() {
        var notices = shortNotices(8);
        var board = new AnnouncementBoard(notices);
        assertEquals(8, board.size());
        for (int index = 0; index < board.size(); index++) {
            assertEquals(index, board.page(index).announcementIndex());
            assertEquals(notices.get(index).content(), board.page(index).text());
        }
    }

    @Test
    void longBodiesContinueInAdjacentPagesWithoutLosingTextOrEmoji() {
        String content = ("第一行 😀 with  two spaces\n第二行公告正文\n\n").repeat(20) + "最后一行";
        var board = new AnnouncementBoard(List.of(new Announcement(100, content)));
        assertTrue(board.size() > 1);
        List<AnnouncementBoard.Page> parts = new ArrayList<>();
        var position = new AnnouncementBoard.Position(0, 0);
        for (int step = 0; step <= board.maximumStart(); step++) {
            parts.add(board.page(board.start(position)));
            position = board.move(position, 1);
        }
        String restored = String.join("\n", parts.stream().map(AnnouncementBoard.Page::text).toList());
        assertEquals(content.replace("\n", ""), restored.replace("\n", ""));
        assertEquals(board.size(), parts.size());
        assertTrue(parts.stream().allMatch(part -> part.announcementIndex() == 0));
    }

    @Test
    void singlePageBrowsingVisitsEveryNoticeAndStopsAtBothEnds() {
        var board = new AnnouncementBoard(shortNotices(14));
        var position = new AnnouncementBoard.Position(0, 0);
        assertEquals(position, board.move(position, -1));
        Set<Integer> visited = new HashSet<>();
        for (int index = 0; index <= board.maximumStart(); index++) {
            visited.add(board.page(board.start(position)).announcementIndex());
            position = board.move(position, 1);
        }
        assertEquals(14, visited.size());
        assertEquals(position, board.move(position, 1));
        assertEquals(13, board.start(position));
        for (int index = 0; index < 14; index++) position = board.move(position, -1);
        assertEquals(new AnnouncementBoard.Position(0, 0), position);
    }

    @Test
    void emptyBoardsAndStalePositionsRemainValidAfterReloads() {
        var empty = new AnnouncementBoard(List.of());
        assertEquals(0, empty.size());
        assertEquals(new AnnouncementBoard.Position(0, 0), empty.move(new AnnouncementBoard.Position(-1, -1), 1));
        var shortBoard = new AnnouncementBoard(shortNotices(3));
        assertEquals(new AnnouncementBoard.Position(2, 0), shortBoard.normalize(new AnnouncementBoard.Position(100, 100)));
    }

    @Test
    void contentPagePositionsSurviveNewNoticesBeingInserted() {
        Announcement longNotice = new Announcement(200, "正文\n".repeat(70));
        List<Announcement> previous = new ArrayList<>();
        previous.add(longNotice);
        previous.addAll(shortNotices(10));
        List<Announcement> current = new ArrayList<>(previous);
        current.addFirst(new Announcement(300, "新公告"));
        int remapped = AnnouncementModule.remapSelectedIndex(previous, current, 0);
        var board = new AnnouncementBoard(current);
        var position = board.normalize(new AnnouncementBoard.Position(remapped, 3));
        assertEquals(new AnnouncementBoard.Position(1, 3), position);
        assertEquals(longNotice, board.page(board.start(position)).announcement());
    }

    private static List<Announcement> shortNotices(int count) {
        List<Announcement> notices = new ArrayList<>();
        for (int index = 0; index < count; index++) notices.add(new Announcement(1000 - index, "完整正文 " + index));
        return notices;
    }
}
