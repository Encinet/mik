package org.encinet.mik.module.communication;

import org.encinet.mik.module.communication.AnnouncementCatalog.Announcement;

import java.util.ArrayList;
import java.util.List;

final class AnnouncementBoard {
    private final List<Page> pages;
    private final List<Integer> starts;

    record Position(int announcementIndex, int part) { }
    record Page(int announcementIndex, int part, int parts, Announcement announcement, String text) { }

    AnnouncementBoard(List<Announcement> announcements) {
        List<Page> content = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < announcements.size(); index++) {
            offsets.add(content.size());
            Announcement announcement = announcements.get(index);
            List<String> parts = AnnouncementReading.pages(announcement.content());
            for (int part = 0; part < parts.size(); part++)
                content.add(new Page(index, part, parts.size(), announcement, parts.get(part)));
        }
        pages = List.copyOf(content);
        starts = List.copyOf(offsets);
    }

    int size() { return pages.size(); }
    int maximumStart() { return Math.max(0, size() - 1); }

    int start(Position position) {
        if (pages.isEmpty()) return 0;
        int announcementIndex = Math.clamp(position.announcementIndex(), 0, starts.size() - 1);
        int first = starts.get(announcementIndex);
        int end = announcementIndex + 1 < starts.size() ? starts.get(announcementIndex + 1) : size();
        return Math.min(maximumStart(), first + Math.clamp(position.part(), 0, end - first - 1));
    }

    Position normalize(Position position) { return positionAt(start(position)); }

    Position move(Position position, int direction) {
        return positionAt(Math.clamp(start(position) + Integer.signum(direction), 0, maximumStart()));
    }

    Page page(int index) { return pages.get(index); }

    private Position positionAt(int index) {
        if (pages.isEmpty()) return new Position(0, 0);
        Page page = pages.get(index);
        return new Position(page.announcementIndex(), page.part());
    }
}
