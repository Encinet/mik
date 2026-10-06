package org.encinet.mik.module.social.platform.qq;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.social.runtime.SocialPlatformAdapter;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.runtime.SocialPlatformPlan;
import org.encinet.mik.module.social.runtime.SocialRuntimePolicy;
import org.encinet.mik.module.social.runtime.SocialOutputPolicy;

import java.util.Objects;
import java.util.Optional;

/** QQ protocol adapter: configuration, Gateway WebSocket, OpenAPI and Markdown only. */
public final class QqPlatformAdapter implements SocialPlatformAdapter {
    public static final IdentityPlatform IDENTITY_PLATFORM = new IdentityPlatform(
            QqPlatformConfig.PLATFORM_ID, "QQ", "/绑定 {code}", true);
    static final SocialPlatformDescriptor DESCRIPTOR =
            new SocialPlatformDescriptor(QqPlatformConfig.PLATFORM_ID, "QQ", IDENTITY_PLATFORM);

    private final JavaPlugin plugin;

    public QqPlatformAdapter(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public SocialPlatformDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Optional<SocialPlatformPlan> prepare() {
        QqPlatformConfig config = QqPlatformConfig.load(plugin);
        if (!config.enabled()) {
            plugin.getLogger().info("QQ is disabled in " + QqPlatformConfig.FILE_NAME);
            return Optional.empty();
        }
        SocialRuntimePolicy policy = new SocialRuntimePolicy(
                config.workerMaxConcurrentTasks(),
                new SocialOutputPolicy(config.contentSafetyEnabled(),
                        config.maxOutboundLength()));
        return Optional.of(new QqPlatformPlan(config, policy, plugin.getLogger()));
    }
}
