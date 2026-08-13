package org.encinet.mik.module.social.platform.matrix.render;

import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixRenderersTest {

    @Test
    void rendersEscapedStructuredHtmlAndPlainFallback() {
        SocialDocument document = SocialDocument.of("Title <unsafe>",
                SocialDocument.Tone.INFO,
                SocialDocument.paragraph("First & second\nnext"),
                SocialDocument.fields(new SocialDocument.Field("Player", "A < B")),
                SocialDocument.orderedList("one", "two"),
                SocialDocument.image("Skin & face",
                        URI.create("https://example.org/skin.png?a=1&b=2"), 64, 64));

        String html = MatrixHtmlRenderer.render(document);
        String plain = MatrixPlainTextRenderer.render(document, 1_000);

        assertTrue(html.startsWith("<h3>Title &lt;unsafe&gt;</h3>"));
        assertTrue(html.contains("First &amp; second<br>next"));
        assertTrue(html.contains("<strong>Player:</strong> A &lt; B"));
        assertTrue(html.contains("<ol><li>one</li><li>two</li></ol>"));
        assertTrue(html.contains("href=\"https://example.org/skin.png?a=1&amp;b=2\""));
        assertFalse(html.contains("<unsafe>"));
        assertEquals("""
                Title <unsafe>

                First & second
                next

                Player: A < B

                1. one
                2. two

                Skin & face: https://example.org/skin.png?a=1&b=2""", plain);
    }

    @Test
    void plainFallbackRespectsSurrogateSafeLimit() {
        SocialDocument document = SocialDocument.of("😀 long title",
                SocialDocument.Tone.INFO,
                SocialDocument.paragraph("body"));

        String plain = MatrixPlainTextRenderer.render(document, 16);

        assertTrue(plain.endsWith("…"));
        assertFalse(Character.isHighSurrogate(plain.charAt(plain.length() - 2)));
    }
}
