package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiFrame;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.ui.UiThemeColors;
import org.encinet.mik.shared.menu.ui.UiStyle;
import org.encinet.mik.shared.menu.ui.UiSurfaceFacing;
import org.encinet.mik.shared.menu.ui.UiPointerBehavior;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Published component geometry: rendering, fitting, tooltips and input consume this same frame. */
record SceneGeometry(Map<String, FloatingMenuPose> poses, Map<String, FloatingMenuSize> sizes,
                     Map<String, Component> labels,
                     Map<String, FloatingMenuNodeSizing.Measurement> measurements,
                     Map<String, FloatingMenuDecoration> generatedDecorations,
                     Map<String, FloatingMenuDecoration.RangePart> ranges,
                     Map<String, FloatingMenuDecoration.IconPart> icons,
                     List<MenuPaginationInput.Region> pagination,
                     Map<String, UiStyle> styles, Set<String> panelContents, Set<String> transformedContents,
                     FloatingMenuAnchorResolver.SceneBounds bounds, UiFrame frame, MenuScene definition, MenuPopoverGeometry popover) {
    static SceneGeometry measure(MenuScene definition, double typography, FloatingMenuFieldOfView fov, double scale) {
        return measure(definition, typography, fov, scale, UiPalette.DEFAULT);
    }

    static SceneGeometry measure(MenuScene definition, double typography, FloatingMenuFieldOfView fov, double scale,
                                 UiThemeColors theme) {
        var base = measureBase(definition, typography, fov, scale, theme);
        return base.withPopover(definition, typography, fov, scale, theme,
                MenuReadingParameters.estimated(base.bounds(), fov, scale));
    }

    static SceneGeometry measureBase(MenuScene definition, double typography, FloatingMenuFieldOfView fov,
                                     double scale, UiThemeColors theme) {
        return measureBase(definition, typography, fov, scale, theme, null);
    }

    static SceneGeometry measureBase(MenuScene definition, double typography, FloatingMenuFieldOfView fov,
                                     double scale, UiThemeColors theme, MenuTextMeasurementCache measurements) {
        return measureBase(definition, typography, fov, scale, theme, measurements, MenuViewportLayout.forScene(definition, fov, scale));
    }

    static SceneGeometry measureBase(MenuScene definition, double typography, FloatingMenuFieldOfView fov,
                                     double scale, UiThemeColors theme, MenuTextMeasurementCache measurements,
                                     org.encinet.mik.shared.menu.ui.UiSize viewport) {
        ComponentSceneLayout.Result result = ComponentSceneLayout.measure(definition, typography, fov, scale, theme, measurements, viewport);
        definition = definition.measured(result.frame());
        Map<String, UiStyle> styles = new HashMap<>();
        Map<String, Component> labels = new HashMap<>();
        Set<String> panelContents = new HashSet<>();
        Set<String> transformedContents = new HashSet<>();
        for (var element : result.frame().elements()) {
            styles.put(element.path(), element.node().style());
            labels.put(element.path(), element.node().content().label());
            if (result.frame().groups().stream().anyMatch(group -> element.path().startsWith(group.path() + "/"))
                    || result.frame().panels().stream().anyMatch(panel -> panel.facing() != UiSurfaceFacing.INHERIT
                            && element.path().startsWith(panel.path() + "/")))
                transformedContents.add(element.path());
            if (result.frame().panels().stream().anyMatch(panel -> element.path().startsWith(panel.path() + "/"))
                    || result.frame().surfaces().stream().anyMatch(surface -> element.path().startsWith(surface.path() + "/")))
                panelContents.add(element.path());
        }
        requireEntityBudget(definition,styles,result.generatedDecorations().size());
        var bounds = FloatingMenuAnchorResolver.measure(definition, result.poses(), result.sizes(), typography, fov, scale, result.surfaceBounds());
        var base = new SceneGeometry(result.poses(), result.sizes(), Map.copyOf(labels), result.measurements(), result.generatedDecorations(), result.ranges(), result.icons(), result.pagination(),
                Map.copyOf(styles), Set.copyOf(panelContents), Set.copyOf(transformedContents), bounds, result.frame(), definition, null);
        return base;
    }

    SceneGeometry withPopover(MenuScene requested, double typography, FloatingMenuFieldOfView fov, double scale,
                              UiThemeColors theme, MenuReadingParameters reading) {
        return withPopover(requested, typography, fov, scale, theme, reading, null);
    }

    SceneGeometry withPopover(MenuScene requested, double typography, FloatingMenuFieldOfView fov, double scale,
                              UiThemeColors theme, MenuReadingParameters reading, MenuTextMeasurementCache measurements) {
        SceneGeometry base = this;
        var companion = MenuPopoverGeometry.measure(requested.popover(), base, typography, fov, scale, theme, reading, measurements);
        if (companion == null) return base;
        var layer = companion.content();
        var allStyles = merge(base.styles(), layer.styles());
        var allPanelContents = new HashSet<>(base.panelContents());
        var allTransformed = new HashSet<>(base.transformedContents());
        layer.poses().keySet().forEach(path -> { allPanelContents.add(MenuPopover.scoped(path)); allTransformed.add(MenuPopover.scoped(path)); });
        var generated = new LinkedHashMap<>(base.generatedDecorations());
        layer.generatedDecorations().forEach((path, decoration) -> {
            String scoped = MenuPopover.scoped(path);
            generated.put(scoped, new FloatingMenuDecoration(scoped, decoration.placement(), decoration.content(), decoration.motion(),
                    FloatingMenuDecoration.Transition.TRACKING));
        });
        var realized = requested.measured(base.frame(), layer.frame());
        requireEntityBudget(realized,allStyles,generated.size());
        return new SceneGeometry(merge(base.poses(), layer.poses()), merge(base.sizes(), layer.sizes()), merge(base.labels(), layer.labels()),
                merge(base.measurements(), layer.measurements()), Map.copyOf(generated), merge(base.ranges(), layer.ranges()), merge(base.icons(), layer.icons()), base.pagination(), allStyles, Set.copyOf(allPanelContents),
                Set.copyOf(allTransformed), base.bounds(), base.frame(), realized, companion);
    }

    private static <T> Map<String, T> merge(Map<String, T> base, Map<String, T> layer) {
        var result = new LinkedHashMap<>(base);
        layer.forEach((path, value) -> result.put(MenuPopover.scoped(path), value));
        return Map.copyOf(result);
    }

    /** Parent and companion count the same resources that the renderer actually publishes. */
    private static void requireEntityBudget(MenuScene scene, Map<String,UiStyle> styles, int generatedDecorations) {
        MenuEntityBudget.require(entityCount(scene,styles,generatedDecorations),"Scene");
    }

    int entityCount() { return entityCount(definition,styles,generatedDecorations.size()); }

    private static int entityCount(MenuScene scene, Map<String,UiStyle> styles, int generatedDecorations) {
        int entities = scene.decorations().size() + generatedDecorations + (scene.titleVisible() ? 1 : 0) + 2;
        for (var entry : scene.entries().values()) {
            entities += entry.style() == FloatingMenuElementStyle.TEXT ? 1 : 2;
            if (entry.pointerBehavior() == UiPointerBehavior.OPERATE) entities++;
            var style = styles.get(entry.id());
            if (entry.interactive() && style != null && style.controlSurface()) entities++;
        }
        return entities;
    }

    Map<String, FloatingMenuDecoration> decorations(MenuScene definition) {
        Map<String, FloatingMenuDecoration> result = new LinkedHashMap<>(definition.decorations());
        generatedDecorations.forEach((key, decoration) -> {
            if (result.putIfAbsent(key, decoration) != null) throw new IllegalArgumentException("Reserved generated decoration identity: " + key);
        });
        return result;
    }
}
