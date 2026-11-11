package org.encinet.mik.feature.community.social.platform.qq;
import org.encinet.mik.feature.community.social.runtime.SocialPlatformPlan;
import org.encinet.mik.feature.community.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.feature.community.social.api.SocialPlatformSession;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.platform.qq.client.QqAccessTokenProvider;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewaySession;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinApproval;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinReconciler;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.runtime.SocialRuntimePolicy;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

/** Immutable QQ configuration prepared for one host generation. */
record QqPlatformPlan(
        QqPlatformConfig config,
        SocialRuntimePolicy runtimePolicy,
        Path membershipsFile,
        Logger logger
) implements SocialPlatformPlan {
    QqPlatformPlan {
        config = Objects.requireNonNull(config, "config");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        membershipsFile = Objects.requireNonNull(membershipsFile, "membershipsFile");
        logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SocialCommandSyntax commandSyntax() {
        return SocialCommandSyntax.caseInsensitive(config.defaultLanguage(), "/");
    }

    @Override
    public SocialPlatformSession open(SocialPlatformRuntimeContext context) {
        QqMembershipDirectory memberships = new QqMembershipDirectory(config, membershipsFile, logger);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        QqAccessTokenProvider tokenProvider = new QqAccessTokenProvider(
                httpClient, config);
        QqOpenApiClient api = new QqOpenApiClient(httpClient, config, tokenProvider);
        QqInboundMapper inbound = new QqInboundMapper(config, api, context.events(), memberships);
        QqGroupJoinApproval joinApproval = new QqGroupJoinApproval(config, api, logger);
        QqGroupJoinReconciler reconciler = new QqGroupJoinReconciler(config, api, joinApproval, logger);
        QqGatewaySession gateway = new QqGatewaySession(
                httpClient, config, tokenProvider, inbound::accept, joinApproval::accept, memberships::accept,
                status -> {
                    memberships.gateway(status);
                    context.status().update(status);
                    if (status.state() == SocialSessionStatus.State.READY) reconciler.requestScan();
                }, logger);
        return new QqPlatformSession(gateway, httpClient, api, config, joinApproval, reconciler, memberships);
    }
}
