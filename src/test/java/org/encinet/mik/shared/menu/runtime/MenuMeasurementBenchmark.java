package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("benchmark")
class MenuMeasurementBenchmark {
    private volatile double observed;

    @Test void repeatedCatalogueRedrawsCompareFreshActionsWithRetainedTextMeasurements() {
        List<MenuScene> versions = IntStream.range(0, 32).mapToObj(this::catalogue).toList();
        var cache = new MenuTextMeasurementCache();
        var expected = measure(versions.getFirst(), null);
        var retained = measure(versions.getFirst(), cache);
        assertEquals(expected.poses(), retained.poses());
        assertEquals(expected.sizes(), retained.sizes());
        for (int iteration = 0; iteration < 80; iteration++) {
            measure(versions.get(iteration % versions.size()), null);
            measure(versions.get(iteration % versions.size()), cache);
        }
        long[] plain = new long[240], cached = new long[240];
        for (int iteration = 0; iteration < plain.length; iteration++) {
            var scene = versions.get(iteration % versions.size());
            if (iteration % 2 == 0) {
                plain[iteration] = elapsed(scene, null);
                cached[iteration] = elapsed(scene, cache);
            } else {
                cached[iteration] = elapsed(scene, cache);
                plain[iteration] = elapsed(scene, null);
            }
        }
        java.lang.ref.Reference.reachabilityFence(observed);
        Arrays.sort(plain); Arrays.sort(cached);
        System.out.printf(java.util.Locale.ROOT,
                "Menu catalogue redraw (120 entries): uncached median %.3f ms / p95 %.3f ms; cached median %.3f ms / p95 %.3f ms; %s%n",
                plain[plain.length / 2] / 1e6, plain[(int) (plain.length * .95)] / 1e6,
                cached[cached.length / 2] / 1e6, cached[(int) (cached.length * .95)] / 1e6, cache.stats());
    }

    private long elapsed(MenuScene scene, MenuTextMeasurementCache cache) {
        long start = System.nanoTime();
        var result = measure(scene, cache);
        observed = result.frame().size().height() + result.frame().elements().size();
        return System.nanoTime() - start;
    }

    private ComponentSceneLayout.Result measure(MenuScene scene, MenuTextMeasurementCache cache) {
        return ComponentSceneLayout.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1, UiPalette.DEFAULT, cache);
    }

    private MenuScene catalogue(int generation) {
        var items = IntStream.range(0, 120).mapToObj(index -> (UiNode) Ui.button(Component.text(
                "Track " + index + " · Artist · A title long enough to exercise fitting and styled text measurement"))
                .key(UiKey.item(index)).singleLine().textWidth(4096)
                .primary((_, _) -> observed = generation + index).build()).toList();
        var list = Ui.pagedList().key("catalogue").header(Ui.heading(Component.text("Catalogue")).key("heading").build())
                .items(items).page(0, _ -> {}).build();
        return MenuScene.screen("measurement-benchmark").viewport(new UiSize(420, 560))
                .content(UiTemplates.window("results", list)).build();
    }
}
