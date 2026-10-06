package org.encinet.mik.module.plot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;

final class PlotPreviewGeometry {
    private static final double SPACING = 0.45;

    private PlotPreviewGeometry() {
    }

    static List<Edge> selection(PlotSelectionShape shape) {
        return shape.composite() ? core(shape.cells()) : box(shape.alignedBounds());
    }

    static boolean sameBoundary(List<Edge> first, List<Edge> second) {
        return first.equals(second) || first.size() == second.size()
                && new HashSet<>(first).equals(new HashSet<>(second));
    }

    static List<Edge> box(PlotSelection.Bounds bounds) {
        List<Edge> edges = new ArrayList<>(12);
        double[] horizontal = {bounds.minimumX(), (double) bounds.maximumX() + 1};
        double[] vertical = {bounds.minimumY(), (double) bounds.maximumY() + 1};
        double[] depth = {bounds.minimumZ(), (double) bounds.maximumZ() + 1};
        for (double height : vertical) {
            for (double forward : depth) {
                edges.add(new Edge(new Point(horizontal[0], height, forward),
                        new Point(horizontal[1], height, forward)));
            }
        }
        for (double right : horizontal) {
            for (double forward : depth) {
                edges.add(new Edge(new Point(right, vertical[0], forward),
                        new Point(right, vertical[1], forward)));
            }
        }
        for (double right : horizontal) {
            for (double height : vertical) {
                edges.add(new Edge(new Point(right, height, depth[0]),
                        new Point(right, height, depth[1])));
            }
        }
        return List.copyOf(edges);
    }

    static List<Edge> core(Set<PlotGeometry.Cell> cells) {
        return cells.isEmpty() ? List.of() : core(cells, bounds(cells));
    }

    static PlotSelection.Bounds bounds(Set<PlotGeometry.Cell> cells) {
        int minX = Integer.MAX_VALUE, minY = minX, minZ = minX;
        int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
        for (PlotGeometry.Cell cell : cells) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            minX = Math.min(minX, cell.x()); minY = Math.min(minY, cell.y()); minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x()); maxY = Math.max(maxY, cell.y()); maxZ = Math.max(maxZ, cell.z());
        }
        int size = PlotGeometry.CELL;
        return new PlotSelection.Bounds(minX * size, minY * size, minZ * size,
                maxX * size + size - 1, maxY * size + size - 1, maxZ * size + size - 1);
    }

    static boolean solid(Set<PlotGeometry.Cell> cells, PlotSelection.Bounds bounds) {
        long width = bounds.width() / PlotGeometry.CELL;
        long height = bounds.height() / PlotGeometry.CELL;
        long depth = bounds.depth() / PlotGeometry.CELL;
        long count = cells.size();
        return height > 0 && depth > 0 && width <= count / height && width * height <= count / depth
                && width * height * depth == count;
    }

    static List<Edge> core(Set<PlotGeometry.Cell> source, PlotSelection.Bounds bounds) {
        if (source.isEmpty()) return List.of();
        if (solid(source, bounds)) return box(bounds);
        Set<PlotGeometry.Cell> cells = new HashSet<>(source);
        Map<Line, TreeSet<Integer>> segments = new HashMap<>();
        for (PlotGeometry.Cell cell : cells) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (surrounded(cells, cell)) continue;
            for (int offsetFirst = 0; offsetFirst <= 1; offsetFirst++) {
                for (int offsetSecond = 0; offsetSecond <= 1; offsetSecond++) {
                    add(segments, cells, new Line(Axis.X, cell.y() + offsetFirst,
                            cell.z() + offsetSecond), cell.x());
                    add(segments, cells, new Line(Axis.Y, cell.x() + offsetFirst,
                            cell.z() + offsetSecond), cell.y());
                    add(segments, cells, new Line(Axis.Z, cell.x() + offsetFirst,
                            cell.y() + offsetSecond), cell.z());
                }
            }
        }
        List<Edge> edges = new ArrayList<>();
        List<Line> lines = segments.keySet().stream().sorted(Comparator.comparing(Line::axis)
                .thenComparingInt(Line::fixedFirst).thenComparingInt(Line::fixedSecond)).toList();
        for (Line line : lines) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int start = 0;
            int end = 0;
            boolean active = false;
            for (int position : segments.get(line)) {
                if (!active) {
                    start = position;
                    end = position + 1;
                    active = true;
                } else if (position == end) {
                    end++;
                } else {
                    edges.add(new Edge(line.point(start), line.point(end)));
                    start = position;
                    end = position + 1;
                }
            }
            if (active) edges.add(new Edge(line.point(start), line.point(end)));
        }
        return List.copyOf(edges);
    }

    private static boolean surrounded(Set<PlotGeometry.Cell> cells, PlotGeometry.Cell cell) {
        return cells.contains(new PlotGeometry.Cell(cell.x() - 1, cell.y(), cell.z()))
                && cells.contains(new PlotGeometry.Cell(cell.x() + 1, cell.y(), cell.z()))
                && cells.contains(new PlotGeometry.Cell(cell.x(), cell.y() - 1, cell.z()))
                && cells.contains(new PlotGeometry.Cell(cell.x(), cell.y() + 1, cell.z()))
                && cells.contains(new PlotGeometry.Cell(cell.x(), cell.y(), cell.z() - 1))
                && cells.contains(new PlotGeometry.Cell(cell.x(), cell.y(), cell.z() + 1));
    }

    private static void add(Map<Line, TreeSet<Integer>> segments, Set<PlotGeometry.Cell> cells,
                            Line line, int position) {
        boolean lowerLower = cells.contains(line.cell(position, -1, -1));
        boolean lowerUpper = cells.contains(line.cell(position, -1, 0));
        boolean upperLower = cells.contains(line.cell(position, 0, -1));
        boolean upperUpper = cells.contains(line.cell(position, 0, 0));
        int occupied = (lowerLower ? 1 : 0) + (lowerUpper ? 1 : 0)
                + (upperLower ? 1 : 0) + (upperUpper ? 1 : 0);
        if (occupied == 1 || occupied == 3 || occupied == 2
                && (lowerLower && upperUpper || lowerUpper && upperLower)) {
            segments.computeIfAbsent(line, ignored -> new TreeSet<>()).add(position);
        }
    }

    static List<Point> sample(List<Edge> edges, Point viewer, double distance, int budget) {
        if (!Double.isFinite(distance) || distance <= 0 || budget < 0)
            throw new IllegalArgumentException("Invalid preview limits");
        if (budget == 0) return List.of();
        List<Edge> visible = edges.stream().map(edge -> clip(edge, viewer, distance))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(edge -> nearestDistanceSquared(edge, viewer)))
                .limit(Math.max(1, budget / 4)).toList();
        LinkedHashSet<Point> points = new LinkedHashSet<>();
        for (Edge edge : visible) {
            if (points.size() < budget) points.add(edge.first());
            if (points.size() < budget) points.add(edge.second());
        }
        int available = budget - points.size();
        double length = visible.stream().mapToDouble(Edge::length).sum();
        int[] interior = new int[visible.size()];
        int[] desired = new int[visible.size()];
        int remaining = available;
        for (int index = 0; index < visible.size(); index++) {
            double edgeLength = visible.get(index).length();
            desired[index] = (int) Math.min(budget, Math.max(0, Math.ceil(edgeLength / SPACING) - 1));
            interior[index] = length == 0 ? 0 : Math.min(desired[index],
                    (int) Math.floor(available * edgeLength / length));
            remaining -= interior[index];
        }
        while (remaining > 0) {
            boolean allocated = false;
            for (int index = 0; index < visible.size() && remaining > 0; index++) {
                if (interior[index] < desired[index]) {
                    interior[index]++;
                    remaining--;
                    allocated = true;
                }
            }
            if (!allocated) break;
        }
        for (int index = 0; index < visible.size(); index++) {
            Edge edge = visible.get(index);
            for (int step = 1; step <= interior[index]; step++) {
                points.add(edge.at((double) step / (interior[index] + 1)));
            }
        }
        return List.copyOf(points);
    }

    private static Edge clip(Edge edge, Point viewer, double distance) {
        double length = edge.length();
        if (length == 0) return viewer.distanceSquared(edge.first()) <= distance * distance ? edge : null;
        Point direction = new Point((edge.second().horizontal() - edge.first().horizontal()) / length,
                (edge.second().vertical() - edge.first().vertical()) / length,
                (edge.second().forward() - edge.first().forward()) / length);
        double projection = (viewer.horizontal() - edge.first().horizontal()) * direction.horizontal()
                + (viewer.vertical() - edge.first().vertical()) * direction.vertical()
                + (viewer.forward() - edge.first().forward()) * direction.forward();
        double horizontal = viewer.horizontal() - edge.first().horizontal() - projection * direction.horizontal();
        double vertical = viewer.vertical() - edge.first().vertical() - projection * direction.vertical();
        double forward = viewer.forward() - edge.first().forward() - projection * direction.forward();
        double perpendicular = horizontal * horizontal + vertical * vertical + forward * forward;
        if (perpendicular > distance * distance) return null;
        double span = Math.sqrt(distance * distance - perpendicular);
        double start = Math.max(0, projection - span);
        double end = Math.min(length, projection + span);
        return start > end ? null : new Edge(edge.at(start / length), edge.at(end / length));
    }

    private static double nearestDistanceSquared(Edge edge, Point viewer) {
        double length = edge.length();
        if (length == 0) return viewer.distanceSquared(edge.first());
        double projection = ((viewer.horizontal() - edge.first().horizontal()) * (edge.second().horizontal() - edge.first().horizontal())
                + (viewer.vertical() - edge.first().vertical()) * (edge.second().vertical() - edge.first().vertical())
                + (viewer.forward() - edge.first().forward()) * (edge.second().forward() - edge.first().forward())) / (length * length);
        return viewer.distanceSquared(edge.at(Math.clamp(projection, 0, 1)));
    }

    record Point(double horizontal, double vertical, double forward) {
        double distanceSquared(Point other) {
            double rightDistance = horizontal - other.horizontal();
            double upDistance = vertical - other.vertical();
            double forwardDistance = forward - other.forward();
            return rightDistance * rightDistance + upDistance * upDistance
                    + forwardDistance * forwardDistance;
        }
    }

    record Edge(Point first, Point second) {
        double length() { return Math.sqrt(first.distanceSquared(second)); }

        Point at(double fraction) {
            return new Point(first.horizontal() + (second.horizontal() - first.horizontal()) * fraction,
                    first.vertical() + (second.vertical() - first.vertical()) * fraction,
                    first.forward() + (second.forward() - first.forward()) * fraction);
        }
    }

    private enum Axis { X, Y, Z }

    private record Line(Axis axis, int fixedFirst, int fixedSecond) {
        @Override
        public int hashCode() {
            return new PlotGeometry.Cell(axis.ordinal(), fixedFirst, fixedSecond).hashCode();
        }

        PlotGeometry.Cell cell(int position, int offsetFirst, int offsetSecond) {
            return switch (axis) {
                case X -> new PlotGeometry.Cell(position, fixedFirst + offsetFirst, fixedSecond + offsetSecond);
                case Y -> new PlotGeometry.Cell(fixedFirst + offsetFirst, position, fixedSecond + offsetSecond);
                case Z -> new PlotGeometry.Cell(fixedFirst + offsetFirst, fixedSecond + offsetSecond, position);
            };
        }

        Point point(int position) {
            double size = PlotGeometry.CELL;
            return switch (axis) {
                case X -> new Point(position * size, fixedFirst * size, fixedSecond * size);
                case Y -> new Point(fixedFirst * size, position * size, fixedSecond * size);
                case Z -> new Point(fixedFirst * size, fixedSecond * size, position * size);
            };
        }
    }
}
