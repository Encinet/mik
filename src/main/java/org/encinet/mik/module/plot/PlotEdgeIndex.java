package org.encinet.mik.module.plot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

final class PlotEdgeIndex {
    private static final int LEAF_SIZE = 8;
    static final int MAX_VISITS = 2048;
    private final Indexed[] edges;
    private final Node root;

    record Visible(List<PlotPreviewGeometry.Edge> edges, boolean limited, int visits) { }
    private record Indexed(int ordinal, PlotPreviewGeometry.Edge edge) { }
    private record Node(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                        int start, int end, Node left, Node right) { }

    PlotEdgeIndex(List<PlotPreviewGeometry.Edge> source) {
        edges = new Indexed[source.size()];
        for (int index = 0; index < source.size(); index++) edges[index] = new Indexed(index, source.get(index));
        root = source.isEmpty() ? null : build(0, edges.length);
    }

    Visible nearby(PlotPreviewGeometry.Point viewer, double distance, int budget) {
        return query(viewer.horizontal() - distance, viewer.vertical() - distance, viewer.forward() - distance,
                viewer.horizontal() + distance, viewer.vertical() + distance, viewer.forward() + distance, budget);
    }

    Visible within(PlotSelection.Bounds bounds, int budget) {
        return query(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ(),
                bounds.maximumX() + 1.0, bounds.maximumY() + 1.0, bounds.maximumZ() + 1.0, budget);
    }

    private Visible query(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int budget) {
        if (root == null || budget <= 0) return new Visible(List.of(), root != null, 0);
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double centerZ = (minZ + maxZ) / 2;
        PriorityQueue<Node> pending = new PriorityQueue<>(Comparator.comparingDouble(node ->
                distance(node, centerX, centerY, centerZ)));
        pending.add(root);
        List<Indexed> visible = new ArrayList<>(budget);
        int visits = 0;
        while (!pending.isEmpty() && visits < MAX_VISITS && visible.size() <= budget) {
            Node node = pending.remove();
            visits++;
            if (!intersects(node, minX, minY, minZ, maxX, maxY, maxZ)) continue;
            if (node.left() != null) {
                pending.add(node.left());
                pending.add(node.right());
            } else {
                for (int index = node.start(); index < node.end() && visible.size() <= budget; index++) {
                    var edge = edges[index].edge();
                    if (Math.min(edge.first().horizontal(), edge.second().horizontal()) <= maxX
                            && Math.max(edge.first().horizontal(), edge.second().horizontal()) >= minX
                            && Math.min(edge.first().vertical(), edge.second().vertical()) <= maxY
                            && Math.max(edge.first().vertical(), edge.second().vertical()) >= minY
                            && Math.min(edge.first().forward(), edge.second().forward()) <= maxZ
                            && Math.max(edge.first().forward(), edge.second().forward()) >= minZ)
                        visible.add(edges[index]);
                }
            }
        }
        boolean limited = !pending.isEmpty() || visible.size() > budget;
        visible.sort(Comparator.comparingInt(Indexed::ordinal));
        return new Visible(visible.stream().limit(budget).map(Indexed::edge).toList(), limited, visits);
    }

    private Node build(int start, int end) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int index = start; index < end; index++) {
            var edge = edges[index].edge();
            minX = Math.min(minX, Math.min(edge.first().horizontal(), edge.second().horizontal()));
            minY = Math.min(minY, Math.min(edge.first().vertical(), edge.second().vertical()));
            minZ = Math.min(minZ, Math.min(edge.first().forward(), edge.second().forward()));
            maxX = Math.max(maxX, Math.max(edge.first().horizontal(), edge.second().horizontal()));
            maxY = Math.max(maxY, Math.max(edge.first().vertical(), edge.second().vertical()));
            maxZ = Math.max(maxZ, Math.max(edge.first().forward(), edge.second().forward()));
        }
        Node left = null, right = null;
        if (end - start > LEAF_SIZE) {
            int axis = maxX - minX >= maxY - minY && maxX - minX >= maxZ - minZ ? 0
                    : maxY - minY >= maxZ - minZ ? 1 : 2;
            Arrays.sort(edges, start, end, Comparator.comparingDouble(value -> center(value.edge(), axis)));
            int middle = start + (end - start) / 2;
            left = build(start, middle);
            right = build(middle, end);
        }
        return new Node(minX, minY, minZ, maxX, maxY, maxZ, start, end, left, right);
    }

    private static double center(PlotPreviewGeometry.Edge edge, int axis) {
        return switch (axis) {
            case 0 -> (edge.first().horizontal() + edge.second().horizontal()) / 2;
            case 1 -> (edge.first().vertical() + edge.second().vertical()) / 2;
            default -> (edge.first().forward() + edge.second().forward()) / 2;
        };
    }

    private static boolean intersects(Node node, double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        return node.minX() <= maxX && node.maxX() >= minX && node.minY() <= maxY && node.maxY() >= minY
                && node.minZ() <= maxZ && node.maxZ() >= minZ;
    }

    private static double distance(Node node, double centerX, double centerY, double centerZ) {
        double horizontal = Math.max(node.minX() - centerX, Math.max(0, centerX - node.maxX()));
        double vertical = Math.max(node.minY() - centerY, Math.max(0, centerY - node.maxY()));
        double forward = Math.max(node.minZ() - centerZ, Math.max(0, centerZ - node.maxZ()));
        return horizontal * horizontal + vertical * vertical + forward * forward;
    }
}
