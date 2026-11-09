package org.encinet.mik.feature.world.plot;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotMapTerrainTest {
    private final Thread caller = Thread.currentThread();
    private final UUID worldId = UUID.randomUUID();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicBoolean loaded = new AtomicBoolean(true);
    private final BlockData data = proxy(BlockData.class, (method, _) -> {
        assertEquals("getMapColor", method); return Color.fromRGB(0x85bb65);
    });
    private final Block block = proxy(Block.class, (method, _) -> {
        assertEquals("getBlockData", method); return data;
    });
    private final World world = proxy(World.class, (method, _) -> {
        assertSame(caller, Thread.currentThread());
        return switch (method) {
            case "getUID" -> worldId;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> loaded.get();
            case "getHighestBlockYAt" -> { reads.incrementAndGet(); assertTrue(loaded.get()); yield 64; }
            case "getBlockAt" -> { assertTrue(loaded.get()); yield block; }
            default -> throw new AssertionError("Map attempted forbidden world access: " + method);
        };
    });

    @Test void samplingIsBoundedAndFairAndNeverLoadsUnknownChunks() {
        var terrain = new PlotMapTerrain();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var viewport = PlotMapViewport.near(-1, -1);
        terrain.request(first, world, viewport);
        terrain.request(second, world, viewport);
        terrain.tick();
        terrain.tick();
        assertTrue(reads.get() <= PlotMapTerrain.READS_PER_TICK * 2);
        assertTrue(terrain.request(first, world, viewport).read() > 0);
        assertTrue(terrain.request(second, world, viewport).read() > 0);
        for (int tick = 0; tick < 128 && !(terrain.request(first, world, viewport).ready()
                && terrain.request(second, world, viewport).ready()); tick++) {
            terrain.request(first, world, viewport);
            terrain.request(second, world, viewport);
            terrain.tick();
        }
        var complete = terrain.request(first, world, viewport);
        assertTrue(complete.ready());
        assertNotEquals(0, complete.colors().getFirst());
        loaded.set(false);
        terrain.forget(first);
        terrain.forget(second);
        terrain.request(first, world, viewport);
        reads.set(0);
        for (int tick = 0; tick < 64; tick++) { terrain.request(first, world, viewport); terrain.tick(); }
        assertEquals(0, reads.get());
        var unknown = terrain.request(first, world, viewport);
        assertTrue(unknown.ready());
        assertEquals(PlotMapViewport.COUNT, unknown.unknown());
        assertTrue(unknown.colors().stream().allMatch(color -> color == 0));
        terrain.disable();
    }

    @Test void forgottenOrExpiredViewsStopReadingImmediately() {
        var terrain = new PlotMapTerrain();
        UUID player = UUID.randomUUID();
        var viewport = PlotMapViewport.near(0, 0);
        terrain.request(player, world, viewport);
        terrain.tick();
        terrain.forget(player);
        reads.set(0);
        terrain.tick();
        assertEquals(0, reads.get());
        terrain.request(player, world, viewport);
        for (int tick = 0; tick < 32; tick++) terrain.tick();
        assertEquals(0, terrain.request(player, world, viewport).read());
    }

    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (_, method, args) -> call.invoke(method.getName(), args)));
    }
}
