package org.encinet.mik.shared.menu.runtime;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

import java.util.Map;

/** Time sampling and native flat painting only. No tasks, layout, IO or feature callbacks. */
final class MenuIconRenderer {
    private MenuIconRenderer() { }
    record Paint(double right, double up, double forward, double width, double height, double roll, int color, double alpha) { }

    static Paint paint(FloatingMenuDecoration.IconPart part, long ticks, boolean still, boolean enabled, UiThemeColors theme) {
        var rectangle = part.icon().sample(ticks, still).parts().get(part.part());
        double unit = part.size() / UiIcon.CANVAS;
        boolean feather = part.layer() == FloatingMenuDecoration.IconPart.Layer.FEATHER;
        double inset = Math.min(.2, Math.min(rectangle.width(),rectangle.height())/4);
        double padding = feather ? featherPadding(rectangle) : 0;
        // Keep stroke lengths at joins; narrowing both dimensions would reopen the corner gaps.
        double width = rectangle.width()+2*padding - (!feather && rectangle.width() <= rectangle.height() ? 2*inset : 0);
        double height = rectangle.height()+2*padding - (!feather && rectangle.height() <= rectangle.width() ? 2*inset : 0);
        // All soft edges sit behind all cores. Separate overlapping strokes to avoid coplanar seams.
        double forward = .002 + (feather ? 0 : .0005) + part.part()*.00001;
        return new Paint((rectangle.x()-12)*unit, (12-rectangle.y())*unit, forward,
                width*unit, height*unit,
                -rectangle.angle(), part.tint().argb(theme), rectangle.alpha()*part.alpha()*(enabled ? 1 : .55)*(feather ? .35 : 1));
    }

    /** A narrow translucent silhouette softens native quad edges without changing the drawing bounds. */
    private static double featherPadding(UiIcon.Part part) {
        double radians = Math.toRadians(part.angle()), cosine = Math.abs(Math.cos(radians)), sine = Math.abs(Math.sin(radians));
        double horizontal = (cosine*part.width()+sine*part.height())/2;
        double vertical = (sine*part.width()+cosine*part.height())/2;
        double margin = Math.min(Math.min(part.x(),UiIcon.CANVAS-part.x())-horizontal,
                Math.min(part.y(),UiIcon.CANVAS-part.y())-vertical);
        return Math.clamp(margin/(cosine+sine),0,.25);
    }

    /** One loop per session; a foreground wait takes precedence over decorative playback activity. */
    static String animationOwner(Map<String, FloatingMenuDecoration.IconPart> icons) {
        return icons.entrySet().stream().filter(entry -> entry.getValue().icon().animated())
                .sorted(java.util.Comparator.<Map.Entry<String, FloatingMenuDecoration.IconPart>>comparingInt(
                        entry -> org.encinet.mik.shared.menu.MenuPopover.owns(entry.getKey()) ? 0 : 1)
                        .thenComparingInt(entry -> entry.getValue().icon() == UiIcons.LOADING ? 0 : 1).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    static void render(VirtualMenuEntityRenderer renderer, Player player, int entityId, Paint paint,
                       double scale, double reveal, UiRotation parent) {
        renderer.surface(player, entityId, paint.color(), (float)(paint.width()*scale), (float)(paint.height()*scale),
                paint.alpha()*reveal, parent.compose(UiRotation.degrees(0,0,paint.roll())));
    }
}
