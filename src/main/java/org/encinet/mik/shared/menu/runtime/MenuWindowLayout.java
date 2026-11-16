package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import org.encinet.mik.shared.menu.MenuWindowSpec;
import org.encinet.mik.shared.menu.ui.*;

/** Independent viewport allocation; sibling windows never subtract from their owner's reading budget. */
final class MenuWindowLayout implements UiLayout {
    private final List<UiSize> viewports;
    private final List<MenuWindowSpec.Position> positions;
    private final boolean tray;

    MenuWindowLayout(List<UiSize> viewports, List<MenuWindowSpec.Position> positions) {
        this(viewports,positions,false);
    }
    MenuWindowLayout(List<UiSize> viewports, List<MenuWindowSpec.Position> positions, boolean tray) {
        this.viewports = List.copyOf(viewports); this.positions = List.copyOf(positions);
        this.tray = tray;
        if (viewports.size() != positions.size()) throw new IllegalArgumentException("Mismatched window policies");
    }

    @Override public Result layout(MeasureScope children, UiConstraints limits) {
        if (children.count() != viewports.size()) throw new IllegalArgumentException("Mismatched window count");
        var sizes = new ArrayList<UiSize>();
        double width = 0, height = 0;
        for (int index = 0; index < children.count(); index++) {
            var viewport = viewports.get(index);
            var size = children.measure(index, UiConstraints.bounded(Math.min(limits.maxWidth(), viewport.width()),
                    Math.min(limits.maxHeight(), viewport.height())));
            sizes.add(size); width = Math.max(width, size.width());
            // The tray does not consume a reading budget, but still needs a non-negative placement envelope.
            height = Math.max(height, size.height());
        }
        var frame = limits.constrain(new UiSize(width, height));
        double readingHeight = frame.height();
        var placed = new ArrayList<Position>();
        for (int index = 0; index < sizes.size(); index++) {
            var size = sizes.get(index);
            double x = switch (positions.get(index)) {
                case CENTER -> (frame.width() - size.width()) / 2;
                case LEFT, BOTTOM_LEFT -> 0;
                case RIGHT -> frame.width() - size.width();
            };
            if (tray && index == sizes.size()-1) placed.add(new Position((frame.width()-size.width())/2,frame.height()-size.height()));
            else placed.add(new Position(x, positions.get(index) == MenuWindowSpec.Position.BOTTOM_LEFT ? readingHeight - size.height() : (readingHeight - size.height()) / 2));
        }
        return new Result(frame, placed).focusing(children.key(0));
    }
}
