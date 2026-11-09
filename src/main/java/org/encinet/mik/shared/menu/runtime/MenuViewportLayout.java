package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.ui.UiSize;

/** Allocates one finite viewport to the complete page before any component is measured. */
final class MenuViewportLayout {
    static final double DEFAULT_DISTANCE = 2.8;
    private static final double DEFAULT_SCALE = 0.25;
    // Tune physical size without changing logical capacity; collision padding and the optional title are separate.
    private static final UiSize WORLD_VIEWPORT = new UiSize(
            MenuScene.DEFAULT_VIEWPORT.width() * (DEFAULT_SCALE * UiSceneContent.WORLD_PER_UI),
            MenuScene.DEFAULT_VIEWPORT.height() * (DEFAULT_SCALE * UiSceneContent.WORLD_PER_UI));

    private MenuViewportLayout() { }

    static double scale(double interfaceScale) {
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0)
            throw new IllegalArgumentException("Invalid interface scale");
        return DEFAULT_SCALE * interfaceScale;
    }

    static double distance(FloatingMenuFieldOfView fov) {
        return Math.max(DEFAULT_DISTANCE,
                WORLD_VIEWPORT.height() / (1.8 * fov.readingFraming().verticalTangent()));
    }

    static double referenceDistance(FloatingMenuFieldOfView fov, double interfaceScale) {
        return distance(fov) / scale(interfaceScale);
    }

    static UiSize forScene(MenuScene scene, FloatingMenuFieldOfView fov, double interfaceScale) {
        return scene.spatialFrame() != FloatingMenuSpatialFrame.AROUND_VIEWER
                ? viewport(scene.viewport(), fov, interfaceScale) : scene.viewport();
    }

    static UiSize viewport(UiSize declared, FloatingMenuFieldOfView fov, double interfaceScale) {
        return viewport(declared, fov, distance(fov), scale(interfaceScale));
    }

    /** Companion windows use the same page envelope at their actual distance and inherited scale. */
    static UiSize viewport(UiSize declared, FloatingMenuFieldOfView fov, double distance, double scale) {
        return viewport(declared, fov, distance, scale, scale);
    }

    static UiSize viewport(UiSize declared, FloatingMenuFieldOfView fov, double distance, double scale, double nominalScale) {
        double units = scale * UiSceneContent.WORLD_PER_UI;
        double nominalUnits = nominalScale * UiSceneContent.WORLD_PER_UI;
        var framing = fov.readingFraming();
        double width = Math.min(WORLD_VIEWPORT.width() / nominalUnits, 1.8 * distance * framing.horizontalTangent() / units);
        double height = Math.min(WORLD_VIEWPORT.height() / nominalUnits, 1.8 * distance * framing.verticalTangent() / units);
        return new UiSize(Math.min(declared.width(), width), Math.min(declared.height(), height));
    }
}
