package org.encinet.mik.feature.community.social.platform.matrix;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.identity.IdentityPlatform;
import org.encinet.mik.feature.community.social.runtime.SocialPlatformAdapter;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.runtime.SocialPlatformPlan;
import org.encinet.mik.feature.community.social.runtime.SocialOutputPolicy;
import org.encinet.mik.feature.community.social.runtime.SocialRuntimePolicy;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoControl;

import java.util.Objects;
import java.util.Optional;

/** Matrix Client-Server API adapter with a persistent encryption device. */
public final class MatrixPlatformAdapter implements SocialPlatformAdapter {
    public static final IdentityPlatform IDENTITY_PLATFORM = new IdentityPlatform(
            MatrixPlatformConfig.PLATFORM_ID, "Matrix", "!bind {code}");
    static final SocialPlatformDescriptor DESCRIPTOR =
            new SocialPlatformDescriptor(MatrixPlatformConfig.PLATFORM_ID, "Matrix", 0x55FFFF, IDENTITY_PLATFORM);

    private final JavaPlugin plugin;
    private final MatrixCryptoControl cryptoControl;

    public MatrixPlatformAdapter(JavaPlugin plugin, MatrixCryptoControl cryptoControl) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cryptoControl = Objects.requireNonNull(cryptoControl, "cryptoControl");
    }

    @Override
    public SocialPlatformDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Optional<SocialPlatformPlan> prepare() {
        MatrixPlatformConfig config = MatrixPlatformConfig.load(plugin);
        if (!config.enabled()) {
            plugin.getLogger().info("Matrix is disabled in " + MatrixPlatformConfig.FILE_NAME);
            return Optional.empty();
        }
        SocialRuntimePolicy policy = new SocialRuntimePolicy(
                config.workerMaxConcurrentTasks(),
                new SocialOutputPolicy(config.contentSafetyEnabled(),
                        config.maxOutboundLength()));
        return Optional.of(new MatrixPlatformPlan(config, policy,
                plugin.getDataFolder().toPath().resolve("social/matrix-crypto"), cryptoControl, plugin.getLogger()));
    }
}
