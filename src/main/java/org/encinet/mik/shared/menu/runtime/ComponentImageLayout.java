package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.util.Map;

/** Projects one proportional image quad per component, independently of its optional background. */
final class ComponentImageLayout {
    private ComponentImageLayout() { }

    static UiSize contain(double width, double height, UiSize box) {
        double ratio = Math.min(box.width() / width, box.height() / height);
        return new UiSize(width * ratio, height * ratio);
    }

    static void append(UiFrame frame, ComponentSceneProjector projector,
                       Map<String, FloatingMenuDecoration> output, UiThemeColors theme) {
        for (UiFrame.Element element : frame.elements()) {
            if (!(element.node().content() instanceof UiContent.Image image)
                    || element.size().width() == 0 || element.size().height() == 0) continue;
            var style = element.node().style();
            var center = projector.project(element.path(), element.x(), element.y(), element.size());
            if (style.backgroundAlpha(theme) > 0) {
                String id = "$image/" + element.path() + "/background";
                var background = new FloatingMenuDecoration(id, ComponentSceneLayout.offsetNormal(center, -0.015),
                        new FloatingMenuDecoration.Surface((float) (element.size().width() * UiSceneContent.WORLD_PER_UI),
                                (float) (element.size().height() * UiSceneContent.WORLD_PER_UI), style.backgroundArgb(theme)),
                        FloatingMenuDecoration.Motion.NONE);
                output.put(id, projector.transformed(element.path()) ? background.tracking() : background);
            }
            if (style.contentAlpha() == 0) continue;
            var fitted = contain(image.source().pixelWidth(), image.source().pixelHeight(), element.size());
            String id = "$image/" + element.path() + "/content";
            var decoration = new FloatingMenuDecoration(id, ComponentSceneLayout.offsetNormal(center, -0.01),
                    new FloatingMenuDecoration.Image(image.source(), image.label(),
                            (float) (fitted.width() * UiSceneContent.WORLD_PER_UI),
                            (float) (fitted.height() * UiSceneContent.WORLD_PER_UI), (float) style.contentAlpha()),
                    FloatingMenuDecoration.Motion.NONE);
            output.put(id, projector.transformed(element.path()) ? decoration.tracking() : decoration);
        }
    }
}
