package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class MenuRangeTest {
    @Test void paintingAdvancesWithoutRemeasuringOrMovingTheHitSurface() {
        double[] value = {10};
        var range = UiRange.live(0, 3600000, () -> value[0]).withLabels().format(number -> Component.text("%d:%02d".formatted((long) number / 60000, (long) number / 1000 % 60)));
        var scene = MenuScene.screen("range").content(UiTemplates.window("panel", Ui.slider(Component.text("Seek"), range, (_,_,_) -> {}).key("seek").build())).build();
        for (double typography : new double[]{.7, 1, 1.8}) {
            var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
            String path = element(geometry.frame(), "seek").path();
            var part = geometry.ranges().get(path);
            var before = paint(part, FloatingMenuDecoration.RangePart.Part.FILL, OptionalDouble.empty());
            var pose = geometry.poses().get(path);
            var size = geometry.sizes().get(path);
            value[0] = 1800000;
            var after = paint(part, FloatingMenuDecoration.RangePart.Part.FILL, OptionalDouble.empty());
            assertEquals(part.geometry().trackWidth() / 2, after.width(), 1e-8);
            assertTrue(after.width() > before.width());
            assertEquals(pose, geometry.poses().get(path));
            assertEquals(size, geometry.sizes().get(path));
            assertEquals(6, geometry.generatedDecorations().values().stream().filter(d -> d.content() instanceof FloatingMenuDecoration.RangePart).count());
            value[0] = 10;
        }
    }

    @Test void pointerInputExcludesTimeLabelsAndStillReachesBothTrackEndpoints() {
        var range = UiRange.fixed(0, 100, 20).withLabels();
        var scene = MenuScene.screen("range").content(Ui.slider(Component.text("Value"), range, (_,_,_) -> {}).build()).build();
        var geometry = SceneGeometry.measure(scene, 1.8, FloatingMenuFieldOfView.DEFAULT, 1).ranges().values().iterator().next().geometry();
        double left = (geometry.leading() + geometry.gap()) / geometry.width();
        double right = 1 - (geometry.trailing() + geometry.gap()) / geometry.width();
        assertEquals(0, geometry.pointer(left).orElseThrow(), 1e-8);
        assertEquals(1, geometry.pointer(right).orElseThrow(), 1e-8);
        assertEquals(.5, geometry.pointer((left + right) / 2).orElseThrow(), 1e-8);
        assertTrue(geometry.pointer(left / 2).isEmpty());
        assertTrue(geometry.pointer((1 + right) / 2).isEmpty());
        assertTrue(geometry.pointer(-1).isEmpty()); assertTrue(geometry.pointer(2).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> geometry.pointer(Double.NaN));
    }

    @Test void thumbFillAndPreviewStayInsideTheTrackIncludingItsEndpoints() {
        for (double value : new double[]{0, 50, 100}) {
            var range = UiRange.fixed(0, 100, value);
            var geometry = new UiRange.Geometry(4, .5, 0, 0, 0, 1);
            var part = new FloatingMenuDecoration.RangePart("range", range, geometry, FloatingMenuDecoration.RangePart.Part.TRACK, 1);
            for (var kind : new FloatingMenuDecoration.RangePart.Part[]{FloatingMenuDecoration.RangePart.Part.FILL,
                    FloatingMenuDecoration.RangePart.Part.THUMB, FloatingMenuDecoration.RangePart.Part.PREVIEW}) {
                var paint = paint(part, kind, OptionalDouble.of(value / 100));
                if (paint.alpha() == 0) continue;
                assertTrue(paint.right() - paint.width() / 2 >= geometry.trackStart() - 1e-8);
                assertTrue(paint.right() + paint.width() / 2 <= geometry.trackStart() + geometry.trackWidth() + 1e-8);
                assertTrue(paint.height() <= geometry.height());
            }
            assertEquals(0, MenuRangeRenderer.paint(new FloatingMenuDecoration.RangePart("range", range, geometry,
                    FloatingMenuDecoration.RangePart.Part.THUMB, 1), false, OptionalDouble.empty(), UiPalette.DEFAULT).alpha());
        }
    }

    @Test void clickAndWheelUseTheSameBoundedLiveValueAndReadOnlyIndicatorsHaveNoActions() {
        double[] value = {95};
        var result = new AtomicReference<Double>();
        var range = UiRange.live(0, 100, () -> value[0]).step(10);
        var slider = Ui.slider(Component.text("Volume"), range, (_,_,n) -> result.set(n)).build();
        var handle = (FloatingMenuHandle) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FloatingMenuHandle.class},
                (_, method, _) -> method.getName().equals("horizontalFraction") ? OptionalDouble.of(.25) : null);
        slider.input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null, handle, FloatingMenuInteraction.PRIMARY);
        assertEquals(25, result.get());
        slider.input().actions().get(FloatingMenuInteraction.SCROLL_UP).execute(null, handle, FloatingMenuInteraction.SCROLL_UP);
        assertEquals(100, result.get());
        value[0] = 0;
        slider.input().actions().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, handle, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(100, result.get(), "No callback when already at the boundary");
        assertFalse(Ui.progress(Component.text("Progress"), range).build().input().interactive());
        assertThrows(IllegalArgumentException.class, () -> range.at(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> UiRange.fixed(1, 1, 1));
    }

    @Test void rangesInsideCompanionsUseTheScopedGeometryAndOrdinaryEntityBudget() {
        var scene = MenuScene.screen("range").content(Ui.button(Component.text("Volume")).key("volume").build())
                .popover(MenuPopover.attached(UiKey.named("volume"), Component.text("Details"), Ui.slider(Component.text("Value"), UiRange.fixed(0, 100, 50), (_,_,_) -> {}).key("value").build(), Component.text("Close"))).build();
        var geometry = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(1, geometry.ranges().size());
        assertTrue(geometry.ranges().keySet().stream().allMatch(MenuPopover::owns));
        assertEquals(4, geometry.generatedDecorations().values().stream().filter(d -> d.content() instanceof FloatingMenuDecoration.RangePart).count());
    }

    private static MenuRangeRenderer.Paint paint(FloatingMenuDecoration.RangePart original, FloatingMenuDecoration.RangePart.Part kind, OptionalDouble pointer) {
        return MenuRangeRenderer.paint(new FloatingMenuDecoration.RangePart(original.source(), original.range(), original.geometry(), kind, original.alpha()),
                true, pointer, UiPalette.DEFAULT);
    }
}
