package org.encinet.mik.module.skript;

import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.module.space.NonEuclideanSpaceService;
import org.encinet.mik.module.space.SpaceLink;
import org.encinet.mik.module.space.SpaceLinkEntrances;
import org.encinet.mik.module.space.SpaceNetwork;
import org.encinet.mik.module.space.SpaceRegistration;
import org.encinet.mik.module.space.SpaceReloadResult;
import org.encinet.mik.module.space.SpaceTransform;
import org.encinet.mik.module.space.SpaceVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikSkriptSpaceRegistryTest {

    @Test
    void registersTheRequestedRigidMappingAndOnlyOwnerCanRemoveIt() {
        FakeSpaceService service = new FakeSpaceService();
        MikSkriptSpaceRegistry registry = new MikSkriptSpaceRegistry(service);
        World world = world("world");

        registry.register(
                "scripts/jump.sk",
                "jump-door",
                location(world, 329.5, 80.0, 1363.0),
                location(world, 329.5, 83.0, 1366.0),
                "west",
                "up",
                location(world, 324.0, 72.5, 1363.0),
                location(world, 321.0, 72.5, 1366.0),
                "down",
                "west",
                SpaceLinkEntrances.BOTH);

        SpaceLink link = service.snapshot().link("jump-door").orElseThrow();
        SpaceTransform transform = new SpaceTransform(
                link.first().frame(), link.second().frame());
        assertVector(new SpaceVector(0.0, -1.0, 0.0),
                transform.mapDirection(new SpaceVector(-1.0, 0.0, 0.0)));
        assertVector(new SpaceVector(0.0, 0.0, -1.0),
                transform.mapDirection(new SpaceVector(0.0, 0.0, -1.0)));

        SpaceTransform inverse = new SpaceTransform(
                link.second().frame(), link.first().frame());
        assertVector(new SpaceVector(-1.0, 0.0, 0.0),
                inverse.mapDirection(new SpaceVector(0.0, -1.0, 0.0)));
        assertVector(new SpaceVector(0.0, 0.0, -1.0),
                inverse.mapDirection(new SpaceVector(0.0, 0.0, -1.0)));
        assertTrue(registry.registered("jump-door"));

        registry.unregister("scripts/other.sk", "jump-door");
        assertTrue(registry.registered("jump-door"));
        registry.unregister("scripts/jump.sk", "jump-door");
        assertFalse(registry.registered("jump-door"));
    }

    @Test
    void unloadRemovesOnlyLinksOwnedByThatScript() {
        FakeSpaceService service = new FakeSpaceService();
        MikSkriptSpaceRegistry registry = new MikSkriptSpaceRegistry(service);
        World world = world("world");

        registerWallPair(registry, world, "scripts/a.sk", "space-a", 0.0);
        registerWallPair(registry, world, "scripts/b.sk", "space-b", 20.0);
        registry.clearOwnedBy("scripts/a.sk");

        assertFalse(registry.registered("space-a"));
        assertTrue(registry.registered("space-b"));
        registry.clear();
        assertFalse(registry.registered("space-b"));
    }

    @Test
    void invalidSurfaceDoesNotRegisterAPartialLink() {
        FakeSpaceService service = new FakeSpaceService();
        MikSkriptSpaceRegistry registry = new MikSkriptSpaceRegistry(service);
        World world = world("world");

        assertThrows(IllegalArgumentException.class, () -> registry.register(
                "scripts/broken.sk",
                "broken",
                location(world, 0.0, 0.0, 0.0),
                location(world, 0.0, 3.0, 3.0),
                "west",
                "west",
                location(world, 10.0, 0.0, 0.0),
                location(world, 10.0, 3.0, 3.0),
                "east",
                "up",
                SpaceLinkEntrances.FIRST));
        assertFalse(registry.registered("broken"));
    }

    private void registerWallPair(
            MikSkriptSpaceRegistry registry,
            World world,
            String owner,
            String id,
            double offset
    ) {
        registry.register(
                owner,
                id,
                location(world, offset, 0.0, 0.0),
                location(world, offset, 3.0, 3.0),
                "west",
                "up",
                location(world, offset + 10.0, 0.0, 0.0),
                location(world, offset + 10.0, 3.0, 3.0),
                "east",
                "up",
                SpaceLinkEntrances.FIRST);
    }

    private static Location location(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "World[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static void assertVector(SpaceVector expected, SpaceVector actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9);
        assertEquals(expected.y(), actual.y(), 1.0E-9);
        assertEquals(expected.z(), actual.z(), 1.0E-9);
    }

    private static final class FakeSpaceService implements NonEuclideanSpaceService {

        private final Map<String, SpaceLink> links = new LinkedHashMap<>();

        @Override
        public SpaceNetwork snapshot() {
            return SpaceNetwork.of(links.values());
        }

        @Override
        public SpaceReloadResult reload() {
            return SpaceReloadResult.success(links.size());
        }

        @Override
        public SpaceRegistration register(SpaceLink link) {
            if (links.putIfAbsent(link.id(), link) != null) {
                throw new IllegalArgumentException(
                        "A spatial link named '" + link.id() + "' is already registered");
            }
            return new FakeRegistration(link.id());
        }

        private final class FakeRegistration implements SpaceRegistration {

            private final String id;
            private boolean active = true;

            private FakeRegistration(String id) {
                this.id = id;
            }

            @Override
            public String linkId() {
                return id;
            }

            @Override
            public boolean active() {
                return active;
            }

            @Override
            public void close() {
                if (!active) {
                    return;
                }
                active = false;
                links.remove(id);
            }
        }
    }
}
