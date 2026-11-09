package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

import java.util.OptionalDouble;

/** Pure range paint geometry; the ordinary decoration lifecycle owns its virtual displays. */
final class MenuRangeRenderer {
    private MenuRangeRenderer() { }

    record Paint(double right, double up, double width, double height, int color, double alpha,
                 Component text, FloatingMenuNodeSizing.TextLayout layout, double textScale) { }

    static Paint paint(FloatingMenuDecoration.RangePart part, boolean interactive, OptionalDouble pointer, UiThemeColors theme) {
        var geometry = part.geometry();
        var range = part.range();
        double fraction = range.fraction(range.current());
        double start = geometry.trackStart(), width = geometry.trackWidth(), center = start + width / 2;
        double thickness = Math.min(.055, geometry.height() / 5);
        double thumb = Math.min(.065, width / 8);
        int accent = theme.argb(UiColorRole.ACCENT);
        return switch (part.part()) {
            case TRACK -> new Paint(center, 0, width, thickness, theme.argb(UiColorRole.TEXT), .25 * part.alpha(), null, null, 1);
            case FILL -> new Paint(start + width * fraction / 2, 0, Math.max(.001, width * fraction), thickness,
                    accent, fraction == 0 ? 0 : part.alpha(), null, null, 1);
            case THUMB -> new Paint(start + Math.clamp(width * fraction, thumb / 2, width - thumb / 2), 0, thumb, thickness * 2.5,
                    accent, interactive ? part.alpha() : 0, null, null, 1);
            case PREVIEW -> new Paint(start + Math.clamp(width * pointer.orElse(0), .0125, width - .0125), 0, .025, thickness * 3.5,
                    theme.argb(UiColorRole.TEXT), interactive && pointer.isPresent() ? .8 * part.alpha() : 0, null, null, 1);
            case VALUE, MAXIMUM -> {
                var content = range.text(part.part() == FloatingMenuDecoration.RangePart.Part.VALUE ? range.current() : range.maximum());
                var layout = ComponentRangeLayout.text(content);
                double textWidth = layout.contentWidth() * geometry.textScale();
                double right = part.part() == FloatingMenuDecoration.RangePart.Part.VALUE
                        ? start - geometry.gap() - textWidth / 2 : start + width + geometry.gap() + textWidth / 2;
                double up = -FloatingMenuNodeGeometry.textAnchorDown(layout.renderedHeight(), geometry.textScale());
                yield new Paint(right, up, 0, 0, 0, part.alpha(), content.colorIfAbsent(theme.color(UiColorRole.TEXT)), layout, geometry.textScale());
            }
        };
    }

    static void render(VirtualMenuEntityRenderer renderer, Player player, int entityId, Paint paint,
                       double scale, double reveal, UiRotation rotation) {
        if (paint.text() == null) {
            renderer.surface(player, entityId, paint.color(), (float) (paint.width() * scale), (float) (paint.height() * scale),
                    reveal * paint.alpha(), rotation);
        } else {
            renderer.text(player, entityId, paint.text(), 0, paint.layout().displayWidth(), paint.layout().displayHeight(),
                    paint.layout().lineWidthPixels(), (float) (FloatingMenuNodeSizing.TEXT_SCALE * paint.textScale() * scale),
                    FloatingMenuDecoration.Alignment.CENTER, false, reveal, paint.alpha(), rotation);
        }
    }
}
