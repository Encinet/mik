package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialFutures;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;
import org.encinet.mik.feature.community.social.platform.qq.client.QqApiErrors;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.feature.community.social.platform.qq.render.QqMarkdownRenderer;
import org.encinet.mik.feature.community.social.platform.qq.render.QqPlainTextRenderer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Prepares one passive reply without I/O; only a definitely unsent image may fall back to text. */
final class QqReplyChannel implements SocialReplyChannel {
    private final QqOpenApiClient api;
    private final QqGatewayGroupMessage event;

    QqReplyChannel(QqOpenApiClient api, QqGatewayGroupMessage event) {
        this.api = Objects.requireNonNull(api, "api");
        this.event = Objects.requireNonNull(event, "event");
    }

    static SocialDocument layout(SocialDocument document, int maximumLength) {
        return SocialDocumentLayout.fit(document, maximumLength, value -> Math.max(value.plainText().length(),
                Math.max(QqMarkdownRenderer.encode(value).length(), QqPlainTextRenderer.encode(value).length())));
    }

    @Override
    public SocialPreparedReply prepare(SocialDocument document, int maximumLength) {
        SocialDocument bounded = layout(document, maximumLength);
        String markdown = QqMarkdownRenderer.encode(bounded);
        String caption = QqPlainTextRenderer.encode(bounded);
        var image = bounded.blocks().stream().filter(SocialDocument.Image.class::isInstance)
                .map(SocialDocument.Image.class::cast).findFirst();
        return new SocialPreparedReply(bounded, () -> {
            if (image.isEmpty()) return text(markdown);
            var media = image.orElseThrow();
            CompletableFuture<SocialDeliveryResult> send = switch (media.source()) {
                case SocialDocument.RemoteImage remote -> api.replyRemoteImage(event.groupOpenId(), event.messageId(), 1, remote.url(), caption);
                case SocialDocument.EmbeddedImage embedded -> api.replyBase64Image(event.groupOpenId(), event.messageId(), 1, embedded.base64Data(), caption);
            };
            return SocialFutures.compose(send, result -> result.status() == SocialDeliveryResult.Status.NOT_SENT
                    ? text(markdown) : CompletableFuture.completedFuture(result));
        });
    }

    private CompletableFuture<SocialDeliveryResult> text(String markdown) {
        return SocialDeliveryResult.observe(api.replyMarkdown(event.groupOpenId(), event.messageId(), 1, markdown), QqApiErrors::deliveryStatus);
    }
}
