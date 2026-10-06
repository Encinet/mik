package org.encinet.mik.module.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FloatingMenuWorkbenchLayout implements FloatingMenuLayout {
    private final double width;
    private final double height;
    private final List<FloatingMenuLayouts.Panel> panels;
    private List<Node> measured = List.of();
    private Map<String, FloatingMenuPose> poses = Map.of();

    FloatingMenuWorkbenchLayout(double width, double height, FloatingMenuLayouts.Panel left,
                                FloatingMenuLayouts.Panel upper, FloatingMenuLayouts.Panel right,
                                FloatingMenuLayouts.Panel lower) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Workbench bounds must be positive");
        this.width = width;
        this.height = height;
        panels = List.of(left, upper, right, lower);
    }

    @Override public FloatingMenuPose pose(Context context) {
        if (!measured.equals(context.nodes())) measure(context);
        return poses.get(context.elementId());
    }

    private void measure(Context context) {
        Map<String, FloatingMenuPose> next = new HashMap<>();
        for (int panelIndex = 0; panelIndex < panels.size(); panelIndex++) {
            var panel = panels.get(panelIndex);
            Set<String> regions = panel.regions();
            List<Node> nodes = context.nodes().stream().filter(node -> regions.contains(node.region())).toList();
            if (nodes.isEmpty()) continue;
            Map<String, FloatingMenuPose> local = new HashMap<>();
            double minimumRight = Double.POSITIVE_INFINITY;
            double maximumRight = Double.NEGATIVE_INFINITY;
            double minimumUp = Double.POSITIVE_INFINITY;
            double maximumUp = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < nodes.size(); index++) {
                Node node = nodes.get(index);
                FloatingMenuPose pose = panel.layout().pose(context.regions(regions, index));
                local.put(node.elementId(), pose);
                minimumRight = Math.min(minimumRight, pose.right() - node.size().width() / 2);
                maximumRight = Math.max(maximumRight, pose.right() + node.size().width() / 2);
                minimumUp = Math.min(minimumUp, pose.up() - node.size().height() / 2);
                maximumUp = Math.max(maximumUp, pose.up() + node.size().height() / 2);
            }
            double offsetRight = -(minimumRight + maximumRight) / 2;
            double offsetUp = -(minimumUp + maximumUp) / 2 - 0.35;
            if (panelIndex == 0) offsetRight = -width / 2 - 0.3 - maximumRight;
            if (panelIndex == 2) offsetRight = width / 2 + 0.3 - minimumRight;
            if (panelIndex == 1) offsetUp = height / 2 - 0.35 + 0.25 - minimumUp;
            if (panelIndex == 3) offsetUp = -height / 2 - 0.35 - 0.25 - maximumUp;
            for (Node node : nodes) {
                FloatingMenuPose pose = local.get(node.elementId());
                double horizontal = pose.right() + offsetRight;
                double slope = Math.max(-1, Math.min(1, 0.04 * horizontal));
                next.put(node.elementId(), FloatingMenuPose.oriented(new FloatingMenuPoint(
                        horizontal, pose.up() + offsetUp, -0.65 + Math.min(0.35, 0.02 * horizontal * horizontal)),
                        Math.toDegrees(Math.atan(slope)), 0));
            }
        }
        if (next.size() != context.count()) throw new IllegalArgumentException("Unassigned workbench region");
        measured = List.copyOf(context.nodes());
        poses = Map.copyOf(next);
    }
}
