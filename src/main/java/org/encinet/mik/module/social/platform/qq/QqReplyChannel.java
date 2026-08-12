package org.encinet.mik.module.social.platform.qq;

import org.encinet.mik.module.social.api.SocialReplyChannel;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.module.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.module.social.platform.qq.render.QqMarkdownRenderer;
import org.encinet.mik.module.social.platform.qq.render.QqPlainTextRenderer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Passive QQ reply credentials captured from one inbound message. */
final class QqReplyChannel implements SocialReplyChannel {
    private final QqOpenApiClient api;
    private final QqGatewayGroupMessage event;
    private final int maximumLength;

    QqReplyChannel(QqOpenApiClient api, QqGatewayGroupMessage event, int maximumLength) {
        this.api = Objects.requireNonNull(api, "api");
        this.event = Objects.requireNonNull(event, "event");
        this.maximumLength = maximumLength;
    }

    @Override
    public CompletionStage<Void> send(SocialDocument document) {
        Optional<SocialDocument.Image> image = document.blocks().stream()
                .filter(SocialDocument.Image.class::isInstance)
                .map(SocialDocument.Image.class::cast)
                .findFirst();
        if (image.isPresent()) {
            SocialDocument.Image media = image.orElseThrow();
            String caption = QqPlainTextRenderer.render(document, maximumLength);
            CompletableFuture<Void> mediaReply = switch (media.source()) {
                case SocialDocument.RemoteImage remote -> api.replyRemoteImage(
                        event.groupOpenId(), event.messageId(), 1, remote.url(), caption);
                case SocialDocument.EmbeddedImage embedded -> api.replyBase64Image(
                        event.groupOpenId(), event.messageId(), 1,
                        embedded.base64Data(), caption);
            };
            return mediaReply
                    .exceptionallyCompose(ignored -> api.replyMarkdown(
                            event.groupOpenId(), event.messageId(), 1,
                            QqMarkdownRenderer.render(document, maximumLength)));
        }
        return api.replyMarkdown(event.groupOpenId(), event.messageId(), 1,
                QqMarkdownRenderer.render(document, maximumLength));
    }
}
