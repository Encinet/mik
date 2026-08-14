package org.encinet.mik.module.social.platform.matrix;

import org.encinet.mik.module.social.api.SocialReplyChannel;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.platform.matrix.render.MatrixHtmlRenderer;
import org.encinet.mik.module.social.platform.matrix.render.MatrixPlainTextRenderer;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixRoomMessage;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Passive Matrix room/event credentials captured from one inbound message. */
final class MatrixReplyChannel implements SocialReplyChannel {
    private final MatrixClient client;
    private final MatrixRoomMessage event;
    private final int maximumLength;

    MatrixReplyChannel(MatrixClient client, MatrixRoomMessage event, int maximumLength) {
        this.client = Objects.requireNonNull(client, "client");
        this.event = Objects.requireNonNull(event, "event");
        this.maximumLength = maximumLength;
    }

    @Override
    public CompletionStage<Void> send(SocialDocument document) {
        String plain = MatrixPlainTextRenderer.render(document, maximumLength);
        String html = MatrixHtmlRenderer.render(document);
        Optional<SocialDocument.Image> image = document.blocks().stream()
                .filter(SocialDocument.Image.class::isInstance)
                .map(SocialDocument.Image.class::cast)
                .filter(value -> value.source() instanceof SocialDocument.EmbeddedImage)
                .findFirst();
        if (image.isEmpty()) {
            return textReply(plain, html);
        }

        SocialDocument.Image media = image.orElseThrow();
        SocialDocument.EmbeddedImage embedded =
                (SocialDocument.EmbeddedImage) media.source();
        byte[] data = Base64.getDecoder().decode(embedded.base64Data());
        return client.replyImage(event.roomId(), event.eventId(),
                        event.threadRootEventId(), embedded.mediaType(), data,
                        media.width(), media.height(), media.alternativeText(), plain, html)
                .exceptionallyCompose(ignored -> textReply(plain, html));
    }

    private java.util.concurrent.CompletableFuture<Void> textReply(
            String plain,
            String html
    ) {
        return client.replyText(event.roomId(), event.eventId(),
                event.threadRootEventId(), plain, html);
    }
}
