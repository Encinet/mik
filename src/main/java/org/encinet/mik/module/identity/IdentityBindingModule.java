package org.encinet.mik.module.identity;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

/** Composition root and lifecycle boundary for Minecraft identity binding. */
public final class IdentityBindingModule {

    private final JavaPlugin plugin;
    private final IdentityBindingRuntime runtime;
    private final IdentityBindingCommandRegistrar commands;
    private final IdentityPlayerListener playerListener;

    public IdentityBindingModule(JavaPlugin plugin, LanguageService languages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(languages, "languages");

        IdentityBindingService service = new IdentityBindingService(
                new IdentityBindingRepository(
                        new File(plugin.getDataFolder(), "identity-bindings.db")));
        IdentityPlatformRegistry platforms = new IdentityPlatformRegistry();
        IdentityBindingNotifier notifier = new IdentityBindingNotifier(plugin, languages);
        runtime = new IdentityBindingRuntime(service, platforms, notifier);
        IdentityBindingCommandPresenter presenter =
                new IdentityBindingCommandPresenter(languages, runtime);
        notifier.platformNames(presenter::platformName);
        commands = new IdentityBindingCommandRegistrar(plugin, languages, runtime, presenter);
        playerListener = new IdentityPlayerListener(plugin, runtime);
    }

    public void enable() {
        try {
            runtime.open();
            Bukkit.getServicesManager().register(
                    ExternalIdentityLinker.class, runtime, plugin, ServicePriority.Normal);
            Bukkit.getServicesManager().register(
                    IdentityBindingManager.class, runtime, plugin, ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(playerListener, plugin);
            plugin.getLogger().info("Identity binding module enabled with "
                    + runtime.platformCount() + " platform adapter(s)");
        } catch (RuntimeException error) {
            Bukkit.getServicesManager().unregister(IdentityBindingManager.class, runtime);
            Bukkit.getServicesManager().unregister(ExternalIdentityLinker.class, runtime);
            HandlerList.unregisterAll(playerListener);
            try {
                runtime.close();
            } catch (IdentityBindingException closeError) {
                error.addSuppressed(closeError);
            }
            plugin.getLogger().log(Level.SEVERE,
                    "Identity binding database could not be opened; binding is disabled", error);
        }
    }

    public void disable() {
        Bukkit.getServicesManager().unregister(IdentityBindingManager.class, runtime);
        Bukkit.getServicesManager().unregister(ExternalIdentityLinker.class, runtime);
        HandlerList.unregisterAll(playerListener);
        try {
            runtime.close();
        } catch (IdentityBindingException error) {
            plugin.getLogger().warning("Could not close identity binding database: "
                    + error.getMessage());
        }
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        commands.register(manager);
    }

    public IdentityBindingManager manager() {
        return runtime;
    }

    public boolean isAvailable() {
        return runtime.isAvailable();
    }
}
