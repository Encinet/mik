package org.encinet.mik.module.social.platform.qq;
import org.encinet.mik.module.social.api.SocialPlatformPlan;
import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.platform.qq.client.QqAccessTokenProvider;
import org.encinet.mik.module.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.module.social.platform.qq.gateway.QqGatewaySession;
import org.encinet.mik.module.social.runtime.SocialRuntimePolicy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

/** Immutable QQ configuration prepared for one host generation. */
record QqPlatformPlan(
        QqPlatformConfig config,
        SocialRuntimePolicy runtimePolicy,
        Logger logger
) implements SocialPlatformPlan {
    QqPlatformPlan {
        config = Objects.requireNonNull(config, "config");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SocialCommandSyntax commandSyntax() {
        return SocialCommandSyntax.caseInsensitive(config.defaultLanguage(), "/");
    }

    @Override
    public SocialPlatformSession open(SocialPlatformRuntimeContext context) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        QqAccessTokenProvider tokenProvider = new QqAccessTokenProvider(
                httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokenProvider);
        QqInboundMapper inbound = new QqInboundMapper(config, api, context.events());
        QqGatewaySession gateway = new QqGatewaySession(
                httpClient, config, tokenProvider, inbound::accept,
                context.status()::update, logger);
        return new QqPlatformSession(gateway, httpClient);
    }
}
