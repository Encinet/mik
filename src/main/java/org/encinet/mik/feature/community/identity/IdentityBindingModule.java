package org.encinet.mik.feature.community.identity;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Composition root and lifecycle boundary for Minecraft identity binding. */
public final class IdentityBindingModule {

    private final JavaPlugin plugin;
    private final IdentityBindingRuntime runtime;
    private final IdentityBindingCommandRegistrar commands;
    private final IdentityPlayerListener playerListener;
    private final IdentityAdmission admission;
    private final ExecutorService writes = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256), Thread.ofPlatform().daemon().name("mik-identity-writes").factory());

    public IdentityBindingModule(JavaPlugin plugin, LanguageService languages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(languages, "languages");

        IdentityBindingService service = new IdentityBindingService(
                new IdentityBindingRepository(
                        new File(plugin.getDataFolder(), "identity-bindings.db")));
        IdentityPlatformRegistry platforms = new IdentityPlatformRegistry();
        IdentityBindingNotifier notifier = new IdentityBindingNotifier(plugin, languages);
        runtime = new IdentityBindingRuntime(service, platforms, notifier, writes);
        IdentityBindingCommandPresenter presenter =
                new IdentityBindingCommandPresenter(languages, runtime);
        notifier.platformNames(presenter::platformName);
        commands = new IdentityBindingCommandRegistrar(plugin, languages, runtime, presenter, writes);
        playerListener = new IdentityPlayerListener(plugin, runtime, writes);
        admission = new IdentityAdmission(plugin, runtime, languages);
    }

    public void enable() {
        try {
            runtime.open();
            Bukkit.getServicesManager().register(
                    ExternalIdentityLinker.class, runtime, plugin, ServicePriority.Normal);
            Bukkit.getServicesManager().register(
                    IdentityBindingManager.class, runtime, plugin, ServicePriority.Normal);
            Bukkit.getPluginManager().registerEvents(playerListener, plugin);
            admission.enable();
            plugin.getLogger().info("Identity binding module enabled with "
                    + runtime.platformCount() + " platform adapter(s)");
        } catch (RuntimeException error) {
            Bukkit.getServicesManager().unregister(IdentityBindingManager.class, runtime);
            Bukkit.getServicesManager().unregister(ExternalIdentityLinker.class, runtime);
            HandlerList.unregisterAll(playerListener);
            admission.close();
            writes.shutdownNow();
            try {
                runtime.close();
            } catch (RuntimeException closeError) {
                error.addSuppressed(closeError);
            }
            plugin.getLogger().log(Level.SEVERE,
                    "Identity binding module could not start", error);
            throw error;
        }
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("identity binding manager registration", () ->
                Bukkit.getServicesManager().unregister(IdentityBindingManager.class, runtime));
        shutdown.attempt("external identity linker registration", () ->
                Bukkit.getServicesManager().unregister(ExternalIdentityLinker.class, runtime));
        shutdown.attempt("identity listener", () -> HandlerList.unregisterAll(playerListener));
        shutdown.attempt("identity admission", admission::close);
        shutdown.attempt("identity binding writes", writes::shutdownNow);
        shutdown.attempt("identity binding database", runtime::close);
        shutdown.finish("identity binding module");
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
