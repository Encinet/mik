package org.encinet.mik.module.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FloatingMenuBalancedLayout implements FloatingMenuLayout {
    private final int maximumColumns;
    private final int maximumRows;
    private final double columnGap;
    private final double rowGap;
    private List<Node> measuredNodes = List.of();
    private Map<String, FloatingMenuPose> poses = Map.of();

    FloatingMenuBalancedLayout(int maximumColumns, int maximumRows, double columnGap, double rowGap) {
        this.maximumColumns = maximumColumns;
        this.maximumRows = maximumRows;
        this.columnGap = columnGap;
        this.rowGap = rowGap;
    }

    @Override
    public FloatingMenuPose pose(Context context) {
        if (!context.nodes().equals(measuredNodes)) measure(context);
        return poses.get(context.elementId());
    }

    private void measure(Context context) {
        if (context.count() > maximumColumns * maximumRows) {
            throw new IllegalArgumentException("Balanced layout capacity exceeded");
        }
        double bestScore = Double.POSITIVE_INFINITY;
        Map<String, FloatingMenuPose> chosen = Map.of();
        for (int rows = 1; rows <= Math.min(maximumRows, context.count()); rows++) {
            int columns = (context.count() + rows - 1) / rows;
            if (columns > maximumColumns) continue;
            FloatingMenuLayout candidate = FloatingMenuLayouts.adaptiveList(columns, rows, columnGap, rowGap);
            Map<String, FloatingMenuPose> measured = new HashMap<>();
            double minimumRight = Double.POSITIVE_INFINITY;
            double maximumRight = Double.NEGATIVE_INFINITY;
            double minimumUp = Double.POSITIVE_INFINITY;
            double maximumUp = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < context.count(); index++) {
                FloatingMenuPose pose = candidate.pose(context.at(index));
                Node node = context.nodes().get(index);
                measured.put(node.elementId(), pose);
                minimumRight = Math.min(minimumRight, pose.right() - node.size().width() / 2);
                maximumRight = Math.max(maximumRight, pose.right() + node.size().width() / 2);
                minimumUp = Math.min(minimumUp, pose.up() - node.size().height() / 2);
                maximumUp = Math.max(maximumUp, pose.up() + node.size().height() / 2);
            }
            double width = maximumRight - minimumRight;
            double height = maximumUp - minimumUp;
            double score = Math.max(width / 1.8, height)
                    + Math.abs(Math.log(width / height / 1.8)) * 0.15;
            if (score < bestScore) {
                bestScore = score;
                chosen = measured;
            }
        }
        poses = Map.copyOf(chosen);
        measuredNodes = List.copyOf(context.nodes());
    }
}
