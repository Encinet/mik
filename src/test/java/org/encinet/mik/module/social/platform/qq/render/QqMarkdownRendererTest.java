package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqMarkdownRendererTest {
    @Test
    void rendersEveryStructuredBlockAndEscapesDynamicText() {
        SocialDocument document = SocialDocument.of("📊 Minecraft", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("server_*[]"),
                SocialDocument.fields(new SocialDocument.Field("Online", "2 / 20")),
                SocialDocument.orderedList("Run /bind qq", "Send [code](bad)"),
                SocialDocument.unorderedList("safe"),
                SocialDocument.image("Skin",
                        URI.create("https://textures.minecraft.net/texture/abc"), 64, 64));

        String markdown = QqMarkdownRenderer.render(document, 500);

        assertTrue(markdown.startsWith("## 📊 Minecraft"));
        assertTrue(markdown.contains("server\\_\\*\\[\\]"));
        assertTrue(markdown.contains("- **Online：** 2 / 20"));
        assertTrue(markdown.contains("1. Run /bind qq"));
        assertTrue(markdown.contains("\\[code\\]\\(bad\\)"));
        assertFalse(markdown.contains("[code](bad)"));
        assertFalse(markdown.contains("textures.minecraft.net"));
    }

    @Test
    void rendersFieldsAsOneCompactListWithoutBlankLines() {
        SocialDocument document = SocialDocument.of("📊 Minecraft", SocialDocument.Tone.INFO,
                SocialDocument.fields(
                        new SocialDocument.Field("Online", "2 / 20"),
                        new SocialDocument.Field("TPS", "20.00 / 20.00 / 19.99"),
                        new SocialDocument.Field("MSPT", "12.34")));

        assertEquals("""
                ## 📊 Minecraft

                - **Online：** 2 / 20
                - **TPS：** 20\\.00 / 20\\.00 / 19\\.99
                - **MSPT：** 12\\.34""",
                QqMarkdownRenderer.render(document, 500));
    }

    @Test
    void honorsLimitWithoutSplittingUnicodeOrAnEscape() {
        SocialDocument document = SocialDocument.of("Status", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("😀_".repeat(100)));
        String markdown = QqMarkdownRenderer.render(document, 100);

        assertTrue(markdown.length() <= 100);
        assertTrue(markdown.endsWith("…"));
        assertFalse(Character.isHighSurrogate(markdown.charAt(markdown.length() - 2)));
        assertFalse(markdown.endsWith("\\…"));
    }
}
