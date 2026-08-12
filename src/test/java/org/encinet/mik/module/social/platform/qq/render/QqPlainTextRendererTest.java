package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqPlainTextRendererTest {
    @Test
    void rendersMediaCaptionWithoutRepeatingTheRemoteUrl() {
        SocialDocument document = SocialDocument.of("👤 Minecraft", SocialDocument.Tone.INFO,
                SocialDocument.image("Skin",
                        URI.create("https://textures.minecraft.net/texture/abc"), 64, 64),
                SocialDocument.fields(
                        new SocialDocument.Field("玩家", "PlayerOne"),
                        new SocialDocument.Field("状态", "在线")));

        String text = QqPlainTextRenderer.render(document, 500);

        assertEquals("👤 Minecraft\n\n玩家: PlayerOne\n状态: 在线", text);
        assertFalse(text.contains("textures.minecraft.net"));
    }

    @Test
    void boundsMediaCaptionWithoutSplittingUnicode() {
        SocialDocument document = SocialDocument.of("Profile", SocialDocument.Tone.INFO,
                SocialDocument.paragraph("😀".repeat(100)));

        String text = QqPlainTextRenderer.render(document, 80);

        assertTrue(text.length() <= 80);
        assertTrue(text.endsWith("…"));
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 2)));
    }
}
