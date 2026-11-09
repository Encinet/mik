package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.WorldMenuSurface;
import org.encinet.mik.shared.menu.ui.UiRotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owns bounded mount discovery, including the empty space and failed candidates it inspected. */
final class IronElevatorPanelPlacement {
    private static final List<BlockFace> DIRECTIONS = List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST);
    private static final int MAX_CACHED = 64;
    private static final double MARGIN = .12;
    private final LongSupplier clock;
    private final Map<IronElevatorPlatform,Cached> cache = new LinkedHashMap<>(16,.75F,true);
    private final Map<IronElevatorPlatform,Job> jobs = new LinkedHashMap<>();

    record Plan(WorldMenuSurface surface, Predicate<WorldMenuSurface> backing) {
        boolean valid() { return backing.test(surface); }
    }
    private record Cached(long measuredAt, Plan plan) { }
    private record Area(int first, int last, int height) { int width() { return last - first + 1; } }

    static final class Request {
        private final Consumer<Plan> ready;
        private boolean cancelled;
        private boolean complete;
        private Plan result;
        Request(Consumer<Plan> ready) { this.ready = ready; }
        void cancel() { cancelled = true; }
        boolean active() { return !cancelled && !complete; }
        boolean completed() { return complete; }
        Plan result() { return result; }
    }

    IronElevatorPanelPlacement() { this(System::nanoTime); }
    IronElevatorPanelPlacement(LongSupplier clock) { this.clock = clock; }

    Request prepare(IronElevatorPlatform source, IronElevatorWorkQueue queue, Consumer<Plan> ready) {
        var request = new Request(ready);
        var cached = cache.get(source);
        if (cached != null && clock.getAsLong() - cached.measuredAt() < IronElevatorCatalog.CACHE_NANOS) {
            request.result = cached.plan();
            request.complete = true;
            ready.accept(cached.plan());
            return request;
        }
        Job job = jobs.get(source);
        if (job == null) {
            job = new Job(source);
            Job scheduled = job;
            job.task = queue.submit(IronElevatorWorkQueue.HINT,() -> {
                try { return scheduled.step(); }
                catch (RuntimeException error) { cancel(scheduled); throw error; }
            });
            if (job.task == null) { request.cancel(); return request; }
            jobs.put(source,job);
        }
        job.requests.add(request);
        return request;
    }

    private final class Job {
        final IronElevatorPlatform source;
        final List<Request> requests = new ArrayList<>();
        List<Candidate> candidates;
        IronElevatorWorkQueue.Task task;
        Survey survey;
        int index;
        Job(IronElevatorPlatform source) { this.source = source; }
        boolean step() {
            requests.removeIf(request -> request.cancelled);
            if (requests.isEmpty()) { jobs.remove(source,this); return true; }
            if (candidates == null) { candidates = candidates(source); return false; }
            if (survey != null) {
                if (!survey.step()) return false;
                Area area = survey.area();
                if (area != null) { finish(survey.candidate.plan(area)); return true; }
                survey = null;
            }
            if (index == candidates.size()) { finish(null); return true; }
            survey = new Survey(candidates.get(index++));
            return false;
        }
        void finish(Plan plan) {
            jobs.remove(source,this);
            cache.put(source,new Cached(clock.getAsLong(),plan));
            while (cache.size() > MAX_CACHED) cache.remove(cache.keySet().iterator().next());
            RuntimeException failure = null;
            for (var request : List.copyOf(requests)) {
                if (request.cancelled) continue;
                request.result = plan;
                request.complete = true;
                try { request.ready.accept(plan); }
                catch (RuntimeException error) {
                    if (failure == null) failure = error; else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private static List<Candidate> candidates(IronElevatorPlatform source) {
        var result = new ArrayList<Candidate>();
        for (int distance = 1; distance <= 4; distance++) {
            var walls = new ArrayList<Candidate>();
            for (Block block : source.blocks()) for (var direction : DIRECTIONS) {
                var candidate = new Candidate(source,block,direction,distance,true);
                if (candidate.edge(0)) walls.add(candidate);
            }
            walls.sort(Comparator.comparingDouble(Candidate::distanceFromCenter));
            result.addAll(walls);
        }
        var floating = new ArrayList<Candidate>();
        for (Block block : source.blocks()) for (var direction : DIRECTIONS) {
            var candidate = new Candidate(source,block,direction,0,false);
            if (candidate.edge(0)) floating.add(candidate);
        }
        floating.sort(Comparator.comparingDouble(Candidate::distanceFromCenter));
        result.addAll(floating);
        return List.copyOf(result);
    }

    /** One bounded column cell per step, shared by wall and exposed-edge candidates. */
    private static final class Survey {
        final Candidate candidate;
        final boolean[][] cells = new boolean[7][3];
        int cell;
        Survey(Candidate candidate) { this.candidate = candidate; }
        boolean step() {
            int column = cell / 3 - 3, above = cell % 3 + 1;
            cells[column + 3][above - 1] = candidate.cell(column,above);
            return ++cell == 21;
        }
        Area area() {
            Area best = null;
            for (int height = 2; height <= 3; height++) {
                if (!fits(3,height)) break;
                int first = 0, last = 0;
                while (first > -3 && fits(first + 2,height)) first--;
                while (last < 3 && fits(last + 4,height)) last++;
                var next = new Area(first,last,height);
                if (best == null || next.width() >= best.width()) best = next;
            }
            return best;
        }
        boolean fits(int column, int height) {
            for (int above = 0; above < height; above++) if (!cells[column][above]) return false;
            return true;
        }
    }

    private record Candidate(IronElevatorPlatform source, Block anchor, BlockFace direction, int offset, boolean wall) {
        Vector right() { return new Vector(-direction.getModZ(),0,direction.getModX()); }
        float yaw() { return switch (direction) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; case WEST -> -90; default -> throw new IllegalStateException(); }; }
        Location position(double sideways, double above) {
            double distance = wall ? offset - .5 : .5;
            return anchor.getLocation().add(.5 + direction.getModX() * distance,2 + above,
                    .5 + direction.getModZ() * distance).add(right().multiply(sideways));
        }
        double distanceFromCenter() {
            var point = position(0,0);
            return Math.pow(point.getX() - source.centerX(),2) + Math.pow(point.getZ() - source.centerZ(),2);
        }
        boolean edge(int column) {
            int x = anchor.getX() - direction.getModZ() * column, z = anchor.getZ() + direction.getModX() * column;
            return source.contains(x,z) && !source.contains(x + direction.getModX(),z + direction.getModZ());
        }
        boolean supported(int column) {
            int x = anchor.getX() - direction.getModZ() * column, z = anchor.getZ() + direction.getModX() * column;
            int outsideX = x + direction.getModX(), outsideZ = z + direction.getModZ();
            var world = anchor.getWorld();
            return edge(column) && world.isChunkLoaded(x >> 4,z >> 4) && world.isChunkLoaded(outsideX >> 4,outsideZ >> 4)
                    && world.getBlockAt(x,source.height(),z).getType() == Material.IRON_BLOCK
                    && world.getBlockAt(outsideX,source.height(),outsideZ).getType() != Material.IRON_BLOCK;
        }
        boolean cell(int column, int above) {
            if (!supported(column)) return false;
            if (!wall) return free(column - .49,column + .49,0,above <= 2 ? .99 : 1.6);
            int x = anchor.getX() + direction.getModX() * offset - direction.getModZ() * column;
            int z = anchor.getZ() + direction.getModZ() * offset + direction.getModX() * column;
            int y = source.height() + above;
            var world = anchor.getWorld();
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4,z >> 4)) return false;
            if (!world.getBlockAt(x,y,z).getBlockData().isFaceSturdy(direction.getOppositeFace(),BlockSupport.FULL)) return false;
            for (int distance = 1; distance <= offset; distance++) {
                int frontX = x - direction.getModX() * distance, frontZ = z - direction.getModZ() * distance;
                if (!world.isChunkLoaded(frontX >> 4,frontZ >> 4) || world.getBlockAt(frontX,y,frontZ).getCollisionShape()
                        .overlaps(new BoundingBox(.05,.01,.05,.95,.99,.95))) return false;
            }
            return true;
        }
        boolean free(double first, double last, double bottom, double top) {
            var area = BoundingBox.of(position(first,bottom),position(last,top));
            area.expand(Math.abs(direction.getModX()) * .02,0,Math.abs(direction.getModZ()) * .02);
            var world = anchor.getWorld();
            if (area.getMinY() < world.getMinHeight() || area.getMaxY() > world.getMaxHeight()) return false;
            for (int x = (int)Math.floor(area.getMinX()); x < area.getMaxX(); x++)
                for (int z = (int)Math.floor(area.getMinZ()); z < area.getMaxZ(); z++) {
                    if (!world.isChunkLoaded(x >> 4,z >> 4)) return false;
                    for (int y = (int)Math.floor(area.getMinY()); y < area.getMaxY(); y++)
                        if (world.getBlockAt(x,y,z).getCollisionShape().overlaps(area.clone().shift(-x,-y,-z))) return false;
                }
            return true;
        }
        Plan plan(Area area) {
            double left = area.first() - .5 + MARGIN, right = area.last() + .5 - MARGIN;
            double bottom = MARGIN, top = Math.min(area.height() - 1,1.6) - MARGIN;
            var surface = new WorldMenuSurface(position((left + right) / 2,(bottom + top) / 2),UiRotation.degrees(yaw(),0,0),
                    new FloatingMenuSize(right - left,top - bottom),.25,5);
            return new Plan(surface,this::supports);
        }
        boolean supports(WorldMenuSurface surface) {
            if (!surface.rotation().equals(UiRotation.degrees(yaw(),0,0))) return false;
            var delta = surface.center().subtract(position(0,0)).toVector();
            double side = delta.dot(right()), above = delta.getY();
            if (Math.abs(delta.dot(new Vector(-direction.getModX(),0,-direction.getModZ()))) > 1e-6) return false;
            if (above - surface.size().height() / 2 < 0 || above + surface.size().height() / 2 > 1.6) return false;
            double first = side - surface.size().width() / 2, last = side + surface.size().width() / 2;
            int height = above + surface.size().height() / 2 > 1 ? 3 : 2;
            for (int column = (int)Math.floor(first + .5); column <= (int)Math.floor(last + .5); column++) {
                if (column < -3 || column > 3) return false;
                for (int y = 1; y <= height; y++) if (!cell(column,y)) return false;
            }
            return wall || free(first - .05,last + .05,above - surface.size().height() / 2 - .05,
                    above + surface.size().height() / 2 + .05);
        }
    }

    void invalidate(Block changed) { invalidate(source -> touches(source,changed)); }
    static boolean touches(IronElevatorPlatform source, Block changed) {
        return source.anchor().getWorld().equals(changed.getWorld()) && changed.getY() >= source.height()
                && changed.getY() <= source.height() + 3 && source.blocks().stream().anyMatch(block ->
                Math.abs((long)block.getX() - changed.getX()) <= 7 && Math.abs((long)block.getZ() - changed.getZ()) <= 7);
    }
    static boolean touchesChunk(IronElevatorPlatform source, UUID world, int x, int z) {
        return source.anchor().getWorld().getUID().equals(world) && source.blocks().stream().anyMatch(block ->
                x >= (block.getX() - 7 >> 4) && x <= (block.getX() + 7 >> 4)
                        && z >= (block.getZ() - 7 >> 4) && z <= (block.getZ() + 7 >> 4));
    }
    void invalidateChunk(UUID world, int x, int z) { invalidate(source -> touchesChunk(source,world,x,z)); }
    void invalidateWorld(UUID world) { invalidate(source -> source.anchor().getWorld().getUID().equals(world)); }
    private void invalidate(Predicate<IronElevatorPlatform> affected) {
        cache.keySet().removeIf(affected);
        List.copyOf(jobs.values()).stream().filter(job -> affected.test(job.source)).forEach(this::cancel);
    }
    private void cancel(Job job) {
        job.requests.forEach(Request::cancel);
        job.task.cancel();
        jobs.remove(job.source,job);
    }
    void clear() { List.copyOf(jobs.values()).forEach(this::cancel); cache.clear(); }
}
