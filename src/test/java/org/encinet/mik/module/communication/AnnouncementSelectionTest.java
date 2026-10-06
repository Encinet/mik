package org.encinet.mik.module.communication;

import org.encinet.mik.module.communication.AnnouncementCatalog.Announcement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnouncementSelectionTest {
    @Test
    void keepsTheOpenAnnouncementSelectedWhenNewerEntriesArePublished() {
        Announcement newest = new Announcement(300, "Newest");
        Announcement selected = new Announcement(200, "Reading this");
        Announcement oldest = new Announcement(100, "Oldest");

        assertEquals(2, AnnouncementModule.remapSelectedIndex(
                List.of(newest, selected, oldest),
                List.of(new Announcement(400, "New"), newest, selected, oldest), 1));
        assertEquals(1, AnnouncementModule.remapSelectedIndex(
                List.of(newest, selected, oldest),
                List.of(newest, new Announcement(200, "Edited"), oldest), 1));
        assertEquals(1, AnnouncementModule.remapSelectedIndex(
                List.of(newest, selected, oldest), List.of(newest, oldest), 1));
    }
}
