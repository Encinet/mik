package org.encinet.mik.module.world.regen;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Regenerates selections without reading or mutating a live Bukkit world off-thread.
 * Paper generates source chunks asynchronously, immutable snapshots are compared on a
 * worker pool, and the resulting block and biome changes are applied on the server thread under
 * a strict per-tick time budget.
 */
final class AsyncRegenService implements AutoCloseable {

    private static final long APPLY_BUDGET_NANOS = 3_000_000L;
    private static final int MAX_CHANGES_PER_TICK = 4_096;
    private static final int MAX_CHANGES_PER_TASK_SLICE = 256;
    private static final int MAX_BUFFERED_CHUNKS_PER_TASK = 6;
    private static final int MAX_CONCURRENT_CHUNK_PLANS = 2;
    private static final int PROGRESS_INTERVAL_TICKS = 40;
    private static final int MAX_PREVIEW_CHANGE_SAMPLES = 384;
    private static final int MAX_PREVIEW_RISK_SAMPLES = 128;

    private final JavaPlugin plugin;
    private final Listener listener;
    private final ShadowWorldManager shadowWorlds;
    private final ExecutorService comparisonExecutor;
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private final Map<ChunkTicketKey, Integer> ticketReferences = new HashMap<>();

    private BukkitTask tickTask;
    private boolean closed;
    private long ticks;
    private int roundRobinStart;
    private int planningRoundRobinStart;
    private int inFlightPlans;

    AsyncRegenService(JavaPlugin plugin, Listener listener) {
        this.plugin = plugin;
        this.listener = listener;
        this.shadowWorlds = new ShadowWorldManager(plugin);
        this.comparisonExecutor = Executors.newFixedThreadPool(2, new RegenThreadFactory(plugin.getName()));
    }

    void start() {
        requireServerThread();
        if (closed) {
            throw new IllegalStateException("Regeneration service is already closed");
        }
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    SubmitResult previewRegeneration(UUID owner, RegenSelection selection, RegenBlockFilter filter) {
        return submit(owner, selection, RegenPlan.regeneratePreview(filter));
    }

    SubmitResult applyRegeneration(UUID owner, RegenSelection selection, RegenBlockFilter filter) {
        return submit(owner, selection, RegenPlan.regenerateApply(filter));
    }

    SubmitResult previewUpgrade(UUID owner, RegenSelection selection, RegenUpgradeScope scope) {
        return submit(owner, selection, RegenPlan.upgradePreview(scope));
    }

    SubmitResult applyUpgrade(
            UUID owner,
            RegenSelection selection,
            RegenUpgradeScope scope,
            Set<String> structureMetadataIdentities
    ) {
        return submit(owner, selection, RegenPlan.upgradeApply(scope, structureMetadataIdentities));
    }

    private SubmitResult submit(UUID owner, RegenSelection selection, RegenPlan plan) {
        requireServerThread();
        if (closed) {
            throw new IllegalStateException("Regeneration service is closed");
        }
        Task existing = tasks.get(owner);
        if (existing != null && !existing.state.terminal()) {
            return SubmitResult.ALREADY_RUNNING;
        }

        World target = selection.world();
        if (Bukkit.getWorld(target.getUID()) != target) {
            throw new IllegalStateException("The selected world is not loaded");
        }
        World shadow = shadowWorlds.worldFor(target);
        Task task = new Task(owner, selection, plan, target, shadow);
        tasks.put(owner, task);
        return SubmitResult.ACCEPTED;
    }

    RegenTaskSnapshot snapshot(UUID owner) {
        requireServerThread();
        Task task = tasks.get(owner);
        return task == null ? null : task.snapshot();
    }

    boolean cancel(UUID owner) {
        requireServerThread();
        Task task = tasks.get(owner);
        if (task == null || task.state.terminal()) {
            return false;
        }
        cancel(task, true);
        return true;
    }

    @Override
    public void close() {
        requireServerThread();
        if (closed) {
            return;
        }
        closed = true;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        for (Task task : List.copyOf(tasks.values())) {
            cancel(task, false);
        }
        tasks.clear();
        comparisonExecutor.shutdownNow();
        inFlightPlans = 0;
        releaseOrphanedTickets();
        shadowWorlds.close();
    }

    private void tick() {
        requireServerThread();
        if (closed) {
            return;
        }
        ticks++;

        List<Task> active = new ArrayList<>(tasks.values());
        int activeSize = active.size();
        for (int offset = 0; offset < activeSize; offset++) {
            Task task = active.get((planningRoundRobinStart + offset) % activeSize);
            if (!task.state.terminal()) {
                pumpPlanning(task);
            }
        }
        if (activeSize > 0) {
            planningRoundRobinStart = (planningRoundRobinStart + 1) % activeSize;
        } else {
            planningRoundRobinStart = 0;
        }

        long deadline = System.nanoTime() + APPLY_BUDGET_NANOS;
        int remainingChanges = MAX_CHANGES_PER_TICK;
        int size = active.size();
        for (int offset = 0; offset < size && remainingChanges > 0; offset++) {
            if (offset > 0 && System.nanoTime() >= deadline) {
                break;
            }
            Task task = active.get((roundRobinStart + offset) % size);
            if (task.state.terminal()) {
                continue;
            }
            try {
                int applied = applySlice(task,
                        Math.min(remainingChanges, MAX_CHANGES_PER_TASK_SLICE), deadline);
                remainingChanges -= applied;
                tryComplete(task);
            } catch (Throwable error) {
                fail(task, error);
            }
        }
        if (size > 0) {
            roundRobinStart = (roundRobinStart + 1) % size;
        } else {
            roundRobinStart = 0;
        }

        if (ticks % PROGRESS_INTERVAL_TICKS == 0) {
            for (Task task : List.copyOf(tasks.values())) {
                if (!task.state.terminal()) {
                    listener.progress(task.snapshot());
                }
            }
        }
    }

    private void pumpPlanning(Task task) {
        if (task.state.terminal() || task.inFlight != null) {
            return;
        }
        if (task.nextChunk >= task.selection.chunks().size()) {
            task.planningComplete = true;
            if (!task.hasPendingChanges()) {
                tryComplete(task);
            } else {
                task.state = RegenTaskState.APPLYING;
            }
            return;
        }
        if (inFlightPlans >= MAX_CONCURRENT_CHUNK_PLANS
                || task.bufferedChunks() >= MAX_BUFFERED_CHUNKS_PER_TASK) {
            return;
        }

        RegenChunkPos chunk = task.selection.chunks().get(task.nextChunk++);
        task.state = RegenTaskState.GENERATING;
        acquirePlanningSlot(task);
        try {
            CompletableFuture<RegenChunkCapture> current = captureChunk(task, task.target, chunk, false);
            task.inFlight = current.thenCompose(currentCapture -> {
                if (currentCapture == null) {
                    return CompletableFuture.completedFuture(RegenChunkChanges.ungenerated(
                            chunk, task.selection.worldMinHeight()));
                }
                return captureChunk(task, task.shadow, chunk, true)
                        .thenApplyAsync(generatedCapture -> RegenChunkPlanner.plan(
                                task.selection, generatedCapture, currentCapture, task.plan),
                                comparisonExecutor);
            });
            task.inFlight.whenComplete((changes, error) -> runOnServerThread(() -> {
                task.inFlight = null;
                releasePlanningSlot(task);
                if (task.state.terminal() || closed) {
                    return;
                }
                if (error != null) {
                    fail(task, unwrap(error));
                    return;
                }
                acceptPlan(task, changes);
            }));
        } catch (Throwable error) {
            releasePlanningSlot(task);
            fail(task, error);
        }
    }

    private CompletableFuture<RegenChunkCapture> captureChunk(
            Task task,
            World world,
            RegenChunkPos position,
            boolean source
    ) {
        return world.getChunkAtAsync(position.x(), position.z(), source, false).thenApply(chunk -> {
            requireServerThread();
            if (chunk == null) {
                return null;
            }
            if (!source && !task.state.terminal()) {
                retainTicket(task, chunk);
            }
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, task.plan.biomes(), false, false);
            boolean captureBlockEntities = task.plan.blocks()
                    && (source || task.plan.preview() || task.plan.kind().upgrade());
            Map<Integer, BlockState> blockEntities = captureBlockEntities
                    ? captureBlockEntities(task.selection, chunk)
                    : Map.of();
            boolean shouldCaptureStructures = task.plan.structures() && (source || task.plan.preview());
            RegenStructureCapture structures = shouldCaptureStructures
                    ? captureStructures(chunk, source && task.plan.kind() == RegenTaskKind.UPGRADE_APPLY)
                    : RegenStructureCapture.EMPTY;
            return new RegenChunkCapture(snapshot, blockEntities, structures);
        });
    }

    private Map<Integer, BlockState> captureBlockEntities(RegenSelection selection, Chunk chunk) {
        Map<Integer, BlockState> result = new HashMap<>();
        int minHeight = selection.worldMinHeight();
        for (BlockState state : chunk.getTileEntities(true)) {
            int x = state.getX();
            int y = state.getY();
            int z = state.getZ();
            if (y < minHeight || y >= selection.worldMaxHeight() || !selection.contains(x, y, z)) {
                continue;
            }
            result.put(PackedBlockPosition.pack(x & 15, y, z & 15, minHeight), state);
        }
        return result;
    }

    private RegenStructureCapture captureStructures(Chunk chunk, boolean includeMigrationData) {
        ChunkAccess handle = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        net.minecraft.server.level.ServerLevel source = ((CraftWorld) chunk.getWorld()).getHandle();
        Registry<Structure> registry = source.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<String, RegenStructureDescriptor> descriptors = new HashMap<>();
        Map<String, RegenStructureStartData> starts = new HashMap<>();
        Set<RegenStructureReference> references = new HashSet<>();

        for (Map.Entry<Structure, LongSet> entry : handle.getAllReferences().entrySet()) {
            String type = structureId(registry, entry.getKey());
            for (long packedStart : entry.getValue()) {
                int startX = ChunkPos.getX(packedStart);
                int startZ = ChunkPos.getZ(packedStart);
                RegenStructureDescriptor descriptor = RegenStructureDescriptor.reference(type, startX, startZ);
                descriptors.putIfAbsent(descriptor.identity(), descriptor);
                if (includeMigrationData) {
                    references.add(new RegenStructureReference(
                            descriptor.identity(), type, packedStart));
                }
            }
        }
        for (Map.Entry<Structure, StructureStart> entry : handle.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) {
                continue;
            }
            net.minecraft.world.level.levelgen.structure.BoundingBox box = start.getBoundingBox();
            RegenStructureDescriptor descriptor = RegenStructureDescriptor.start(
                    structureId(registry, entry.getKey()),
                    start.getChunkPos().x(),
                    start.getChunkPos().z(),
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            descriptors.put(descriptor.identity(), descriptor);
            if (includeMigrationData) {
                starts.put(descriptor.identity(), RegenStructureStartData.capture(
                        descriptor.identity(), descriptor.type(), start, source));
            }
        }
        return new RegenStructureCapture(Set.copyOf(descriptors.values()), starts, references);
    }

    private static String structureId(Registry<Structure> registry, Structure structure) {
        Identifier key = registry.getKey(structure);
        return key == null ? "unknown" : key.toString();
    }

    private void acceptPlan(Task task, RegenChunkChanges changes) {
        task.plannedChunks++;
        if (changes.skippedUngeneratedChunk()) {
            task.skippedUngeneratedChunks++;
            task.ungeneratedChunks.add(changes.chunk());
            task.appliedChunks++;
            return;
        }
        task.discoveredChanges += changes.blockSize();
        task.discoveredBiomeChanges += changes.biomeSize();
        task.skippedBoundaryBiomeCells += changes.skippedBoundaryBiomeCells();
        task.atRiskBlockEntities += changes.atRiskBlockEntities();
        for (RegenStructureDescriptor structure : changes.structures()) {
            task.structures.merge(structure.identity(), structure,
                    (existing, discovered) -> discovered.boundsKnown() ? discovered : existing);
        }
        for (RegenStructureDescriptor structure : changes.targetStructures()) {
            task.targetStructures.merge(structure.identity(), structure,
                    (existing, discovered) -> discovered.boundsKnown() ? discovered : existing);
        }
        task.discoveredStructureStarts += changes.structureStartSize();
        task.discoveredStructureReferences += changes.structureReferenceSize();

        if (task.plan.preview()) {
            task.previewChangedBlocks.addAll(changes.previewSamples().changedBlocks());
            task.previewRiskBlockEntities.addAll(changes.previewSamples().atRiskBlockEntities());
            task.appliedChunks++;
            releaseTicket(task, changes.chunk());
            return;
        }
        if (changes.totalUnits() == 0) {
            task.appliedChunks++;
            releaseTicket(task, changes.chunk());
        } else {
            task.pendingChanges.addLast(changes);
        }
    }

    private int applySlice(Task task, int maximumChanges, long deadline) {
        if (maximumChanges <= 0 || task.state.terminal()) {
            return 0;
        }
        if (Bukkit.getWorld(task.target.getUID()) != task.target) {
            throw new IllegalStateException("The selected world was unloaded during regeneration");
        }

        int processed = 0;
        while (processed < maximumChanges) {
            if (processed > 0 && System.nanoTime() >= deadline) {
                break;
            }
            if (task.currentChanges == null) {
                task.currentChanges = task.pendingChanges.pollFirst();
                task.currentBlockIndex = 0;
                task.currentBiomeIndex = 0;
                task.currentStructureStartIndex = 0;
                task.currentStructureReferenceIndex = 0;
                task.currentChunkBiomeChanged = false;
                task.currentChunkStructureStartsChanged = false;
                if (task.currentChanges == null) {
                    break;
                }
            }

            RegenChunkChanges changes = task.currentChanges;
            if (task.currentBlockIndex < changes.blockSize()) {
                applyBlock(task, changes, task.currentBlockIndex++);
            } else if (task.currentBiomeIndex < changes.biomeSize()) {
                applyBiome(task, changes, task.currentBiomeIndex++);
            } else if (task.currentStructureStartIndex < changes.structureStartSize()) {
                applyStructureStart(task, changes, task.currentStructureStartIndex++);
            } else {
                applyStructureReference(task, changes, task.currentStructureReferenceIndex++);
            }
            processed++;

            if (task.currentBlockIndex >= changes.blockSize()
                    && task.currentBiomeIndex >= changes.biomeSize()
                    && task.currentStructureStartIndex >= changes.structureStartSize()
                    && task.currentStructureReferenceIndex >= changes.structureReferenceSize()) {
                task.appliedChunks++;
                if (task.currentChunkStructureStartsChanged) {
                    ((CraftWorld) task.target).getHandle().onStructureStartsAvailable(
                            targetChunkHandle(task, changes.chunk()));
                }
                releaseTicket(task, changes.chunk());
                boolean refreshed = task.currentChunkBiomeChanged;
                if (refreshed) {
                    task.target.refreshChunk(changes.chunk().x(), changes.chunk().z());
                }
                task.currentChanges = null;
                task.currentBlockIndex = 0;
                task.currentBiomeIndex = 0;
                task.currentStructureStartIndex = 0;
                task.currentStructureReferenceIndex = 0;
                task.currentChunkBiomeChanged = false;
                task.currentChunkStructureStartsChanged = false;
                if (refreshed) {
                    break;
                }
            }
        }
        return processed;
    }

    private void applyBlock(Task task, RegenChunkChanges changes, int index) {
        Block block = task.target.getBlockAt(
                changes.worldX(index), changes.worldY(index), changes.worldZ(index));
        if (!task.plan.blockFilter().includes(block.getType())) {
            return;
        }

        BlockData generatedData = changes.blockData(index);
        BlockState generatedState = changes.blockEntity(index);
        boolean changed = false;
        if (!block.getBlockData().equals(generatedData)) {
            block.setBlockData(generatedData, false);
            changed = true;
        }
        if (generatedState != null) {
            try {
                BlockState restored = generatedState.copy(block.getLocation());
                if (restored.update(true, false)) {
                    changed = true;
                } else {
                    plugin.getLogger().warning("Could not restore generated block entity at "
                            + block.getX() + "," + block.getY() + "," + block.getZ());
                }
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not restore generated block entity at "
                                + block.getX() + "," + block.getY() + "," + block.getZ(), error);
            }
        }
        if (changed) {
            task.appliedChanges++;
        }
    }

    private void applyBiome(Task task, RegenChunkChanges changes, int index) {
        int x = changes.biomeWorldX(index);
        int y = changes.biomeWorldY(index);
        int z = changes.biomeWorldZ(index);
        Biome generatedBiome = changes.biome(index);
        if (task.target.getBiome(x, y, z).equals(generatedBiome)) {
            return;
        }
        task.target.setBiome(x, y, z, generatedBiome);
        task.appliedBiomeChanges++;
        task.currentChunkBiomeChanged = true;
    }

    private void applyStructureStart(Task task, RegenChunkChanges changes, int index) {
        RegenStructureStartData data = changes.structureStart(index);
        net.minecraft.server.level.ServerLevel target = ((CraftWorld) task.target).getHandle();
        StructureStart start = data.load(target);
        Identifier expectedId = Identifier.parse(data.type());
        Structure expected = target.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(expectedId);
        if (expected == null || start.getStructure() != expected) {
            throw new IllegalStateException("Generated structure type changed while applying " + data.identity());
        }
        targetChunkHandle(task, changes.chunk()).setStartForStructure(expected, start);
        task.appliedStructureStarts++;
        task.currentChunkStructureStartsChanged = true;
    }

    private void applyStructureReference(Task task, RegenChunkChanges changes, int index) {
        RegenStructureReference reference = changes.structureReference(index);
        net.minecraft.server.level.ServerLevel target = ((CraftWorld) task.target).getHandle();
        Structure structure = target.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(Identifier.parse(reference.type()));
        if (structure == null) {
            throw new IllegalStateException("Unknown generated structure type " + reference.type());
        }
        ChunkAccess handle = targetChunkHandle(task, changes.chunk());
        if (handle.getReferencesForStructure(structure).contains(reference.packedStartChunk())) {
            return;
        }
        handle.addReferenceForStructure(structure, reference.packedStartChunk());
        task.appliedStructureReferences++;
    }

    private ChunkAccess targetChunkHandle(Task task, RegenChunkPos position) {
        ChunkTicketKey key = new ChunkTicketKey(task.target.getUID(), position.x(), position.z());
        Chunk chunk = task.heldChunks.get(key);
        if (!(chunk instanceof CraftChunk craftChunk)) {
            throw new IllegalStateException("Target chunk ticket was released before metadata application");
        }
        return craftChunk.getHandle(ChunkStatus.FULL);
    }

    private void tryComplete(Task task) {
        if (task.state.terminal()
                || !task.planningComplete
                || task.inFlight != null
                || task.hasPendingChanges()) {
            return;
        }
        task.state = RegenTaskState.COMPLETED;
        releaseAllTickets(task);
        tasks.remove(task.owner, task);
        listener.completed(task.snapshot());
    }

    private void cancel(Task task, boolean notify) {
        task.state = RegenTaskState.CANCELLED;
        if (task.inFlight != null) {
            task.inFlight.cancel(true);
            task.inFlight = null;
        }
        releasePlanningSlot(task);
        task.pendingChanges.clear();
        task.currentChanges = null;
        releaseAllTickets(task);
        tasks.remove(task.owner, task);
        if (notify) {
            listener.cancelled(task.snapshot());
        }
    }

    private void fail(Task task, Throwable error) {
        if (task.state.terminal()) {
            return;
        }
        task.state = RegenTaskState.FAILED;
        if (task.inFlight != null) {
            task.inFlight.cancel(true);
            task.inFlight = null;
        }
        releasePlanningSlot(task);
        task.pendingChanges.clear();
        task.currentChanges = null;
        releaseAllTickets(task);
        tasks.remove(task.owner, task);
        plugin.getLogger().log(Level.SEVERE,
                "Asynchronous regeneration failed for " + task.owner, error);
        listener.failed(task.snapshot(), error);
    }

    private void retainTicket(Task task, Chunk chunk) {
        ChunkTicketKey key = new ChunkTicketKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!task.heldTickets.add(key)) {
            return;
        }
        task.heldChunks.put(key, chunk);
        int references = ticketReferences.getOrDefault(key, 0);
        if (references == 0) {
            chunk.getWorld().addPluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
        }
        ticketReferences.put(key, references + 1);
    }

    private void releaseTicket(Task task, RegenChunkPos chunk) {
        releaseTicket(task, new ChunkTicketKey(task.target.getUID(), chunk.x(), chunk.z()));
    }

    private void releaseTicket(Task task, ChunkTicketKey key) {
        if (!task.heldTickets.remove(key)) {
            return;
        }
        task.heldChunks.remove(key);
        Integer references = ticketReferences.get(key);
        if (references == null || references <= 1) {
            ticketReferences.remove(key);
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                world.removePluginChunkTicket(key.x(), key.z(), plugin);
            }
            return;
        }
        ticketReferences.put(key, references - 1);
    }

    private void releaseAllTickets(Task task) {
        for (ChunkTicketKey key : Set.copyOf(task.heldTickets)) {
            releaseTicket(task, key);
        }
    }

    private void releaseOrphanedTickets() {
        for (ChunkTicketKey key : Set.copyOf(ticketReferences.keySet())) {
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                world.removePluginChunkTicket(key.x(), key.z(), plugin);
            }
        }
        ticketReferences.clear();
    }

    private void acquirePlanningSlot(Task task) {
        if (task.planningSlotHeld) {
            throw new IllegalStateException("Task already owns a chunk planning slot");
        }
        task.planningSlotHeld = true;
        inFlightPlans++;
    }

    private void releasePlanningSlot(Task task) {
        if (!task.planningSlotHeld) {
            return;
        }
        task.planningSlotHeld = false;
        inFlightPlans = Math.max(0, inFlightPlans - 1);
    }

    private void runOnServerThread(Runnable action) {
        if (closed) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Regeneration state may only be accessed from the server thread");
        }
    }

    enum SubmitResult {
        ACCEPTED,
        ALREADY_RUNNING
    }

    interface Listener {

        void progress(RegenTaskSnapshot snapshot);

        void completed(RegenTaskSnapshot snapshot);

        void cancelled(RegenTaskSnapshot snapshot);

        void failed(RegenTaskSnapshot snapshot, Throwable error);
    }

    private static final class Task {

        private final UUID owner;
        private final RegenSelection selection;
        private final RegenPlan plan;
        private final World target;
        private final World shadow;
        private final long startedAtNanos = System.nanoTime();
        private final ArrayDeque<RegenChunkChanges> pendingChanges = new ArrayDeque<>();
        private final Set<ChunkTicketKey> heldTickets = new HashSet<>();
        private final Map<ChunkTicketKey, Chunk> heldChunks = new HashMap<>();
        private final RegenPreviewPointSampler previewChangedBlocks =
                new RegenPreviewPointSampler(MAX_PREVIEW_CHANGE_SAMPLES);
        private final RegenPreviewPointSampler previewRiskBlockEntities =
                new RegenPreviewPointSampler(MAX_PREVIEW_RISK_SAMPLES);

        private RegenTaskState state = RegenTaskState.PREPARING;
        private CompletableFuture<RegenChunkChanges> inFlight;
        private RegenChunkChanges currentChanges;
        private int currentBlockIndex;
        private int currentBiomeIndex;
        private int currentStructureStartIndex;
        private int currentStructureReferenceIndex;
        private int nextChunk;
        private int plannedChunks;
        private int appliedChunks;
        private long discoveredChanges;
        private long appliedChanges;
        private long discoveredBiomeChanges;
        private long appliedBiomeChanges;
        private long discoveredStructureStarts;
        private long appliedStructureStarts;
        private long discoveredStructureReferences;
        private long appliedStructureReferences;
        private long skippedBoundaryBiomeCells;
        private int skippedUngeneratedChunks;
        private long atRiskBlockEntities;
        private final Map<String, RegenStructureDescriptor> structures = new HashMap<>();
        private final Map<String, RegenStructureDescriptor> targetStructures = new HashMap<>();
        private final Set<RegenChunkPos> ungeneratedChunks = new HashSet<>();
        private boolean planningComplete;
        private boolean planningSlotHeld;
        private boolean currentChunkBiomeChanged;
        private boolean currentChunkStructureStartsChanged;

        private Task(UUID owner, RegenSelection selection, RegenPlan plan, World target, World shadow) {
            this.owner = owner;
            this.selection = selection;
            this.plan = plan;
            this.target = target;
            this.shadow = shadow;
        }

        private int bufferedChunks() {
            return pendingChanges.size() + (currentChanges == null ? 0 : 1);
        }

        private boolean hasPendingChanges() {
            return currentChanges != null || !pendingChanges.isEmpty();
        }

        private RegenTaskSnapshot snapshot() {
            Set<String> metadataEligibleStructures = metadataEligibleStructures();
            return new RegenTaskSnapshot(
                    owner,
                    plan.kind(),
                    plan.upgradeScope(),
                    plan.blockFilter().ids(),
                    state,
                    plannedChunks,
                    appliedChunks,
                    selection.chunks().size(),
                    discoveredChanges,
                    appliedChanges,
                    discoveredBiomeChanges,
                    appliedBiomeChanges,
                    discoveredStructureStarts,
                    appliedStructureStarts,
                    discoveredStructureReferences,
                    appliedStructureReferences,
                    skippedBoundaryBiomeCells,
                    skippedUngeneratedChunks,
                    atRiskBlockEntities,
                    structures.size(),
                    (int) structures.values().stream()
                            .filter(structure -> !structure.fullyInside(selection))
                            .count(),
                    metadataEligibleStructures,
                    new RegenPreviewSamples(
                            previewChangedBlocks.snapshot(), previewRiskBlockEntities.snapshot()),
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos))
            );
        }

        private Set<String> metadataEligibleStructures() {
            if (!plan.structures()) {
                return Set.of();
            }
            if (!plan.preview()) {
                return plan.structureMetadataIdentities();
            }
            Set<String> eligible = new HashSet<>();
            for (RegenStructureDescriptor source : structures.values()) {
                if (!source.metadataEligible(selection, ungeneratedChunks)) {
                    continue;
                }
                RegenStructureDescriptor existing = targetStructures.get(source.identity());
                if (existing != null && existing.boundsKnown() && !existing.fullyInside(selection)) {
                    continue;
                }
                eligible.add(source.identity());
            }
            return Set.copyOf(eligible);
        }
    }

    private record ChunkTicketKey(UUID worldId, int x, int z) {
    }

    private static final class RegenThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private RegenThreadFactory(String pluginName) {
            this.namePrefix = pluginName + "-regen-compare-";
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, namePrefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
