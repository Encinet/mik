package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuCompanionReadingTest {
    @Test void aSideSourceInAViewerCenteredSceneStillHasAUsableReadingBudget() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 64, 0);
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye, new Vector(0, 0, 1), new Vector(1, 0, 0), 1, 0, 0);
        var source = FloatingMenuPose.oriented(new FloatingMenuPoint(3, 0, 0), 90, 0);
        var before = MenuReadingParameters.from(eye, anchor, FloatingMenuFieldOfView.DEFAULT,1).viewport(new UiSize(640, 2048), source);
        assertTrue(before.height() > 100);
        var after = MenuReadingParameters.from(eye, anchor.contract(eye, .3), FloatingMenuFieldOfView.DEFAULT,1)
                .viewport(new UiSize(640, 2048), source);
        assertEquals(before.width(), after.width(), 1e-8);
        assertEquals(before.height(), after.height(), 1e-8);
    }

    @Test void aFailedCompanionLayoutDoesNotReplaceThePreviousReadingReference() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var before = present(view, eye, scene(360), 70, 1);
        var oversized = MenuPopover.attached(UiKey.named("source"), Component.text("Details"),
                Ui.text(Component.text("Too large")).key("oversized").size(new UiSize(4096, 4096)).build(), Component.text("Close"));
        assertThrows(UiOverflowException.class, () -> present(view, eye, scene(160).toBuilder().popover(oversized).build(), 110, 1));
        var after = present(view, eye, scene(160), 70, 1);
        assertEquals(before.anchor().orElseThrow().spatialScale(), after.anchor().orElseThrow().spatialScale(), 1e-8);
    }

    @Test void aNarrowRefreshRetainsTheCompanionsActualReadingBudgetFromTheWideOpeningPage() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        for (int fov : new int[]{30, 70, 110}) for (double text : new double[]{.7, 1, 1.8}) {
            var view = new MenuPresentation();
            var wide = present(view, eye, scene(200), fov, text);
            var narrow = present(view, eye, scene(120), fov, text);
            assertTrue(wide.geometry().frame().size().width() > narrow.geometry().frame().size().width());
            assertEquals(wide.anchor().orElseThrow().spatialScale(), narrow.anchor().orElseThrow().spatialScale(), 1e-8);
            assertEquals(popup(wide).definition().viewport().width(), popup(narrow).definition().viewport().width(), 1e-6);
            assertEquals(popup(wide).definition().viewport().height(), popup(narrow).definition().viewport().height(), 1e-6);
            assertEquals(wide.geometry().bounds(), SceneGeometry.measureBase(scene(200), text,
                    new FloatingMenuFieldOfView(fov), 1, UiPalette.DEFAULT).bounds(), "The companion never expands its parent");
        }
    }

    @Test void uniformWorldContractionPreservesTheCompanionsApparentReadingBudget() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), .5, 64.5, 0);
        var view = new MenuPresentation();
        var clear = present(view, eye, scene(400), 70, 1);
        for (int x = -3; x <= 3; x++) for (int y = 62; y <= 67; y++)
            fixture.block(x, y, 2, new BoundingBox(0, 0, 0, 1, 1, 1));
        var obstructed = present(view, eye, scene(400), 70, 1);
        assertTrue(obstructed.anchor().orElseThrow().distance() < clear.anchor().orElseThrow().distance());
        assertEquals(popup(clear).definition().viewport().width(), popup(obstructed).definition().viewport().width(), 1e-6);
        assertEquals(popup(clear).definition().viewport().height(), popup(obstructed).definition().viewport().height(), 1e-6);
    }

    @Test void anOccludedOpeningKeepsTheDefinitionAndCanRecoverWithItsCompanionAndActions() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), .5, 64.5, .5);
        fixture.block(0, 64, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        var view = new MenuPresentation();
        var hidden = present(view, eye, scene(300), 70, 1);
        assertTrue(hidden.anchor().isEmpty());
        assertNotNull(hidden.geometry().popover());
        assertTrue(hidden.geometry().definition().entries().keySet().stream().anyMatch(MenuPopover::owns));
        fixture.block(0, 64, 0);
        var recovered = present(view, eye, scene(300), 70, 1);
        assertTrue(recovered.anchor().isPresent());
        assertEquals(hidden.geometry().definition().entries().keySet(), recovered.geometry().definition().entries().keySet());
    }

    private static SceneGeometry popup(MenuPresentation.Frame frame) { return frame.geometry().popover().content(); }

    private static MenuPresentation.Frame present(MenuPresentation view, Location eye, MenuScene scene, int fov, double text) {
        return view.prepare(null, eye, new Vector(0, 0, 1), scene, text, 1, new FloatingMenuFieldOfView(fov), UiPalette.DEFAULT);
    }

    private static MenuScene scene(double width) {
        var panel = UiTemplates.window("body", Ui.button(Component.text("Details")).key("source").primary((_, _) -> {}).build())
                .withSizing(UiSizing.width(UiLength.ui(width), UiLength.ui(width)));
        var companion = MenuPopover.attached(UiKey.named("source"), Component.text("Details"),
                Ui.text(Component.text("Current task details")).key("details").build(), Component.text("Close"));
        return MenuScene.screen("reading").content(panel).popover(companion).build();
    }
}
