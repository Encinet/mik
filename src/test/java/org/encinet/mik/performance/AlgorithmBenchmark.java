package org.encinet.mik.performance;

import com.sun.management.ThreadMXBean;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.feature.world.space.SpaceAperture;
import org.encinet.mik.feature.world.space.SpaceFrame;
import org.encinet.mik.feature.world.space.SpaceLink;
import org.encinet.mik.feature.world.space.SpaceLinkEntrances;
import org.encinet.mik.feature.world.space.SpaceNetwork;
import org.encinet.mik.feature.world.space.SpaceSurface;
import org.encinet.mik.feature.world.space.SpaceVector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntFunction;

/** Repeatable microbenchmarks; reports observations without machine-dependent timing assertions. */
@Tag("benchmark")
class AlgorithmBenchmark {
    private static volatile Object sink;

    @Test
    void reportWindowQueriesAndSpatialMisses() {
        var pulses = new ArrayList<RhythmPulse>();
        for (int index = 0; index < 100_000; index++)
            pulses.add(new RhythmPulse(index * 100L, 0.8));
        var timeline = new RhythmTimeline("performance");
        timeline.publish(RhythmSource.NBS_NOTES, pulses, 10_000_000, 0, 0);
        report("timeline/100k-loop-window", 3_000, index -> {
            long from = 19_000_000L + index % 1_000;
            return timeline.between(from, from + 250);
        });

        var links = new ArrayList<SpaceLink>();
        for (int index = 0; index < 256; index++) {
            var source = new SpaceSurface("source" + index, "world",
                    SpaceFrame.oriented(new SpaceVector(index * 16, 64, 0), 0, 0, 0),
                    new SpaceAperture(4, 4));
            var destination = new SpaceSurface("destination" + index, "world",
                    SpaceFrame.oriented(new SpaceVector(index * 16, 64, 0), 90, 0, 0),
                    new SpaceAperture(4, 4));
            links.add(new SpaceLink("link" + index, source, destination, SpaceLinkEntrances.FIRST));
        }
        var network = SpaceNetwork.of(links);
        var from = new SpaceVector(-100, 64, -2);
        var look = new SpaceVector(0, 0, 1);
        report("space/256-links-miss", 10_000, index -> network.trace("world", from,
                new SpaceVector(-100 + index % 10, 64, -1), look, look));
    }

    private static void report(String label, int iterations, IntFunction<Object> operation) {
        for (int index = 0; index < 2_000; index++) sink = operation.apply(index);
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
            for (int index = 0; index < iterations; index++) sink = operation.apply(index);
            times[round] = (System.nanoTime() - start) / (double) iterations;
            bytes[round] = allocation == null ? Double.NaN
                    : (allocation.getThreadAllocatedBytes(thread) - beforeBytes) / (double) iterations;
        }
        Arrays.sort(times);
        Arrays.sort(bytes);
        System.out.printf(Locale.ROOT, "%s: median %.1f ns/op, %.1f bytes/op%n",
                label, times[2], bytes[2]);
    }
}
