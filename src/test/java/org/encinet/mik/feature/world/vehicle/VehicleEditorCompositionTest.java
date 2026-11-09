package org.encinet.mik.feature.world.vehicle;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorCompositionTest {
    @Test void selectingAToolKeepsTheObjectAndDoesNotExecuteAParameterAdjustment() {
        for (Section section : Section.values()) {
            var current = new Inspector(new ComponentRef(section, 2));
            var chosen = new ArrayList<ObjectView>();
            var edits = new AtomicInteger();
            var menu = VehicleModelEditor.createMenu(Component.text("Model"), current);
            menu.control("adjust", Component.text("Change a value")).region("items").primary((_, _) -> edits.incrementAndGet());
            VehicleEditorComposition.tools(menu, current, message -> Component.text(message.key()), (_, tool) -> chosen.add(tool));
            var scene = menu.build();
            assertTrue(chosen.isEmpty());
            assertEquals(1, scene.entries().values().stream().filter(MenuScene.Entry::selected).count());
            assertEquals(current.tools().size(), UiSceneTestSupport.elements(scene).values().stream()
                    .filter(element -> element.key().value().startsWith("object-tool:")).count());
            UiSceneTestSupport.entry(scene, "object-tool:position").trigger(FloatingMenuInteraction.PRIMARY)
                    .execute(null, null, FloatingMenuInteraction.PRIMARY);
            assertEquals(List.of(new Position(current.target(), false)), chosen);
            assertEquals(0, edits.get());
            UiSceneTestSupport.entry(scene, "adjust").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
            assertEquals(1, edits.get());
        }
    }

    @Test void axisPairsToolSelectionAndTaskActionsKeepTheirOwnRegionsAtLargeText() {
        for (Section section : Section.values()) {
            var current = new Position(new ComponentRef(section, 0), false);
            var menu = VehicleModelEditor.createMenu(Component.text("Model editor · 车辆模型"), current);
            menu.information("summary", Component.text("Part 0 · local axes\nX/Y/Z adjustments")).region("summary");
            VehicleEditorComposition.tools(menu, current, message -> Component.text(message.key()), (_, _) -> { });
            for (Section category : Section.values()) menu.navigation("section:" + category, Component.text(category.name())).region("sections");
            for (String axis : List.of("x", "y", "z")) for (String direction : List.of("minus", "plus"))
                menu.control(axis + ":" + direction, Component.text(axis + " " + direction)).region("items");
            menu.control("manual", Component.text("Manual input")).region("actions");
            menu.control("step", Component.text("Step: 0.1")).region("actions");
            menu.navigation("back", Component.text("Back")).region("navigation");
            var scene = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            var frame = SceneGeometryTestSupport.measure(scene, 1.8);
            UiSceneTestSupport.assertDisjoint(frame);
            var minus = UiSceneTestSupport.element(frame, "x:minus");
            var plus = UiSceneTestSupport.element(frame, "x:plus");
            assertEquals(minus.y(), plus.y(), 1e-6);
            assertTrue(minus.x() < plus.x());
            assertTrue(UiSceneTestSupport.element(frame, "object-tool:position").y() < minus.y());
            assertTrue(UiSceneTestSupport.element(frame, "manual").y() > UiSceneTestSupport.element(frame, "z:plus").y());
        }
    }
}
