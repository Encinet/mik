package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Artwork follows inherited typography, with a square footprint independent of labels and animation frames. */
final class ComponentIconLayout {
    private ComponentIconLayout() { }

    static UiSize measure(UiElement element, double typography, UiConstraints constraints) {
        var size = minimum(element, typography);
        if (size.width() > constraints.maxWidth() + 1e-6 || size.height() > constraints.maxHeight() + 1e-6)
            throw new UiOverflowException("Icon needs its drawing and hit bounds within " + constraints);
        return constraints.constrain(size);
    }

    static UiSize minimum(UiElement element, double typography) {
        var padding = element.style().padding();
        double drawing = drawingSize(element, typography);
        double control = element.input().interactive()
                ? UiTheme.CONTROL_MIN_HEIGHT.value()*typography*element.style().textScale() : 0;
        return new UiSize(Math.max(control, drawing + padding.horizontal()),
                Math.max(control, drawing + padding.vertical()));
    }

    static Map<String, FloatingMenuDecoration.IconPart> append(UiFrame frame, ComponentSceneProjector projector,
                                                              Map<String, FloatingMenuDecoration> output, double typography) {
        var icons = new LinkedHashMap<String, FloatingMenuDecoration.IconPart>();
        for (var element : frame.elements()) {
            if (!(element.node().content() instanceof UiContent.Icon content)) continue;
            var padding = element.node().style().padding();
            double size = Math.min(drawingSize(element.node(), typography),
                    Math.min(element.size().width() - padding.horizontal(), element.size().height() - padding.vertical()))
                    * UiSceneContent.WORLD_PER_UI;
            if (size <= 0) throw new UiOverflowException("Icon has no drawing bounds at " + element.path());
            var pose = projector.project(element.path(), element.x(), element.y(), element.size());
            for (var layer : FloatingMenuDecoration.IconPart.Layer.values()) for (int part = 0; part < content.value().partCount(); part++) {
                String id = "$icon/" + element.path() + "/" + layer + "/" + part;
                var paint = new FloatingMenuDecoration.IconPart(element.path(), content.value(), part, layer, size,
                        content.tint(), element.node().style().contentAlpha());
                output.put(id, new FloatingMenuDecoration(id, pose, paint, FloatingMenuDecoration.Motion.NONE).tracking());
                if (layer == FloatingMenuDecoration.IconPart.Layer.CORE && part == 0) icons.put(element.path(), paint);
            }
        }
        return Map.copyOf(icons);
    }

    private static double drawingSize(UiElement element, double typography) {
        return UiTheme.ICON_SIZE.value()*typography*element.style().textScale();
    }
}
