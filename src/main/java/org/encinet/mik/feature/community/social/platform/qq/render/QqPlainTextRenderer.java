package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;

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
        return encode(SocialDocumentLayout.fit(document, maximumLength, value -> encode(value).length()));
    }

    public static String encode(SocialDocument document) {
        StringBuilder text = new StringBuilder(document.title());
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image) {
                continue;
            }
            String value = block.plainText();
            if (!value.isBlank()) {
                if (!text.isEmpty()) text.append("\n\n");
                text.append(value);
            }
        }
        return text.isEmpty() ? document.plainText() : text.toString();
    }

}
