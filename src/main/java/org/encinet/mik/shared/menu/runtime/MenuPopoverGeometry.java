package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** A separately measured layer; its footprint cannot enlarge or recenter the parent scene. */
record MenuPopoverGeometry(String sourceId, SceneGeometry content) {
    FloatingMenuSize size() {
        return new FloatingMenuSize(content.frame().size().width() * UiSceneContent.WORLD_PER_UI,
                content.frame().size().height() * UiSceneContent.WORLD_PER_UI);
    }

    static MenuPopoverGeometry measure(MenuPopover popover, SceneGeometry parent, double typography,
                                       FloatingMenuFieldOfView fov, double interfaceScale, UiThemeColors theme, MenuReadingParameters reading,
                                       org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache measurements) {
        if (popover == null) return null;
        var sources = parent.frame().elements().stream().filter(element -> element.node().key().equals(popover.source())).toList();
        if (sources.isEmpty()) return null;
        if (sources.size() != 1) throw new IllegalArgumentException("Ambiguous popover source: " + popover.source());
        String source = sources.getFirst().path();
        var pose = parent.poses().get(source);
        if (pose == null) return null;
        var viewport = reading.viewport(parent.definition().viewport(), pose);
        var scene = MenuScene.screen("$popover").viewport(parent.definition().viewport()).content(popover.contentTree()).build();
        // The host already supplied the actual reading viewport. Applying a second nominal
        // budget would discard the source distance and independently reframe the companion.
        return new MenuPopoverGeometry(source, SceneGeometry.measureBase(scene, typography, fov, interfaceScale,
                theme, measurements, viewport));
    }
}
