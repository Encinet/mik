package org.encinet.mik.feature.community.communication;

import java.util.List;
import java.util.stream.IntStream;
import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementBrowserStateTest {
    private static final UiReadingPosition BOOKMARK = new UiReadingPosition(UiKey.named("body"), 125, 0);

    @Test void directoryBrowsingDoesNotChangeTheOpenNoticeAndSelectionRestoresItsSourceBookmark() {
        var board = new AnnouncementBoard(notices());
        var state = AnnouncementBrowserState.INITIAL.read(board, BOOKMARK).browse(board, 1);
        assertEquals(1, state.directoryPage());
        assertEquals(new AnnouncementBoard.Position(0, BOOKMARK), state.position());
        state = state.select(board, 5);
        assertEquals(new AnnouncementBoard.Position(5, UiReadingPosition.START), state.position());
        state = state.select(board, 0);
        assertEquals(BOOKMARK, state.position().reading());
        assertEquals(0, state.directoryPage());
        assertEquals(UiReadingPosition.START, state.latest(board).position().reading());
    }

    @Test void crossingDocumentBoundariesStopsWithoutWrappingAndEntersAtTheNearestEdge() {
        var board = new AnnouncementBoard(notices());
        var state = AnnouncementBrowserState.INITIAL.normalize(board);
        assertEquals(state, state.across(board, -1));
        for (int index = 0; index < 9; index++) state = state.across(board, 1);
        assertEquals(9, state.position().announcementIndex());
        assertEquals(state, state.across(board, 1));
        state = state.across(board, -1);
        assertEquals(8, state.position().announcementIndex());
        assertEquals(Integer.MAX_VALUE, state.position().reading().offset());
        for (int index = 0; index < 8; index++) state = state.across(board, -1);
        assertEquals(0, state.position().announcementIndex());
    }

    @Test void movingWithinTheReaderDoesNotJumpTheIndependentlyBrowsedDirectoryBackToTheSelection() {
        var board = new AnnouncementBoard(notices());
        var state = AnnouncementBrowserState.INITIAL.browse(board, 2).read(board, BOOKMARK);
        assertEquals(2, state.directoryPage());
        assertEquals(new AnnouncementBoard.Position(0, BOOKMARK), state.position());
    }

    @Test void reloadKeepsTheOpenNoticeAndBookmarkAndPrunesRemovedRememberedSources() {
        var previous = notices();
        var oldBoard = new AnnouncementBoard(previous);
        var state = AnnouncementBrowserState.INITIAL.read(oldBoard, BOOKMARK).select(oldBoard, 3).select(oldBoard, 0);
        var current = new java.util.ArrayList<>(previous);
        current.addFirst(new Announcement(200, "New notice"));
        var changedBoard = new AnnouncementBoard(current);
        state = state.remap(previous, changedBoard);
        assertEquals(new AnnouncementBoard.Position(1, BOOKMARK), state.position());
        assertEquals(oldBoard.entry(0).id(), changedBoard.entry(1).id());
        var edited = new java.util.ArrayList<>(current);
        edited.set(1, new Announcement(previous.getFirst().timestamp(), "Shortened"));
        state = state.remap(current, new AnnouncementBoard(edited));
        assertEquals(BOOKMARK, state.position().reading(), "the document clamps against the new actual layout");
        assertFalse(state.rememberedPages().containsKey(previous.getFirst()));
        var beforeRemoval = List.copyOf(edited);
        edited.remove(1);
        state = state.remap(beforeRemoval, new AnnouncementBoard(edited));
        assertEquals(UiReadingPosition.START, state.position().reading());
        assertTrue(edited.containsAll(state.rememberedPages().keySet()));
    }

    @Test void emptyCatalogsAreSafeAndReopeningResolvesTheSourceRatherThanTheOldIndex() {
        var previous = notices();
        var state = AnnouncementBrowserState.INITIAL.read(new AnnouncementBoard(previous), BOOKMARK);
        var current = new java.util.ArrayList<>(previous);
        current.addFirst(new Announcement(200, "New while closed"));
        assertEquals(new AnnouncementBoard.Position(1, BOOKMARK), state.normalize(new AnnouncementBoard(current)).position());
        var empty = new AnnouncementBoard(List.of());
        assertEquals(AnnouncementBrowserState.INITIAL, state.select(empty, 9));
        assertEquals(AnnouncementBrowserState.INITIAL, state.across(empty, 1));
        assertEquals(AnnouncementBrowserState.INITIAL, state.remap(previous, empty));
    }

    static List<Announcement> notices() {
        return IntStream.range(0, 10).mapToObj(index -> new Announcement(100 - index,
                index == 0 ? ("甲".repeat(35) + "\n").repeat(120) + "End" : "Announcement " + index)).toList();
    }
}
