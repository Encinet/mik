package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialFutures;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.platform.qq.client.QqApiErrors;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage;
import org.encinet.mik.feature.community.social.platform.qq.render.QqDocumentRenderer;

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

    @Override
    public SocialPreparedReply prepare(SocialDocument document, int maximumLength) {
        var rendered = QqDocumentRenderer.prepare(document, maximumLength);
        SocialDocument bounded = rendered.document();
        String caption = rendered.caption();
        var image = bounded.blocks().stream().filter(SocialDocument.Image.class::isInstance)
                .map(SocialDocument.Image.class::cast).findFirst();
        return new SocialPreparedReply(bounded, () -> {
            if (image.isEmpty() || !rendered.supportsNativeMedia()) return text(rendered.message());
            var media = image.orElseThrow();
            CompletableFuture<SocialDeliveryResult> send = switch (media.source()) {
                case SocialDocument.RemoteImage remote -> api.replyRemoteImage(event.groupOpenId(), event.messageId(), 1, remote.url(), caption);
                case SocialDocument.EmbeddedImage embedded -> api.replyBase64Image(event.groupOpenId(), event.messageId(), 1, embedded.base64Data(), caption);
            };
            return SocialFutures.compose(send, result -> result.status() == SocialDeliveryResult.Status.NOT_SENT
                    ? text(rendered.message()) : CompletableFuture.completedFuture(result));
        });
    }

    private CompletableFuture<SocialDeliveryResult> text(QqOutgoingMessage message) {
        return SocialDeliveryResult.observe(api.replyMessage(event.groupOpenId(), event.messageId(), 1, message), QqApiErrors::deliveryStatus);
    }
}
