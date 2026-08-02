package org.encinet.mik.module.geyser;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Optional, failure-contained access to the local Geyser API. */
public final class GeyserService implements BedrockPlayerDetector {

    private static final String PLUGIN_NAME = "Geyser-Spigot";

    private final GeyserApiAdapter adapter;
    private final Logger logger;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private GeyserService(GeyserApiAdapter adapter, Logger logger) {
        this.adapter = adapter;
        this.logger = logger;
    }

    public static GeyserService create(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Plugin geyser = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (geyser == null || !geyser.isEnabled()) {
            return new GeyserService(null, plugin.getLogger());
        }
        try {
            GeyserApiAdapter adapter = GeyserApiAdapter.create();
            plugin.getLogger().info("Geyser detected; Bedrock client adaptations enabled.");
            return new GeyserService(adapter, plugin.getLogger());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().warning("Geyser is enabled but its API is unavailable; Bedrock adaptations are disabled: "
                    + message(error));
            return new GeyserService(null, plugin.getLogger());
        }
    }

    public boolean available() {
        return adapter != null;
    }

    @Override
    public boolean isBedrockPlayer(UUID playerId) {
        if (adapter == null) return false;
        try {
            return adapter.isBedrockPlayer(playerId);
        } catch (LinkageError | RuntimeException error) {
            reportFailure("query a Bedrock player", error);
            return false;
        }
    }

    public boolean sendForm(UUID playerId, BedrockSimpleForm form) {
        if (adapter == null) return false;
        try {
            return adapter.sendForm(playerId, Objects.requireNonNull(form, "form"),
                    error -> reportFailure("handle a Bedrock form response", error));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            reportFailure("send a Bedrock form", error);
            return false;
        }
    }

    public void closeForm(UUID playerId) {
        if (adapter == null) return;
        try {
            adapter.closeForm(playerId);
        } catch (LinkageError | RuntimeException error) {
            reportFailure("close a Bedrock form", error);
        }
    }

    private void reportFailure(String operation, Throwable error) {
        if (failureLogged.compareAndSet(false, true)) {
            logger.warning("Unable to " + operation + " through Geyser: " + message(error));
        }
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }
}
