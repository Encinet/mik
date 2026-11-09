package org.encinet.mik.shell.menu;

import java.util.List;
import org.encinet.mik.shared.menu.ui.*;

/** The launcher has a stable center; independent widget columns share a bounded, symmetric rail. */
final class MainMenuLayout implements UiLayout {
    private static final double GAP = 16;

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        if (scope.count() != 3) throw new IllegalArgumentException("The launcher needs two widget columns and its central sheet");
        double rail = Math.min(MainMenuSidebar.MAX_WIDTH, Math.max(0, limits.maxWidth() - 2 * GAP) * .26);
        var left = scope.measure(0, UiConstraints.bounded(rail, limits.maxHeight()));
        var right = scope.measure(2, UiConstraints.bounded(rail, limits.maxHeight()));
        double side = Math.max(left.width(), right.width());
        double gap = side > 0 ? GAP : 0;
        var center = scope.measure(1, UiConstraints.bounded(Math.max(0, limits.maxWidth() - 2 * (side + gap)), limits.maxHeight()));
        var size = limits.constrain(new UiSize(center.width() + 2 * (side + gap),
                Math.max(center.height(), Math.max(left.height(), right.height()))));
        double x = (size.width() - center.width()) / 2;
        return new Result(size, List.of(new Position(x - gap - left.width(), (size.height() - left.height()) / 2),
                new Position(x, (size.height() - center.height()) / 2),
                new Position(x + center.width() + gap, (size.height() - right.height()) / 2)));
    }
}
