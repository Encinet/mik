package org.encinet.mik.module.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FloatingMenuArcLayout implements FloatingMenuLayout {

    private static final double HALF_ANGLE = Math.toRadians(80);
    private static final double VERTICAL_HALF_ANGLE = Math.toRadians(30);
    private final FloatingMenuLayout planarLayout;
    private final Set<String> centerRegions;
    private List<Node> measuredNodes = List.of();
    private Map<String, FloatingMenuPose> poses = Map.of();

    FloatingMenuArcLayout(FloatingMenuLayout planarLayout, Set<String> centerRegions) {
        this.planarLayout = planarLayout;
        this.centerRegions = Set.copyOf(centerRegions);
    }

    @Override
    public FloatingMenuPose pose(Context context) {
        if (!measuredNodes.equals(context.nodes())) {
            measure(context);
        }
        return poses.get(context.elementId());
    }

    private void measure(Context context) {
        Map<String, FloatingMenuPose> planar = new HashMap<>();
        double minimumCenter = Double.POSITIVE_INFINITY;
        double maximumCenter = Double.NEGATIVE_INFINITY;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double verticalExtent = 0.0;
        for (int index = 0; index < context.count(); index++) {
            Node node = context.nodes().get(index);
            FloatingMenuPose pose = planarLayout.pose(context.at(index));
            planar.put(node.elementId(), pose);
            double left = pose.right() - node.size().width() / 2;
            double right = pose.right() + node.size().width() / 2;
            minimum = Math.min(minimum, left);
            maximum = Math.max(maximum, right);
            verticalExtent = Math.max(verticalExtent,
                    Math.abs(pose.up()) + node.size().height() / 2);
            if (centerRegions.contains(node.region())) {
                minimumCenter = Math.min(minimumCenter, left);
                maximumCenter = Math.max(maximumCenter, right);
            }
        }
        if (!Double.isFinite(minimumCenter)) {
            throw new IllegalArgumentException("Panoramic layout requires a present center panel");
        }
        double center = (minimumCenter + maximumCenter) / 2;
        double radius = Math.max(3, Math.max(
                Math.max(center - minimum, maximum - center) / HALF_ANGLE,
                verticalExtent / Math.tan(VERTICAL_HALF_ANGLE)));
        Map<String, FloatingMenuPose> projected = new HashMap<>();
        for (Node node : context.nodes()) {
            FloatingMenuPose pose = planar.get(node.elementId());
            double angle = (pose.right() - center) / radius;
            projected.put(node.elementId(), FloatingMenuPose.oriented(new FloatingMenuPoint(
                    radius * Math.sin(angle), pose.up(), -radius * Math.cos(angle)),
                    Math.toDegrees(angle), 0));
        }
        poses = Map.copyOf(projected);
        measuredNodes = List.copyOf(context.nodes());
    }
}
