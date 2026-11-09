package org.encinet.mik.feature.server.performance;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.server.performance.NetworkEgressThrottlePolicy.Limit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkEgressModuleTest {
    private static final Limit AFK_ONLY = new Limit(1.0D, 2.0D / 32.0D);
    private static final Limit ACTIVE = new Limit(0.5D, 2.0D / 32.0D);

    @Test
    void appliesToTheOriginalClientBoundDistanceWithoutCompounding() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.clientDistance = 8;
            fixture.module.applyThrottle(ACTIVE);
            assertEquals(5, fixture.effectiveDistance());
            fixture.module.applyThrottle(ACTIVE);
            assertEquals(5, fixture.effectiveDistance());
            fixture.module.applyThrottle(NetworkEgressThrottlePolicy.OPEN);
            assertEquals(-1, fixture.explicitDistance);
            assertEquals(9, fixture.effectiveDistance());
        }
    }

    @Test
    void afkChangesImmediatelyUseTheCurrentCategoryAndRestoreInheritance() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.module.applyThrottle(AFK_ONLY);
            assertEquals(-1, fixture.explicitDistance);
            fixture.afk = true;
            fixture.module.onAfkStateChanged(fixture.player, null);
            assertEquals(2, fixture.effectiveDistance());
            fixture.afk = false;
            fixture.module.onAfkStateChanged(fixture.player, null);
            assertEquals(-1, fixture.explicitDistance);
            assertEquals(32, fixture.effectiveDistance());
        }
    }

    @Test
    void worldAndClientChangesRecomputeFromTheInheritedBaseline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.module.applyThrottle(ACTIVE);
            assertEquals(16, fixture.effectiveDistance());
            fixture.worldDistance = 8;
            fixture.module.applyThrottle(ACTIVE);
            assertEquals(4, fixture.effectiveDistance());
            fixture.clientDistance = 2;
            fixture.module.applyThrottle(ACTIVE);
            assertEquals(3, fixture.effectiveDistance());
            fixture.module.applyThrottle(NetworkEgressThrottlePolicy.OPEN);
            assertEquals(-1, fixture.explicitDistance);
        }
    }

    @Test
    void recoveryDoesNotExpandAnExternalLowerDistance() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.module.applyThrottle(ACTIVE);
            fixture.explicitDistance = 4;
            fixture.module.applyThrottle(new Limit(0.75D, 2.0D / 32.0D));
            assertEquals(4, fixture.effectiveDistance());
            fixture.module.applyThrottle(NetworkEgressThrottlePolicy.OPEN);
            assertEquals(4, fixture.explicitDistance);
        }
    }

    @Test
    void globalAndPersonalLimitsUseTheOriginalRangeInsteadOfMultiplyingEachOther() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.module.applyThrottle(ACTIVE);
            var personal = new ChunkExplorationPolicy.Limit(0.5D, 0.5D, 0.2f);
            fixture.module.applyPlayerLimits(fixture.player, personal);
            assertEquals(16, fixture.viewDistance());
            assertEquals(16, fixture.effectiveDistance());
            fixture.module.applyPlayerLimits(fixture.player, personal);
            assertEquals(16, fixture.effectiveDistance());
            fixture.module.applyPlayerLimits(fixture.player, new ChunkExplorationPolicy.Limit(0.5D, 0.75D, 0.3f));
            assertEquals(24, fixture.viewDistance());
            assertEquals(16, fixture.effectiveDistance());
            fixture.module.applyThrottle(NetworkEgressThrottlePolicy.OPEN);
            assertEquals(-1, fixture.explicitView);
            assertEquals(-1, fixture.explicitDistance);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Server previousServer;
        private final UUID playerId = UUID.randomUUID();
        private int worldDistance = 32;
        private int clientDistance = 32;
        private int explicitDistance = -1;
        private int explicitView = -1;
        private boolean afk;
        private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (_, method, _) -> switch (method.getName()) {
                    case "getSendViewDistance", "getViewDistance" -> worldDistance;
                    default -> throw new AssertionError(method.getName());
                });
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (_, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getWorld" -> world;
                    case "getSendViewDistance" -> effectiveDistance();
                    case "getViewDistance" -> viewDistance();
                    case "setViewDistance" -> {
                        explicitView = (int) args[0];
                        yield null;
                    }
                    case "getClientViewDistance" -> clientDistance;
                    case "setSendViewDistance" -> {
                        explicitDistance = (int) args[0];
                        yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        private final AfkService afkService = (AfkService) Proxy.newProxyInstance(AfkService.class.getClassLoader(),
                new Class<?>[]{AfkService.class}, (_, method, _) -> {
                    if (method.getName().equals("isAfk")) return afk;
                    throw new AssertionError(method.getName());
                });
        private final NetworkEgressModule module = new NetworkEgressModule(null, afkService, () -> 10.0D,
                new org.encinet.mik.feature.player.movement.FlightSpeedControl() {
                    public void limitExplorationSpeed(Player ignored, float speed) {}
                    public void releaseExplorationSpeed(Player ignored) {}
                });

        Fixture() throws ReflectiveOperationException {
            Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                    new Class<?>[]{Server.class}, (_, method, _) -> {
                        if (method.getName().equals("getOnlinePlayers")) return List.of(player);
                        throw new AssertionError(method.getName());
                    });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = Bukkit.getServer();
            serverField.set(null, server);
        }

        int effectiveDistance() {
            return Math.min(explicitDistance == -1 ? worldDistance : explicitDistance,
                    Math.min(viewDistance(), PlayerViewDistancePolicy.clientChunkLimit(clientDistance)));
        }

        int viewDistance() {
            return explicitView == -1 ? worldDistance : explicitView;
        }

        @Override
        public void close() throws IllegalAccessException {
            serverField.set(null, previousServer);
        }
    }
}
