package org.encinet.mik.shared.menu.runtime.layout;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MenuTextMeasurementCacheTest {
    @Test void documentFooterAndFontHeightChangesReuseSourceLinesButChangedWidthOrFormattingRemeasures() {
        var cache = new MenuTextMeasurementCache();
        var source = Component.text("Aé界👩‍🔬".repeat(200));
        var measured = new AtomicInteger();
        java.util.function.BiFunction<Component,Integer,UiTextMetrics.ReadingLines> read = (text, width) ->
                cache.documentLines(text, width, () -> { measured.incrementAndGet(); return UiTextMetrics.readingLines(text, width); });
        var lines = read.apply(source, 240);
        assertNotEquals(lines.pageEnds(4), lines.pageEnds(8));
        assertSame(lines, read.apply(source, 240));
        assertEquals(1, measured.get());
        read.apply(source, 120);
        read.apply(source.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD), 240);
        read.apply(source.font(net.kyori.adventure.key.Key.key("test", "font")), 240);
        assertEquals(4, measured.get());
        cache.clear();
        assertEquals(0, cache.stats().entries());
        read.apply(source, 240);
        assertEquals(5, measured.get());
    }

    @Test void documentSourcesAndLeafMeasurementsShareOneWeightBudgetAndNeverRetainInteractivePayloads() {
        var cache = new MenuTextMeasurementCache();
        for (int index = 0; index < 6; index++) {
            var source = Component.text("x".repeat(60000) + index);
            cache.documentLines(source, 320, () -> UiTextMetrics.readingLines(source, 320));
            cache.measure(Ui.text(Component.text("Label " + index)).build(), UiConstraints.bounded(100,100), 1, 0,
                    () -> new UiCompiler.ElementMeasurement(new UiSize(40,20),100));
            assertTrue(cache.stats().weight() <= MenuTextMeasurementCache.WEIGHT_BUDGET);
        }
        int count = cache.stats().entries();
        var interactive = Component.text("Source").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/menu"));
        cache.documentLines(interactive, 320, () -> UiTextMetrics.readingLines(interactive, 320));
        assertEquals(count, cache.stats().entries());
        assertThrows(IllegalStateException.class, () -> cache.documentLines(Component.text("Failed"), 320,
                () -> { throw new IllegalStateException("No result"); }));
        assertEquals(count, cache.stats().entries());
    }

    @Test void largeTextAndInteractiveComponentPayloadsCannotAccumulateInTheViewCache() {
        var cache = new MenuTextMeasurementCache();
        var limits = UiConstraints.bounded(100, 100);
        java.util.function.Supplier<UiCompiler.ElementMeasurement> measure = () -> new UiCompiler.ElementMeasurement(new UiSize(30, 20), 160);
        for (int index = 0; index < 100; index++) cache.measure(Ui.text(Component.text("x".repeat(2000) + index)).build(),
                limits, 1, 0, measure);
        assertTrue(cache.stats().weight() <= MenuTextMeasurementCache.WEIGHT_BUDGET);
        int before = cache.stats().entries();
        cache.measure(Ui.text(Component.text("Too large".repeat(1000))).build(), limits, 1, 0, measure);
        cache.measure(Ui.text(Component.text("Interactive").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/menu"))).build(),
                limits, 1, 0, measure);
        assertEquals(before, cache.stats().entries());
        cache.clear();
        assertEquals(0, cache.stats().weight());
    }

    @Test void distinctQueriesHaveABoundedFootprintAndEvictedEntriesAreMeasuredAgain() {
        var cache = new MenuTextMeasurementCache();
        var measurements = new AtomicInteger();
        var constraints = UiConstraints.bounded(100, 100);
        java.util.function.IntConsumer query = index -> cache.measure(Ui.text(Component.text("Result " + index)).build(),
                constraints, 1, 0, () -> {
                    measurements.incrementAndGet();
                    return new UiCompiler.ElementMeasurement(new UiSize(30, 20), 160);
                });
        for (int index = 0; index < MenuTextMeasurementCache.CAPACITY + 20; index++) query.accept(index);
        assertEquals(MenuTextMeasurementCache.CAPACITY, cache.stats().entries());
        int before = measurements.get();
        query.accept(MenuTextMeasurementCache.CAPACITY + 19);
        assertEquals(before, measurements.get());
        query.accept(0);
        assertEquals(before + 1, measurements.get());
    }

    @Test void failedMeasurementsAreNotCachedAndDoNotReplaceWorkingValues() {
        var cache = new MenuTextMeasurementCache();
        var node = Ui.text(Component.text("Complete text")).noWrap().build();
        var small = UiConstraints.bounded(1, 1);
        for (int attempt = 0; attempt < 2; attempt++) assertThrows(UiOverflowException.class,
                () -> cache.measure(node, small, 1, 0, () -> { throw new UiOverflowException("Does not fit"); }));
        assertEquals(0, cache.stats().entries());
    }
}
