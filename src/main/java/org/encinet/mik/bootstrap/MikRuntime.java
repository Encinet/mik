package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.presentation.BrandingModule;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.logging.Level;

/** Connects feature groups in dependency order and owns the plugin's resources. */
public final class MikRuntime {
    private final JavaPlugin plugin;
    private final BrandingModule brandingModule = new BrandingModule();
    private final ModuleLifecycle lifecycle;
    private final MaintenanceCrashMarker crashMarker;
    private boolean enableCompleted;

    public MikRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lifecycle = new ModuleLifecycle(plugin.getLogger());
        this.crashMarker = new MaintenanceCrashMarker(
                plugin.getDataFolder().toPath().resolve(MaintenanceCrashMarker.FILE_NAME),
                plugin.getLogger());
    }

    public void load() {
        PlasmoVoiceServer.getAddonsLoader().load(plugin);
    }

    public void enable(PlasmoVoiceServer voiceServer) {
        try {
            lifecycle.startManaged("branding", brandingModule::enable, brandingModule::disable);
            boolean resumeFromCrash = crashMarker.begin();

            SharedServices shared = SharedServices.enable(plugin, lifecycle);
            PlayerFeatures players = PlayerFeatures.enable(plugin, lifecycle, shared, voiceServer);
            ServerFeatures server = ServerFeatures.enable(plugin, lifecycle, shared, players, resumeFromCrash);
            CommunityFeatures community = CommunityFeatures.enable(plugin, lifecycle, shared, players, server);
            WorldFeatures world = WorldFeatures.enable(plugin, lifecycle, shared, community);
            EntertainmentFeatures.enable(plugin, lifecycle, shared, players, voiceServer);
            EntryPoints.enable(plugin, lifecycle, shared, players, server, community, world);

            enableCompleted = true;
        } catch (RuntimeException | LinkageError startupError) {
            lifecycle.stopAll(startupError);
            try {
                plugin.getServer().getScheduler().cancelTasks(plugin);
            } catch (RuntimeException | LinkageError cleanupError) {
                if (cleanupError != startupError) startupError.addSuppressed(cleanupError);
            }
            throw startupError;
        }
    }

    public void disable() {
        boolean cleanShutdown = enableCompleted;
        if (!lifecycle.stopAll(null)) cleanShutdown = false;
        try {
            PlasmoVoiceServer.getAddonsLoader().unload(plugin);
        } catch (RuntimeException | LinkageError error) {
            cleanShutdown = false;
            plugin.getLogger().log(Level.SEVERE, "Could not unload Plasmo Voice addon", error);
        }
        if (cleanShutdown) crashMarker.clearAfterCleanShutdown();
        enableCompleted = false;
    }
}
