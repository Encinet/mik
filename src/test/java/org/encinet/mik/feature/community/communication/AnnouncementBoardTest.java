package org.encinet.mik.feature.community.communication;

import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementBoardTest {
    @Test void sourceBodiesAreCompleteAndUnwrappedSoViewportChangesCanRepaginateThem() {
        String content = ("第一行 😀 with  two spaces\n第二行公告正文\n\n").repeat(20) + "最后一行";
        var source = List.of(new Announcement(100, content), new Announcement(99, "Short"));
        var board = new AnnouncementBoard(source);
        assertEquals(source, board.announcements());
        assertSame(content, board.announcements().getFirst().content());
        assertFalse(board.entry(0).excerpt().contains("\n"));
        assertTrue(board.entry(0).excerpt().endsWith("…"));
        assertThrows(UnsupportedOperationException.class, () -> board.announcements().clear());
    }

    @Test void duplicateTimestampsHaveDistinctKeysAndInsertionDoesNotRenameExistingNotices() {
        var first = new Announcement(100, "First");
        var second = new Announcement(100, "Second");
        var board = new AnnouncementBoard(List.of(first, second));
        assertNotEquals(board.entry(0).id(), board.entry(1).id());
        var changed = new AnnouncementBoard(List.of(new Announcement(101, "New"), first, second));
        assertEquals(board.entry(0).id(), changed.entry(1).id());
        assertEquals(board.entry(1).id(), changed.entry(2).id());
    }

    @Test void clampingAnIndexNeverClampsASourceOffsetBeforeMeasurement() {
        var bookmark = new UiReadingPosition(UiKey.named("body"), 999, 0);
        var board = new AnnouncementBoard(List.of(new Announcement(1, "Short")));
        assertEquals(new AnnouncementBoard.Position(0, bookmark), board.normalize(new AnnouncementBoard.Position(50, bookmark)));
        assertEquals(AnnouncementBrowserState.INITIAL.position(),
                new AnnouncementBoard(List.of()).normalize(new AnnouncementBoard.Position(50, bookmark)));
    }
}
