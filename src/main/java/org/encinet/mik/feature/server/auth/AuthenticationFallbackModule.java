package org.encinet.mik.feature.server.auth;

import net.minecraft.server.MinecraftServer;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Owns the cache and session-service hook, without changing online mode or password admission. */
public final class AuthenticationFallbackModule {
    private static final Duration MAX_AGE = Duration.ofHours(72);
    private static final int MAX_PLAYERS = 10_000;
    private final JavaPlugin plugin;
    private ExecutorService worker;
    private AuthenticationFallbackStore store;
    private FallbackSessionService service;
    private PaperAuthenticationHook hook;

    public AuthenticationFallbackModule(JavaPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        if (worker != null) return;
        try {
            if (!PaperAuthenticationHook.supported()) {
                plugin.getLogger().warning("Authentication fallback requires direct online-mode connections without proxy forwarding; module inactive.");
                return;
            }
            worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("mik-auth-cache").factory());
            store = new AuthenticationFallbackStore(plugin.getDataFolder().toPath().resolve("auth-fallback.db"),
                    Clock.systemUTC(), MAX_AGE, MAX_PLAYERS);
            worker.submit(() -> { store.open(); return null; }).get();
            var server = MinecraftServer.getServer();
            service = new FallbackSessionService(server.services().sessionService(), store,
                    server.getPreventProxyConnections(), failure -> plugin.getLogger().log(Level.SEVERE,
                    "Authentication cache failed; fallback disabled until restart. Upstream authentication remains active.", failure),
                    id -> plugin.getLogger().warning("Authentication unavailable; using cached identity for " + id));
            hook = new PaperAuthenticationHook(server, service);
            hook.install();
            plugin.getLogger().info("Authentication fallback enabled; cached identities expire after " + MAX_AGE.toHours() + " hours.");
        } catch (Exception | LinkageError error) {
            try { disable(); } catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Could not enable authentication fallback; upstream authentication remains active.", error);
        }
    }

    public void disable() {
        if (service != null) service.close();
        var shutdown = new ShutdownSequence();
        if (hook != null) shutdown.attempt("authentication hook", hook::close);
        if (worker != null) {
            if (store != null) shutdown.attempt("authentication cache", () -> worker.submit(() -> {
                store.close(); return null;
            }).get());
            shutdown.attempt("authentication worker", () -> {
                worker.shutdown();
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                    throw new IllegalStateException("Authentication cache worker did not stop");
                }
            });
        }
        shutdown.finish("authentication fallback");
        hook = null;
        service = null;
        store = null;
        worker = null;
    }
}
