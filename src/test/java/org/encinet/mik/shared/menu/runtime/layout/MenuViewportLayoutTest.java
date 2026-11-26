package org.encinet.mik.shared.menu.runtime.layout;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuViewportLayoutTest {
    @Test void everyPreferenceAllocatesOneFiniteViewportBeforeLayoutWithoutInflatingWorldDimensions() {
        var declared = new UiSize(1440, 2048);
        for (var size : FloatingMenuScale.PRESETS) for (var fov : FloatingMenuFieldOfView.PRESETS) {
            var budget = MenuViewportLayout.viewport(declared, fov, size.factor());
            double units = MenuViewportLayout.scale(size.factor()) * UiSceneContent.WORLD_PER_UI;
            assertTrue(budget.width() > 0 && budget.height() > 0);
            assertTrue(budget.width() <= declared.width() && budget.height() <= declared.height());
            assertTrue(budget.width() * units <= 6 + 1e-6);
            assertTrue(budget.height() * units <= 3 + 1e-6);
        }
        assertEquals(new UiSize(1200,600), MenuViewportLayout.viewport(declared,FloatingMenuFieldOfView.DEFAULT,1));
        assertEquals(.25, MenuViewportLayout.scale(1), 1e-8);
        assertEquals(2.8, MenuViewportLayout.distance(FloatingMenuFieldOfView.DEFAULT), 1e-8);
        assertTrue(MenuViewportLayout.distance(new FloatingMenuFieldOfView(30)) > 2.8);
    }

    @Test void focusingTheReaderCannotExemptAnOversizedSidebarFromTheCompleteViewport() {
        var root = UiTemplates.panoramicPanels("sheet", UiLength.ui(16),
                UiTemplates.sidebar("sidebar", Ui.stack().size(new UiSize(100, 1000)).build()),
                UiTemplates.sheet(UiTemplates.window("reader", Ui.text(Component.text("Read")).build()),
                        UiTemplates.toolbar("tools", Ui.button(Component.text("Back")).build())));
        var scene = MenuScene.screen("reader").viewport(new UiSize(1440, 2048)).content(root).build();
        assertThrows(UiOverflowException.class, () -> SceneGeometry.measureBase(scene,1,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT));
    }

    @Test void optedInAspectBudgetsFrontContentAlongTheClientFieldOfView() {
        var fov = new FloatingMenuFieldOfView(30);
        var defaultScene = MenuScene.screen("standard").build();
        var wide = defaultScene.toBuilder().viewportAspect(1.5).build();
        assertEquals(1200, MenuViewportLayout.forScene(defaultScene, fov, 1).width(), 1e-6);
        assertEquals(900, MenuViewportLayout.forScene(wide, fov, 1).width(), 1e-6);
        assertEquals(900 / 1.3, MenuViewportLayout.forScene(wide.toBuilder().build(), fov, 1.3).width(), 1e-6);
    }

    @Test void companionWindowsUseTheSameWorldSizeBudgetAtTheirActualReadingScale() {
        for (double scale : new double[]{.1,.2,.4}) {
            var reading = new MenuReadingParameters(new UiVector3(0,0,-2.8),scale,scale,FloatingMenuFieldOfView.DEFAULT);
            var size = reading.viewport(new UiSize(4096,4096),FloatingMenuPose.ORIGIN);
            assertEquals(6,size.width() * scale * UiSceneContent.WORLD_PER_UI,1e-6);
            assertEquals(3,size.height() * scale * UiSceneContent.WORLD_PER_UI,1e-6);
        }
    }
}
