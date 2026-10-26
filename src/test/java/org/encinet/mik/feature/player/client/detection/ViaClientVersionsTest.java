package org.encinet.mik.feature.player.client.detection;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ViaClientVersionsTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUniqueId")) return id;
                throw new AssertionError(method.getName());
            });

    @Test
    void reportsCurrentVersionFactsWithoutAnyTipOrLanguageService() {
        AtomicReference<ProtocolVersion> client = new AtomicReference<>(ProtocolVersion.v1_21_4);
        var versions = new ViaClientVersions("1.21.8", ProtocolVersion.v1_21_7, ignored -> client.get());
        var older = versions.version(player).orElseThrow();
        assertEquals("1.21.4", older.name());
        assertEquals("1.21.8", older.serverVersion());
        assertTrue(older.outdated());

        client.set(ProtocolVersion.v1_21_7);
        assertFalse(versions.version(player).orElseThrow().outdated());
    }

    @Test
    void versionsSharingAProtocolAndNewerClientsAreNotOutdated() {
        var patch = new ViaClientVersions("1.21.8", ProtocolVersion.getClosest("1.21.8"),
                ignored -> ProtocolVersion.getClosest("1.21.7"));
        assertFalse(patch.version(player).orElseThrow().outdated());
        var newer = new ViaClientVersions("1.21.4", ProtocolVersion.v1_21_4, ignored -> ProtocolVersion.v1_21_7);
        assertFalse(newer.version(player).orElseThrow().outdated());
    }

    @Test
    void unknownClientHasNoVersionAndUnknownServerDoesNotImplyAnOutdatedClient() {
        var clientUnknown = new ViaClientVersions("1.21.8", ProtocolVersion.v1_21_7,
                ignored -> ProtocolVersion.unknown);
        assertTrue(clientUnknown.version(player).isEmpty());
        var serverUnknown = new ViaClientVersions("custom", ProtocolVersion.unknown,
                ignored -> ProtocolVersion.v1_21_4);
        assertEquals("1.21.4", serverUnknown.version(player).orElseThrow().name());
        assertFalse(serverUnknown.version(player).orElseThrow().outdated());
    }
}
