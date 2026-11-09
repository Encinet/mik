package org.encinet.mik.feature.world.plot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Bounded, round-robin surface sampling on the server thread. Never requests chunk loads. */
final class PlotMapTerrain {
    static final int READS_PER_TICK = 256;
    static final int READS_PER_TURN = 64;
    private final Map<UUID, Sample> samples = new HashMap<>();
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private BukkitTask task;
    private long tick;
    private long revision;

    record Snapshot(long revision, List<Integer> colors, int read, int unknown) {
        boolean ready() { return read == PlotMapViewport.COUNT; }
        int progress() { return read * 100 / PlotMapViewport.COUNT; }
    }

    void enable(Plugin plugin) { task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1); }
    void disable() {
        if (task != null) task.cancel();
        task = null;
        samples.clear();
        queue.clear();
    }
    void forget(UUID player) { samples.remove(player); queue.remove(player); }

    Snapshot request(UUID player, World world, PlotMapViewport viewport) {
        Sample sample = samples.get(player);
        if (sample == null || !sample.world.getUID().equals(world.getUID()) || !sample.viewport.equals(viewport)) {
            forget(player);
            sample = new Sample(world, viewport);
            samples.put(player, sample);
            queue.addLast(player);
        }
        sample.touched = tick;
        return sample.snapshot;
    }

    void tick() {
        tick++;
        int remaining = READS_PER_TICK, idle = queue.size();
        while (remaining > 0 && idle > 0) {
            UUID player = queue.removeFirst();
            Sample sample = samples.get(player);
            if (tick - sample.touched > 30) {
                samples.remove(player);
                idle--;
                continue;
            }
            queue.addLast(player);
            if (sample.cursor == PlotMapViewport.COUNT) {
                if (tick - sample.completed < 20) { idle--; continue; }
                sample.cursor = 0;
                sample.unknown = 0;
                sample.published = 0;
            }
            int reads = Math.min(READS_PER_TURN, Math.min(remaining, PlotMapViewport.COUNT - sample.cursor));
            for (int count = 0; count < reads; count++) {
                int index = sample.cursor++;
                int x = sample.viewport.x(index % PlotMapViewport.SIDE) + sample.viewport.step() / 2;
                int z = sample.viewport.z(index / PlotMapViewport.SIDE) + sample.viewport.step() / 2;
                int color = 0;
                int height = Integer.MIN_VALUE;
                if (sample.world.isChunkLoaded(x >> 4, z >> 4)) {
                    int y = sample.world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                    if (y >= sample.world.getMinHeight() && y < sample.world.getMaxHeight()) {
                        height = y;
                        color = sample.world.getBlockAt(x, y, z).getBlockData().getMapColor().asRGB();
                        int north = index >= PlotMapViewport.SIDE ? sample.heights[index - PlotMapViewport.SIDE] : y;
                        double slope = north == Integer.MIN_VALUE ? 0 : (y - north) * 4.0 / (sample.viewport.step() + 4);
                        int shade = slope > .6 ? 255 : slope < -.6 ? 180 : 220;
                        color = shade(color, shade);
                    }
                } else sample.unknown++;
                sample.heights[index] = height;
                sample.colors.set(index, color);
            }
            remaining -= reads;
            if (sample.cursor == PlotMapViewport.COUNT || sample.cursor - sample.published >= READS_PER_TICK) {
                sample.snapshot = new Snapshot(++revision, List.copyOf(sample.colors), sample.cursor, sample.unknown);
                sample.published = sample.cursor;
            }
            if (sample.cursor == PlotMapViewport.COUNT) sample.completed = tick;
            idle = queue.size();
        }
    }

    private static int shade(int rgb, int brightness) {
        return (((rgb >> 16) & 255) * brightness / 255) << 16
                | (((rgb >> 8) & 255) * brightness / 255) << 8 | (rgb & 255) * brightness / 255;
    }

    private static final class Sample {
        final World world;
        final PlotMapViewport viewport;
        final List<Integer> colors = new ArrayList<>(java.util.Collections.nCopies(PlotMapViewport.COUNT, 0));
        final int[] heights = new int[PlotMapViewport.COUNT];
        Snapshot snapshot = new Snapshot(0, List.copyOf(colors), 0, 0);
        int cursor, unknown, published;
        long touched, completed;
        Sample(World world, PlotMapViewport viewport) { this.world = world; this.viewport = viewport; }
    }
}
