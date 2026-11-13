package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;

/** Encodes literal document text for plain messages and native media captions. */
final class QqPlainTextRenderer {
    private QqPlainTextRenderer() {
    }

    static String encode(SocialDocument document) {
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
