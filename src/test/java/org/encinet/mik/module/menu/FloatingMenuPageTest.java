package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuPageTest {
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
