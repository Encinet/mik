package org.encinet.mik.module.plot;

import com.sun.management.ThreadMXBean;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotHotPathPerformanceTest {
    private static volatile long resultSink;
    private static volatile Object objectSink;

    @Test
    void reportSteadyStateHotPathTimeAndAllocationsWithoutTimingAssertions() throws Exception {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        var registry = new PlotRegistry(new PlotRepository(Path.of("unused-performance.db")).plan());
        var edits = new PlotEdits(registry);
        Plot first = null;
        for (int index = 0; index < 64; index++) {
            int offset = index * 32;
            Plot parent = edits.create(owner, "Parent " + index, world,
                    shape(world, offset, offset + 7, 64, 71));
            edits.flag(parent, parent.owner(), false, "member.place", true);
            parent = registry.byId(parent.id());
            if (first == null) first = parent;
            for (int child = 0; child < 4; child++) {
                int horizontal = offset + child % 2 * 4;
                int forward = offset + child / 2 * 4;
                edits.createSubPlot(parent, owner, false, owner, "Child " + child,
                        PlotSelectionShape.of(new PlotPosition(world, horizontal, 64, forward),
                                new PlotPosition(world, horizontal + 3, 67, forward + 3)));
            }
        }
        Plot standalone = edits.create(owner, "Standalone", world, shape(world, 4096, 4103, 64, 71));
        edits.flag(standalone, standalone.owner(), false, "member.place", true);
        UUID parentId = first.id();
        assertEquals(321, registry.all().size());
        assertEquals(4, registry.childrenOf(parentId).size());
        report("permission/root", 100_000,
                () -> registry.allowed(world, 4097, 65, 4097, viewer, true, false, "place") ? 1 : 0);
        report("permission/child", 100_000,
                () -> registry.allowed(world, 1, 65, 1, viewer, true, false, "place") ? 1 : 0);
        report("catalog/all", 20_000, () -> registry.all().size());
        report("catalog/children", 20_000, () -> registry.childrenOf(parentId).size());
        var setting = new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR);
        var applied = new PlotAtmosphereController.Applied(setting, null, null);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Unchanged atmosphere must not call " + method.getName());
                });
        report("atmosphere/unchanged", 100_000, () -> {
            objectSink = PlotAtmosphereController.apply(player, applied, setting, false);
            return 1;
        });
    }

    private static PlotSelectionShape shape(UUID world, int minimum, int maximum, int bottom, int top) {
        return PlotSelectionShape.of(new PlotPosition(world, minimum, bottom, minimum),
                new PlotPosition(world, maximum, top, maximum));
    }

    private static void report(String label, int count, LongSupplier query) {
        for (int warmup = 0; warmup < 10_000; warmup++) resultSink += query.getAsLong();
        var management = ManagementFactory.getThreadMXBean();
        ThreadMXBean allocation = management instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
                ? bean : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled())
            allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long beforeBytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        long total = 0;
        for (int iteration = 0; iteration < count; iteration++) total += query.getAsLong();
        long elapsed = System.nanoTime() - started;
        long bytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread) - beforeBytes;
        resultSink = total;
        String allocated = allocation == null ? "allocation unavailable"
                : String.format(java.util.Locale.ROOT, "%.1f bytes/op", bytes / (double) count);
        System.out.printf(java.util.Locale.ROOT, "%s: %.1f ns/op, %s%n", label,
                elapsed / (double) count, allocated);
    }
}
