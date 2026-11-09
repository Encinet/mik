package org.encinet.mik.shared.menu.runtime;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Separately tagged: source parsing and off-screen paragraph fitting are included. */
@Tag("benchmark")
class MenuDocumentBenchmark {
    private volatile double observed;

    @Test void longDocumentMeasurementRemainsBoundedAndRetainedMeasurementsPreserveTheGeometry() {
        for (int points : new int[]{4096, 65536}) {
            var scene = document(points);
            var cache = new MenuTextMeasurementCache();
            var plain = measure(scene, null);
            var retained = measure(scene, cache);
            assertEquals(plain.sizes(), retained.sizes());
            assertEquals(plain.labels(), retained.labels());
            assertTrue(plain.frame().pagination().getFirst().page().count() > 1);
            assertTrue(plain.frame().elements().size() < 16, "off-screen text never allocates published entities");
            for (int index = 0; index < 12; index++) { measure(scene, null); measure(scene, cache); }
            long[] fresh = new long[40], cached = new long[40];
            for (int index = 0; index < fresh.length; index++) {
                if (index % 2 == 0) { fresh[index] = elapsed(scene, null); cached[index] = elapsed(scene, cache); }
                else { cached[index] = elapsed(scene, cache); fresh[index] = elapsed(scene, null); }
            }
            java.lang.ref.Reference.reachabilityFence(observed);
            Arrays.sort(fresh); Arrays.sort(cached);
            System.out.printf(java.util.Locale.ROOT,
                    "Menu document (%d source points, %d pages): fresh median %.3f ms / p95 %.3f ms; retained median %.3f ms / p95 %.3f ms; %s%n",
                    points, plain.frame().pagination().getFirst().page().count(), fresh[20] / 1e6, fresh[38] / 1e6,
                    cached[20] / 1e6, cached[38] / 1e6, cache.stats());
            cache.clear();
            assertEquals(0, cache.stats().entries());
        }
    }

    private long elapsed(MenuScene scene, MenuTextMeasurementCache cache) {
        long start = System.nanoTime();
        var geometry = measure(scene, cache);
        observed = geometry.frame().size().height() + geometry.frame().elements().size();
        return System.nanoTime() - start;
    }

    private static SceneGeometry measure(MenuScene scene, MenuTextMeasurementCache cache) {
        return SceneGeometry.measureBase(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1, UiPalette.DEFAULT, cache);
    }

    private static MenuScene document(int points) {
        String content = "A界é🌿".repeat((points + 4) / 5);
        content = content.substring(0, content.offsetByCodePoints(0, points));
        return MenuScene.screen("document-benchmark").viewport(new UiSize(640,240)).content(UiTemplates.window("reading", Ui.document().key("document")
                .blocks(List.of(Ui.text(Component.text(content)).key("body").textWidth(4096).build()))
                .position(UiReadingPosition.START, _ -> {}).build())).build();
    }
}
