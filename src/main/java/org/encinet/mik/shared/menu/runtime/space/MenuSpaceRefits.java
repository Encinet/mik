package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.diagnostics.MenuTiming;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Main-thread capture and polling. Planning, indexing and fitting use detached geometry on one worker. */
public final class MenuSpaceRefits implements AutoCloseable {
    static final int MAX_REQUESTS = 32;
    static final int BLOCKS_PER_TICK = 512;
    static final double HORIZONTAL_RESERVE = 1.25;
    static final double VERTICAL_RESERVE = .55;
    private static final int RESERVED_INTERACTIVE = 4;
    private static final int RESERVED_VERIFICATION = 4;
    private static final double MAX_POSE_DRIFT_SQUARED = .75 * .75;
    private static final double MIN_HEADING_DOT = Math.cos(Math.toRadians(12));
    private static final int BLOCKS_PER_TURN = 256;
    private static final int ENTITY_CAPTURES_PER_TICK = 2;
    private static final long SAMPLE_NANOS = 1_500_000;
    private static final int TERRAIN_TICKS = 20;
    private static final int MODEL_TICKS = 4;
    // The last published frame can bridge a verification interval and a short refit.
    // This does not make its model sample usable for solving or publishing new geometry.
    static final int PUBLISHED_FRAME_TICKS = 3 * MODEL_TICKS;
    private final Map<Key, Ticket<?>> requests = new LinkedHashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(MAX_REQUESTS), Thread.ofPlatform().daemon(true).name("mik-menu-space").factory());
    private final AtomicInteger readySolves = new AtomicInteger();
    private long sequence;
    private volatile long tick;

    public record Plan<T>(BoundingBox region, Function<MenuCollisionSpace, T> solve, Probe<T> probe) {
        public Plan(BoundingBox region, Function<MenuCollisionSpace, T> solve) {
            this(region, solve, null);
        }

        public static <T> Plan<T> bounded(BoundingBox nominal, BiFunction<MenuCollisionSpace, Double, T> solve,
                                          Supplier<T> unavailable) {
            return bounded(nominal, solve, unavailable, null, null);
        }

        public static <T> Plan<T> bounded(BoundingBox nominal, BiFunction<MenuCollisionSpace, Double, T> solve,
                                          Supplier<T> unavailable, BoundingBox first, BiFunction<MenuCollisionSpace, Double, T> tryFirst) {
            var budget = MenuSpaceBudget.forRegion(nominal);
            if (!budget.usable()) return new Plan<>(new BoundingBox(), _ -> unavailable.get());
            var probe = first == null || !nominal.contains(first) ? null
                    : new Probe<>(budget.scale(first), space -> tryFirst.apply(budget.units(space), budget.factor()));
            return new Plan<>(budget.region(), space -> solve.apply(budget.units(space), budget.factor()), probe);
        }
    }

    public record Probe<T>(BoundingBox region, Function<MenuCollisionSpace, T> solve) { }
    public enum Status { COMPLETE, STALE, OUTSIDE_BUDGET }
    public enum Priority { INTERACTIVE, VERIFY, PREVIEW }
    public enum Surface { ANCHOR, POPOVER }
    public record Key(UUID session, Surface surface) { }
    public record Pose(Location eye, Vector forward) { }
    public record Result<T>(Status status, T value, Evidence evidence, Pose pose) { }

    public static final class Evidence {
        private final Location surveyEye;
        private final Location eye;
        private final MenuBlockCollisionSnapshot.Survey survey;
        private final BoundingBox coverage;
        private final Indexed<?> indexed;
        private final long terrainTick, modelTick;
        private boolean invalidated;

        private Evidence(Location surveyEye, Location eye, MenuBlockCollisionSnapshot.Survey survey, BoundingBox coverage,
                         Indexed<?> indexed, long terrainTick, long modelTick) {
            this.eye = eye;
            this.surveyEye = surveyEye;
            this.survey = survey;
            this.coverage = coverage;
            this.indexed = indexed;
            this.terrainTick = terrainTick;
            this.modelTick = modelTick;
        }

        private boolean reusable(long now, Location nextEye, BoundingBox region) {
            return !invalidated && now - terrainTick < TERRAIN_TICKS && surveyEye.getWorld().equals(nextEye.getWorld())
                    && coverage.contains(region.clone().shift(nextEye.toVector().subtract(surveyEye.toVector()))) && survey.current();
        }

        public boolean fresh(long now) { return !invalidated && now - modelTick <= MODEL_TICKS && survey.current(); }

        public boolean canHoldPublishedFrame(long now, Pose verified, Location currentEye, Vector currentForward) {
            return verified != null && !invalidated && now - modelTick <= PUBLISHED_FRAME_TICKS
                    && samePosition(verified.eye(), currentEye)
                    && verified.forward().distanceSquared(currentForward) <= 1e-12 && survey.current();
        }

        public boolean clear(Location nextEye, BoundingBox region) {
            if (!eye.getWorld().equals(nextEye.getWorld())) return false;
            var shift = nextEye.toVector().subtract(eye.toVector());
            return indexed.combined().clear(region.clone().shift(shift));
        }

        public boolean invalidate(Location changed) {
            if (!covers(survey, changed)) return false;
            invalidated = true;
            return true;
        }
    }

    public static final class Ticket<T> {
        private final Key key;
        private final UUID viewer;
        private final Priority priority;
        private final Pose initial;
        private final Supplier<Pose> latest;
        private final Evidence reuse;
        private final Function<Vector, Plan<T>> planner;
        private final MenuTiming timing;
        private long lastTurn;
        private Step<Plan<T>> planning;
        private Plan<T> plan;
        private Location surveyEye;
        private Pose solvedPose;
        private BoundingBox coverage;
        private MenuBlockCollisionSnapshot.Survey survey;
        private boolean probing;
        private Step<Indexed<T>> solving;
        private long terrainTick, modelTick;
        private Result<T> discarded;
        private RuntimeException failure;

        private Ticket(Key key, UUID viewer, Priority priority, Pose initial, Evidence reuse, Supplier<Pose> latest,
                       Function<Vector, Plan<T>> planner, long tick, MenuTiming timing) {
            this.key = key;
            this.viewer = viewer;
            this.priority = priority;
            this.initial = initial;
            this.latest = latest;
            this.reuse = reuse;
            this.planner = planner;
            this.lastTurn = tick;
            this.timing = timing;
        }

        private long turnOrder() { return lastTurn + priority.ordinal(); }
        private int priorityOrder() { return priority.ordinal(); }
    }

    private record Indexed<T>(MenuSpaceSnapshot terrain, MenuSpaceSnapshot combined, List<Box> models, T value) { }
    private record Work(boolean solve, long turn, Priority priority, long order, FutureTask<Void> task, Runnable release)
            implements Runnable, Comparable<Work> {
        @Override public int compareTo(Work other) {
            if (solve != other.solve) return solve ? -1 : 1;
            int byTurn = Long.compare(turn, other.turn);
            if (byTurn != 0) return byTurn;
            int byPriority = priority.compareTo(other.priority);
            return byPriority == 0 ? Long.compare(order, other.order) : byPriority;
        }

        @Override public void run() {
            try { task.run(); }
            finally { release.run(); }
        }
    }

    private record Step<T>(Work work, CompletableFuture<T> result) {
        public T poll() { return result.getNow(null); }
    }

    public long tick() { return tick; }

    <T> Ticket<T> request(UUID viewer, Location eye, Evidence reuse, Supplier<Plan<T>> plan) {
        return request(viewer, new Pose(eye, null), reuse, null, _ -> plan.get());
    }

    <T> Ticket<T> request(UUID viewer, Pose pose, Evidence reuse, Supplier<Pose> latest, Function<Vector, Plan<T>> plan) {
        return request(new Key(viewer, Surface.ANCHOR), viewer, Priority.INTERACTIVE, pose, reuse, latest, plan);
    }

    public <T> Ticket<T> request(Key key, UUID viewer, Priority priority, Pose pose, Evidence reuse,
                                Supplier<Pose> latest, Function<Vector, Plan<T>> plan) {
        var replacing = requests.get(key);
        var timing = MenuTiming.start("fit_request");
        if (worker.isShutdown() || !admissible(priority, replacing)) {
            if (timing != null) MenuTiming.finish(timing, "deferred_" + priority.name(), requests.size());
            return null;
        }
        if (replacing != null) discard(replacing, Status.STALE, "superseded");
        var ticket = new Ticket<T>(key, viewer, priority, pose, reuse, latest, plan, tick, timing);
        var heading = pose.forward();
        ticket.planning = submit(ticket, false, () -> plan.apply(heading));
        requests.put(key, ticket);
        return ticket;
    }

    private boolean admissible(Priority priority, Ticket<?> replacing) {
        if (requests.size() - (replacing == null ? 0 : 1) >= MAX_REQUESTS) return false;
        int interactive = 0, verification = 0;
        for (var ticket : requests.values()) {
            if (ticket == replacing) continue;
            if (ticket.priority == Priority.INTERACTIVE) interactive++;
            if (ticket.priority == Priority.VERIFY) verification++;
        }
        int size = requests.size() - (replacing == null ? 0 : 1);
        return (priority == Priority.INTERACTIVE || size - interactive < MAX_REQUESTS - RESERVED_INTERACTIVE)
                && (priority == Priority.VERIFY || size - verification < MAX_REQUESTS - RESERVED_VERIFICATION)
                && (priority != Priority.PREVIEW || size - interactive - verification
                < MAX_REQUESTS - RESERVED_INTERACTIVE - RESERVED_VERIFICATION);
    }

    public <T> Result<T> poll(Ticket<T> ticket) {
        if (ticket.discarded != null) return ticket.discarded;
        if (ticket.failure != null) {
            discard(ticket, Status.STALE, "capture_error");
            throw ticket.failure;
        }
        if (ticket.solving == null || !ticket.solving.result().isDone()) return null;
        if (tick - ticket.modelTick > MODEL_TICKS || !ticket.survey.current()) {
            discard(ticket, Status.STALE, "evidence_expired");
            return ticket.discarded;
        }
        Indexed<T> solved;
        try { solved = ticket.solving.poll(); }
        catch (RuntimeException failure) {
            discard(ticket, Status.STALE, "solve_error");
            throw failure;
        }
        if (ticket.probing && solved != null && solved.value() == null) {
            ticket.survey.expand();
            ticket.coverage = ticket.plan.region().clone();
            ticket.solving = null;
            ticket.probing = false;
            return null;
        }
        requests.remove(ticket.key, ticket);
        if (solved == null) {
            MenuTiming.finish(ticket.timing, "stale_pose_or_model", 0);
            return new Result<>(Status.STALE, null, null, ticket.initial);
        }
        MenuTiming.finish(ticket.timing, ticket.probing ? "complete_centered" : "complete", 0);
        return new Result<>(Status.COMPLETE, solved.value(), new Evidence(ticket.surveyEye, ticket.solvedPose.eye(), ticket.survey,
                ticket.coverage, solved, ticket.terrainTick, ticket.modelTick), ticket.solvedPose);
    }

    public void sample(Function<UUID, Player> viewers, Function<Player, Collection<Entity>> models) {
        tick++;
        if (requests.isEmpty()) return;
        var timing = MenuTiming.start("main_capture_tick");
        try { sampleRequests(viewers, models); }
        finally { MenuTiming.finish(timing); }
    }

    private void sampleRequests(Function<UUID, Player> viewers, Function<Player, Collection<Entity>> models) {
        long deadline = System.nanoTime() + SAMPLE_NANOS;
        var reads = new HashMap<World, MenuBlockCollisionSnapshot>();
        int remaining = BLOCKS_PER_TICK, captures = ENTITY_CAPTURES_PER_TICK;
        var ordered = new ArrayList<>(requests.values());
        ordered.sort(Comparator.comparingLong((Ticket<?> ticket) -> ticket.turnOrder()).thenComparingInt(Ticket::priorityOrder));
        for (var ticket : ordered) {
            if (ticket.solving != null && tick - ticket.modelTick > MODEL_TICKS) {
                discard(ticket, Status.STALE, "evidence_expired");
                continue;
            }
            if (System.nanoTime() - deadline >= 0) break;
            if (ticket.failure != null || ticket.solving != null) continue;
            try {
                if (!planSurvey(ticket)) continue;
                if (!ticket.survey.possible()) {
                    discard(ticket, Status.OUTSIDE_BUDGET, "outside_budget");
                    continue;
                }
                var world = ticket.surveyEye.getWorld();
                if (!ticket.survey.done() && remaining > 0) {
                    var timing = MenuTiming.start("terrain_capture");
                    int sampled = ticket.survey.sample(reads.computeIfAbsent(world, MenuBlockCollisionSnapshot::new),
                            Math.min(remaining, BLOCKS_PER_TURN), deadline);
                    MenuTiming.finish(timing, null, sampled);
                    remaining -= sampled;
                    ticket.lastTurn = tick;
                }
                if (!ticket.survey.done() || captures <= 0 || readySolves.get() >= ENTITY_CAPTURES_PER_TICK
                        || System.nanoTime() - deadline >= 0) continue;
                if (!ticket.survey.current()) {
                    discard(ticket, Status.STALE, "terrain_changed");
                    continue;
                }
                if (ticket.reuse != null && ticket.survey == ticket.reuse.survey && tick - ticket.terrainTick >= TERRAIN_TICKS) {
                    discard(ticket, Status.STALE, "terrain_expired");
                    continue;
                }
                startSolve(ticket, viewers.apply(ticket.viewer), models);
                ticket.lastTurn = tick;
                captures--;
            } catch (RuntimeException failure) {
                ticket.failure = failure;
            }
        }
    }

    private <T> boolean planSurvey(Ticket<T> ticket) {
        if (ticket.survey != null) return true;
        ticket.plan = ticket.planning.poll();
        if (ticket.plan == null) return false;
        ticket.planning = null;
        var eye = ticket.initial.eye();
        ticket.coverage = ticket.plan.region().clone();
        if (ticket.reuse != null && ticket.reuse.reusable(tick, eye, ticket.coverage)) {
            ticket.survey = ticket.reuse.survey;
            ticket.coverage = ticket.reuse.coverage;
            ticket.surveyEye = ticket.reuse.surveyEye;
            ticket.terrainTick = ticket.reuse.terrainTick;
        } else {
            if (ticket.latest != null) ticket.coverage.expand(HORIZONTAL_RESERVE, VERTICAL_RESERVE, HORIZONTAL_RESERVE);
            ticket.surveyEye = eye;
            var probe = ticket.latest == null ? ticket.plan.probe() : null;
            var first = probe != null && ticket.coverage.contains(probe.region()) ? probe.region() : null;
            ticket.survey = new MenuBlockCollisionSnapshot.Survey(eye.getWorld(), eye.toVector(), ticket.coverage, first);
            ticket.probing = ticket.survey.partial();
            if (ticket.probing) ticket.coverage = first.clone();
            ticket.terrainTick = tick;
        }
        return true;
    }

    private <T> void startSolve(Ticket<T> ticket, Player player, Function<Player, Collection<Entity>> models) {
        var coverage = ticket.coverage;
        var pose = ticket.initial;
        var surveyOrigin = ticket.surveyEye.toVector();
        var world = ticket.surveyEye.getWorld();
        if (ticket.latest != null) {
            var next = ticket.latest.get();
            if (world.equals(next.eye().getWorld()) && coverage.contains(ticket.plan.region().clone().shift(next.eye().toVector().subtract(surveyOrigin))))
                pose = next;
            else if (!poseClose(pose.eye(), pose.forward(), next.eye(), next.forward())) {
                discard(ticket, Status.STALE, "pose_changed");
                return;
            }
        }
        var eye = pose.eye();
        var offset = eye.toVector().subtract(surveyOrigin);
        var relativeCoverage = coverage.clone().shift(offset.clone().multiply(-1));
        var reach = new Vector(Math.max(-relativeCoverage.getMinX(), relativeCoverage.getMaxX()),
                Math.max(-relativeCoverage.getMinY(), relativeCoverage.getMaxY()),
                Math.max(-relativeCoverage.getMinZ(), relativeCoverage.getMaxZ())).length();
        var captureTiming = MenuTiming.start("model_capture");
        var entities = player == null || !world.equals(player.getWorld()) ? List.<Box>of()
                : MenuEntityObstacles.capture(player, eye, Math.min(reach, FloatingMenuWorldSpace.MAX_TRACE_DISTANCE),
                models.apply(player), relativeCoverage);
        MenuTiming.finish(captureTiming, null, entities.size());
        var cached = ticket.reuse != null && ticket.survey == ticket.reuse.survey ? ticket.reuse.indexed : null;
        var terrain = cached == null ? ticket.survey.finish() : List.<BoundingBox>of();
        var plan = ticket.plan;
        var probe = ticket.probing ? plan.probe() : null;
        var planner = ticket.planner;
        var heading = pose.forward();
        boolean turned = heading != null && heading.distanceSquared(ticket.initial.forward()) > 1e-12;
        boolean sameOrigin = cached != null && samePosition(eye, ticket.reuse.eye);
        ticket.solvedPose = pose;
        long sampledAt = ticket.modelTick = tick;
        ticket.solving = submit(ticket, true, () -> {
            if (tick - sampledAt > MODEL_TICKS) return null;
            var adjusted = turned ? planner.apply(heading) : plan;
            var region = probe == null ? adjusted.region() : probe.region();
            if (!relativeCoverage.contains(region)) return null;
            var indexTiming = MenuTiming.start("index");
            MenuSpaceSnapshot index, combined;
            try {
                index = cached == null ? new MenuSpaceSnapshot(coverage, terrain) : cached.terrain();
                combined = sameOrigin && cached.models().equals(entities) ? cached.combined() : index.at(offset, entities);
            } finally { MenuTiming.finish(indexTiming); }
            var solve = probe == null ? adjusted.solve() : probe.solve();
            var solveTiming = MenuTiming.start(probe == null ? "solve" : "solve_centered");
            try { return new Indexed<>(index, combined, entities, solve.apply(combined)); }
            finally { MenuTiming.finish(solveTiming); }
        });
    }

    private <T> Step<T> submit(Ticket<?> ticket, boolean solve, Supplier<T> action) {
        var result = new CompletableFuture<T>();
        var queueTiming = MenuTiming.start(solve ? "solve_queue" : "plan_queue");
        var task = new FutureTask<Void>(() -> {
            MenuTiming.finish(queueTiming);
            if (!result.isCancelled()) {
                var timing = solve ? null : MenuTiming.start("plan");
                try { result.complete(action.get()); }
                catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
                finally { MenuTiming.finish(timing); }
            }
            return null;
        });
        if (solve) readySolves.incrementAndGet();
        var work = new Work(solve, (solve ? tick : ticket.lastTurn) + ticket.priorityOrder(), ticket.priority,
                sequence++, task, () -> { if (solve) readySolves.decrementAndGet(); });
        worker.execute(work);
        return new Step<>(work, result);
    }

    public static boolean samePosition(Location first, Location next) {
        return first.getWorld().equals(next.getWorld()) && first.distanceSquared(next) <= 1e-12;
    }

    public static boolean poseClose(Location eye, Vector forward, Location nextEye, Vector nextForward) {
        return eye.getWorld().equals(nextEye.getWorld()) && eye.distanceSquared(nextEye) <= MAX_POSE_DRIFT_SQUARED
                && forward.dot(nextForward) >= MIN_HEADING_DOT;
    }

    private static boolean covers(MenuBlockCollisionSnapshot.Survey survey, Location changed) {
        return survey.covers(changed.getWorld(), changed.getBlockX(), changed.getBlockY(), changed.getBlockZ());
    }

    public void invalidate(Location changed) {
        for (var ticket : List.copyOf(requests.values()))
            if (ticket.survey != null && covers(ticket.survey, changed)) discard(ticket, Status.STALE, "terrain_changed");
    }

    public void cancel(Ticket<?> ticket) { discard(ticket, Status.STALE, "cancelled"); }

    private void discard(Ticket<?> ticket, Status status, String reason) {
        if (ticket == null || !requests.remove(ticket.key, ticket)) return;
        ticket.discarded = new Result<>(status, null, null, ticket.initial);
        cancelStep(ticket.planning);
        cancelStep(ticket.solving);
        MenuTiming.finish(ticket.timing, reason, 0);
    }

    private void cancelStep(Step<?> step) {
        if (step == null) return;
        step.result().cancel(false);
        step.work().task().cancel(true);
        if (worker.remove(step.work())) step.work().release().run();
    }

    @Override public void close() {
        for (var ticket : List.copyOf(requests.values())) cancel(ticket);
        worker.shutdownNow();
    }
}
