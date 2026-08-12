package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;

/** Renders the text accompanying a native QQ media message. */
public final class QqPlainTextRenderer {
    private QqPlainTextRenderer() {
    }

    public static String render(SocialDocument document, int maximumLength) {
        Objects.requireNonNull(document, "document");
        if (maximumLength < 16) {
            throw new IllegalArgumentException("maximumLength must be at least 16");
        }
        StringBuilder text = new StringBuilder(document.title());
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image) {
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
