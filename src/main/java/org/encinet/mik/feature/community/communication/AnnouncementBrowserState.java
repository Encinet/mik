package org.encinet.mik.feature.community.communication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;

/** Directory browsing, notice selection and source bookmarks have independent ownership. */
record AnnouncementBrowserState(AnnouncementBoard.Position position, int directoryPage,
                                Map<Announcement, UiReadingPosition> rememberedPages, Announcement selectedNotice) {
    static final int DIRECTORY_PAGE_SIZE = 4;
    static final AnnouncementBrowserState INITIAL = new AnnouncementBrowserState(
            new AnnouncementBoard.Position(0, UiReadingPosition.START), 0, Map.of(), null);

    AnnouncementBrowserState { rememberedPages = Map.copyOf(rememberedPages); }

    FloatingMenuPage directory(AnnouncementBoard board) {
        return new FloatingMenuPage(directoryPage, board.announcements().size(), DIRECTORY_PAGE_SIZE);
    }

    AnnouncementBrowserState normalize(AnnouncementBoard board) {
        if (board.announcements().isEmpty()) return INITIAL;
        int index = Math.clamp(position.announcementIndex(), 0, board.announcements().size() - 1);
        var reading = position.reading();
        int directory = directory(board).index();
        if (selectedNotice != null && !board.announcements().get(index).equals(selectedNotice)) {
            int found = board.announcements().indexOf(selectedNotice);
            if (found < 0) for (int candidate = 0; candidate < board.announcements().size(); candidate++) {
                if (board.announcements().get(candidate).timestamp() == selectedNotice.timestamp()) { found = candidate; break; }
            }
            if (found >= 0) index = found;
            else reading = rememberedPages.getOrDefault(board.announcements().get(index), UiReadingPosition.START);
            directory = index / DIRECTORY_PAGE_SIZE;
        }
        return new AnnouncementBrowserState(new AnnouncementBoard.Position(index, reading), directory,
                rememberedPages, board.announcements().get(index));
    }

    AnnouncementBrowserState browse(AnnouncementBoard board, int page) {
        var state = normalize(board);
        return new AnnouncementBrowserState(state.position(),
                new FloatingMenuPage(page, board.announcements().size(), DIRECTORY_PAGE_SIZE).index(),
                state.rememberedPages(), state.selectedNotice());
    }

    AnnouncementBrowserState select(AnnouncementBoard board, int index) {
        if (board.announcements().isEmpty()) return INITIAL;
        int selected = Math.clamp(index, 0, board.announcements().size() - 1);
        var announcement = board.announcements().get(selected);
        return at(board, new AnnouncementBoard.Position(selected, rememberedPages.getOrDefault(announcement, UiReadingPosition.START)));
    }

    AnnouncementBrowserState read(AnnouncementBoard board, UiReadingPosition reading) {
        var state = normalize(board);
        var next = state.at(board, new AnnouncementBoard.Position(state.position().announcementIndex(), reading));
        return new AnnouncementBrowserState(next.position(), state.directoryPage(), next.rememberedPages(), next.selectedNotice());
    }

    /** Crossing a boundary opens the adjacent notice at its nearest edge, never wraps the catalog. */
    AnnouncementBrowserState across(AnnouncementBoard board, int direction) {
        var state = normalize(board);
        if (board.announcements().isEmpty()) return state;
        int index = Math.clamp(state.position().announcementIndex() + Integer.signum(direction), 0, board.announcements().size() - 1);
        if (index == state.position().announcementIndex()) return state;
        var edge = direction < 0 ? new UiReadingPosition(UiKey.named("body"), Integer.MAX_VALUE, 0) : UiReadingPosition.START;
        return state.at(board, new AnnouncementBoard.Position(index, edge));
    }

    AnnouncementBrowserState latest(AnnouncementBoard board) {
        return at(board, new AnnouncementBoard.Position(0, UiReadingPosition.START));
    }

    private AnnouncementBrowserState at(AnnouncementBoard board, AnnouncementBoard.Position next) {
        if (board.announcements().isEmpty()) return INITIAL;
        var normalized = board.normalize(next);
        var remembered = new HashMap<>(rememberedPages);
        remembered.put(board.announcements().get(normalized.announcementIndex()), normalized.reading());
        return new AnnouncementBrowserState(normalized, normalized.announcementIndex() / DIRECTORY_PAGE_SIZE,
                remembered, board.announcements().get(normalized.announcementIndex()));
    }

    AnnouncementBrowserState remap(List<Announcement> previous, AnnouncementBoard current) {
        if (current.announcements().isEmpty()) return INITIAL;
        var retained = new HashMap<>(rememberedPages);
        retained.keySet().retainAll(current.announcements());
        var selected = selectedNotice != null ? selectedNotice : previous.isEmpty() ? null
                : previous.get(Math.clamp(position.announcementIndex(), 0, previous.size() - 1));
        var remapped = new AnnouncementBrowserState(position, directoryPage, retained, selected).normalize(current);
        return remapped.at(current, remapped.position());
    }
}
