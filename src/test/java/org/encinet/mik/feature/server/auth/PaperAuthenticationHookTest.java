package org.encinet.mik.feature.server.auth;

import com.destroystokyo.paper.profile.PaperMinecraftSessionService;
import com.destroystokyo.paper.profile.PaperServicesDiscoveryService;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import com.mojang.authlib.services.response.discovery.DiscoveryResponse;
import org.junit.jupiter.api.Test;

import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperAuthenticationHookTest {
    @Test
    void acceptsTheSessionServiceCreatedByPaper() {
        var discovery = new PaperServicesDiscoveryService(Proxy.NO_PROXY, false, DiscoveryResponse::offline);
        var service = discovery.createMinecraftSessionService();

        assertDoesNotThrow(() -> PaperAuthenticationHook.checkSessionService(service));
    }

    @Test
    void acceptsTheSessionServiceCreatedByMojang() {
        var discovery = MinecraftServicesDiscoveryService.createOffline(Proxy.NO_PROXY);
        var service = discovery.createMinecraftSessionService();

        assertDoesNotThrow(() -> PaperAuthenticationHook.checkSessionService(service));
    }

    @Test
    void rejectsCustomSubclassesAndReportsTheirActualType() {
        var discovery = new PaperServicesDiscoveryService(Proxy.NO_PROXY, false, DiscoveryResponse::offline);
        var keys = discovery.getServicesKeySet();

        assertUnsupported(new MinecraftServicesSessionService(keys, Proxy.NO_PROXY, discovery) {});
        assertUnsupported(new PaperMinecraftSessionService(keys, Proxy.NO_PROXY, discovery) {});
    }

    private static void assertUnsupported(SessionService service) {
        var failure = assertThrows(IllegalStateException.class,
                () -> PaperAuthenticationHook.checkSessionService(service));
        assertTrue(failure.getMessage().contains(service.getClass().getName()));
    }
}
