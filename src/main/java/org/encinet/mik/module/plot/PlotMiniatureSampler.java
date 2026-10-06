package org.encinet.mik.module.plot;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

final class PlotMiniatureSampler {
    static final int READS_PER_TICK = 512;
    static final int READS_PER_TURN = 128;
    static final int MAX_TERRAIN = 512;
    static final int MAX_UNKNOWN = 12;
    private final Map<UUID, Sample> samples = new HashMap<>();
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private final PlotPreviewCache worker;
    private BukkitTask task;
    private long tick;
    private long revision;

    PlotMiniatureSampler(PlotPreviewCache worker) { this.worker = worker; }

    record Voxel(int index, PlotMiniatureGeometry.Box box, BlockData block) { }
    record Snapshot(long revision, boolean ready, int unknown, List<Voxel> terrain,
                    List<PlotMiniatureGeometry.Box> unknownBoxes, boolean limited, int sampled, int total) {
        Snapshot(long revision, boolean ready, int unknown, List<Voxel> terrain,
                 List<PlotMiniatureGeometry.Box> unknownBoxes, boolean limited) {
            this(revision, ready, unknown, terrain, unknownBoxes, limited, 0, 0);
        }
        static Snapshot empty(int total) { return new Snapshot(0, false, 0, List.of(), List.of(), false, 0, total); }
        int progress() { return total == 0 ? ready ? 100 : 0 : (int) ((long) sampled * 100 / total); }
    }

    void enable(Plugin plugin) { task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1); }

    void disable() {
        if (task != null) task.cancel();
        task = null;
        samples.values().forEach(this::cancelMesh);
        samples.clear();
        queue.clear();
    }

    void forget(UUID player) {
        Sample removed = samples.remove(player);
        if (removed != null) cancelMesh(removed);
        queue.remove(player);
    }

    Snapshot request(UUID player, World world, PlotMiniatureGeometry.Grid grid) {
        Sample sample = samples.get(player);
        if (sample == null || !sample.world.getUID().equals(world.getUID()) || !sample.grid.equals(grid)) {
            forget(player);
            sample = new Sample(world, grid, tick);
            samples.put(player, sample);
            queue.addLast(player);
        }
        sample.touched = tick;
        return sample.snapshot;
    }

    void tick() {
        tick++;
        int remaining = READS_PER_TICK;
        int idle = queue.size();
        boolean meshed = false;
        while (remaining > 0 && idle > 0) {
            UUID player = queue.removeFirst();
            Sample sample = samples.get(player);
            if (sample == null) { idle--; continue; }
            if (tick - sample.touched > 30) {
                samples.remove(player);
                cancelMesh(sample);
                idle--;
                continue;
            }
            queue.addLast(player);
            publish(sample);
            if (sample.finished) {
                if (tick - sample.completed < 200) { idle--; continue; }
                sample.cursor = 0;
                sample.published = 0;
                sample.finished = false;
                sample.blocks = new BlockData[sample.grid.count()];
                sample.states = new String[sample.grid.count()];
                sample.known = new boolean[sample.grid.count()];
            }
            int reads = Math.min(READS_PER_TURN, Math.min(remaining, sample.grid.count() - sample.cursor));
            for (int count = 0; count < reads; count++) sample.read(sample.cursor++);
            remaining -= reads;
            if (reads == 0) idle--;
            else idle = queue.size();
            boolean complete = sample.cursor == sample.grid.count();
            if (!meshed && sample.mesh == null && (complete || !sample.snapshot.ready()
                    && sample.cursor - sample.published >= Math.max(1, sample.grid.count() / 4))) {
                MeshJob job = new MeshJob();
                var grid = sample.grid;
                BlockData[] blocks = sample.blocks.clone();
                String[] states = sample.states.clone();
                boolean[] known = sample.known.clone();
                int cursor = sample.cursor;
                long version = ++revision;
                job.task = new FutureTask<>(() -> {
                    try {
                        Snapshot result = finish(grid, blocks, states, known, cursor, version);
                        if (!Thread.currentThread().isInterrupted() && !job.task.isCancelled()) job.result = result;
                    } catch (CancellationException ignored) {
                    } catch (RuntimeException failure) {
                        System.getLogger(PlotMiniatureSampler.class.getName()).log(System.Logger.Level.ERROR,
                                "Could not build plot miniature", failure);
                    }
                    return null;
                });
                try {
                    worker.execute(job.task);
                    sample.mesh = job;
                    sample.published = cursor;
                    meshed = true;
                    publish(sample);
                } catch (RejectedExecutionException rejected) {
                    worker.cancel(job.task);
                }
            }
        }
    }

    private void publish(Sample sample) {
        MeshJob job = sample.mesh;
        if (job == null || !job.task.isDone()) return;
        sample.mesh = null;
        if (job.result == null) return;
        sample.snapshot = job.result;
        if (job.result.ready()) {
            sample.finished = true;
            sample.completed = tick;
        }
    }

    private void cancelMesh(Sample sample) {
        if (sample.mesh != null) worker.cancel(sample.mesh.task);
    }

    private static final class MeshJob {
        private FutureTask<Void> task;
        private volatile Snapshot result;
    }

    private static final class Sample {
        private final World world;
        private final PlotMiniatureGeometry.Grid grid;
        private BlockData[] blocks;
        private String[] states;
        private boolean[] known;
        private int cursor;
        private long touched;
        private long completed;
        private int published;
        private boolean finished;
        private Snapshot snapshot;
        private MeshJob mesh;

        private Sample(World world, PlotMiniatureGeometry.Grid grid, long tick) {
            this.world = world;
            this.grid = grid;
            touched = tick;
            blocks = new BlockData[grid.count()];
            states = new String[grid.count()];
            known = new boolean[grid.count()];
            snapshot = Snapshot.empty(grid.count());
        }

        private void read(int index) {
            int sampleIndex = grid.index(grid.column(index), grid.rows() - 1 - grid.row(index), grid.depth(index));
            var box = grid.box(sampleIndex);
            int blockX = (int) Math.floor(box.worldX());
            int blockY = (int) Math.floor(box.worldY());
            int blockZ = (int) Math.floor(box.worldZ());
            if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
                known[sampleIndex] = true;
                return;
            }
            if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                return;
            }
            var block = world.getBlockAt(blockX, blockY, blockZ);
            known[sampleIndex] = true;
            if (!block.isEmpty()) {
                BlockData data = block.getBlockData();
                blocks[sampleIndex] = data;
                if (data.isOccluding()) states[sampleIndex] = data.getAsString();
            }
        }
    }

    private static Snapshot finish(PlotMiniatureGeometry.Grid grid, BlockData[] blocks, String[] states,
                                   boolean[] known, int cursor, long revision) {
        List<Integer> unknown = new ArrayList<>();
        for (int index = 0; index < cursor; index++) {
            int sampleIndex = grid.index(grid.column(index), grid.rows() - 1 - grid.row(index), grid.depth(index));
            if (!known[sampleIndex]) unknown.add(sampleIndex);
        }
        var mesh = PlotMiniatureMesher.mesh(grid, blocks, states, MAX_TERRAIN);
        List<PlotMiniatureGeometry.Box> missing = new ArrayList<>();
        int missingCount = Math.min(MAX_UNKNOWN, unknown.size());
        for (int count = 0; count < missingCount; count++)
            missing.add(grid.box(unknown.get(count * unknown.size() / missingCount)));
        return new Snapshot(revision, cursor == grid.count(), unknown.size(), mesh.terrain(),
                List.copyOf(missing), mesh.limited() || !grid.exact(), cursor, grid.count());
    }
}
