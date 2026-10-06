package org.encinet.mik.module.plot;

import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;

final class PlotMiniatureGeometry {
    static final int GRID_SIDE = 128;
    static final int HEIGHT_LAYERS = 384;
    static final int MAX_SAMPLES = 32768;
    static final double TILT = 35;
    static final double MODEL_BOTTOM = -1.37;

    private PlotMiniatureGeometry() { }

    record Grid(PlotSelection.Bounds bounds, int stepX, int stepY, int stepZ) {
        Grid(PlotSelection.Bounds bounds) {
            this(bounds, horizontalStep(bounds));
        }

        private Grid(PlotSelection.Bounds bounds, int horizontalStep) {
            this(bounds, Math.max(step(bounds.width()), horizontalStep), verticalStep(bounds.height()),
                    Math.max(step(bounds.depth()), horizontalStep));
        }

        static Grid withinHeight(PlotSelection.Bounds bounds, int minimumHeight, int maximumHeight) {
            int minimumY = Math.max(bounds.minimumY(), minimumHeight);
            int maximumY = Math.min(bounds.maximumY(), maximumHeight - 1);
            return new Grid(minimumY <= maximumY ? new PlotSelection.Bounds(bounds.minimumX(), minimumY,
                    bounds.minimumZ(), bounds.maximumX(), maximumY, bounds.maximumZ()) : bounds);
        }

        int columns() { return count(bounds.width(), stepX); }
        int rows() { return count(bounds.height(), stepY); }
        int depths() { return count(bounds.depth(), stepZ); }
        boolean exact() { return stepX == 1 && stepY == 1 && stepZ == 1; }
        int count() { return columns() * rows() * depths(); }
        int index(int column, int row, int depth) { return (row * depths() + depth) * columns() + column; }
        int column(int index) { return index % columns(); }
        int row(int index) { return index / (columns() * depths()); }
        int depth(int index) { return index / columns() % depths(); }

        Box box(int index) {
            double minimumX = bounds.minimumX() + (long) column(index) * stepX;
            double minimumY = bounds.minimumY() + (long) row(index) * stepY;
            double minimumZ = bounds.minimumZ() + (long) depth(index) * stepZ;
            double width = Math.min(stepX, bounds.maximumX() + 1.0 - minimumX);
            double height = Math.min(stepY, bounds.maximumY() + 1.0 - minimumY);
            double depth = Math.min(stepZ, bounds.maximumZ() + 1.0 - minimumZ);
            return new Box(minimumX + width / 2, minimumY + height / 2, minimumZ + depth / 2,
                    width, height, depth);
        }

        Box block(int index) {
            Box sample = box(index);
            return new Box(Math.floor(sample.worldX()) + 0.5, Math.floor(sample.worldY()) + 0.5,
                    Math.floor(sample.worldZ()) + 0.5, 1, 1, 1);
        }

        Box box(int column, int row, int depth, int columns, int rows, int depths) {
            Box first = box(index(column, row, depth));
            Box last = box(index(column + columns - 1, row + rows - 1, depth + depths - 1));
            double minimumX = first.worldX() - first.width() / 2;
            double minimumY = first.worldY() - first.height() / 2;
            double minimumZ = first.worldZ() - first.depth() / 2;
            double width = last.worldX() + last.width() / 2 - minimumX;
            double height = last.worldY() + last.height() / 2 - minimumY;
            double length = last.worldZ() + last.depth() / 2 - minimumZ;
            return new Box(minimumX + width / 2, minimumY + height / 2, minimumZ + length / 2,
                    width, height, length);
        }

        private static int horizontalStep(PlotSelection.Bounds bounds) {
            int minimum = 1;
            int maximum = (int) Math.min(Integer.MAX_VALUE, Math.max(bounds.width(), bounds.depth()));
            int rows = count(bounds.height(), verticalStep(bounds.height()));
            while (minimum < maximum) {
                int candidate = minimum + (maximum - minimum) / 2;
                long samples = (long) count(bounds.width(), Math.max(step(bounds.width()), candidate))
                        * count(bounds.depth(), Math.max(step(bounds.depth()), candidate)) * rows;
                if (samples <= MAX_SAMPLES) maximum = candidate;
                else minimum = candidate + 1;
            }
            return minimum;
        }

        private static int step(long length) { return (int) Math.max(1, (length + GRID_SIDE - 1) / GRID_SIDE); }
        private static int verticalStep(long length) {
            return (int) Math.max(1, (length + HEIGHT_LAYERS - 1) / HEIGHT_LAYERS);
        }
        private static int count(long length, int step) { return (int) ((length + step - 1) / step); }
    }

    record Box(double worldX, double worldY, double worldZ, double width, double height, double depth) { }

    record Projection(PlotSelection.Bounds bounds, double rotation, double tilt) {
        Projection(PlotSelection.Bounds bounds, double rotation) { this(bounds, rotation, TILT); }

        double scale() {
            if (tilt == 90) return Math.min(2.75 / Math.hypot(bounds.width(), bounds.depth()), 1.8 / bounds.depth());
            double pitch = Math.toRadians(tilt);
            double depth = bounds.height() * Math.sin(pitch) + bounds.depth() * Math.cos(pitch);
            return Math.min(2.75 / Math.hypot(bounds.width(), depth), 1.8 / verticalSpan());
        }

        double centerUp() { return MODEL_BOTTOM + verticalSpan() * scale() / 2; }

        double heightScale() { return tilt == 90 ? Math.min(scale(), 1.0 / bounds.height()) : scale(); }

        private double verticalSpan() {
            if (tilt == 90) return bounds.depth();
            double pitch = Math.toRadians(tilt);
            return bounds.height() * Math.cos(pitch) + bounds.depth() * Math.sin(pitch);
        }

        FloatingMenuPoint point(double worldX, double worldY, double worldZ) {
            double horizontal = (worldX - (bounds.minimumX() + (double) bounds.maximumX() + 1) / 2) * scale();
            double vertical = (worldY - (bounds.minimumY() + (double) bounds.maximumY() + 1) / 2) * heightScale();
            double depth = (worldZ - (bounds.minimumZ() + (double) bounds.maximumZ() + 1) / 2) * scale();
            double yaw = Math.toRadians(rotation);
            double pitch = Math.toRadians(tilt);
            return new FloatingMenuPoint(horizontal * Math.cos(yaw)
                    - vertical * Math.sin(yaw) * Math.sin(pitch) - depth * Math.sin(yaw) * Math.cos(pitch),
                    centerUp() + vertical * Math.cos(pitch) - depth * Math.sin(pitch),
                    0.65 + horizontal * Math.sin(yaw)
                            + vertical * Math.cos(yaw) * Math.sin(pitch) + depth * Math.cos(yaw) * Math.cos(pitch));
        }

        FloatingMenuPose pose(Box box) {
            return FloatingMenuPose.oriented(point(box.worldX(), box.worldY(), box.worldZ()), rotation, tilt);
        }

        boolean contains(double worldX, double worldY, double worldZ) {
            return worldX >= bounds.minimumX() && worldX <= bounds.maximumX() + 1.0
                    && worldY >= bounds.minimumY() && worldY <= bounds.maximumY() + 1.0
                    && worldZ >= bounds.minimumZ() && worldZ <= bounds.maximumZ() + 1.0;
        }
    }

    static Box clipped(PlotPreviewGeometry.Edge edge, PlotSelection.Bounds bounds) {
        double minimumX = Math.max(bounds.minimumX(), Math.min(edge.first().horizontal(), edge.second().horizontal()));
        double maximumX = Math.min(bounds.maximumX() + 1.0, Math.max(edge.first().horizontal(), edge.second().horizontal()));
        double minimumY = Math.max(bounds.minimumY(), Math.min(edge.first().vertical(), edge.second().vertical()));
        double maximumY = Math.min(bounds.maximumY() + 1.0, Math.max(edge.first().vertical(), edge.second().vertical()));
        double minimumZ = Math.max(bounds.minimumZ(), Math.min(edge.first().forward(), edge.second().forward()));
        double maximumZ = Math.min(bounds.maximumZ() + 1.0, Math.max(edge.first().forward(), edge.second().forward()));
        if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) return null;
        if (minimumX == maximumX && minimumY == maximumY && minimumZ == maximumZ) return null;
        return new Box((minimumX + maximumX) / 2, (minimumY + maximumY) / 2, (minimumZ + maximumZ) / 2,
                maximumX - minimumX, maximumY - minimumY, maximumZ - minimumZ);
    }
}
