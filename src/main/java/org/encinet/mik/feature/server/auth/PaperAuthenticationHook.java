package org.encinet.mik.feature.server.auth;

import com.destroystokyo.paper.profile.PaperMinecraftSessionService;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import org.spigotmc.SpigotConfig;

import java.lang.reflect.Field;

/** The Paper 26.3-specific replacement lives here; all other authentication rules use authlib interfaces. */
final class PaperAuthenticationHook implements AutoCloseable {
    private final MinecraftServer server;
    private final Field field;
    private final Services original;
    private final Services replacement;
    private final boolean preventProxyConnections;

    static boolean supported() {
        return MinecraftServer.getServer().usesAuthentication() && !SpigotConfig.bungee
                && !GlobalConfiguration.get().proxies.velocity.enabled
                && !GlobalConfiguration.get().proxies.proxyProtocol;
    }

    PaperAuthenticationHook(MinecraftServer server, FallbackSessionService service) throws ReflectiveOperationException {
        this.server = server;
        original = server.services();
        checkSessionService(original.sessionService());
        field = MinecraftServer.class.getDeclaredField("services");
        field.setAccessible(true);
        // Preserve Paper's auxiliary services, caches, keys and profile resolver by identity.
        replacement = new Services(service, original.servicesKeySet(), original.profileRepository(),
                original.nameToIdCache(), original.profileResolver(), original.paper());
        preventProxyConnections = server.getPreventProxyConnections();
    }

    static void checkSessionService(SessionService service) {
        Class<?> type = service.getClass();
        // Paper creates its own subclass even when no plugin has replaced authentication.
        if (type != MinecraftServicesSessionService.class && type != PaperMinecraftSessionService.class) {
            throw new IllegalStateException("Unsupported session service: " + type.getName()
                    + "; expected the built-in Mojang or Paper implementation");
        }
    }

    void install() throws IllegalAccessException {
        if (server.services() != original) throw new IllegalStateException("Session service changed during startup");
        field.set(server, replacement);
        server.setPreventProxyConnections(true);
    }

    @Override public void close() throws IllegalAccessException {
        if (server.services() != replacement) return;
        field.set(server, original);
        server.setPreventProxyConnections(preventProxyConnections);
    }
}
