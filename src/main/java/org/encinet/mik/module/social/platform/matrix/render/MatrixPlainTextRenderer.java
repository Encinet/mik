package org.encinet.mik.module.social.platform.matrix.render;

import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;

/** Renders a bounded fallback body for Matrix clients without custom-HTML support. */
public final class MatrixPlainTextRenderer {
    private MatrixPlainTextRenderer() {
    }

    public static String render(SocialDocument document, int maximumLength) {
        Objects.requireNonNull(document, "document");
        if (maximumLength < 16) {
            throw new IllegalArgumentException("maximumLength must be at least 16");
        }
        StringBuilder text = new StringBuilder(document.title());
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image image) {
                if (image.source() instanceof SocialDocument.RemoteImage remote) {
                    text.append("\n\n").append(image.alternativeText())
                            .append(": ").append(remote.url().toASCIIString());
                }
                continue;
            }
            String value = block.plainText();
            if (!value.isBlank()) {
                text.append("\n\n").append(value);
            }
        }
        return limit(text.toString(), maximumLength);
    }

    private static String limit(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = maximumLength - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, Math.max(0, end)).stripTrailing() + '…';
    }
}
