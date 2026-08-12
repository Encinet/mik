package org.encinet.mik.module.social.game;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Crops the front face and its slightly enlarged outer layer from a Minecraft skin. */
final class MinecraftSkinFaceRenderer {
    static final int OUTPUT_SIZE = 256;
    private static final int BASE_LAYER_SIZE = Math.round(OUTPUT_SIZE * 8.0f / 9.0f);
    private static final int BASE_LAYER_OFFSET = (OUTPUT_SIZE - BASE_LAYER_SIZE) / 2;

    private MinecraftSkinFaceRenderer() {
    }

    static Optional<SocialPlayerAvatar> render(byte[] skinPng) {
        if (skinPng == null || skinPng.length == 0) {
            return Optional.empty();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(skinPng);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage skin = ImageIO.read(input);
            if (skin == null || skin.getWidth() != 64
                    || (skin.getHeight() != 64 && skin.getHeight() != 32)) {
                return Optional.empty();
            }

            BufferedImage face = new BufferedImage(
                    OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = face.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);
                // Reserve room for the inflated outer cube instead of filling the canvas with
                // the base face and clipping the overhang afterwards. Minecraft renders the
                // outer head as 9x9 model units over an 8x8 base head.
                graphics.drawImage(skin,
                        BASE_LAYER_OFFSET,
                        BASE_LAYER_OFFSET,
                        BASE_LAYER_OFFSET + BASE_LAYER_SIZE,
                        BASE_LAYER_OFFSET + BASE_LAYER_SIZE,
                        8, 8, 16, 16, null);
                graphics.drawImage(skin,
                        0,
                        0,
                        OUTPUT_SIZE,
                        OUTPUT_SIZE,
                        40, 8, 48, 16, null);
            } finally {
                graphics.dispose();
            }
            if (!ImageIO.write(face, "png", output)) {
                return Optional.empty();
            }
            return Optional.of(new SocialPlayerAvatar(output.toByteArray()));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
