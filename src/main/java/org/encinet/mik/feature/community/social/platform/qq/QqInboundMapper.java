package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialMessageReferences;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.feature.community.social.platform.qq.message.QqContentProjector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Maps trusted QQ dispatches into the platform-neutral inbound contract. */
final class QqInboundMapper {
    private final QqPlatformConfig config;
    private final QqOpenApiClient api;
    private final SocialEventSink events;
    private final QqMembershipDirectory memberships;

    QqInboundMapper(
            QqPlatformConfig config,
            QqOpenApiClient api,
            SocialEventSink events,
            QqMembershipDirectory memberships
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.api = Objects.requireNonNull(api, "api");
        this.events = Objects.requireNonNull(events, "events");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
    }

    SocialEventSink.Acceptance accept(QqGatewayGroupMessage event) {
        if (event.authorIsBot() || !config.acceptsGroup(event.groupOpenId())) {
            return SocialEventSink.Acceptance.IGNORED;
        }
        SocialEventSink.Acceptance observed = memberships.accept(event);
        if (observed != SocialEventSink.Acceptance.ACCEPTED) return observed;
        SocialInboundMessage message = map(event, config.appId(),
                new QqReplyChannel(api, event));
        return events.accept(message);
    }

    static SocialInboundMessage map(QqGatewayGroupMessage event, String appId, SocialReplyChannel replyChannel) {
        Optional<ExternalIdentity> sender = QqAccountIdentity.from(
                appId, event.memberOpenId(), event.authorName());
        List<ExternalIdentity> mentioned = event.mentions().stream()
                .filter(reference -> !reference.bot() && !reference.isYou())
                .map(reference -> QqAccountIdentity.from(appId, reference.memberOpenId(), reference.username()))
                .flatMap(Optional::stream).toList();
        QqContentProjector.Projection projected = QqContentProjector.project(
                event.content(), mentionTargets(event, appId));
        Optional<ExternalIdentity> replied = event.repliedAuthor()
                .filter(reference -> !reference.bot() && !reference.isYou())
                .filter(reference -> event.mentions().stream().noneMatch(mention ->
                        mention.memberOpenId().equals(reference.memberOpenId())
                                && (mention.bot() || mention.isYou())))
                .flatMap(reference -> QqAccountIdentity.from(appId, reference.memberOpenId(), reference.username()));
        return new SocialInboundMessage(event.eventId(),
                new SocialConversation(event.groupOpenId(), SocialConversation.Type.GROUP),
                sender, event.authorName().isBlank() ? event.memberOpenId() : event.authorName(),
                new SocialInboundMessage.Text(projected.body(), projected.commandBody(), projected.placeholders()), event.authorIsBot(),
                new SocialMessageReferences(mentioned, replied, projected.mentions()), replyChannel);
    }

    private static Map<String, QqContentProjector.MentionTarget> mentionTargets(
            QqGatewayGroupMessage event, String appId
    ) {
        Map<String, QqContentProjector.MentionTarget> result = new LinkedHashMap<>();
        for (var reference : event.mentions()) {
            String label = reference.username().isBlank()
                    ? (reference.memberOpenId().isBlank() ? reference.tokenId() : reference.memberOpenId())
                    : reference.username();
            var target = new QqContentProjector.MentionTarget(label,
                    QqAccountIdentity.from(appId, reference.memberOpenId(), reference.username()),
                    reference.bot() || reference.isYou());
            if (!reference.tokenId().isBlank()) result.putIfAbsent(reference.tokenId(), target);
            if (!reference.memberOpenId().isBlank()) result.putIfAbsent(reference.memberOpenId(), target);
        }
        return result;
    }
}
