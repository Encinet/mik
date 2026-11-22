package org.encinet.mik.feature.server.auth;

import com.destroystokyo.paper.profile.PaperMinecraftSessionService;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import org.spigotmc.SpigotConfig;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;

/** The Paper 26.3-specific replacement lives here; all other authentication rules use authlib interfaces. */
final class PaperAuthenticationHook implements AutoCloseable {
    private final MinecraftServer server;
    private final Field field;
    private final Services original;
    private final Services replacement;
    private final FallbackSessionService fallback;
    private final boolean preventProxyConnections;

    static boolean supported() {
        return MinecraftServer.getServer().usesAuthentication() && !SpigotConfig.bungee
                && !GlobalConfiguration.get().proxies.velocity.enabled
                && !GlobalConfiguration.get().proxies.proxyProtocol;
    }

    PaperAuthenticationHook(MinecraftServer server, AuthenticationFallbackStore store, Consumer<Exception> storageFailure,
                            Consumer<UUID> fallbackUsed) throws ReflectiveOperationException {
        this.server = server;
        original = server.services();
        var upstream = original.sessionService();
        var verifier = new OfficialSessionVerifier(sessionDiscovery(upstream));
        preventProxyConnections = server.getPreventProxyConnections();
        fallback = new FallbackSessionService(upstream, verifier, store, preventProxyConnections, storageFailure, fallbackUsed);
        field = MinecraftServer.class.getDeclaredField("services");
        field.setAccessible(true);
        // Preserve Paper's auxiliary services, caches, keys and profile resolver by identity.
        replacement = new Services(fallback, original.servicesKeySet(), original.profileRepository(),
                original.nameToIdCache(), original.profileResolver(), original.paper());
    }

    static MinecraftServicesDiscoveryService sessionDiscovery(SessionService service) throws ReflectiveOperationException {
        Class<?> type = service.getClass();
        // Paper creates its own subclass even when no plugin has replaced authentication.
        if (type != MinecraftServicesSessionService.class && type != PaperMinecraftSessionService.class) {
            throw new IllegalStateException("Unsupported session service: " + type.getName()
                    + "; expected the built-in Mojang or Paper implementation");
        }
        // Reuse the running service's discovered endpoints and proxy rather than starting a second discovery.
        var upstreamDiscovery = MinecraftServicesSessionService.class.getDeclaredField("discoveryService");
        upstreamDiscovery.setAccessible(true);
        return (MinecraftServicesDiscoveryService) upstreamDiscovery.get(service);
    }

    void install() throws IllegalAccessException {
        if (server.services() != original) throw new IllegalStateException("Session service changed during startup");
        field.set(server, replacement);
        server.setPreventProxyConnections(true);
    }

    @Override public void close() throws IllegalAccessException {
        fallback.close();
        if (server.services() != replacement) return;
        field.set(server, original);
        server.setPreventProxyConnections(preventProxyConnections);
    }
}
