package org.encinet.mik.shared.menu.runtime;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("benchmark")
class MenuWindowedCollectionBenchmark {
    private volatile double observed;

    @Test void windowMeasurementCostDoesNotScaleWithCollectionLength() {
        var created = new AtomicInteger();
        benchmark("Menu window",catalogue(256,created),catalogue(Integer.MAX_VALUE,created),created);
    }

    @Test void tableMeasurementCostDoesNotScaleWithCollectionLength() {
        var created = new AtomicInteger();
        benchmark("Menu table window",table(256,created),table(Integer.MAX_VALUE,created),created);
    }

    private void benchmark(String name, MenuScene small, MenuScene large, AtomicInteger created) {
        var first = measure(small);
        int firstCount = created.getAndSet(0);
        var larger = measure(large);
        assertEquals(firstCount, created.getAndSet(0));
        assertEquals(first.frame().pagination().getFirst().page().pageSize(), larger.frame().pagination().getFirst().page().pageSize());
        assertEquals(first.frame().size(), larger.frame().size());
        assertTrue(firstCount <= UiWindowedList.MAX_VISIBLE_ITEMS);
        for (int index = 0; index < 80; index++) { measure(small); measure(large); }
        long[] bounded = new long[240], huge = new long[240];
        for (int index = 0; index < bounded.length; index++) {
            if (index % 2 == 0) { bounded[index] = elapsed(small); huge[index] = elapsed(large); }
            else { huge[index] = elapsed(large); bounded[index] = elapsed(small); }
        }
        java.lang.ref.Reference.reachabilityFence(observed);
        Arrays.sort(bounded); Arrays.sort(huge);
        System.out.printf(java.util.Locale.ROOT,
                "%s (%d row constructions): 256 items median %.3f ms / p95 %.3f ms; 2147483647 items median %.3f ms / p95 %.3f ms%n",
                name,firstCount,bounded[120] / 1e6,bounded[228] / 1e6,huge[120] / 1e6,huge[228] / 1e6);
    }

    private long elapsed(MenuScene scene) {
        long start = System.nanoTime();
        var frame = measure(scene);
        observed = frame.frame().size().height() + frame.frame().elements().size();
        return System.nanoTime() - start;
    }

    private static ComponentSceneLayout.Result measure(MenuScene scene) {
        return ComponentSceneLayout.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
    }

    private static MenuScene catalogue(int size, AtomicInteger created) {
        var template = Ui.button(Component.text("M")).singleLine().primary((_, _) -> {}).build();
        var list = Ui.windowedList(size, template, index -> {
            created.incrementAndGet();
            return Ui.button(Component.text("Object " + index)).key(UiKey.item(index)).singleLine()
                    .primary((_, _) -> {}).build();
        }).header(Ui.heading(Component.text("Catalogue")).build()).build();
        return MenuScene.screen("window-benchmark").viewport(new UiSize(420, 560))
                .content(UiTemplates.window("results", list)).build();
    }

    private static MenuScene table(int size, AtomicInteger created) {
        var table = Ui.<Integer>table()
                .column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("Object")).singleLine().build(),UiTrack.content(),
                        id -> Ui.button(Component.text("Object " + id)).key("object:" + id).singleLine().primary((_,_) -> {}).build()))
                .column(new UiTable.Column<>(UiKey.named("value"),Ui.heading(Component.text("Value")).singleLine().build(),UiTrack.content(),
                        id -> Ui.text(Component.text(id)).singleLine().build()))
                .windowed(size,-1,index -> { created.incrementAndGet(); return index; },id -> UiKey.named("row:" + id));
        return MenuScene.screen("table-window-benchmark").viewport(new UiSize(420,560))
                .content(UiTemplates.window("results",table)).build();
    }
}
