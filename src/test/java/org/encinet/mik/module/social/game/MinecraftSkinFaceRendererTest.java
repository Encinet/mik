package org.encinet.mik.module.social.game;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSkinFaceRendererTest {
    @Test
    void cropsFrontFaceAndAppliesTheExpandedHatLayer() throws Exception {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                skin.setRGB(x, y, 0xffff0000);
            }
        }
        skin.setRGB(40, 8, 0xff0000ff);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(skin, "png", source);

        Optional<SocialPlayerAvatar> result = MinecraftSkinFaceRenderer.render(
                source.toByteArray());

        assertTrue(result.isPresent());
        BufferedImage avatar = ImageIO.read(
                new ByteArrayInputStream(result.orElseThrow().pngData()));
        assertEquals(256, avatar.getWidth());
        assertEquals(256, avatar.getHeight());
        assertEquals(0xff0000ff, avatar.getRGB(10, 10));
        // The transparent outer layer exposes a real margin around the smaller base face.
        assertEquals(0x00000000, avatar.getRGB(5, 100));
        assertEquals(0xffff0000, avatar.getRGB(20, 100));
        assertEquals(0xffff0000, avatar.getRGB(128, 128));
    }

    @Test
    void rejectsImagesThatAreNotStandardMinecraftSkinDimensions() throws Exception {
        BufferedImage invalid = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(invalid, "png", source);

        assertTrue(MinecraftSkinFaceRenderer.render(source.toByteArray()).isEmpty());
    }
}
