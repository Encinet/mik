package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bounded drivers for the actual production steps. Synchronous convenience belongs only in tests. */
final class IronElevatorTestSupport {
    private IronElevatorTestSupport() { }
    static void finish(BooleanSupplier step) {
        for (int steps = 0; steps < 2_000_000; steps++) if (step.getAsBoolean()) return;
        throw new AssertionError("Elevator work did not finish within its bounded fixture");
    }
    static IronElevatorPlatform platform(Block source) {
        var search = new IronElevatorPlatform.Search(source); finish(search::step); return search.result();
    }
    static IronElevatorShaft shaft(Block source) {
        var search = new IronElevatorShaft.Scan(source); finish(search::step); return search.result();
    }
    static IronElevatorShaft.Level level(Block source) {
        var platform = platform(source); return platform == null ? null : IronElevatorShaft.Level.of(platform);
    }
    static IronElevatorPlatform warm(IronElevatorCatalog catalog, Block source) {
        var cached = catalog.cached(source);
        if (cached == null) { cached = shaft(source); catalog.publish(cached); }
        return cached.source(source);
    }
    static List<IronElevatorCatalog.Floor> floors(IronElevatorCatalog catalog, Block source) {
        var cached = catalog.cached(source);
        var platform = cached == null ? platform(source) : cached.source(source);
        if (platform == null) return List.of();
        var queue = new IronElevatorWorkQueue(() -> 0,error -> { throw error; });
        var results = new ArrayList<IronElevatorCatalog.Result>();
        catalog.prepare(source,platform,queue,results::add);
        finish(() -> { queue.tick(); return queue.empty(); });
        return results.isEmpty() ? List.of() : results.getFirst().floors();
    }
    static Location landing(Block source, Location origin, IronElevatorLanding.Size size, Predicate<BoundingBox> collision) {
        var level = level(source); return level == null ? null : IronElevatorLanding.on(level,origin,size,collision);
    }
    static Location route(Block source, int direction, Location origin, IronElevatorLanding.Size size, Predicate<BoundingBox> collision) {
        var query = new IronElevatorRouteSearch(source,List.of(IronElevatorPlatform.Column.of(source)),
                level -> IronElevatorLanding.on(level,origin,size,collision) != null,null,null);
        finish(query::step);
        var level = direction > 0 ? query.up() : query.down();
        return level == null ? null : IronElevatorLanding.on(level,origin,size,collision);
    }
    static Location destination(IronElevatorCatalog catalog, Block source, int direction, Integer target,
                                Location origin, IronElevatorLanding.Size size, Predicate<BoundingBox> collision) {
        return destination(catalog,source,direction,target,origin,size,collision,_ -> true);
    }
    static Location destination(IronElevatorCatalog catalog, Block source, int direction, Integer target,
                                Location origin, IronElevatorLanding.Size size, Predicate<BoundingBox> collision,
                                Predicate<IronElevatorShaft.Level> groupFits) {
        return destination(catalog,source,direction,target,level -> groupFits.test(level)
                ? IronElevatorLanding.on(level,origin,size,collision) : null);
    }
    static Location destination(IronElevatorCatalog catalog, Block source, int direction, Integer target,
                                Function<IronElevatorShaft.Level,Location> landing) {
        warm(catalog,source);
        var query = new IronElevatorRouteSearch(source,List.of(IronElevatorPlatform.Column.of(source)),
                level -> landing.apply(level) != null,target,catalog.cached(source));
        finish(query::step);
        var level = target != null || direction > 0 ? query.up() : query.down();
        return level == null ? null : landing.apply(level);
    }
    /** Independent complete-topology oracle for the incremental route search. */
    static Location expectedDestination(IronElevatorShaft shaft, int sourceY, int direction, Integer target,
                                        Function<IronElevatorShaft.Level,Location> landing) {
        if (target != null) return target == sourceY || shaft.at(target) == null ? null : landing.apply(shaft.at(target));
        var levels = shaft.levels();
        for (int index = direction > 0 ? 0 : levels.size() - 1; index >= 0 && index < levels.size(); index += direction) {
            var level = levels.get(index);
            if (direction * (level.height() - sourceY) <= 0) continue;
            var result = landing.apply(level); if (result != null) return result;
        }
        return null;
    }
    static IronElevatorGround.Estimate ground(Block source, List<Integer> heights) {
        var measurement = new IronElevatorGround.Measurement(source,heights); finish(measurement::step); return measurement.result();
    }
    static int groundHeight(Block source, int fallback) { return ground(source,List.of(fallback)).height(); }
    static IronElevatorPanelPlacement.Plan placement(IronElevatorPanelPlacement resolver, IronElevatorPlatform source) {
        var queue = new IronElevatorWorkQueue(() -> 0,error -> { throw error; });
        var result = new ArrayList<IronElevatorPanelPlacement.Plan>();
        resolver.prepare(source,queue,result::add); finish(() -> { queue.tick(); return queue.empty(); });
        return result.isEmpty() ? null : result.getFirst();
    }
}
