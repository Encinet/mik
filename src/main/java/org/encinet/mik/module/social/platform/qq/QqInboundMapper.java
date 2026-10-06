package org.encinet.mik.module.social.platform.qq;

import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.module.social.platform.qq.gateway.QqGatewayGroupMessage;

import java.util.Objects;

/** Maps trusted QQ dispatches into the platform-neutral inbound contract. */
final class QqInboundMapper {
    private final QqPlatformConfig config;
    private final QqOpenApiClient api;
    private final SocialEventSink events;

    QqInboundMapper(
            QqPlatformConfig config,
            QqOpenApiClient api,
            SocialEventSink events
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.api = Objects.requireNonNull(api, "api");
        this.events = Objects.requireNonNull(events, "events");
    }

    SocialEventSink.Acceptance accept(QqGatewayGroupMessage event) {
        if (event.authorIsBot() || !config.acceptsGroup(event.groupOpenId())) {
            return SocialEventSink.Acceptance.IGNORED;
        }
        return events.accept(event.toInboundMessage(
                QqPlatformConfig.PLATFORM_ID, config.appId(),
                new QqReplyChannel(api, event, config.maxOutboundLength())));
    }
}
