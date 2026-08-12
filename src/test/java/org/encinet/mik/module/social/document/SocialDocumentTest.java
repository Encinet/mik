package org.encinet.mik.module.social.document;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialDocumentTest {
    @Test
    void plainTextIncludesEveryBlockWithoutFormatting() {
        SocialDocument document = SocialDocument.of("Status", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("Running"),
                SocialDocument.fields(
                        new SocialDocument.Field("Online", "2 / 20"),
                        new SocialDocument.Field("TPS", "20.00")),
                SocialDocument.orderedList("first", "second"),
                SocialDocument.unorderedList("alpha", "beta"),
                SocialDocument.image("Player skin",
                        URI.create("https://textures.minecraft.net/texture/abc"), 64, 64));

        assertEquals("""
                Status

                Running

                Online: 2 / 20
                TPS: 20.00

                1. first
                2. second

                - alpha
                - beta

                Player skin: https://textures.minecraft.net/texture/abc""",
                document.plainText());
        assertEquals("", document.untrustedPlainText());
        assertSame(document, document.truncated(500));
    }

    @Test
    void untrustedTextIsExplicitAndSeparateFromRenderedContent() {
        SocialDocument document = SocialDocument.of("Status", SocialDocument.Tone.INFO,
                        SocialDocument.paragraph("Trusted system text"))
                .withUntrustedText("PlayerOne", "PlayerTwo", "PlayerOne");

        assertEquals("Status\n\nTrusted system text", document.plainText());
        assertEquals("PlayerOne\nPlayerTwo", document.untrustedPlainText());
        assertEquals(document.untrustedText(), document.truncated(100).untrustedText());
    }

    @Test
    void truncationIsUnicodeSafeAndBounded() {
        SocialDocument document = SocialDocument.of("Profile", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("😀".repeat(200)));
        SocialDocument truncated = document.truncated(100);

        assertTrue(truncated.plainText().length() <= 100);
        assertTrue(truncated.plainText().endsWith("…"));
    }

    @Test
    void imageRejectsNonHttpAndCredentialBearingUrls() {
        assertThrows(IllegalArgumentException.class, () -> SocialDocument.image(
                "Skin", URI.create("javascript:alert(1)"), 64, 64));
        assertThrows(IllegalArgumentException.class, () -> SocialDocument.image(
                "Skin", URI.create("https://user:secret@example.com/skin.png"), 64, 64));
    }
}
