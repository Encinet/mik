package org.encinet.mik.feature.world.elevator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Primary-thread sessions own inputs; geometry, route evaluation and presentation have separate lifetimes. */
final class IronElevatorController {
    interface Presentation {
        void hint(Player player, boolean up, boolean down);
        void clearHint(Player player);
        void clearPanel(Player player);
        void clear(Player player);
        void forget(UUID player);
        void panel(Player player, PanelSnapshot snapshot);
        void travelled(Player player, Location departure);
    }

    private static final long COOLDOWN_NANOS = 500_000_000L;
    private static final long INPUT_NANOS = 1_500_000_000L;
    private final IronElevatorCatalog directory;
    private final Presentation presentation;
    private final ArrayDeque<Runnable> deferred = new ArrayDeque<>();
    private final Set<UUID> panelUpdates = new HashSet<>();
    private final Set<UUID> executing = new HashSet<>();
    private final IronElevatorPanelPlacement placement;
    private final Function<UUID, Player> players;
    private final Consumer<String> warning;
    private final LongSupplier clock;
    private final IronElevatorWorkQueue queue;
    private final Map<UUID, Visit> visits = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private int ticks;
    private boolean closed;

    sealed interface Target permits Direction, Floor { }
    enum Direction implements Target { UP, DOWN }
    record Floor(int height) implements Target { }
    private sealed interface Action permits Idle, Intent, Trip { }
    private enum Idle implements Action { INSTANCE }
    private record Intent(Target target, long createdAt, BooleanSupplier allowed) implements Action {
        Integer targetY() { return target instanceof Floor floor ? floor.height() : null; }
        int direction() { return target == Direction.DOWN ? -1 : 1; }
    }
    record PanelSnapshot(Object visit, IronElevatorPlatform source, IronElevatorCatalog.Result catalog,
                         IronElevatorPanelPlacement.Plan placement, Map<Integer,Boolean> available,
                         Predicate<Player> valid) {
        PanelSnapshot { available = Map.copyOf(available); }
    }
    private static final class FloorChecks {
        final IronElevatorCatalog.Result catalog;
        final FloatingMenuPage page;
        final Map<Integer,Boolean> states = new HashMap<>();
        IronElevatorTravelGroup group;
        IronElevatorWorkQueue.Task task;
        int index;
        FloorChecks(IronElevatorCatalog.Result catalog, FloatingMenuPage page) {
            this.catalog = catalog; this.page = page; index = page.fromIndex();
        }
        void cancel() { if (task != null) task.cancel(); task = null; }
    }
    private record Trip(Geometry geometry, IronElevatorShaft.Level target, Intent intent) implements Action { }
    private record Sample(int tick, IronElevatorTravelGroup group) { }
    private record Availability(Geometry geometry, IronElevatorTravelGroup group,
                                IronElevatorShaft.Level up, IronElevatorShaft.Level down, boolean complete) { }

    private static final class Geometry {
        final Block source;
        final IronElevatorPlatform previous;
        IronElevatorPlatform.Search resolving;
        IronElevatorPlatform platform;
        long measuredAt;

        Geometry(Block source, IronElevatorPlatform platform, long measuredAt, IronElevatorPlatform previous) {
            this.source = source;
            this.platform = platform;
            this.measuredAt = measuredAt;
            this.previous = previous;
            if (platform == null) resolving = new IronElevatorPlatform.Search(source);
        }

        boolean contains(Block standing) {
            IronElevatorPlatform known = platform == null ? previous : platform;
            return known == null ? source.equals(standing) : known.contains(standing);
        }
    }

    private static final class Query {
        final Integer targetY;
        final IronElevatorRouteSearch routes;
        IronElevatorTravelGroup group;

        Query(Geometry geometry, IronElevatorTravelGroup group, Integer targetY, IronElevatorShaft cached) {
            this.targetY = targetY;
            this.group = group;
            routes = new IronElevatorRouteSearch(geometry.source, group.supportColumns(),
                    target -> group.landing(target) != null, targetY, cached);
        }

        void update(IronElevatorTravelGroup current, boolean refresh) {
            if (refresh || group != current && !group.sameGeometry(current))
                routes.reevaluate(target -> current.landing(target) != null);
            group = current;
        }
    }

    private static final class Navigation {
        Query query;
        Availability availability;
        Action action = Idle.INSTANCE;
        IronElevatorWorkQueue.Task work;
        boolean refresh;
        Intent intent() { return action instanceof Intent intent ? intent : null; }
        Trip trip() { return action instanceof Trip trip ? trip : null; }
        void cancel() { if (work != null) work.cancel(); action = Idle.INSTANCE; }
    }

    private static final class PanelState {
        IronElevatorCatalog.Subscription subscription;
        IronElevatorCatalog.Result catalog;
        IronElevatorPanelPlacement.Request mount;
        FloorChecks checks;
        void cancel() {
            if (subscription != null) subscription.cancel();
            if (mount != null) mount.cancel();
            if (checks != null) checks.cancel();
        }
    }

    private static final class Visit {
        Geometry geometry;
        Location feet;
        Sample sample;
        final Navigation navigation = new Navigation();
        final PanelState panel = new PanelState();
        int checkedTick = -1;
        boolean checkedValid;
        Visit(Geometry geometry, Location feet) { this.geometry = geometry; this.feet = feet.clone(); }
        void cancel() { navigation.cancel(); panel.cancel(); }
    }

    IronElevatorController(IronElevatorCatalog directory, Presentation presentation,
                           Function<UUID, Player> players, Consumer<String> warning) {
        this(directory, presentation, players, warning, System::nanoTime,
                new IronElevatorWorkQueue(error -> warning.accept("Elevator query failed: " + error.getMessage())));
    }

    IronElevatorController(IronElevatorCatalog directory, Presentation presentation,
                           Function<UUID, Player> players, Consumer<String> warning, LongSupplier clock,
                           IronElevatorWorkQueue queue) {
        this.directory = directory;
        this.presentation = presentation;
        this.players = players;
        this.warning = warning;
        this.clock = clock;
        this.placement = new IronElevatorPanelPlacement(clock);
        this.queue = queue;
    }

    void tick() {
        if (closed) return;
        ticks++;
        var callbacks = List.copyOf(deferred);
        deferred.clear();
        for (var callback : callbacks) {
            if (closed) return;
            try { callback.run(); }
            catch (RuntimeException error) { warning.accept("Elevator deferred work failed: " + error.getMessage()); }
        }
        queue.tick();
        var updates = Set.copyOf(panelUpdates);
        panelUpdates.clear();
        for (UUID id : updates) {
            Player player = players.apply(id);
            Visit visit = visits.get(id);
            if (player == null || visit == null) continue;
            try { publishPanel(player,visit); }
            catch (RuntimeException error) {
                warning.accept("Elevator panel failed: " + error.getMessage());
                presentation.clear(player);
            }
        }
        if (ticks % 20 != 0) return;
        for (UUID id : List.copyOf(visits.keySet())) {
            Player player = players.apply(id);
            if (player == null) forget(id);
            else {
                Visit visit = visits.get(id);
                if (visit.navigation.work == null && visit.navigation.trip() == null) observe(player, player.getLocation(), true);
            }
        }
    }

    private void defer(Runnable action) { if (!closed) deferred.addLast(action); }

    private Geometry geometry(Block standing, IronElevatorPlatform previous) {
        IronElevatorShaft cached = directory.cached(standing);
        IronElevatorPlatform platform = cached == null ? null : cached.source(standing);
        return new Geometry(standing, platform, platform == null ? clock.getAsLong() : directory.measuredAt(standing), previous);
    }

    private void renewGeometry(Visit visit, Block standing) {
        boolean continuing = visit.navigation.work != null && visit.navigation.query != null
                && !(visit.navigation.query.routes.ready(1) && visit.navigation.query.routes.ready(-1));
        visit.geometry = geometry(standing, visit.geometry.platform);
        if (!continuing || visit.geometry.platform != null
                && !visit.geometry.platform.equals(visit.geometry.previous)) visit.navigation.query = null;
        visit.sample = null;
        visit.navigation.availability = null;
        visit.checkedTick = -1;
        if (visit.panel.mount != null) visit.panel.mount.cancel();
        visit.panel.mount = null;
        if (visit.panel.checks != null) { visit.panel.checks.cancel(); visit.panel.checks.group = null; }
        // The accepted input and an in-flight shared catalog outlive a geometry refresh.
    }

    void observe(Player player, Location feet, boolean refresh) {
        if (closed) return;
        UUID id = player.getUniqueId();
        if (executing.contains(id)) return;
        Block standing = canUse(player) ? IronElevatorTravelGroup.platformAt(player, feet) : null;
        if (standing == null) {
            forget(id);
            return;
        }
        Visit visit = visits.get(id);
        if (visit == null || !visit.geometry.contains(standing)) {
            forget(id);
            visit = new Visit(geometry(standing, null), feet);
            visits.put(id, visit);
        } else {
            if (visit.navigation.trip() != null) return;
            boolean moved = !samePosition(visit.feet, feet);
            boolean expired = visit.geometry.platform != null && !fresh(visit.geometry);
            if (!refresh && !moved && !expired && (visit.navigation.work != null || visit.navigation.availability != null)) return;
            if (expired) renewGeometry(visit, standing);
            if (moved) {
                visit.feet = feet.clone();
                visit.sample = null;
            }
        }
        visit.navigation.refresh |= refresh;
        start(player, visit);
    }

    private void start(Player player, Visit visit) {
        if (visit.navigation.work != null) {
            visit.navigation.work.priority(visit.navigation.intent() == null ? IronElevatorWorkQueue.HINT : IronElevatorWorkQueue.INPUT);
            return;
        }
        visit.navigation.work = queue.submit(visit.navigation.intent() == null ? IronElevatorWorkQueue.HINT : IronElevatorWorkQueue.INPUT,
                () -> {
                    try {
                        return progress(player, visit);
                    } catch (RuntimeException error) {
                        visit.navigation.work = null;
                        visit.navigation.action = Idle.INSTANCE;
                        visit.navigation.availability = null;
                        presentation.clear(player);
                        throw error;
                    }
                });
    }

    private IronElevatorTravelGroup measure(Player player, Visit visit, boolean force) {
        if (force || visit.sample == null || visit.sample.tick() != ticks)
            visit.sample = new Sample(ticks, IronElevatorTravelGroup.collect(player, visit.geometry.platform));
        return visit.sample.group();
    }

    private void prepareCatalog(Player player, Visit visit) {
        if (visit.panel.subscription != null && visit.panel.subscription.active()
                || visit.panel.catalog != null && clock.getAsLong() - visit.panel.catalog.measuredAt() < IronElevatorCatalog.CACHE_NANOS) return;
        visit.panel.subscription = directory.prepare(visit.geometry.source, visit.geometry.platform, queue, result -> {
            if (!valid(player, visit)) return;
            visit.panel.catalog = result;
            if (visit.panel.checks != null && visit.panel.checks.catalog != result) {
                visit.panel.checks.cancel(); visit.panel.checks = null;
            }
            requestPanelUpdate(player, visit);
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
        if (visit.navigation.intent() != null && !active(visit.navigation.intent())) {
            visit.navigation.action = Idle.INSTANCE;
            visit.navigation.work.priority(IronElevatorWorkQueue.HINT);
        }
        if (visit.geometry.platform != null && !fresh(visit.geometry))
            renewGeometry(visit, IronElevatorTravelGroup.platformAt(player, player.getLocation()));
        Geometry geometry = visit.geometry;
        if (geometry.resolving != null) {
            if (!geometry.resolving.step()) return false;
            geometry.platform = geometry.resolving.result();
            geometry.measuredAt = clock.getAsLong();
            geometry.resolving = null;
            if (geometry.platform == null) {
                forget(player.getUniqueId());
                return true;
            }
            if (geometry.previous != null && !geometry.previous.equals(geometry.platform)) visit.navigation.query = null;
        }
        IronElevatorTravelGroup group = measure(player, visit, false);
        if (group == null) {
            visit.navigation.work = null;
            visit.navigation.action = Idle.INSTANCE;
            visit.navigation.availability = null;
            presentation.clear(player);
            return true;
        }
        refreshFloorChecks(player,visit,group);
        Integer targetY = visit.navigation.intent() == null ? null : visit.navigation.intent().targetY();
        if (visit.navigation.query == null || !Objects.equals(visit.navigation.query.targetY, targetY)
                || !visit.navigation.query.group.supportColumns().equals(group.supportColumns())) {
            visit.navigation.query = new Query(geometry, group, targetY, directory.cached(geometry.source));
        } else visit.navigation.query.update(group, visit.navigation.refresh);
        visit.navigation.refresh = false;
        prepareCatalog(player, visit);
        Intent intent = visit.navigation.intent();
        Query query = visit.navigation.query;
        int direction = intent == null ? 0 : intent.targetY() == null ? intent.direction() : 1;
        boolean complete = direction == 0 ? query.routes.step() : query.routes.step(direction);
        if ((complete || intent != null && query.routes.ready(direction)) && !query.routes.resultsSafe()) {
            query.routes.reevaluate(target -> group.landing(target) != null);
            return false;
        }
        if (targetY == null && query.routes.ready(1))
            visit.navigation.availability = new Availability(geometry, group, query.routes.up(), query.routes.down(), complete && query.routes.confirmed(1) && query.routes.confirmed(-1));
        if (intent != null && query.routes.ready(direction)) {
            IronElevatorShaft.Level target = direction > 0 ? query.routes.up() : query.routes.down();
            visit.navigation.action = Idle.INSTANCE;
            visit.navigation.work.priority(IronElevatorWorkQueue.HINT);
            if (target != null) {
                commit(player, visit, target, intent);
                visit.navigation.work = null;
                return true;
            }
            if (targetY != null) {
                visit.navigation.query = null;
                return false;
            }
        }
        if (!complete) return false;
        visit.navigation.work = null;
        Availability availability = visit.navigation.availability;
        if (availability == null || availability.up() == null && availability.down() == null) presentation.clearHint(player);
        else presentation.hint(player,availability.up() != null,availability.down() != null);
        if (visit.panel.catalog != null) requestPanelUpdate(player, visit);
        return true;
    }

    private void requestPanelUpdate(Player player, Visit visit) {
        if (visits.get(player.getUniqueId()) == visit) panelUpdates.add(player.getUniqueId());
    }

    private void publishPanel(Player player, Visit visit) {
        Geometry geometry = visit.geometry;
        if (!valid(player,visit) || geometry.platform == null || !fresh(geometry) || visit.navigation.trip() != null
                || visit.panel.catalog == null || visit.panel.catalog.floors().size() < 2) {
            presentation.clearPanel(player);
            return;
        }
        if (visit.panel.mount == null || !visit.panel.mount.completed()) {
            if (visit.panel.mount == null || !visit.panel.mount.active())
                visit.panel.mount = placement.prepare(geometry.platform,queue,_ -> {
                    if (visits.get(player.getUniqueId()) != visit || visit.geometry != geometry) return;
                    requestPanelUpdate(player,visit);
                });
            return;
        }
        var mount = visit.panel.mount;
        var plan = mount.result();
        if (plan == null) { presentation.clearPanel(player); return; }
        var group = measure(player,visit,false);
        if (group == null) { presentation.clearPanel(player); return; }
        refreshFloorChecks(player,visit,group);
        var checks = visit.panel.checks;
        Map<Integer,Boolean> states = checks == null || checks.catalog != visit.panel.catalog || checks.group == null
                || !checks.group.sameGeometry(group) ? Map.of() : checks.states;
        presentation.panel(player,new PanelSnapshot(visit,geometry.platform,visit.panel.catalog,plan,states,
                viewer -> visits.get(viewer.getUniqueId()) == visit && visit.geometry == geometry
                        && visit.panel.mount == mount && valid(viewer,visit) && plan.valid()));
    }

    void selectFloor(Player player, Object token, int height) {
        Visit visit = visits.get(player.getUniqueId());
        if (visit != token || visit.panel.catalog == null || visit.panel.catalog.floors().stream().noneMatch(floor -> floor.level().height() == height)) return;
        request(player,player.getLocation(),new Floor(height),() -> visits.get(player.getUniqueId()) == token);
    }

    void visibleFloors(Player player, Object token, IronElevatorCatalog.Result catalog, FloatingMenuPage page) {
        Visit visit = visits.get(player.getUniqueId());
        if (visit != token || visit.panel.catalog != catalog || page.itemCount() != catalog.floors().size()) return;
        var previous = visit.panel.checks;
        if (previous != null && previous.catalog == catalog && previous.page.equals(page)) return;
        if (previous != null) previous.cancel();
        visit.panel.checks = new FloorChecks(catalog,page);
        startFloorChecks(player,visit);
    }

    void pausePanel(Player player, Object token) {
        Visit visit = visits.get(player.getUniqueId());
        if (visit != token || visit.panel.checks == null) return;
        visit.panel.checks.cancel();
        visit.panel.checks = null;
    }

    private void refreshFloorChecks(Player player, Visit visit, IronElevatorTravelGroup group) {
        var checks = visit.panel.checks;
        if (checks == null || checks.catalog != visit.panel.catalog) return;
        if (checks.group != null && checks.group.sameGeometry(group) && !visit.navigation.refresh) {
            startFloorChecks(player,visit);
            return;
        }
        checks.cancel();
        checks.group = group;
        checks.states.clear();
        checks.index = checks.page.fromIndex();
        startFloorChecks(player,visit);
        requestPanelUpdate(player,visit);
    }

    private void startFloorChecks(Player player, Visit visit) {
        var checks = visit.panel.checks;
        if (checks == null || checks.task != null || checks.index >= checks.page.toIndex()) return;
        checks.task = queue.submit(IronElevatorWorkQueue.HINT,() -> {
            try {
                boolean complete = progressFloorChecks(player,visit,checks);
                if (complete) checks.task = null;
                return complete;
            } catch (RuntimeException error) {
                checks.task = null;
                throw error;
            }
        });
    }

    private boolean progressFloorChecks(Player player, Visit visit, FloorChecks checks) {
        if (visits.get(player.getUniqueId()) != visit || visit.panel.checks != checks || visit.panel.catalog != checks.catalog
                || !valid(player,visit) || visit.geometry.platform == null) return true;
        var group = measure(player,visit,false);
        if (group == null) return true;
        if (checks.group == null || !checks.group.sameGeometry(group)) {
            checks.group = group; checks.states.clear(); checks.index = checks.page.fromIndex();
        }
        var level = checks.catalog.floors().get(checks.index).level();
        checks.states.put(level.height(),level.height() != visit.geometry.platform.height() && group.landing(level) != null);
        requestPanelUpdate(player,visit);
        return ++checks.index >= checks.page.toIndex();
    }

    boolean requestJump(Player player, Location origin) {
        if (!acceptsInput(player)) return false;
        Visit visit = visits.get(player.getUniqueId());
        if (visit == null || visit.navigation.availability == null || visit.navigation.availability.up() == null) {
            observe(player, origin, false);
            return false;
        }
        observe(player, origin, false);
        visit = visits.get(player.getUniqueId());
        if (visit == null || visit.navigation.availability == null || visit.navigation.availability.geometry() != visit.geometry
                || !fresh(visit.geometry)) return false;
        IronElevatorTravelGroup group = measure(player, visit, false);
        if (group == null || group.landing(visit.navigation.availability.up()) == null) return false;
        return request(player, origin, Direction.UP, () -> true);
    }

    private boolean acceptsInput(Player player) {
        UUID id = player.getUniqueId();
        Long previous = lastTeleport.get(id);
        Visit visit = visits.get(id);
        return !closed && !executing.contains(id) && canUse(player) && (visit == null || visit.navigation.trip() == null)
                && (previous == null || clock.getAsLong() - previous >= COOLDOWN_NANOS);
    }

    boolean request(Player player, Location origin, Target target, BooleanSupplier allowed) {
        Objects.requireNonNull(target);
        Integer targetY = target instanceof Floor floor ? floor.height() : null;
        int direction = target == Direction.DOWN ? -1 : 1;
        if (!acceptsInput(player)) return false;
        observe(player, origin, false);
        Visit visit = visits.get(player.getUniqueId());
        if (visit == null || targetY != null && targetY == visit.geometry.source.getY()) return false;
        Availability availability = visit.navigation.availability;
        if (targetY == null && availability != null && availability.geometry() == visit.geometry
                && availability.complete() && fresh(visit.geometry)
                && (direction > 0 ? availability.up() : availability.down()) == null) {
            IronElevatorTravelGroup group = measure(player, visit, false);
            if (group != null && availability.group().sameGeometry(group)) return false;
        }
        visit.navigation.action = new Intent(target, clock.getAsLong(), allowed);
        start(player, visit);
        if (visit.navigation.work == null) {
            visit.navigation.action = Idle.INSTANCE;
            return false;
        }
        return true;
    }

    private boolean active(Intent intent) {
        return clock.getAsLong() - intent.createdAt() <= INPUT_NANOS && intent.allowed().getAsBoolean();
    }

    private void commit(Player player, Visit visit, IronElevatorShaft.Level target, Intent intent) {
        Trip trip = new Trip(visit.geometry,target,intent);
        visit.navigation.action = trip;
        defer(() -> {
            try { performTrip(player,visit,trip); }
            catch (RuntimeException error) {
                visit.navigation.action = Idle.INSTANCE;
                if (visits.get(player.getUniqueId()) == visit) forget(player.getUniqueId());
                throw error;
            }
        });
    }

    private void performTrip(Player player, Visit visit, Trip trip) {
        Intent intent = trip.intent();
        if (visit.navigation.trip() != trip) return;
        if (!valid(player, visit)) {
            if (visits.get(player.getUniqueId()) == visit) forget(player.getUniqueId());
            return;
        }
        if (!active(intent)) {
            visit.navigation.action = Idle.INSTANCE;
            observe(player, player.getLocation(), true);
            return;
        }
        if (visit.geometry != trip.geometry() || !fresh(visit.geometry)) {
            visit.navigation.action = intent;
            observe(player, player.getLocation(), true);
            return;
        }
        IronElevatorTravelGroup group = measure(player, visit, true);
        Location departure = player.getLocation();
        Location destination = group == null ? null : group.landing(trip.target());
        if (group != null && destination != null && group.memberIds().stream().noneMatch(executing::contains)) {
            executing.addAll(group.memberIds());
            try {
                if (group.teleport(destination,warning) && !closed) {
                    long completedAt = clock.getAsLong();
                    for (Player member : group.players()) lastTeleport.put(member.getUniqueId(),completedAt);
                    presentation.travelled(player,departure);
                }
            } finally {
                executing.removeAll(group.memberIds());
                visit.navigation.action = Idle.INSTANCE;
                for (Player member : group.players()) defer(() -> observe(member,member.getLocation(),true));
            }
        } else {
            visit.navigation.action = Idle.INSTANCE;
            observe(player,player.getLocation(),true);
        }
    }

    boolean pending(UUID id) {
        Visit visit = visits.get(id);
        return executing.contains(id) || visit != null && (visit.navigation.trip() != null || visit.navigation.intent() != null && active(visit.navigation.intent()));
    }

    private boolean valid(Player player, Visit visit) {
        if (visits.get(player.getUniqueId()) != visit || !canUse(player)) return false;
        Block standing = IronElevatorTravelGroup.platformAt(player, player.getLocation());
        return standing != null && visit.geometry.contains(standing);
    }

    void invalidate(UUID world) {
        directory.invalidateWorld(world);
        placement.invalidateWorld(world);
        invalidateVisits(world, _ -> true);
    }

    void invalidate(Block changed) {
        directory.invalidate(changed);
        placement.invalidate(changed);
        invalidateVisits(changed.getWorld().getUID(), visit -> visit.geometry.platform != null && IronElevatorPanelPlacement.touches(visit.geometry.platform,changed)
                || affected(visit, column -> Math.abs((long) column.blockX() - changed.getX()) <= 1
                        && Math.abs((long) column.blockZ() - changed.getZ()) <= 1));
    }

    void invalidateChunk(UUID world, int x, int z) {
        directory.invalidateChunk(world, x, z);
        placement.invalidateChunk(world,x,z);
        invalidateVisits(world, visit -> visit.geometry.platform != null && IronElevatorPanelPlacement.touchesChunk(visit.geometry.platform,world,x,z)
                || affected(visit, column -> IronElevatorShaft.nearChunk(column, x, z)));
    }

    private static boolean affected(Visit visit, Predicate<IronElevatorPlatform.Column> column) {
        return column.test(IronElevatorPlatform.Column.of(visit.geometry.source))
                || visit.geometry.platform != null && visit.geometry.platform.blocks().stream().map(IronElevatorPlatform.Column::of).anyMatch(column)
                || visit.geometry.previous != null && visit.geometry.previous.blocks().stream().map(IronElevatorPlatform.Column::of).anyMatch(column)
                || visit.geometry.resolving != null && visit.geometry.resolving.columns().stream().anyMatch(column)
                || visit.panel.catalog != null && visit.panel.catalog.shaft().columns().stream().anyMatch(column)
                || visit.navigation.query != null && visit.navigation.query.routes.anyColumn(column);
    }

    private void invalidateVisits(UUID world, Predicate<Visit> affected) {
        for (var entry : List.copyOf(visits.entrySet())) {
            if (!entry.getValue().geometry.source.getWorld().getUID().equals(world) || !affected.test(entry.getValue())) continue;
            UUID id = entry.getKey();
            forget(id);
            defer(() -> {
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
        var shutdown = new ShutdownSequence();
        for (UUID id : List.copyOf(visits.keySet())) shutdown.attempt("elevator visit " + id,() -> forget(id));
        shutdown.attempt("elevator catalog",directory::clear);
        shutdown.attempt("elevator placements",placement::clear);
        shutdown.attempt("elevator work",queue::clear);
        deferred.clear();
        panelUpdates.clear();
        executing.clear();
        lastTeleport.clear();
        shutdown.finish("iron elevator");
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
                    if (platform == null || IronElevatorLanding.on(IronElevatorShaft.Level.of(platform), origin,
                            IronElevatorLanding.DEFAULT_SIZE, _ -> false) == null) return true;
                    search = new IronElevatorRouteSearch(source, List.of(IronElevatorPlatform.Column.of(source)),
                            target -> IronElevatorLanding.on(target, origin,
                                    IronElevatorLanding.DEFAULT_SIZE, _ -> false) != null, null, directory.cached(source));
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

    private boolean fresh(Geometry geometry) {
        return geometry.platform != null && clock.getAsLong() - geometry.measuredAt < IronElevatorCatalog.CACHE_NANOS;
    }

    private static boolean canUse(Player player) {
        return player.isOnline() && !player.isDead() && !player.isFlying() && !player.isGliding()
                && !player.isSwimming() && player.getGameMode() != GameMode.SPECTATOR;
    }
}
