package org.encinet.mik.shared.menu.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Explicit pairs contain only published geometry; navigation never transfers an action or an entity. */
final class MenuSharedElements {
    record Source(MenuSurfaceAttachment.Surface surface, FloatingMenuElementStyle style) { }

    static Map<UiKey,String> targets(SceneGeometry geometry) {
        var visible = new HashMap<UiKey,String>();
        var ambiguous = new HashSet<UiKey>();
        for (var element : geometry.frame().elements()) {
            var key = element.node().key();
            if (visible.putIfAbsent(key,element.path()) != null) ambiguous.add(key);
        }
        var used = new HashSet<UiKey>();
        var duplicate = new HashSet<UiKey>();
        geometry.definition().sharedElements().values().forEach(key -> { if (!used.add(key)) duplicate.add(key); });
        var result = new HashMap<UiKey,String>();
        geometry.definition().sharedElements().forEach((identity,key) -> {
            String path = visible.get(key);
            if (path != null && !ambiguous.contains(key) && !duplicate.contains(key)
                    && geometry.definition().entries().containsKey(path) && !geometry.icons().containsKey(path) && !geometry.ranges().containsKey(path))
                result.put(identity,path);
        });
        return Map.copyOf(result);
    }

    static Map<UiKey,Source> capture(SceneGeometry geometry, Function<String,MenuSurfaceAttachment.Surface> surfaces) {
        var result = new HashMap<UiKey,Source>();
        targets(geometry).forEach((identity,path) -> {
            var surface = surfaces.apply(path);
            if (surface != null) result.put(identity,new Source(surface,geometry.definition().entries().get(path).style()));
        });
        return Map.copyOf(result);
    }

    static FloatingMenuPose local(Source source, UiVector3 origin, float parentYaw, double parentScale,
                                  org.encinet.mik.shared.menu.FloatingMenuSize size) {
        var parent = UiRotation.degrees(parentYaw,0,0);
        var delta = parent.inverse().apply(source.surface().center().subtract(origin)).multiply(1 / parentScale);
        double ratio = Math.min(source.surface().size().width() / size.width(),source.surface().size().height() / size.height());
        return FloatingMenuPose.transformed(new FloatingMenuPoint(delta.right(),delta.up(),delta.forward()),
                parent.inverse().compose(source.surface().rotation()),source.surface().scale() / parentScale * ratio);
    }
}
