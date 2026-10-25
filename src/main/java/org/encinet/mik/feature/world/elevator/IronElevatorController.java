package org.encinet.mik.feature.world.elevator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Primary-thread player sessions. Discovery, UI and final trip validation have separate lifetimes. */
final class IronElevatorController {
    interface Presentation {
        void hint(Player player, boolean up, boolean down);
        void clear(Player player);
        void forget(UUID player);
        void panel(Player player, IronElevatorPlatform source, IronElevatorTravelGroup group,
                   List<IronElevatorFloors.Floor> floors);
        void travelled(Player player, Location departure);
    }

    private static final long COOLDOWN_NANOS = 500_000_000L;
    private static final long INPUT_NANOS = 1_500_000_000L;
    private final IronElevatorFloors floors;
    private final Presentation presentation;
    private final Consumer<Runnable> nextTick;
    private final Function<UUID, Player> players;
    private final Consumer<String> warning;
    private final LongSupplier clock;
    private final IronElevatorWorkQueue queue;
    private final IronElevatorDiscovery discovery;
    private final Map<UUID, Visit> visits = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private int ticks;
    private boolean closed;

    private record Intent(int direction, Integer targetY, long createdAt, BooleanSupplier allowed) { }

    private static final class Visit {
        final Block source;
        Location feet;
        IronElevatorPlatform.Search resolving;
        IronElevatorPlatform platform;
        IronElevatorTravelGroup group;
        IronElevatorRouteSearch routes;
        IronElevatorWorkQueue.Task work;
        IronElevatorDiscovery.Subscription subscription;
        IronElevatorDiscovery.Result catalog;
        Intent intent;
        boolean hintReady;
        Location confirmedFeet;
        boolean confirmedUp;
        boolean confirmedDown;
        boolean committing;
        int checkedTick = -1;
        boolean checkedValid;
        int groupTick;
        long platformMeasuredAt;

        Visit(Block source, Location feet, IronElevatorPlatform platform, long measuredAt) {
            this.source = source;
            this.feet = feet.clone();
            this.platform = platform;
            platformMeasuredAt = measuredAt;
            if (platform == null) resolving = new IronElevatorPlatform.Search(source);
        }

        void cancel() {
            if (work != null) work.cancel();
            if (subscription != null) subscription.cancel();
            intent = null;
        }
    }

    IronElevatorController(IronElevatorFloors floors, Presentation presentation, Consumer<Runnable> nextTick,
                           Function<UUID, Player> players, Consumer<String> warning) {
        this(floors, presentation, nextTick, players, warning, System::nanoTime,
                new IronElevatorWorkQueue(error -> warning.accept("Elevator query failed: " + error.getMessage())));
    }

    IronElevatorController(IronElevatorFloors floors, Presentation presentation, Consumer<Runnable> nextTick,
                           Function<UUID, Player> players, Consumer<String> warning, LongSupplier clock,
                           IronElevatorWorkQueue queue) {
        this.floors = floors;
        this.presentation = presentation;
        this.nextTick = nextTick;
        this.players = players;
        this.warning = warning;
        this.clock = clock;
        this.queue = queue;
        discovery = new IronElevatorDiscovery(floors, queue);
    }

    void tick() {
        if (closed) return;
        ticks++;
        queue.tick();
        if (ticks % 20 != 0) return;
        for (UUID id : List.copyOf(visits.keySet())) {
            Player player = players.apply(id);
            if (player == null) forget(id);
            else {
                Visit visit = visits.get(id);
                if (visit.work == null && !visit.committing) observe(player, player.getLocation(), true);
            }
        }
    }

    void observe(Player player, Location feet, boolean refresh) {
        if (closed) return;
        UUID id = player.getUniqueId();
        Block standing = canUse(player) ? IronElevatorTravelGroup.platformAt(player, feet) : null;
        if (standing == null) {
            forget(id);
            return;
        }
        Visit previous = visits.get(id);
        boolean same = previous != null
                && (previous.platform == null ? previous.source.equals(standing) : previous.platform.contains(standing)
                && clock.getAsLong() - previous.platformMeasuredAt < IronElevatorFloors.CACHE_NANOS);
        if (same && previous.committing) return;
        if (same && !refresh && (previous.hintReady || samePosition(previous.feet, feet))) return;
        Visit visit;
        if (same) {
            visit = previous;
            if (visit.work != null) visit.work.cancel();
            visit.feet = feet.clone();
            visit.routes = null;
            visit.group = null;
            visit.hintReady = false;
            visit.checkedTick = -1;
        } else {
            forget(id);
            IronElevatorShaft cached = floors.cached(standing);
            IronElevatorPlatform platform = cached == null ? null : cached.at(standing.getY());
            if (platform != null && !platform.contains(standing)) platform = null;
            visit = new Visit(standing, feet, platform, platform == null ? clock.getAsLong() : floors.measuredAt(standing));
            visits.put(id, visit);
        }
        start(player, visit);
    }

    private void start(Player player, Visit visit) {
        visit.work = queue.submit(visit.intent == null ? IronElevatorWorkQueue.HINT : IronElevatorWorkQueue.INPUT,
                () -> {
                    try {
                        return progress(player, visit);
                    } catch (RuntimeException error) {
                        visit.work = null;
                        visit.intent = null;
                        presentation.clear(player);
                        throw error;
                    }
                });
    }

    private boolean progress(Player player, Visit visit) {
        if (visit.checkedTick != ticks) {
            visit.checkedTick = ticks;
            visit.checkedValid = valid(player, visit);
        }
        if (!visit.checkedValid || visits.get(player.getUniqueId()) != visit) {
            if (visits.get(player.getUniqueId()) == visit) forget(player.getUniqueId());
            return true;
        }
        if (visit.resolving != null) {
            if (!visit.resolving.step()) return false;
            visit.platform = visit.resolving.result();
            visit.platformMeasuredAt = clock.getAsLong();
            visit.resolving = null;
            if (visit.platform == null) {
                forget(player.getUniqueId());
                return true;
            }
        }
        if (visit.group == null) {
            visit.group = IronElevatorTravelGroup.collect(player, visit.platform);
            visit.groupTick = ticks;
            if (visit.group == null) {
                visit.work = null;
                visit.intent = null;
                presentation.clear(player);
                return true;
            }
            visit.routes = new IronElevatorRouteSearch(visit.source, visit.group,
                    visit.intent == null ? null : visit.intent.targetY(), floors.cached(visit.source));
            if (visit.subscription == null || !visit.subscription.active()) {
                visit.subscription = discovery.prepare(visit.source, visit.platform, result -> {
                    if (!valid(player, visit)) return;
                    visit.catalog = result;
                    if (visit.hintReady) showPanelLater(player, visit);
                });
            }
        }
        Intent intent = visit.intent;
        if (intent != null && (clock.getAsLong() - intent.createdAt() > INPUT_NANOS || !intent.allowed().getAsBoolean())) {
            visit.intent = null;
            if (intent.targetY() != null) {
                visit.work = null;
                nextTick.accept(() -> { if (valid(player, visit)) observe(player, player.getLocation(), true); });
                return true;
            }
            intent = null;
        }
        int direction = intent == null ? 0 : intent.targetY() == null ? intent.direction() : 1;
        boolean complete = direction == 0 ? visit.routes.step() : visit.routes.step(direction);
        if ((complete || intent != null && visit.routes.ready(direction)) && visit.groupTick != ticks) {
            IronElevatorTravelGroup current = IronElevatorTravelGroup.collect(player, visit.platform);
            if (current == null) {
                visit.work = null;
                visit.intent = null;
                visit.confirmedFeet = null;
                presentation.clear(player);
                return true;
            }
            boolean changed = !visit.group.sameGeometry(current) || !visit.routes.resultsSafe();
            visit.groupTick = ticks;
            if (changed) {
                visit.group = current;
                visit.routes = new IronElevatorRouteSearch(visit.source, current,
                        intent == null ? null : intent.targetY(), floors.cached(visit.source));
                return false;
            }
        }
        if (intent != null && visit.routes.ready(direction)) {
            IronElevatorPlatform target = direction > 0 ? visit.routes.up() : visit.routes.down();
            visit.intent = null;
            if (target != null) {
                commit(player, visit, target, intent);
                visit.work = null;
                return true;
            }
            if (intent.targetY() != null) {
                visit.work = null;
                nextTick.accept(() -> { if (valid(player, visit)) observe(player, player.getLocation(), true); });
                return true;
            }
        }
        if (!complete) return false;
        visit.work = null;
        visit.hintReady = true;
        boolean up = visit.routes.up() != null;
        boolean down = visit.routes.down() != null;
        visit.confirmedFeet = visit.feet.clone();
        visit.confirmedUp = up;
        visit.confirmedDown = down;
        if (!up && !down) presentation.clear(player);
        else {
            presentation.hint(player, up, down);
            if (visit.catalog != null) showPanelLater(player, visit);
        }
        return true;
    }

    private void showPanelLater(Player player, Visit visit) {
        nextTick.accept(() -> {
            if (!valid(player, visit) || !visit.hintReady || visit.catalog == null || visit.committing
                    || visit.routes.up() == null && visit.routes.down() == null) return;
            IronElevatorTravelGroup group = IronElevatorTravelGroup.collect(player, visit.platform);
            if (group != null) presentation.panel(player, visit.platform, group, visit.catalog.floors());
        });
    }

    boolean request(Player player, Location origin, int direction, Integer targetY, BooleanSupplier allowed) {
        if (targetY == null && direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid direction");
        UUID id = player.getUniqueId();
        Long previous = lastTeleport.get(id);
        if (closed || !canUse(player) || hasCommitting(id)
                || previous != null && clock.getAsLong() - previous < COOLDOWN_NANOS) return false;
        Block source = IronElevatorTravelGroup.platformAt(player, origin);
        if (source == null || targetY != null && targetY == source.getY()) return false;
        Visit visit = visits.get(id);
        if (visit != null && visit.confirmedFeet != null
                && visit.platform.contains(source) && samePosition(visit.confirmedFeet, origin) && targetY == null
                && !(direction > 0 ? visit.confirmedUp : visit.confirmedDown)) return false;
        observe(player, origin, true);
        visit = visits.get(id);
        if (visit == null || visit.work == null) return false;
        visit.intent = new Intent(direction, targetY, clock.getAsLong(), allowed);
        visit.work.prioritizeInput();
        return true;
    }

    private void commit(Player player, Visit visit, IronElevatorPlatform target, Intent intent) {
        visit.committing = true;
        nextTick.accept(() -> {
            if (!valid(player, visit)) {
                if (visits.get(player.getUniqueId()) == visit) forget(player.getUniqueId());
                return;
            }
            visit.committing = false;
            if (!intent.allowed().getAsBoolean() || clock.getAsLong() - intent.createdAt() > INPUT_NANOS) {
                observe(player, player.getLocation(), true);
                return;
            }
            IronElevatorTravelGroup group = IronElevatorTravelGroup.collect(player, visit.platform);
            Location departure = player.getLocation();
            Location destination = group == null ? null : group.landing(target);
            if (destination != null && group.teleport(destination, warning)) {
                lastTeleport.put(player.getUniqueId(), clock.getAsLong());
                presentation.travelled(player, departure);
            }
            observe(player, player.getLocation(), true);
        });
    }

    private boolean hasCommitting(UUID id) {
        Visit visit = visits.get(id);
        return visit != null && visit.committing;
    }

    boolean pending(UUID id) {
        Visit visit = visits.get(id);
        return visit != null && (visit.committing || visit.intent != null
                && clock.getAsLong() - visit.intent.createdAt() <= INPUT_NANOS);
    }

    private boolean valid(Player player, Visit visit) {
        if (visits.get(player.getUniqueId()) != visit || !canUse(player))
            return false;
        Block standing = IronElevatorTravelGroup.platformAt(player, player.getLocation());
        return standing != null && (visit.platform == null ? visit.source.equals(standing) : visit.platform.contains(standing));
    }

    void invalidate(UUID world) {
        discovery.invalidate(world);
        invalidateVisits(world, visit -> true);
    }

    void invalidate(Block changed) {
        discovery.invalidate(changed);
        invalidateVisits(changed.getWorld().getUID(), visit -> affected(visit, column ->
                Math.abs((long) column.blockX() - changed.getX()) <= 1
                        && Math.abs((long) column.blockZ() - changed.getZ()) <= 1));
    }

    void invalidateChunk(UUID world, int x, int z) {
        discovery.invalidateChunk(world, x, z);
        invalidateVisits(world, visit -> affected(visit, column -> IronElevatorShaft.nearChunk(column, x, z)));
    }

    private static boolean affected(Visit visit, Predicate<IronElevatorPlatform.Column> column) {
        return column.test(IronElevatorPlatform.Column.of(visit.source))
                || visit.platform != null && visit.platform.blocks().stream().map(IronElevatorPlatform.Column::of).anyMatch(column)
                || visit.resolving != null && visit.resolving.columns().stream().anyMatch(column)
                || visit.catalog != null && visit.catalog.shaft().columns().stream().anyMatch(column)
                || visit.routes != null && visit.routes.anyColumn(column);
    }

    private void invalidateVisits(UUID world, Predicate<Visit> affected) {
        for (var entry : List.copyOf(visits.entrySet())) {
            if (!entry.getValue().source.getWorld().getUID().equals(world) || !affected.test(entry.getValue())) continue;
            UUID id = entry.getKey();
            forget(id);
            nextTick.accept(() -> {
                Player player = players.apply(id);
                if (player != null) observe(player, player.getLocation(), true);
            });
        }
    }

    void forget(UUID id) {
        Visit visit = visits.remove(id);
        if (visit != null) {
            visit.cancel();
            Player player = players.apply(id);
            if (player != null) presentation.clear(player);
            else presentation.forget(id);
        }
    }

    void quit(UUID id) {
        forget(id);
        lastTeleport.remove(id);
    }

    void clear() {
        closed = true;
        for (UUID id : List.copyOf(visits.keySet())) forget(id);
        discovery.clear();
        queue.clear();
        lastTeleport.clear();
    }

    void enable() { closed = false; }

    void placed(Block source, Runnable chime) {
        if (closed) return;
        var resolving = new IronElevatorPlatform.Search(source);
        var origin = source.getLocation().add(0.5, 1, 0.5);
        queue.submit(IronElevatorWorkQueue.CATALOG, new BooleanSupplier() {
            IronElevatorRouteSearch search;

            @Override
            public boolean getAsBoolean() {
                if (closed || !source.getWorld().isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)
                        || source.getType() != org.bukkit.Material.IRON_BLOCK) return true;
                if (search == null) {
                    if (!resolving.step()) return false;
                    var platform = resolving.result();
                    if (platform == null || IronElevatorLanding.on(platform, origin,
                            IronElevatorLanding.DEFAULT_SIZE, body -> false) == null) return true;
                    search = new IronElevatorRouteSearch(source, List.of(IronElevatorPlatform.Column.of(source)),
                            target -> IronElevatorLanding.on(target, origin,
                                    IronElevatorLanding.DEFAULT_SIZE, body -> false) != null, null, floors.cached(source));
                }
                boolean complete = search.step();
                if (search.up() != null || search.down() != null) {
                    chime.run();
                    return true;
                }
                return complete;
            }
        });
    }

    private static boolean samePosition(Location first, Location second) {
        return first.getWorld().equals(second.getWorld()) && first.getX() == second.getX()
                && first.getY() == second.getY() && first.getZ() == second.getZ();
    }

    private static boolean canUse(Player player) {
        return player.isOnline() && !player.isDead() && !player.isFlying() && !player.isGliding()
                && !player.isSwimming() && player.getGameMode() != GameMode.SPECTATOR;
    }
}
