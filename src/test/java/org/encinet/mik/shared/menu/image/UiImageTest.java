package org.encinet.mik.shared.menu.image;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiImageTest {
    @Test void preservesOriginalDimensionsWithoutDownsampling() {
        var image = UiImage.remote("https://example.org/封面.png?size=1024", 1920, 1080);
        assertEquals(1920, image.pixelWidth());
        assertEquals(1080, image.pixelHeight());
        assertEquals(16.0 / 9, image.aspectRatio());
        assertTrue(image.url().toASCIIString().contains("%"));
    }

    @Test void requiresAClientAccessibleHttpImageWithRealDimensions() {
        for (String url : new String[]{"file:///tmp/cover.png", "data:image/png;base64,AA", "/cover.png",
                "https:///cover.png", "https://user:password@example.org/cover.png", "https://example.org/a.png#fragment",
                "https://example.org/" + "x".repeat(8192)})
            assertThrows(IllegalArgumentException.class, () -> UiImage.remote(url, 64, 64), url);
        assertThrows(IllegalArgumentException.class, () -> UiImage.remote("https://example.org/a.png", 0, 64));
        assertThrows(IllegalArgumentException.class, () -> UiImage.remote("https://example.org/a.png", 64, -1));
    }
}
