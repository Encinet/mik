package org.encinet.mik.module.social.platform.matrix;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.social.api.SocialPlatformAdapter;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformPlan;
import org.encinet.mik.module.social.runtime.SocialOutputPolicy;
import org.encinet.mik.module.social.runtime.SocialRuntimePolicy;

import java.util.Objects;
import java.util.Optional;

/** Matrix Client-Server API adapter. End-to-end encrypted rooms are intentionally unsupported. */
public final class MatrixPlatformAdapter implements SocialPlatformAdapter {
    public static final IdentityPlatform IDENTITY_PLATFORM = new IdentityPlatform(
            "matrix", "Matrix", "!绑定 {code}");
    static final SocialPlatformDescriptor DESCRIPTOR =
            new SocialPlatformDescriptor("matrix", "Matrix", IDENTITY_PLATFORM);

    private final JavaPlugin plugin;

    public MatrixPlatformAdapter(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        return Optional.of(new MatrixPlatformPlan(config, policy, plugin.getLogger()));
    }
}
