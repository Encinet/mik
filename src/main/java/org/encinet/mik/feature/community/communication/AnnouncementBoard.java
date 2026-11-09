package org.encinet.mik.feature.community.communication;

import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Immutable source snapshots and stable directory identities; the UI measures reading pages. */
final class AnnouncementBoard {
    private final List<Announcement> announcements;
    private final List<Entry> entries;

    record Position(int announcementIndex, UiReadingPosition reading) {
        Position { java.util.Objects.requireNonNull(reading); }
    }
    record Entry(String id, String excerpt) { }

    AnnouncementBoard(List<Announcement> announcements) {
        this.announcements = List.copyOf(announcements);
        var directory = new ArrayList<Entry>();
        var occurrences = new HashMap<Long, Integer>();
        for (var announcement : announcements) {
            int occurrence = occurrences.merge(announcement.timestamp(), 1, Integer::sum) - 1;
            directory.add(new Entry(announcement.timestamp() + ":" + occurrence,
                    AnnouncementReading.excerpt(announcement.content())));
        }
        entries = List.copyOf(directory);
    }

    List<Announcement> announcements() { return announcements; }
    Entry entry(int index) { return entries.get(index); }
    Position normalize(Position position) {
        return announcements.isEmpty() ? new Position(0, UiReadingPosition.START)
                : new Position(Math.clamp(position.announcementIndex(), 0, announcements.size() - 1), position.reading());
    }
}
