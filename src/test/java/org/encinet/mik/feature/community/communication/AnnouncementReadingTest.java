package org.encinet.mik.feature.community.communication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnouncementReadingTest {
    @Test void directoryExcerptsCollapseWhitespaceAndNeverSplitAGrapheme() {
        String prefix = "甲".repeat(23) + "👩🏽‍💻";
        assertEquals(prefix + "…", AnnouncementReading.excerpt(prefix + "后续全文"));
        assertEquals("First line Second line", AnnouncementReading.excerpt("\n First line\n\nSecond line\r\n"));
        assertEquals("", AnnouncementReading.excerpt(" \n\t"));
    }

}
