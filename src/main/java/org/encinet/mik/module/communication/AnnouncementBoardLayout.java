package org.encinet.mik.module.communication;

import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;

import java.util.List;

final class AnnouncementBoardLayout {
    static final double PIXEL_SIZE = 0.025 * AnnouncementViewport.FONT_SCALE;
    static final double BODY_HEIGHT = AnnouncementViewport.ROWS * 10 * PIXEL_SIZE;
    static final double WIDTH = AnnouncementViewport.WIDTH * PIXEL_SIZE + 0.48;
    static final double HEIGHT = BODY_HEIGHT + 3.0;

    private AnnouncementBoardLayout() { }

    static FloatingMenuPose fragmentPose(AnnouncementViewport.Fragment fragment, double typography) {
        double right = (fragment.left() + fragment.width() / 2.0 - AnnouncementViewport.WIDTH / 2.0)
                * PIXEL_SIZE * typography;
        double up = (BODY_HEIGHT / 2 - (fragment.row() + 0.5) * 10 * PIXEL_SIZE
                - 4.5 * PIXEL_SIZE) * typography;
        return FloatingMenuPose.at(new FloatingMenuPoint(right, up, 0.015));
    }

    static FloatingMenuLayout create(double typography) {
        if (!Double.isFinite(typography) || typography <= 0)
            throw new IllegalArgumentException("Typography must be positive and finite");
        return context -> {
            var node = context.node();
            double up = switch (node.region()) {
                case "heading" -> BODY_HEIGHT / 2 + 0.62;
                case "body" -> 0;
                case "browse" -> -BODY_HEIGHT / 2 - 0.52;
                case "footer" -> -BODY_HEIGHT / 2 - 1.12;
                default -> throw new IllegalArgumentException("Unknown announcement region: " + node.region());
            };
            List<FloatingMenuLayout.Node> row = context.nodes().stream()
                    .filter(candidate -> candidate.region().equals(node.region())).toList();
            double gap = 0.24 * typography;
            double width = row.stream().mapToDouble(candidate -> candidate.size().width()).sum()
                    + gap * Math.max(0, row.size() - 1);
            double left = -width / 2;
            for (var candidate : row) {
                if (candidate.elementId().equals(node.elementId()))
                    return FloatingMenuPose.at(new FloatingMenuPoint(left + candidate.size().width() / 2,
                            up * typography, 0));
                left += candidate.size().width() + gap;
            }
            throw new IllegalArgumentException("Missing announcement node");
        };
    }
}
