package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialFutures;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixApiErrors;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixHtmlRenderer;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixPlainTextRenderer;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;

import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** A single prepared layout is used for HTML, plain text and media fallback. */
final class MatrixReplyChannel implements SocialReplyChannel {
    private final MatrixClient client;
    private final MatrixRoomMessage event;

    MatrixReplyChannel(MatrixClient client, MatrixRoomMessage event) {
        this.client = Objects.requireNonNull(client, "client");
        this.event = Objects.requireNonNull(event, "event");
    }

    static SocialDocument layout(SocialDocument document, int maximumLength) {
        return SocialDocumentLayout.fit(document, maximumLength, value -> Math.max(value.plainText().length(),
                Math.max(MatrixPlainTextRenderer.encode(value).length(), MatrixHtmlRenderer.render(value).length())));
    }

    @Override
    public SocialPreparedReply prepare(SocialDocument document, int maximumLength) {
        var bounded = layout(document, maximumLength);
        String plain = MatrixPlainTextRenderer.encode(bounded);
        String html = MatrixHtmlRenderer.render(bounded);
        var image = bounded.blocks().stream().filter(SocialDocument.Image.class::isInstance)
                .map(SocialDocument.Image.class::cast)
                .filter(value -> value.source() instanceof SocialDocument.EmbeddedImage).findFirst();
        return new SocialPreparedReply(bounded, () -> {
            if (image.isEmpty()) return text(plain, html);
            var media = image.orElseThrow();
            var embedded = (SocialDocument.EmbeddedImage) media.source();
            return SocialFutures.compose(client.replyImage(event.roomId(), event.eventId(), event.threadRootEventId(),
                    embedded.mediaType(), Base64.getDecoder().decode(embedded.base64Data()), media.width(), media.height(),
                    media.alternativeText(), plain, html), result -> result.status() == SocialDeliveryResult.Status.NOT_SENT
                    ? text(plain, html) : CompletableFuture.completedFuture(result));
        });
    }

    private CompletableFuture<SocialDeliveryResult> text(String plain, String html) {
        return SocialDeliveryResult.observe(client.replyText(event.roomId(), event.eventId(), event.threadRootEventId(), plain, html),
                MatrixApiErrors::deliveryStatus);
    }
}
