package org.encinet.mik.feature.world.elevator;

import com.sun.management.ThreadMXBean;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntFunction;

/** Reports hot-path observations without machine-dependent timing assertions. */
@Tag("benchmark")
class IronElevatorBenchmark {
    private static volatile Object sink;

    @Test
    void reportSharedShaftLookupAndNearestFloorSelection() {
        var world = new IronElevatorTestWorld();
        var floors = new IronElevatorCatalog(() -> 0);
        for (int shaft = 0; shaft < 64; shaft++) {
            for (int height : List.of(20, 40, 80)) {
                for (int x = 0; x < 2; x++)
                    for (int z = 0; z < 2; z++)
                        world.put(shaft * 32 + x, height, z, Material.IRON_BLOCK, Set.of(BlockFace.UP));
            }
            IronElevatorTestSupport.warm(floors, world.block(shaft * 32, 20, 0));
        }
        var source = world.block(63 * 32 + 1, 80, 1);
        report("elevator/64-shafts-warm", _ -> IronElevatorTestSupport.warm(floors, source));

        var tallWorld = new IronElevatorTestWorld();
        var tallFloors = new IronElevatorCatalog(() -> 0);
        for (int height = -50; height < 350; height += 2) tallWorld.iron(height);
        var middle = tallWorld.block(0, 150, 0);
        var landing = tallWorld.origin(152);
        IronElevatorTestSupport.warm(tallFloors, middle);
        report("elevator/200-floors-next", _ -> IronElevatorTestSupport.destination(tallFloors, middle, 1, null, _ -> landing));
    }

    @Test
    void reportRouteReevaluationAgainstRebuildingAWarmQuery() {
        var world = new IronElevatorTestWorld();
        for (int height : List.of(20, 40, 80)) world.iron(height);
        var source = world.block(0, 40, 0);
        var shaft = IronElevatorTestSupport.shaft(source);
        var supports = List.of(new IronElevatorPlatform.Column(0, 0));
        report("elevator/rebuild-warm-route", _ -> {
            var query = new IronElevatorRouteSearch(source, supports, _ -> true, null, shaft);
            while (!query.step()) { }
            return query.up();
        });
        var query = new IronElevatorRouteSearch(source, supports, _ -> true, null, shaft);
        while (!query.step()) { }
        report("elevator/reevaluate-known-route", _ -> {
            query.reevaluate(_ -> true);
            while (!query.step()) { }
            return query.up();
        });
    }

    private static void report(String label, IntFunction<Object> operation) {
        for (int index = 0; index < 5_000; index++) sink = operation.apply(index);
        var management = ManagementFactory.getThreadMXBean();
        ThreadMXBean allocation = management instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() ? bean : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled())
            allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        double[] times = new double[5];
        double[] bytes = new double[5];
        for (int round = 0; round < times.length; round++) {
            long beforeBytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            for (int index = 0; index < 10_000; index++) sink = operation.apply(index);
            times[round] = (System.nanoTime() - start) / 10_000.0;
            bytes[round] = allocation == null ? Double.NaN
                    : (allocation.getThreadAllocatedBytes(thread) - beforeBytes) / 10_000.0;
        }
        java.lang.ref.Reference.reachabilityFence(sink);
        Arrays.sort(times);
        Arrays.sort(bytes);
        System.out.printf(Locale.ROOT, "%s: median %.1f ns/op, %.1f bytes/op%n",
                label, times[2], bytes[2]);
    }
}
