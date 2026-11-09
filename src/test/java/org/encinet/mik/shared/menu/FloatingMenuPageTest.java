package org.encinet.mik.shared.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuPageTest {
    @Test
    void maximumCollectionCountsAndLastSlicesDoNotOverflow() {
        var page = new FloatingMenuPage(Integer.MAX_VALUE, Integer.MAX_VALUE, 256);
        assertEquals(8388608, page.count());
        assertEquals(2147483392, page.fromIndex());
        assertEquals(Integer.MAX_VALUE, page.toIndex());
        assertFalse(page.hasNext());
        assertEquals(1, new FloatingMenuPage(0, 0, Integer.MAX_VALUE).count());
    }

    @Test
    void clampsNavigationAndSlicesContent() {
        FloatingMenuPage page = new FloatingMenuPage(99, 5, 2);
        assertEquals(2, page.index());
        assertEquals(3, page.count());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(List.of(5), page.slice(List.of(1, 2, 3, 4, 5)));
        assertEquals(1, page.previous().index());
        assertEquals(2, page.next().index());
    }
}
