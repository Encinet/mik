package org.encinet.mik.shared.menu.runtime.window;

import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.ui.*;

/** Captures the exact eye-relative frame once; sampling never accumulates rotation or position error. */
public final class MenuWindowDrag {
    private final UiTransform relative;
    private final UiVector3 pivot;
    private final double scale;

    public MenuWindowDrag(UiTransform view, UiTransform window, UiTransform workspace, double spatialScale, UiTransform original) {
        relative = view.inverse().compose(window);
        pivot = workspace.inverse().apply(window.translation()).multiply(1 / (spatialScale * UiSceneContent.WORLD_PER_UI))
                .subtract(original.translation());
        scale = original.scale();
    }

    public UiTransform sample(UiTransform view, UiTransform workspace, double spatialScale) {
        var target = view.compose(relative);
        return new UiTransform(workspace.inverse().apply(target.translation()).multiply(1 / (spatialScale * UiSceneContent.WORLD_PER_UI)).subtract(pivot),
                workspace.rotation().inverse().compose(target.rotation()),scale);
    }

    public static UiFrame frame(UiFrame original, String path, UiTransform transform) {
        var groups = original.groups().stream().map(group -> !group.path().equals(path) ? group
                : new UiFrame.Group(group.path(),group.x(),group.y(),group.size(),transform,group.pivot())).toList();
        return new UiFrame(original.size(),original.elements(),original.surfaces(),original.panels(),groups,original.focus(),
                original.readingAnchor(),original.pagination(),original.readingRegion());
    }
}
