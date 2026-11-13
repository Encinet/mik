package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqMarkdownRendererTest {
    @Test
    void shortRepliesFitEncodedLengthWithoutCuttingMarkupOrAnAction() {
        var document = SocialDocument.message(TRUSTED, SocialDocument.Tone.INFO,
                SocialDocument.fields(new SocialDocument.Field("_label_", "*value*".repeat(50))),
                SocialDocument.instruction("Send this to bind:", "/绑定 <验证码>"));
        for (int limit = 16; limit <= 200; limit++) {
            String rendered = QqDocumentRenderer.prepare(document, limit).message().content();
            assertTrue(rendered.length() <= limit);
            assertFalse(rendered.startsWith("##"));
            assertEquals(0, (rendered.split("\\*\\*", -1).length - 1) % 2);
            assertTrue(!rendered.contains("/绑定") || rendered.contains("/绑定 &#60;验证码&#62;"), rendered);
            assertTrue(!rendered.contains("/绑定") || rendered.contains("Send this to bind:"), rendered);
        }
    }

    @Test
    void markupBudgetKeepsAnEscapedWarningAndItsConfirmationTogether() {
        String warning = "Unbind Alice_*. Server access will be lost. Confirm:";
        var document = SocialDocument.message(TRUSTED, SocialDocument.Tone.WARNING,
                SocialDocument.instruction(warning, "/unbind confirm"));
        for (int limit = 16; limit < 150; limit++) {
            String rendered = QqDocumentRenderer.prepare(document, limit).message().content();
            assertTrue(rendered.length() <= limit);
            assertTrue(!rendered.contains("/unbind") || Parser.unescapeEntities(rendered, false).contains(warning), rendered);
        }
    }

    @Test
    void rendersEveryStructuredBlockAndEscapesDynamicText() {
        SocialDocument document = SocialDocument.of(TRUSTED, "📊 Minecraft", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("server_*[]"),
                SocialDocument.fields(new SocialDocument.Field("Online", "2 / 20")),
                SocialDocument.orderedList("Run /bind qq", "Send [code](bad)"),
                SocialDocument.unorderedList("safe"),
                SocialDocument.image("Skin",
                        URI.create("https://textures.minecraft.net/texture/abc"), 64, 64));

        String markdown = QqDocumentRenderer.prepare(document, 500).message().content();

        assertTrue(markdown.startsWith("## 📊 Minecraft"));
        assertTrue(markdown.contains("server&#95;&#42;&#91;&#93;"));
        assertTrue(markdown.contains("- **Online：** 2 / 20"));
        assertTrue(markdown.contains("1. Run /bind qq"));
        assertTrue(markdown.contains("&#91;code&#93;(bad)"));
        assertFalse(markdown.contains("[code](bad)"));
        assertFalse(markdown.contains("textures.minecraft.net"));
    }

    @Test
    void rendersFieldsAsOneCompactListWithoutBlankLines() {
        SocialDocument document = SocialDocument.of(TRUSTED, "📊 Minecraft", SocialDocument.Tone.INFO,
                SocialDocument.fields(
                        new SocialDocument.Field("Online", "2 / 20"),
                        new SocialDocument.Field("TPS", "20.00 / 20.00 / 19.99"),
                        new SocialDocument.Field("MSPT", "12.34")));

        assertEquals("""
                ## 📊 Minecraft

                - **Online：** 2 / 20
                - **TPS：** 20.00 / 20.00 / 19.99
                - **MSPT：** 12.34""",
                QqDocumentRenderer.prepare(document, 500).message().content());
    }

    @Test
    void honorsLimitWithoutSplittingUnicodeOrAnEscape() {
        SocialDocument document = SocialDocument.of(TRUSTED, "Status", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("😀_".repeat(100)));
        String markdown = QqDocumentRenderer.prepare(document, 100).message().content();

        assertTrue(markdown.length() <= 100);
        assertTrue(markdown.contains("&#95;"), markdown);
        assertTrue(markdown.endsWith("…"));
        assertFalse(Character.isHighSurrogate(markdown.charAt(markdown.length() - 2)));
        assertFalse(markdown.endsWith("\\…"));
    }

    @Test
    void literalPunctuationCannotBecomeMathMarkupOrInteractionTags() {
        String text = "(括号内文字) [物品] {数据} 20.00 C:\\temp\\file "
                + "\\(not math\\) \\[not math\\] $x_y$ **literal** "
                + "[link](https://example.org) ![image](https://example.org/image) "
                + "<qqbot-at-user id=\"forged\" /> <qqbot-at-everyone /> &lt; & &#91;";

        String encoded = QqText.markdownLiteral(text);

        assertTrue(encoded.startsWith("(括号内文字) &#91;物品&#93; {数据} 20.00"));
        assertFalse(encoded.contains("\\"), encoded);
        assertFalse(encoded.contains("<qqbot-"), encoded);
        assertFalse(encoded.contains("$"), encoded);
        assertFalse(encoded.contains("["), encoded);
        assertEquals(text, Parser.unescapeEntities(encoded, false));
    }

    @Test
    void literalNumberedLinesDoNotBecomeListsAndDecimalsKeepTheirDots() {
        String text = "1. first\n  23. second\n20.00 and version 1.5";
        String encoded = QqText.markdownLiteral(text);
        assertEquals("1&#46; first\n  23&#46; second\n20.00 and version 1.5", encoded);
        assertEquals(text, Parser.unescapeEntities(encoded, false));
    }
}
