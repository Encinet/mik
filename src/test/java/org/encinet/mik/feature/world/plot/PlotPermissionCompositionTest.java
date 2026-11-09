package org.encinet.mik.feature.world.plot;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotPermissionCompositionTest {
    @Test void everyCategorySharesColumnEdgesAndRowTopsWithVariableHeightCells() {
        for (boolean subPlot : List.of(false, true)) for (var category : PlotPermission.Category.values()) {
            Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Plot", 0,
                    Set.of(), Map.of(), Map.of(), subPlot ? UUID.randomUUID() : null);
            var builder = MenuScene.screen("permissions").content(PlotPermissionComposition.create(plot, category,0,_ -> {}));
            for (String port : List.of("heading", "subject", "section", "categories", "operation-heading", "result-heading", "hint", "navigation"))
                builder.information(port, Component.text(port)).region(port);
            for (var permission : category.permissions()) if (permission.appliesTo(plot)) {
                String source = "permission-" + permission.key();
                builder.information(source + "-label", Component.text(permission.key())).region(source + "-group-heading");
                builder.control(source, Component.text("State\nSource and inherited policy")).region(source).primary((_, _) -> { });
            }
            var scene = builder.build();
            var frame = frame(scene);
            assertDisjoint(frame);
            for (var permission : category.permissions()) if (permission.appliesTo(plot)) {
                String source = "permission-" + permission.key();
                var label = element(frame, source + "-label");
                var value = element(frame, source);
                assertEquals(element(frame, "operation-heading").x(), label.x(), 1e-6);
                assertEquals(element(frame, "result-heading").x(), value.x(), 1e-6);
                assertEquals(label.y(), value.y(), 1e-6);
            }
        }
    }

    @Test void unrelatedContentIsRejectedAtConstruction() {
        var builder = MenuScene.screen("permissions").content(PlotMenuLayouts.help(UiTemplates.information("instructions")));
        builder.information("orphan", Component.text("orphan")).region("unknown");
        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
