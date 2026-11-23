package org.encinet.mik.shared.menu.runtime.layout;

import java.util.Objects;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.ui.UiThemeColors;
import org.encinet.mik.shared.menu.ui.UiSize;

/** Holds the last complete page during loading; ready pages always measure their current content. */
public final class MenuLayoutState {
    private record Context(String screenId, UiSize viewport, FloatingMenuSpatialFrame spatialFrame,
                           double typography, double scale, FloatingMenuFieldOfView fieldOfView, int entityCapacity) { }

    private final MenuTextMeasurementCache measurements = new MenuTextMeasurementCache();
    private Context context;
    private SceneGeometry current;
    private UiThemeColors currentTheme;

    public MenuTextMeasurementCache measurements() { return measurements; }

    public SceneGeometry measure(MenuScene scene, double typography, FloatingMenuFieldOfView fieldOfView, double scale) {
        return measure(scene, typography, fieldOfView, scale, UiPalette.DEFAULT);
    }

    public SceneGeometry measure(MenuScene scene, double typography, FloatingMenuFieldOfView fieldOfView, double scale, UiThemeColors theme) {
        return measure(scene, typography, fieldOfView, scale, theme,
                content -> SceneGeometry.measure(content, typography, fieldOfView, scale, theme));
    }

    public SceneGeometry measure(MenuScene scene, double typography, FloatingMenuFieldOfView fieldOfView, double scale,
                          UiThemeColors theme, java.util.function.Function<MenuScene, SceneGeometry> compiler) {
        var geometry = measureCandidate(scene, typography, fieldOfView, scale, theme, compiler);
        publish(geometry, typography, fieldOfView, scale, theme);
        return geometry;
    }

    public SceneGeometry measureCandidate(MenuScene scene, double typography, FloatingMenuFieldOfView fieldOfView, double scale,
                                          UiThemeColors theme, java.util.function.Function<MenuScene, SceneGeometry> compiler) {
        return measure(scene,typography,fieldOfView,scale,theme,MenuEntityBudget.LIMIT,java.util.function.UnaryOperator.identity(),compiler);
    }

    public void publish(SceneGeometry geometry, double typography, FloatingMenuFieldOfView fieldOfView, double scale, UiThemeColors theme) {
        publish(geometry, typography, fieldOfView, scale, theme, MenuEntityBudget.LIMIT);
    }

    /** Host constraints remain authoritative when the current content is retained during loading. */
    public SceneGeometry measureWithin(MenuScene scene, UiSize viewport, double typography, FloatingMenuFieldOfView fieldOfView,
                                double scale, UiThemeColors theme, int entityCapacity, java.util.function.Function<MenuScene, SceneGeometry> compiler) {
        var geometry = measure(scene,typography,fieldOfView,scale,theme,entityCapacity,
                content -> content.toBuilder().viewport(viewport).build(),compiler);
        publish(geometry, typography, fieldOfView, scale, theme, entityCapacity);
        return geometry;
    }

    private SceneGeometry measure(MenuScene scene, double typography, FloatingMenuFieldOfView fieldOfView, double scale,
                                  UiThemeColors theme, int entityCapacity, java.util.function.UnaryOperator<MenuScene> constrain,
                                  java.util.function.Function<MenuScene, SceneGeometry> compiler) {
        boolean refreshing = scene.loading() && current != null
                && Objects.equals(scene.screenId(), current.definition().screenId());
        MenuScene content = constrain.apply(refreshing ? current.definition() : scene);
        var next = context(content, typography, fieldOfView, scale, entityCapacity);
        if (refreshing && next.equals(context) && theme.equals(currentTheme)) return current;
        var geometry = compiler.apply(content);
        MenuEntityBudget.require(geometry.entityCount(),entityCapacity,"Menu host");
        return geometry;
    }

    private void publish(SceneGeometry geometry, double typography, FloatingMenuFieldOfView fieldOfView, double scale,
                         UiThemeColors theme, int entityCapacity) {
        if (!geometry.definition().loading()) {
            currentTheme = theme;
            current = geometry;
            context = context(geometry.definition(), typography, fieldOfView, scale, entityCapacity);
        }
    }

    private static Context context(MenuScene content, double typography, FloatingMenuFieldOfView fieldOfView, double scale, int entityCapacity) {
        return new Context(content.screenId(), content.viewport(), content.spatialFrame(), typography, scale, fieldOfView, entityCapacity);
    }
}
