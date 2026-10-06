package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.encinet.mik.module.i18n.Message;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Publishes immutable chunk indexes so bulk editors can check permissions off-thread. */
public final class PlotRegistry {
    private static final int CELLS_PER_CHUNK = 16 / PlotGeometry.CELL;

    enum Standing {
        STAFF, PARENT_OWNER, OWNER, ADMIN, COLLABORATOR, OUTSIDER;
    }

    private final PlotRepository store;
    private final Map<UUID, Plot> plots = new HashMap<>();
    private final Map<UUID, PlotArrival> arrivals = new HashMap<>();
    private final Map<UUID, String> noticeBoards = new HashMap<>();
    private final Map<UUID, PlotAtmosphere> atmospheres = new HashMap<>();
    private volatile SpatialSnapshot spatial = new SpatialSnapshot(Map.of(), Map.of(), Map.of());
    private volatile PlotCatalog catalog;
    private long revision;
    private long atmosphereRevision;
    private Executor completion;
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    private boolean metadataOnly;
    private volatile boolean writesOpen = true;

    PlotRegistry(PlotRepository store) { this.store = store; }

    void enableAsyncWrites(Executor completion) { this.completion = java.util.Objects.requireNonNull(completion); }

    boolean writesOpen() { return writesOpen; }

    boolean saving() { return !pending.isEmpty(); }

    void stopWrites() {
        writesOpen = false;
        for (CompletableFuture<?> future : pending) future.cancel(false);
        pending.clear();
    }

    <Result> CompletableFuture<Result> writeAsync(Mutation<Result> operation) throws SQLException {
        if (!writesOpen) throw new PlotProblem(Message.PLOT_ERROR_BUSY);
        if (completion == null) return CompletableFuture.completedFuture(operation.apply(this));
        CompletableFuture<Result> result = new CompletableFuture<>();
        pending.add(result);
        try {
            prepare(operation, result);
        } catch (RuntimeException rejected) {
            pending.remove(result);
            throw rejected;
        }
        return result;
    }

    <Result> CompletableFuture<Result> writeBehind(Mutation<Result> operation) throws SQLException {
        if (!writesOpen) throw new PlotProblem(Message.PLOT_ERROR_BUSY);
        if (completion == null) return CompletableFuture.completedFuture(operation.apply(this));
        PlotRegistry staged = snapshot();
        staged.metadataOnly = true;
        Result value = operation.apply(staged);
        accept(staged);
        return CompletableFuture.completedFuture(value);
    }

    private <Result> void prepare(Mutation<Result> operation, CompletableFuture<Result> result) {
        long expectedRevision = revision;
        PlotRegistry staged = snapshot();
        store.prepareAsync(() -> {
                if (!writesOpen || result.isDone())
                    throw new java.util.concurrent.CancellationException("Plot preparation cancelled");
                return operation.apply(staged);
            })
            .whenComplete((value, error) -> {
                if (!writesOpen) return;
                try {
                    completion.execute(() -> {
                        if (!writesOpen || !pending.contains(result)) return;
                        if (result.isDone()) {
                            pending.remove(result);
                            return;
                        }
                        if (revision != expectedRevision) {
                            try { prepare(operation, result); }
                            catch (RuntimeException rejected) {
                                pending.remove(result);
                                result.completeExceptionally(rejected);
                            }
                            return;
                        }
                        pending.remove(result);
                        if (error != null) result.completeExceptionally(error);
                        else {
                            try {
                                accept(staged);
                                result.complete(value);
                            } catch (RuntimeException rejected) {
                                result.completeExceptionally(rejected);
                            }
                        }
                    });
                } catch (RuntimeException rejected) {
                    if (!writesOpen) return;
                    writesOpen = false;
                    System.getLogger(PlotRegistry.class.getName()).log(System.Logger.Level.ERROR,
                            "Could not deliver plot preparation completion; plot writes stopped", rejected);
                    result.completeExceptionally(rejected);
                }
            });
    }

    private void accept(PlotRegistry staged) {
        store.persistAsync(staged.store).whenComplete((ignored, error) -> {
            if (error != null)
                System.getLogger(PlotRegistry.class.getName()).log(System.Logger.Level.ERROR,
                        "Could not persist accepted plot changes", error);
        });
        plots.clear(); plots.putAll(staged.plots);
        arrivals.clear(); arrivals.putAll(staged.arrivals);
        noticeBoards.clear(); noticeBoards.putAll(staged.noticeBoards);
        atmospheres.clear(); atmospheres.putAll(staged.atmospheres);
        spatial = staged.spatial;
        catalog = staged.catalog;
        revision = staged.revision;
        atmosphereRevision = staged.atmosphereRevision;
    }

    void requireGeometry() {
        if (metadataOnly) throw new IllegalStateException("Plot geometry must be prepared asynchronously");
    }

    private void synchronousMutation() throws SQLException {
        if (completion != null || !writesOpen)
            throw new SQLException("Runtime plot mutations must use writeAsync");
    }

    interface Mutation<Result> { Result apply(PlotRegistry registry) throws SQLException; }

    PlotRegistry snapshot() {
        PlotRegistry copy = new PlotRegistry(store.plan());
        copy.plots.putAll(plots);
        copy.arrivals.putAll(arrivals);
        copy.noticeBoards.putAll(noticeBoards);
        copy.atmospheres.putAll(atmospheres);
        copy.spatial = spatial;
        copy.catalog = catalog;
        copy.revision = revision;
        copy.atmosphereRevision = atmosphereRevision;
        return copy;
    }

    void load() throws SQLException {
        synchronousMutation();
        Map<UUID, Plot> loadedPlots = new HashMap<>();
        for (Plot plot : store.load()) loadedPlots.put(plot.id(), plot);
        Map<UUID, PlotArrival> loadedArrivals = store.loadArrivals();
        Map<UUID, String> loadedBoards = store.loadNoticeBoards();
        Map<UUID, PlotAtmosphere> loadedAtmospheres = store.loadAtmospheres();
        plots.clear();
        plots.putAll(loadedPlots);
        arrivals.clear();
        arrivals.putAll(loadedArrivals);
        noticeBoards.clear();
        noticeBoards.putAll(loadedBoards);
        atmospheres.clear();
        atmospheres.putAll(loadedAtmospheres);
        reindex();
        revision++;
    }

    public List<Plot> all() { return catalog().all(); }
    long revision() { return revision; }
    long atmosphereRevision() { return atmosphereRevision; }
    Plot byId(UUID id) { return plots.get(id); }
    int horizontalArea(Plot plot) { return spatial.areas().getOrDefault(plot.id(), 0); }
    Plot parentOf(Plot plot) { return plot.parentId() == null ? null : plots.get(plot.parentId()); }
    List<Plot> childrenOf(UUID id) {
        return catalog().childrenOf(id);
    }

    private PlotCatalog catalog() {
        Map<UUID, Plot> current = spatial.plots();
        PlotCatalog cached = catalog;
        if (cached == null || !cached.matches(current)) {
            cached = new PlotCatalog(current);
            catalog = cached;
        }
        return cached;
    }

    boolean parentOwner(Plot plot, UUID actor) {
        Plot parent = parentOf(plot);
        return parent != null && parent.owner().equals(actor);
    }

    boolean recoveryAuthority(Plot plot, UUID actor, boolean staff) {
        return staff || plot.owner().equals(actor) || parentOwner(plot, actor);
    }

    boolean canEditSubject(Plot plot, UUID actor, boolean staff, PlotAccessPolicy.Subject subject) {
        return permitted(plot, actor, staff, PlotPermission.MANAGE_PERMISSIONS)
                && (subject.group() != PlotAccessPolicy.Group.OWNER || recoveryAuthority(plot, actor, staff));
    }

    Standing standing(Plot plot, UUID actor, boolean staff) {
        return standing(plot, parentOf(plot), actor, staff);
    }

    private static Standing standing(Plot plot, Plot parent, UUID actor, boolean staff) {
        if (plot.owner().equals(actor)) return Standing.OWNER;
        if (parent != null && parent.owner().equals(actor)) return Standing.PARENT_OWNER;
        if (staff) return Standing.STAFF;
        return switch (plot.members().get(actor)) {
            case ADMIN -> Standing.ADMIN;
            case COLLABORATOR -> Standing.COLLABORATOR;
            case null -> Standing.OUTSIDER;
        };
    }

    boolean canManage(Plot plot, UUID actor, boolean staff) {
        return plot.canManage(parentOf(plot), actor, staff);
    }

    boolean permitted(Plot plot, UUID actor, boolean staff, PlotPermission permission) {
        return plot.allows(parentOf(plot), permission.key(), actor, false, staff);
    }

    boolean canRelease(Plot plot, UUID actor, boolean staff) {
        return permitted(plot, actor, staff, PlotPermission.DELETE);
    }
    PlotArrival arrival(UUID id) { return arrivals.get(id); }
    String noticeBoard(UUID id) { return noticeBoards.getOrDefault(id, ""); }
    PlotAtmosphere atmosphere(UUID id) { return atmospheres.getOrDefault(id, PlotAtmosphere.WORLD); }

    PlotAtmosphere effectiveAtmosphere(UUID id) {
        PlotAtmosphere own = atmosphere(id);
        Plot plot = plots.get(id);
        Plot parent = plot == null ? null : parentOf(plot);
        if (parent == null || own.timeTicks() != null && own.weather() != null) return own;
        PlotAtmosphere inherited = atmosphere(parent.id());
        if (own.followsWorld()) return inherited;
        if (inherited.followsWorld()) return own;
        return new PlotAtmosphere(own.timeTicks() == null ? inherited.timeTicks() : own.timeTicks(),
                own.weather() == null ? inherited.weather() : own.weather());
    }

    void setAtmosphere(UUID id, PlotAtmosphere atmosphere) throws SQLException {
        synchronousMutation();
        if (!plots.containsKey(id)) throw new SQLException("Plot no longer exists: " + id);
        if (atmosphere(id).equals(atmosphere)) return;
        store.saveAtmosphere(id, atmosphere);
        if (atmosphere.followsWorld()) atmospheres.remove(id);
        else atmospheres.put(id, atmosphere);
        revision++;
        atmosphereRevision++;
    }

    void setNoticeBoard(UUID id, String body) throws SQLException {
        synchronousMutation();
        if (!plots.containsKey(id)) throw new SQLException("Plot no longer exists: " + id);
        store.saveNoticeBoard(id, body);
        if (body.isEmpty()) noticeBoards.remove(id);
        else noticeBoards.put(id, body);
        revision++;
    }

    void setArrival(UUID id, PlotArrival arrival) throws SQLException {
        synchronousMutation();
        if (!plots.containsKey(id)) throw new SQLException("Plot no longer exists: " + id);
        store.saveArrival(id, arrival);
        arrivals.put(id, arrival);
        revision++;
    }

    void clearArrival(UUID id) throws SQLException {
        synchronousMutation();
        store.deleteArrival(id);
        arrivals.remove(id);
        revision++;
    }

    public Plot at(UUID world, int x, int y, int z) {
        SpatialSnapshot snapshot = spatial;
        Map<Long, ChunkCoverage> worldIndex = snapshot.index().get(world);
        if (worldIndex == null) return null;
        ChunkCoverage coverage = worldIndex.get(chunk(x >> 4, z >> 4));
        if (coverage == null) return null;
        Cell cell = Cell.at(x, y, z);
        for (UUID id : coverage.plots()) {
            Plot plot = snapshot.plots().get(id);
            if (plot.cells().contains(cell)) return plot;
        }
        return null;
    }

    List<Plot> atAll(UUID world, int x, int y, int z) {
        return atAll(spatial, world, x, y, z);
    }

    private static List<Plot> atAll(SpatialSnapshot snapshot, UUID world, int x, int y, int z) {
        Map<Long, ChunkCoverage> worldIndex = snapshot.index().get(world);
        if (worldIndex == null) return List.of();
        ChunkCoverage coverage = worldIndex.get(chunk(x >> 4, z >> 4));
        if (coverage == null) return List.of();
        List<Plot> result = new ArrayList<>();
        Cell cell = Cell.at(x, y, z);
        for (UUID id : coverage.plots()) {
            Plot plot = snapshot.plots().get(id);
            if (plot.cells().contains(cell)) result.add(plot);
        }
        return result;
    }

    public boolean allowed(UUID world, int x, int y, int z, UUID actor,
                           boolean member, boolean staff, String flag) {
        SpatialSnapshot snapshot = spatial;
        Map<Long, ChunkCoverage> worldIndex = snapshot.index().get(world);
        if (worldIndex == null) return true;
        ChunkCoverage coverage = worldIndex.get(chunk(x >> 4, z >> 4));
        if (coverage == null) return true;
        Map<UUID, Plot> snapshotPlots = snapshot.plots();
        Cell cell = Cell.at(x, y, z);
        for (UUID id : coverage.plots()) {
            Plot plot = snapshotPlots.get(id);
            if (!plot.cells().contains(cell) || coveredByChild(coverage, snapshotPlots, id, cell)) continue;
            Plot parent = plot.parentId() == null ? null : snapshotPlots.get(plot.parentId());
            if (!plot.allows(parent, flag, actor, member, staff)) return false;
        }
        return true;
    }

    private static boolean coveredByChild(ChunkCoverage coverage, Map<UUID, Plot> plots,
                                          UUID parent, Cell cell) {
        List<UUID> children = coverage.children().get(parent);
        if (children == null) return false;
        for (UUID id : children) if (plots.get(id).cells().contains(cell)) return true;
        return false;
    }

    void put(Plot plot) throws SQLException {
        synchronousMutation();
        Plot previous = plots.get(plot.id());
        boolean unchangedGeometry = previous != null && previous.cells() == plot.cells()
                && previous.world().equals(plot.world())
                && java.util.Objects.equals(previous.parentId(), plot.parentId());
        if (!unchangedGeometry) {
            requireGeometry();
            validateHierarchy(plot);
        }
        store.save(plot, previous);
        publish(plot);
    }

    private void validateHierarchy(Plot plot) {
        if (plot.subPlot()) {
            Plot parent = parentOf(plot);
            if (parent == null || parent.subPlot() || !parent.world().equals(plot.world())
                    || plot.cells().isEmpty() || !parent.cells().containsAll(plot.cells()))
                throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
            if (childrenOf(parent.id()).stream().anyMatch(other -> !other.id().equals(plot.id())
                    && other.cells().stream().anyMatch(plot.cells()::contains)))
                throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OVERLAP);
            if (overlapsOther(plot)) throw new PlotProblem(Message.PLOT_ERROR_OVERLAP);
        } else if (childrenOf(plot.id()).stream()
                .anyMatch(child -> !plot.cells().containsAll(child.cells()))) {
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        }
    }

    Set<Cell> subPlotCells(Plot parent, PlotPosition first, PlotPosition second) {
        return subPlotCells(parent, first, second, null);
    }

    private Set<Cell> subPlotCells(Plot parent, PlotPosition first, PlotPosition second,
                                   UUID currentChild) {
        if (parent.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
        if (!parent.world().equals(first.world()) || !parent.world().equals(second.world()))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        Cell a = Cell.at(first.x(), first.y(), first.z());
        Cell b = Cell.at(second.x(), second.y(), second.z());
        if (!parent.cells().contains(a) || !parent.cells().contains(b))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        int minX = Math.min(a.x(), b.x()), maxX = Math.max(a.x(), b.x());
        int minY = Math.min(a.y(), b.y()), maxY = Math.max(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z()), maxZ = Math.max(a.z(), b.z());
        Set<Cell> selected = new HashSet<>();
        for (Cell cell : parent.cells()) {
            if (cell.x() >= minX && cell.x() <= maxX && cell.y() >= minY && cell.y() <= maxY
                    && cell.z() >= minZ && cell.z() <= maxZ) selected.add(cell);
        }
        return validateSubPlotCells(parent, selected, currentChild);
    }

    private Set<Cell> subPlotCells(Plot parent, PlotSelectionShape shape, UUID currentChild) {
        if (!shape.composite())
            return subPlotCells(parent, shape.cuboid().first(), shape.cuboid().second(), currentChild);
        if (parent.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
        if (!parent.world().equals(shape.world())) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        if (!parent.cells().containsAll(shape.cells()))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        return validateSubPlotCells(parent, shape.cells(), currentChild);
    }

    private Set<Cell> validateSubPlotCells(Plot parent, Set<Cell> selected, UUID currentChild) {
        if (!PlotGeometry.connected(selected))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DISCONNECTED);
        if (childrenOf(parent.id()).stream()
                .filter(child -> !child.id().equals(currentChild))
                .anyMatch(child -> child.cells().stream().anyMatch(selected::contains)))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OVERLAP);
        return Set.copyOf(selected);
    }

    Plot createSubPlot(UUID parentId, UUID owner, String name,
                       PlotPosition first, PlotPosition second) throws SQLException {
        return createSubPlot(parentId, owner, name, PlotSelectionShape.of(first, second));
    }

    Plot createSubPlot(UUID parentId, UUID owner, String name, PlotSelectionShape shape) throws SQLException {
        requireGeometry();
        Plot parent = plots.get(parentId);
        if (parent == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        Set<Cell> selected = creatableSubPlotCells(parent, shape);
        Plot child = new Plot(UUID.randomUUID(), parent.world(), owner, name,
                parent.publicProject(), System.currentTimeMillis(), selected, Map.of(),
                Map.of(), parentId);
        validateHierarchy(child);
        put(child);
        return child;
    }

    Set<Cell> creatableSubPlotCells(Plot parent, PlotPosition first, PlotPosition second) {
        return creatableSubPlotCells(parent, PlotSelectionShape.of(first, second));
    }

    Set<Cell> creatableSubPlotCells(Plot parent, PlotSelectionShape shape) {
        Set<Cell> selected = subPlotCells(parent, shape, null);
        checkParentArrival(parent.id(), selected);
        return selected;
    }

    Set<Cell> resizedSubPlotCells(Plot child, PlotPosition first, PlotPosition second) {
        return resizedSubPlotCells(child, PlotSelectionShape.of(first, second));
    }

    Set<Cell> resizedSubPlotCells(Plot child, PlotSelectionShape shape) {
        Plot current = plots.get(child.id());
        if (current == null || !current.subPlot())
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        Plot parent = parentOf(current);
        if (parent == null) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        Set<Cell> selected = subPlotCells(parent, shape, current.id());
        checkParentArrival(parent.id(), selected);
        PlotArrival ownArrival = arrivals.get(current.id());
        if (ownArrival != null && !selected.contains(Cell.at(ownArrival.blockX(),
                ownArrival.blockY(), ownArrival.blockZ())))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL);
        return selected;
    }

    void resizeSubPlot(Plot child, PlotPosition first, PlotPosition second) throws SQLException {
        resizeSubPlot(child, PlotSelectionShape.of(first, second));
    }

    void resizeSubPlot(Plot child, PlotSelectionShape shape) throws SQLException {
        requireGeometry();
        Plot current = plots.get(child.id());
        if (current == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        Set<Cell> selected = resizedSubPlotCells(current, shape);
        put(new Plot(current.id(), current.world(), current.owner(), current.name(),
                current.publicProject(), current.createdAt(), selected, current.members(),
                current.flags(), current.parentId()));
    }

    private void checkParentArrival(UUID parentId, Set<Cell> selected) {
        PlotArrival arrival = arrivals.get(parentId);
        if (arrival != null && selected.contains(Cell.at(arrival.blockX(),
                arrival.blockY(), arrival.blockZ())))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_ARRIVAL);
    }

    private void publish(Plot plot) {
        Plot previous = plots.put(plot.id(), plot);
        if (previous != null && sameCoverage(previous, plot))
            spatial = new SpatialSnapshot(spatial.index(), Map.copyOf(plots), spatial.areas());
        else reindex();
        revision++;
    }

    private static boolean sameCoverage(Plot previous, Plot next) {
        return previous.world().equals(next.world())
                && previous.cells().equals(next.cells())
                && java.util.Objects.equals(previous.parentId(), next.parentId());
    }

    void rename(UUID id, String name) throws SQLException {
        synchronousMutation();
        Plot current = plots.get(id);
        if (current == null) throw new SQLException("Plot no longer exists: " + id);
        Plot renamed = new Plot(current.id(), current.world(), current.owner(), name,
                current.publicProject(), current.createdAt(), current.cells(), current.members(),
                current.flags(), current.parentId());
        store.rename(id, name);
        publish(renamed);
    }

    void remove(Plot plot) throws SQLException {
        synchronousMutation();
        requireGeometry();
        if (!childrenOf(plot.id()).isEmpty())
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_CHILDREN);
        store.delete(plot.id());
        plots.remove(plot.id());
        arrivals.remove(plot.id());
        noticeBoards.remove(plot.id());
        atmospheres.remove(plot.id());
        reindex();
        revision++;
    }

    boolean overlapsSelection(UUID world, UUID currentPlot, PlotSelectionShape shape) {
        for (Plot other : plots.values()) {
            if (!other.world().equals(world)) continue;
            if (currentPlot != null && (other.id().equals(currentPlot) || currentPlot.equals(other.parentId()))) continue;
            if (shape.intersects(other.cells())) return true;
        }
        return false;
    }

    boolean overlapsOther(Plot proposed) {
        for (Plot other : plots.values()) {
            if (other.id().equals(proposed.id()) || !other.world().equals(proposed.world())) continue;
            if (related(proposed, other)) continue;
            if (!Collections.disjoint(proposed.cells(), other.cells())) return true;
        }
        return false;
    }

    private static boolean related(Plot first, Plot second) {
        return first.id().equals(second.parentId()) || second.id().equals(first.parentId());
    }

    private void reindex() {
        Map<UUID, Map<Long, Set<UUID>>> building = new HashMap<>();
        Map<UUID, Integer> areas = new HashMap<>();
        for (Plot plot : plots.values()) {
            Plot previous = spatial.plots().get(plot.id());
            areas.put(plot.id(), previous != null && previous.cells() == plot.cells()
                    ? spatial.areas().get(plot.id()) : PlotGeometry.horizontalArea(plot.cells()));
            Map<Long, Set<UUID>> chunks = building.computeIfAbsent(plot.world(), ignored -> new HashMap<>());
            for (Cell cell : plot.cells()) {
                chunks.computeIfAbsent(chunk(Math.floorDiv(cell.x(), CELLS_PER_CHUNK),
                                Math.floorDiv(cell.z(), CELLS_PER_CHUNK)),
                        ignored -> new HashSet<>()).add(plot.id());
            }
        }
        Map<UUID, Map<Long, ChunkCoverage>> immutable = new HashMap<>();
        building.forEach((world, chunks) -> {
            Map<Long, ChunkCoverage> copy = new HashMap<>();
            chunks.forEach((chunk, ids) -> copy.put(chunk, coverage(ids)));
            immutable.put(world, Map.copyOf(copy));
        });
        spatial = new SpatialSnapshot(Map.copyOf(immutable), Map.copyOf(plots), Map.copyOf(areas));
        atmosphereRevision++;
    }

    private ChunkCoverage coverage(Set<UUID> ids) {
        List<UUID> ordered = ids.stream().sorted(Comparator.comparing((UUID id) -> !plots.get(id).subPlot())
                .thenComparing(Comparator.naturalOrder())).toList();
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (UUID id : ordered) {
            UUID parent = plots.get(id).parentId();
            if (parent != null) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
        }
        children.replaceAll((parent, members) -> List.copyOf(members));
        return new ChunkCoverage(ordered, Map.copyOf(children));
    }

    private static long chunk(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private record ChunkCoverage(List<UUID> plots, Map<UUID, List<UUID>> children) { }

    private record SpatialSnapshot(Map<UUID, Map<Long, ChunkCoverage>> index,
                                   Map<UUID, Plot> plots, Map<UUID, Integer> areas) { }

}
