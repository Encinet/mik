package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.junit.jupiter.api.Assertions.*;

class QqDocumentRendererTest {
    @Test
    void shortRepliesAndCopyableInstructionsUseLiteralText() {
        var document = SocialDocument.message(REQUIRED, SocialDocument.Tone.INFO,
                SocialDocument.paragraph("(Alex) [item] C:\\temp 20.00 *literal* &lt;"),
                SocialDocument.instruction("Confirm for Alex:", "/unbind confirm code_123"));
        var prepared = QqDocumentRenderer.prepare(document, 500);
        assertEquals(QqOutgoingMessage.Format.TEXT, prepared.message().format());
        assertEquals(document.plainText(), prepared.message().content());
        assertEquals(document, prepared.document());
    }

    @Test
    void structuredRepliesUseMarkdownAndKeepMetricPunctuation() {
        var document = SocialDocument.of(REQUIRED, "Status", SocialDocument.Tone.INFO,
                SocialDocument.fields(new SocialDocument.Field("MSPT", "20.00 (normal)")));
        var prepared = QqDocumentRenderer.prepare(document, 500);
        assertEquals(QqOutgoingMessage.Format.MARKDOWN, prepared.message().format());
        assertEquals("## Status\n\n- **MSPT：** 20.00 (normal)", prepared.message().content());
    }

    @Test
    void literalInteractionSyntaxCannotBeSentInMediaCaptions() {
        var document = SocialDocument.message(REQUIRED, SocialDocument.Tone.INFO,
                SocialDocument.paragraph("<qqbot-at-everyone /> @everyone"),
                SocialDocument.image("Skin", URI.create("https://example.org/skin.png"), 64, 64));
        var prepared = QqDocumentRenderer.prepare(document, 500);
        assertEquals(QqOutgoingMessage.Format.MARKDOWN, prepared.message().format());
        assertFalse(prepared.supportsNativeMedia());
        assertFalse(prepared.message().content().contains("<qqbot-"));
        assertFalse(prepared.message().content().contains("@everyone"));
    }

    @Test
    void everyRepresentationFitsAndInstructionsStayComplete() {
        var document = SocialDocument.of(REQUIRED, "A_*[long] title", SocialDocument.Tone.WARNING,
                SocialDocument.paragraph("😀_*[]<>\\&".repeat(100)),
                SocialDocument.instruction("Unbind Alice. Access will be lost. Confirm:", "/unbind confirm code_123"));
        for (int limit = 16; limit <= 300; limit++) {
            var prepared = QqDocumentRenderer.prepare(document, limit);
            assertTrue(prepared.message().content().length() <= limit);
            assertTrue(prepared.caption().length() <= limit);
            assertTrue(prepared.document().plainText().length() <= limit);
            assertEquals(REQUIRED, prepared.document().moderation());
            String text = prepared.document().plainText();
            assertTrue(!text.contains("/unbind") || text.contains(
                    "Unbind Alice. Access will be lost. Confirm:\n\n/unbind confirm code_123"), text);
        }
    }
}
