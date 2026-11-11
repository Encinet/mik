package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.social.runtime.SocialPlatformPlan;
import org.encinet.mik.feature.community.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.feature.community.social.api.SocialPlatformSession;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.TrixnityEncryption;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoControl;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory;
import org.encinet.mik.feature.community.social.runtime.SocialRuntimePolicy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/** Immutable Matrix configuration prepared for one host generation. */
record MatrixPlatformPlan(
        MatrixPlatformConfig config,
        SocialRuntimePolicy runtimePolicy,
        Path cryptoDirectory,
        MatrixCryptoControl cryptoControl,
        Logger logger
) implements SocialPlatformPlan {
    MatrixPlatformPlan {
        config = Objects.requireNonNull(config, "config");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        cryptoDirectory = Objects.requireNonNull(cryptoDirectory, "cryptoDirectory");
        cryptoControl = Objects.requireNonNull(cryptoControl, "cryptoControl");
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
        MatrixRoomDirectory rooms = new MatrixRoomDirectory();
        var encryption = new TrixnityEncryption(config, cryptoDirectory, config.recoveryKey(), logger);
        MatrixClient client = new MatrixClient(httpClient, config, rooms::access, encryption);
        MatrixInboundMapper inbound = new MatrixInboundMapper(
                config, client, context.events());
        MatrixPlatformSession session = new MatrixPlatformSession(
                httpClient, client, config, inbound, rooms, encryption, () -> cryptoControl.closed(encryption),
                context.status()::update, logger);
        cryptoControl.opened(encryption);
        return session;
    }
}
