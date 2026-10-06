package org.encinet.mik.shell;

import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuRing;

/** All language choices in one continuously rotating ring, with guidance in front. */
final class LanguageRingLayout implements FloatingMenuLayout {
    private static final String CARD_PREFIX = "language-ring:";
    private static final double READER_RADIUS = 2.05;
    private static final double CARD_RADIUS = 2.35;
    private static final double READER_UP = 0.16;

    private final long rotation;

    LanguageRingLayout(long rotation) { this.rotation = rotation; }

    @Override public boolean continuousOrbit() { return true; }

    static String cardRegion(int index) { return CARD_PREFIX + index; }

    @Override
    public FloatingMenuPose pose(Context context) {
        double readerHeight = context.nodes().stream()
                .filter(node -> node.region().equals("reader"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Language ring needs a reader"))
                .size().height();
        if (context.region().equals("reader")) {
            return FloatingMenuPose.at(new FloatingMenuPoint(0.0, READER_UP,
                    -READER_RADIUS));
        }
        if (context.region().startsWith(CARD_PREFIX)) {
            int count = (int) context.nodes().stream()
                    .filter(node -> node.region().startsWith(CARD_PREFIX)).count();
            int index = Integer.parseInt(context.region().substring(CARD_PREFIX.length()));
            double cardWidth = context.nodes().stream()
                    .filter(node -> node.region().startsWith(CARD_PREFIX))
                    .mapToDouble(node -> node.size().width()).max().orElse(0.62);
            double cardHeight = context.nodes().stream()
                    .filter(node -> node.region().startsWith(CARD_PREFIX))
                    .mapToDouble(node -> node.size().height()).max().orElse(0.42);
            double up = READER_UP + readerHeight * 0.5 + cardHeight * 0.5 + 0.20;
            double radius = Math.max(CARD_RADIUS, (cardWidth + 0.16)
                    / (2 * Math.sin(Math.PI / Math.max(2, count))));
            return FloatingMenuRing.pose(index - (double) rotation, count, radius, up);
        }
        if (context.region().equals("navigation")) {
            var nodes = context.nodes().stream()
                    .filter(node -> node.region().equals("navigation")).toList();
            double height = nodes.stream().mapToDouble(node -> node.size().height())
                    .max().orElse(0.0);
            double center = READER_UP - readerHeight * 0.5 - 0.18 - height * 0.5;
            return FloatingMenuRing.frontArc(context.regionIndex(), nodes.size(), 80.0, 1.90, center);
        }
        throw new IllegalArgumentException("Unknown language region: " + context.region());
    }
}
