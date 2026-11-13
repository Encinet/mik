package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiSplitLayoutTest {
    @Test void failingBothDirectionsPreservesTheOriginalOverflowForDiagnosis() {
        for (var preferred : UiLayouts.Axis.values()) {
            var scene = MenuScene.screen("split").viewport(new UiSize(300,200)).content(UiTemplates.split(
                    panel("navigation",new UiSize(180,140)),panel("reading",new UiSize(240,160)),preferred)).build();
            var error = assertThrows(UiOverflowException.class,() -> SceneGeometryTestSupport.naturalFrame(scene,1));
            var original = assertInstanceOf(UiOverflowException.class,error.getCause());
            assertEquals(1,original.getSuppressed().length);
            assertInstanceOf(UiOverflowException.class,original.getSuppressed()[0]);
            assertNotEquals(original.getMessage(),original.getSuppressed()[0].getMessage());
        }
    }

    @Test void aPreferredDirectionUsesAvailableSpaceAndReflowsTheSameControlsWhenItCannotFit() {
        for (var preferred : UiLayouts.Axis.values()) for (var viewport : new UiSize[]{new UiSize(700,500),new UiSize(700,200),new UiSize(300,500)}) {
            var navigation = panel("navigation",new UiSize(180,140));
            var content = panel("reading",new UiSize(240,160));
            var scene = MenuScene.screen("split").viewport(viewport).content(UiTemplates.split(navigation,content,preferred)).build();
            var frame = SceneGeometryTestSupport.measure(scene,1);
            var rail = frame.panels().stream().filter(panel -> panel.path().endsWith("/sidebar")).findFirst().orElseThrow();
            var main = frame.panels().stream().filter(panel -> panel.path().endsWith("/main")).findFirst().orElseThrow();
            boolean vertical = viewport.width() == 300 || preferred == UiLayouts.Axis.VERTICAL && viewport.height() > 200;
            assertEquals(vertical ? UiSurfaceFacing.VERTICAL : UiSurfaceFacing.HORIZONTAL,rail.facing());
            if (vertical) {
                assertTrue(main.y() >= rail.y() + rail.size().height());
                assertEquals(240,frame.size().width(),1e-6);
            } else assertTrue(main.x() >= rail.x() + rail.size().width());
            assertEquals(2,frame.elements().size());
            assertTrue(frame.readingRegion().path().startsWith("root/main"));
            assertDisjoint(frame);
        }
    }

    @Test void aStackedDirectoryLeavesOnlyTheRemainingHeightForThePaginatedReader() {
        var items = java.util.stream.IntStream.range(0,12).mapToObj(index ->
                Ui.text(Component.text("Option " + index)).key("option:" + index).size(new UiSize(160,30)).build()).toList();
        var reader = Ui.pagedList().key("options").items(items).build();
        var scene = MenuScene.screen("options").viewport(new UiSize(320,260)).content(UiTemplates.split(
                panel("navigation",new UiSize(160,80)),reader,UiLayouts.Axis.VERTICAL)).build();
        var frame = SceneGeometryTestSupport.measure(scene,1);
        assertTrue(frame.pagination().getFirst().page().count() > 1);
        for (var element : frame.elements()) if (element.node().key().value().startsWith("option:")) {
            assertTrue(element.y() >= 92);
            assertTrue(element.y() + element.size().height() <= 260);
        }
        assertDisjoint(frame);
    }

    private static UiSurface panel(String key, UiSize size) {
        return Ui.surface(Ui.button(Component.text(key)).key(key).primary((_,_) -> {}).build())
                .key(key).size(size).facing(UiSurfaceFacing.VIEWER).build();
    }
}
