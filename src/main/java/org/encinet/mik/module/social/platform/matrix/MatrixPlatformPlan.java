package org.encinet.mik.module.social.platform.matrix;

import org.encinet.mik.module.social.api.SocialPlatformPlan;
import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.runtime.SocialRuntimePolicy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

/** Immutable Matrix configuration prepared for one host generation. */
record MatrixPlatformPlan(
        MatrixPlatformConfig config,
        SocialRuntimePolicy runtimePolicy,
        Logger logger
) implements SocialPlatformPlan {
    MatrixPlatformPlan {
        config = Objects.requireNonNull(config, "config");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SocialCommandSyntax commandSyntax() {
        return SocialCommandSyntax.caseInsensitive(config.defaultLanguage(), "!");
    }

    @Override
    public SocialPlatformSession open(SocialPlatformRuntimeContext context) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        MatrixClient client = new MatrixClient(httpClient, config);
        MatrixInboundMapper inbound = new MatrixInboundMapper(
                config, client, context.events());
        MatrixPlatformSession session = new MatrixPlatformSession(
                httpClient, client, config, inbound,
                context.status()::update, logger);
        return session;
    }
}
