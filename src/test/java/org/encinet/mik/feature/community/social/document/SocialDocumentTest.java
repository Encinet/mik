package org.encinet.mik.feature.community.social.document;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialDocumentTest {
    @Test
    void shortReplyHasNoHeadingAndKeepsTheCompleteInstructionWhenBounded() {
        String explanation = "Disconnect Alice. You may lose server access. Send this to confirm:";
        var reply = SocialDocument.message(REQUIRED, SocialDocument.Tone.WARNING,
                SocialDocument.paragraph("Explanation ".repeat(40)),
                SocialDocument.instruction(explanation, "!解除 confirm"))
                .localized(org.encinet.mik.shared.i18n.Language.JA_JP);
        var bounded = org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(reply, 100);

        assertTrue(bounded.plainText().contains(explanation));
        assertTrue(bounded.plainText().endsWith("\n\n!解除 confirm"));
        assertTrue(bounded.plainText().length() <= 100);
        assertEquals("", bounded.title());
        assertEquals(reply.language(), bounded.language());
        assertEquals(REQUIRED, bounded.moderation());
    }

    @Test
    void truncationNeverSeparatesTheConfirmationFromItsObjectAndConsequences() {
        String explanation = "Disconnect Alice. You will lose server access unless another binding is valid.";
        var instruction = SocialDocument.instruction(explanation, "/unbind confirm");
        var document = SocialDocument.message(REQUIRED, SocialDocument.Tone.WARNING, instruction);
        for (int budget = 1; budget <= document.plainText().length() + 10; budget++) {
            String text = org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(document, budget).plainText();
            assertTrue(text.length() <= budget, "budget " + budget);
            assertTrue(!text.contains("/unbind") || text.contains(instruction.plainText()), text);
        }
        assertThrows(IllegalArgumentException.class, () -> SocialDocument.instruction("", "/unbind confirm"));
        assertThrows(IllegalArgumentException.class, () -> SocialDocument.instruction("Confirm", "/unbind\nyes"));
    }

    @Test
    void boundingPreservesMediaAndDoesNotSplitFieldsOrListItems() {
        var media = SocialDocument.image("Avatar", URI.create("https://example.org/skin.png"), 64, 64);
        var document = SocialDocument.message(REQUIRED, SocialDocument.Tone.INFO, media,
                SocialDocument.fields(new SocialDocument.Field("Player", "Alice"),
                        new SocialDocument.Field("UUID", "a".repeat(100))));
        var bounded = org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(document, 80);
        assertEquals(media, bounded.blocks().getFirst());
        assertTrue(bounded.plainText().contains("Player: Alice"));
        assertTrue(bounded.plainText().endsWith("…"));
        assertTrue(!bounded.plainText().contains("UUID:"));

        var list = SocialDocument.message(TRUSTED, SocialDocument.Tone.INFO,
                SocialDocument.orderedList(java.util.stream.IntStream.range(0, 50)
                        .mapToObj(index -> "Player" + index).toArray(String[]::new)));
        for (int budget = 1; budget < 300; budget++) {
            assertTrue(org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(list, budget).plainText().length() <= budget, "budget " + budget);
        }
    }

    @Test
    void plainTextIncludesEveryBlockWithoutFormatting() {
        SocialDocument document = SocialDocument.of(TRUSTED, "Status", SocialDocument.Tone.INFO,
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
        assertEquals(TRUSTED, document.moderation());
        assertSame(document, org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(document, 500));
    }

    @Test
    void moderationPolicySurvivesLocalizationAndTruncation() {
        SocialDocument document = SocialDocument.of(REQUIRED, "Status", SocialDocument.Tone.INFO,
                        SocialDocument.paragraph("Trusted system text"));

        assertEquals("Status\n\nTrusted system text", document.plainText());
        assertEquals(REQUIRED, SocialDocumentLayout.limit(document.localized(org.encinet.mik.shared.i18n.Language.JA_JP), 16).moderation());
    }

    @Test
    void truncationIsUnicodeSafeAndBounded() {
        SocialDocument document = SocialDocument.of(TRUSTED, "Profile", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("😀".repeat(200)));
        SocialDocument truncated = org.encinet.mik.feature.community.social.document.SocialDocumentLayout.limit(document, 100);

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
