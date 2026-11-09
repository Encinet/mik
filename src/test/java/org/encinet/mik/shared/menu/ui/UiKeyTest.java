package org.encinet.mik.shared.menu.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiKeyTest {
    @Test void domainPathsAndLiteralEscapesHaveDistinctSingleSegmentIdentities() {
        assertEquals("local:album%2Fsong.mp3", UiKey.item("local:album/song.mp3").value());
        assertEquals("local:album%252Fsong.mp3", UiKey.item("local:album%2Fsong.mp3").value());
        assertNotEquals(UiKey.item("local:album/song.mp3"), UiKey.item("local:album%2Fsong.mp3"));
        assertThrows(IllegalArgumentException.class, () -> UiKey.named("local:album/song.mp3"));
        assertThrows(IllegalArgumentException.class, () -> UiKey.item(" ".repeat(600)));
    }

    @Test void longAndEscapeExpandedIdentifiersStayBoundedStableAndSeparateFromLiteralKeys() {
        for (String id : new String[] {"x".repeat(513), "/".repeat(180), "%".repeat(180), "专辑/".repeat(200)}) {
            var key = UiKey.item(id);
            assertTrue(key.value().length() <= 512);
            assertFalse(key.value().contains("/"));
            assertEquals(key, UiKey.item(id));
            assertNotEquals(key, UiKey.item(id + "other"));
            assertNotEquals(key, UiKey.item(key.value()), "a literal identifier cannot alias the hashed namespace");
        }
        assertEquals("x".repeat(512), UiKey.item("x".repeat(512)).value());
        assertThrows(IllegalArgumentException.class, () -> UiKey.named("x".repeat(513)));
    }
}
