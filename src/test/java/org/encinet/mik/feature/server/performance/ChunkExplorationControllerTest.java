package org.encinet.mik.feature.server.performance;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.player.movement.FlightSpeedControl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkExplorationControllerTest {
    @Test
    void joinAndTeleportBurstsGetGraceBeforeSustainedExplorationIsLimited() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.sample();
            for (int second = 1; second < 10; second++) {
                fixture.tick(200);
                assertFalse(fixture.controller.limit(fixture.id).throttled());
            }
            for (int second = 10; second <= 14; second++) fixture.tick(200);
            assertTrue(fixture.controller.limit(fixture.id).loadRatio() < 1.0D);
            assertTrue(fixture.flightCeiling < 1.0f);
            var from = fixture.location();
            fixture.x += 10_000.0D;
            fixture.controller.onTeleport(new PlayerTeleportEvent(fixture.player, from, fixture.location(),
                    PlayerTeleportEvent.TeleportCause.PLUGIN));
            assertEquals(ChunkExplorationPolicy.OPEN, fixture.controller.limit(fixture.id));
            assertEquals(1.0f, fixture.flightCeiling);
            fixture.controller.sample();
            for (int second = 0; second < 9; second++) {
                fixture.tick(500);
                assertFalse(fixture.controller.limit(fixture.id).throttled());
            }
        }
    }

    @Test
    void alreadyLoadedTerrainDoesNotCreateGenerationPressure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.loaded = true;
            fixture.controller.sample();
            for (int second = 0; second < 30; second++) fixture.tick(200);
            assertEquals(ChunkExplorationPolicy.OPEN, fixture.controller.limit(fixture.id));
            assertTrue(fixture.probes > 0);
        }
    }

    @Test
    void fastExplorationWithLargeChunkTrafficStaysUnrestrictedWhenResourcesAreHealthy() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.serverMspt = 20.0D;
            fixture.controller.sample();
            for (int second = 0; second < 60; second++) fixture.tick(500);
            assertEquals(ChunkExplorationPolicy.OPEN, fixture.controller.limit(fixture.id));
            assertEquals(1.0f, fixture.flightCeiling);
            assertEquals(0, fixture.changes);
            assertTrue(fixture.probes > 0);
        }
    }

    @Test
    void smallClientAndSimulationRangesAreNotChargedForTheEntireWorldViewRadius() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.clientDistance = 2;
            fixture.simulationDistance = 2;
            fixture.step = 32.0D;
            fixture.serverMspt = 50.0D;
            fixture.controller.sample();
            for (int second = 0; second < 30; second++) fixture.tick(4);
            assertEquals(ChunkExplorationPolicy.OPEN, fixture.controller.limit(fixture.id));
        }
    }

    @Test
    void quittingDoesNotReapplyOverridesToTheStillOnlineQuitSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.controller.sample();
            for (int second = 1; second <= 14; second++) fixture.tick(200);
            assertTrue(fixture.controller.limit(fixture.id).throttled());
            int changes = fixture.changes;
            fixture.controller.onQuit(new PlayerQuitEvent(fixture.player, Component.empty(),
                    PlayerQuitEvent.QuitReason.DISCONNECTED));
            assertEquals(changes, fixture.changes);
            assertEquals(ChunkExplorationPolicy.OPEN, fixture.controller.limit(fixture.id));
            assertEquals(1.0f, fixture.flightCeiling);
        }
    }

    private static final class Fixture implements AutoCloseable, FlightSpeedControl {
        private final UUID id = UUID.randomUUID();
        private final UUID worldId = UUID.randomUUID();
        private final Field serverField;
        private final Server previousServer;
        private long now;
        private double x;
        private boolean loaded;
        private int probes;
        private int changes;
        private float flightCeiling = 1.0f;
        private int clientDistance = 32;
        private int simulationDistance = 6;
        private double step = 100.0D;
        private double serverMspt = 50.0D;
        private final World world = proxy(World.class, (_, method, _) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "isChunkLoaded" -> {
                probes++;
                yield loaded;
            }
            default -> throw new AssertionError("Unexpected world access: " + method.getName());
        });
        private final Player player = proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getLocation" -> location();
            case "getViewDistance" -> 16;
            case "getClientViewDistance" -> clientDistance;
            case "getSimulationDistance" -> simulationDistance;
            case "isFlying" -> true;
            case "getFlySpeed" -> flightCeiling;
            case "hashCode" -> id.hashCode();
            case "equals" -> object == args[0];
            default -> throw new AssertionError(method.getName());
        });
        private final Chunk chunk = proxy(Chunk.class, (_, method, _) -> {
            if (method.getName().equals("getWorld")) return world;
            throw new AssertionError("Observing chunk delivery must not inspect chunk contents");
        });
        private final ChunkExplorationController controller = new ChunkExplorationController(null, this,
                () -> 0.2D, () -> serverMspt, () -> changes++, () -> now);

        Fixture() throws ReflectiveOperationException {
            Server server = proxy(Server.class, (_, method, _) -> {
                if (method.getName().equals("getOnlinePlayers")) return List.of(player);
                throw new AssertionError(method.getName());
            });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = Bukkit.getServer();
            serverField.set(null, server);
        }

        Location location() {
            return new Location(world, x, 100.0D, 0.0D);
        }

        void tick(int chunks) {
            now += 1_000_000_000L;
            x += step;
            for (int i = 0; i < chunks; i++) controller.onChunkSent(new PlayerChunkLoadEvent(chunk, player));
            controller.sample();
        }

        @Override
        public void limitExplorationSpeed(Player player, float maximumSpeed) {
            flightCeiling = maximumSpeed;
        }

        @Override
        public void releaseExplorationSpeed(Player player) {
            flightCeiling = 1.0f;
        }

        @Override
        public void close() throws IllegalAccessException {
            serverField.set(null, previousServer);
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }
}
