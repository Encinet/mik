package org.encinet.mik.feature.community.communication;

import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnouncementSelectionTest {
    @Test
    void keepsTheOpenAnnouncementSelectedWhenNewerEntriesArePublished() {
        Announcement newest = new Announcement(300, "Newest");
        Announcement selected = new Announcement(200, "Reading this");
        Announcement oldest = new Announcement(100, "Oldest");

        var previous = List.of(newest, selected, oldest);
        var state = AnnouncementBrowserState.INITIAL.select(new AnnouncementBoard(previous), 1);
        assertEquals(2, state.remap(previous, new AnnouncementBoard(
                List.of(new Announcement(400, "New"), newest, selected, oldest))).position().announcementIndex());
        assertEquals(1, state.remap(previous, new AnnouncementBoard(
                List.of(newest, new Announcement(200, "Edited"), oldest))).position().announcementIndex());
        assertEquals(1, state.remap(previous, new AnnouncementBoard(
                List.of(newest, oldest))).position().announcementIndex());
    }
}
