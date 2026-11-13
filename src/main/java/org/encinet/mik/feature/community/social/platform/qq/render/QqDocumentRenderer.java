package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;
import org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage;

import java.util.Objects;

/** Selects and bounds every representation of one QQ reply or notice before moderation. */
public final class QqDocumentRenderer {
    private QqDocumentRenderer() { }

    public static PreparedDocument prepare(SocialDocument document, int maximumLength) {
        Objects.requireNonNull(document, "document");
        SocialDocument bounded = SocialDocumentLayout.fit(document, maximumLength, value -> Math.max(
                value.plainText().length(), Math.max(encode(value).content().length(), QqPlainTextRenderer.encode(value).length())));
        return new PreparedDocument(bounded, encode(bounded), QqPlainTextRenderer.encode(bounded));
    }

    private static QqOutgoingMessage encode(SocialDocument document) {
        String plain = QqPlainTextRenderer.encode(document);
        boolean markdown = !document.title().isEmpty() || document.blocks().stream().anyMatch(block ->
                block instanceof SocialDocument.Fields || block instanceof SocialDocument.ItemList)
                || QqText.containsInteractionSyntax(plain);
        return new QqOutgoingMessage(markdown ? QqOutgoingMessage.Format.MARKDOWN : QqOutgoingMessage.Format.TEXT,
                markdown ? QqMarkdownRenderer.encode(document) : plain);
    }

    public record PreparedDocument(SocialDocument document, QqOutgoingMessage message, String caption) {
        public boolean supportsNativeMedia() { return !QqText.containsInteractionSyntax(caption); }
    }
}
