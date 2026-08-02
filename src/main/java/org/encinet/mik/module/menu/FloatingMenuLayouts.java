package org.encinet.mik.module.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Reusable spatial layouts. Custom layouts can implement {@link FloatingMenuLayout} directly. */
public final class FloatingMenuLayouts {
    private static final double MENU_SECTION_GAP = 0.28;
    private static final double MENU_COLUMN_GAP = 0.30;
    private static final double MENU_ROW_GAP = 0.18;
    private static final double MENU_CURVE_DEPTH = 0.18;
    private static final double MENU_NAVIGATION_GAP = 0.22;

    private FloatingMenuLayouts() { }

    /**
     * Standard top-to-bottom application-menu composition. The semantic region
     * factories below keep spacing and curvature consistent across modules.
     */
    public static FloatingMenuLayout menu(Region... regions) {
        return verticalRegions(MENU_SECTION_GAP, regions);
    }

    /** Standard measured action grid, without requiring a named region. */
    public static FloatingMenuLayout actions(int columns) {
        return adaptiveCurvedGrid(columns, MENU_COLUMN_GAP, MENU_ROW_GAP,
                MENU_CURVE_DEPTH);
    }

    /** Standard one-line heading band. */
    public static Region heading(String id) {
        return region(id, adaptiveRow(0.0));
    }

    /** Standard measured action-grid band. */
    public static Region actions(String id, int columns) {
        return region(id, actions(columns));
    }

    /** Standard vertical information band for summaries, warnings, and help. */
    public static Region information(String id) {
        return region(id, adaptiveColumn(0.12));
    }

    /** Standard horizontal navigation band for paging, back, and close controls. */
    public static Region navigation(String id) {
        return region(id, adaptiveArc(MENU_NAVIGATION_GAP, 0.12));
    }

    /** Standard column-major card band for paged item browsers. */
    public static Region cards(String id, int columns, int rowsPerColumn) {
        return region(id, adaptiveCurvedList(columns, rowsPerColumn,
                0.36, MENU_ROW_GAP, 0.20));
    }

    /** Uniform center spacing for equally sized visual nodes. Text menus should use adaptiveGrid. */
    public static FloatingMenuLayout grid(int columns, double horizontalGap, double verticalGap) {
        if (columns < 1 || !positiveFinite(horizontalGap) || !positiveFinite(verticalGap)) {
            throw new IllegalArgumentException();
        }
        return context -> {
            int rows = (context.count() + columns - 1) / columns;
            int column = context.index() % columns;
            int row = context.index() / columns;
            int columnsInRow = Math.min(columns, context.count() - row * columns);
            return at(
                    (column - (columnsInRow - 1) / 2.0) * horizontalGap,
                    ((rows - 1) / 2.0 - row) * verticalGap,
                    0.0);
        };
    }

    /** A grid on a shallow parabolic surface, with each panel following its local tangent. */
    public static FloatingMenuLayout curvedGrid(int columns, double horizontalGap,
                                                double verticalGap, double depth) {
        if (columns < 1 || !positiveFinite(horizontalGap) || !positiveFinite(verticalGap)
                || !Double.isFinite(depth) || depth < 0.0) {
            throw new IllegalArgumentException();
        }
        return context -> {
            int rows = (context.count() + columns - 1) / columns;
            int column = context.index() % columns;
            int row = context.index() / columns;
            int columnsInRow = Math.min(columns, context.count() - row * columns);
            double right = (column - (columnsInRow - 1) / 2.0) * horizontalGap;
            double halfSpan = Math.max(horizontalGap, (columnsInRow - 1) * horizontalGap) / 2.0;
            double normalized = right / halfSpan;
            double columnDepth = depth * normalized * normalized;
            double rowDepth = rows <= 1 ? 0.0 : depth * 0.10 * row / (rows - 1.0);
            double slope = 2.0 * depth * right / (halfSpan * halfSpan);
            return oriented(right,
                    ((rows - 1) / 2.0 - row) * verticalGap,
                    columnDepth + rowDepth,
                    Math.toDegrees(Math.atan(slope)), 0.0);
        };
    }

    /** Uniform center spacing for equally sized nodes. Text lists should use adaptiveList. */
    public static FloatingMenuLayout list(int columns, int rowsPerColumn,
                                          double columnGap, double rowGap) {
        if (columns < 1 || rowsPerColumn < 1
                || !positiveFinite(columnGap) || !positiveFinite(rowGap)) {
            throw new IllegalArgumentException();
        }
        return context -> {
            requireCapacity(context, columns, rowsPerColumn);
            int column = context.index() / rowsPerColumn;
            int row = context.index() % rowsPerColumn;
            int rows = Math.min(rowsPerColumn, context.count() - column * rowsPerColumn);
            int usedColumns = (context.count() + rowsPerColumn - 1) / rowsPerColumn;
            return at(
                    (column - (usedColumns - 1) / 2.0) * columnGap,
                    ((rows - 1) / 2.0 - row) * rowGap,
                    0.0);
        };
    }

    /** A column-major list on a shallow curved surface. */
    public static FloatingMenuLayout curvedList(int columns, int rowsPerColumn,
                                                double columnGap, double rowGap,
                                                double depth) {
        if (columns < 1 || rowsPerColumn < 1
                || !positiveFinite(columnGap) || !positiveFinite(rowGap)
                || !Double.isFinite(depth) || depth < 0.0) {
            throw new IllegalArgumentException();
        }
        FloatingMenuLayout base = list(columns, rowsPerColumn, columnGap, rowGap);
        return context -> {
            FloatingMenuPose basePose = base.pose(context);
            int usedColumns = (context.count() + rowsPerColumn - 1) / rowsPerColumn;
            double halfSpan = Math.max(columnGap, (usedColumns - 1) * columnGap) / 2.0;
            double normalized = basePose.right() / halfSpan;
            double slope = 2.0 * depth * basePose.right() / (halfSpan * halfSpan);
            return oriented(basePose.right(), basePose.up(),
                    depth * normalized * normalized,
                    Math.toDegrees(Math.atan(slope)), 0.0);
        };
    }

    /**
     * Row-major grid whose centers are derived from every node's measured footprint.
     * The gap values represent clear space between surfaces, not distance between centers.
     */
    public static FloatingMenuLayout adaptiveGrid(int columns,
                                                  double columnGap, double rowGap) {
        if (columns < 1 || !nonNegativeFinite(columnGap) || !nonNegativeFinite(rowGap)) {
            throw new IllegalArgumentException();
        }
        return context -> {
            int row = context.index() / columns;
            int rowStart = row * columns;
            int rowEnd = Math.min(context.count(), rowStart + columns);
            List<Double> widths = new ArrayList<>(rowEnd - rowStart);
            for (int index = rowStart; index < rowEnd; index++) {
                widths.add(context.nodes().get(index).size().width());
            }

            int rows = (context.count() + columns - 1) / columns;
            List<Double> heights = new ArrayList<>(rows);
            for (int currentRow = 0; currentRow < rows; currentRow++) {
                double height = 0.0;
                int start = currentRow * columns;
                int end = Math.min(context.count(), start + columns);
                for (int index = start; index < end; index++) {
                    height = Math.max(height, context.nodes().get(index).size().height());
                }
                heights.add(height);
            }
            return at(horizontalCenter(widths, columnGap, context.index() - rowStart),
                    verticalCenter(heights, rowGap, row), 0.0);
        };
    }

    /** Column-major list with content-aware column widths and independent vertical flows. */
    public static FloatingMenuLayout adaptiveList(int columns, int rowsPerColumn,
                                                  double columnGap, double rowGap) {
        if (columns < 1 || rowsPerColumn < 1
                || !nonNegativeFinite(columnGap) || !nonNegativeFinite(rowGap)) {
            throw new IllegalArgumentException();
        }
        return context -> {
            requireCapacity(context, columns, rowsPerColumn);
            int usedColumns = (context.count() + rowsPerColumn - 1) / rowsPerColumn;
            int column = context.index() / rowsPerColumn;
            int row = context.index() % rowsPerColumn;
            List<Double> columnWidths = new ArrayList<>(usedColumns);
            for (int currentColumn = 0; currentColumn < usedColumns; currentColumn++) {
                double width = 0.0;
                int start = currentColumn * rowsPerColumn;
                int end = Math.min(context.count(), start + rowsPerColumn);
                for (int index = start; index < end; index++) {
                    width = Math.max(width, context.nodes().get(index).size().width());
                }
                columnWidths.add(width);
            }
            int start = column * rowsPerColumn;
            int end = Math.min(context.count(), start + rowsPerColumn);
            List<Double> rowHeights = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                rowHeights.add(context.nodes().get(index).size().height());
            }
            return at(horizontalCenter(columnWidths, columnGap, column),
                    verticalCenter(rowHeights, rowGap, row), 0.0);
        };
    }

    public static FloatingMenuLayout adaptiveRow(double gap) {
        if (!nonNegativeFinite(gap)) throw new IllegalArgumentException();
        return context -> at(horizontalCenter(context.nodes().stream()
                .map(node -> node.size().width()).toList(), gap, context.index()), 0.0, 0.0);
    }

    public static FloatingMenuLayout adaptiveColumn(double gap) {
        if (!nonNegativeFinite(gap)) throw new IllegalArgumentException();
        return context -> at(0.0, verticalCenter(context.nodes().stream()
                .map(node -> node.size().height()).toList(), gap, context.index()), 0.0);
    }

    /** A content-aware horizontal arc with a guaranteed clear gap between surfaces. */
    public static FloatingMenuLayout adaptiveArc(double gap, double depth) {
        return curved(adaptiveRow(gap), depth);
    }

    /** Bends any measured planar layout into a shallow spatial surface. */
    public static FloatingMenuLayout curved(FloatingMenuLayout layout, double depth) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(depth) || depth < 0.0) throw new IllegalArgumentException();
        return context -> {
            FloatingMenuPose base = layout.pose(context);
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < context.count(); index++) {
                FloatingMenuPose sibling = layout.pose(context.at(index));
                double halfWidth = context.nodes().get(index).size().width() * 0.5;
                minimum = Math.min(minimum, sibling.right() - halfWidth);
                maximum = Math.max(maximum, sibling.right() + halfWidth);
            }
            double center = (minimum + maximum) * 0.5;
            double halfSpan = Math.max(0.5, (maximum - minimum) * 0.5);
            double localRight = base.right() - center;
            double normalized = localRight / halfSpan;
            double slope = 2.0 * depth * localRight / (halfSpan * halfSpan);
            return oriented(base.right(), base.up(),
                    base.forward() + depth * normalized * normalized,
                    base.yawDegrees() + Math.toDegrees(Math.atan(slope)),
                    base.pitchDegrees());
        };
    }

    /**
     * Bends a measured layout across both axes into a shallow dome. Positions
     * keep the base layout's content-aware clearances, while yaw and pitch make
     * every interactive surface face the viewer from its local tangent.
     */
    public static FloatingMenuLayout domed(FloatingMenuLayout layout,
                                           double horizontalDepth,
                                           double verticalDepth) {
        Objects.requireNonNull(layout, "layout");
        if (!nonNegativeFinite(horizontalDepth) || !nonNegativeFinite(verticalDepth)) {
            throw new IllegalArgumentException("Dome depths must be finite and non-negative");
        }
        return context -> {
            FloatingMenuPose base = layout.pose(context);
            double minimumRight = Double.POSITIVE_INFINITY;
            double maximumRight = Double.NEGATIVE_INFINITY;
            double minimumUp = Double.POSITIVE_INFINITY;
            double maximumUp = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < context.count(); index++) {
                FloatingMenuPose sibling = layout.pose(context.at(index));
                FloatingMenuSize size = context.nodes().get(index).size();
                minimumRight = Math.min(minimumRight,
                        sibling.right() - size.width() * 0.5);
                maximumRight = Math.max(maximumRight,
                        sibling.right() + size.width() * 0.5);
                minimumUp = Math.min(minimumUp,
                        sibling.up() - size.height() * 0.5);
                maximumUp = Math.max(maximumUp,
                        sibling.up() + size.height() * 0.5);
            }
            double centerRight = (minimumRight + maximumRight) * 0.5;
            double centerUp = (minimumUp + maximumUp) * 0.5;
            double halfWidth = Math.max(0.5, (maximumRight - minimumRight) * 0.5);
            double halfHeight = Math.max(0.5, (maximumUp - minimumUp) * 0.5);
            double localRight = base.right() - centerRight;
            double localUp = base.up() - centerUp;
            double horizontal = localRight / halfWidth;
            double vertical = localUp / halfHeight;
            double yawSlope = 2.0 * horizontalDepth * localRight
                    / (halfWidth * halfWidth);
            double pitchSlope = 2.0 * verticalDepth * localUp
                    / (halfHeight * halfHeight);
            return oriented(base.right(), base.up(),
                    base.forward()
                            + horizontalDepth * horizontal * horizontal
                            + verticalDepth * vertical * vertical,
                    base.yawDegrees() + Math.toDegrees(Math.atan(yawSlope)),
                    base.pitchDegrees() + Math.toDegrees(Math.atan(pitchSlope)));
        };
    }

    /** Content-aware row-major grid projected onto a shallow spatial dome. */
    public static FloatingMenuLayout adaptiveDomeGrid(int columns,
                                                      double columnGap,
                                                      double rowGap,
                                                      double horizontalDepth,
                                                      double verticalDepth) {
        return domed(adaptiveGrid(columns, columnGap, rowGap),
                horizontalDepth, verticalDepth);
    }

    public static FloatingMenuLayout adaptiveCurvedGrid(int columns, double columnGap,
                                                        double rowGap, double depth) {
        return curved(adaptiveGrid(columns, columnGap, rowGap), depth);
    }

    public static FloatingMenuLayout adaptiveCurvedList(int columns, int rowsPerColumn,
                                                        double columnGap, double rowGap,
                                                        double depth) {
        return curved(adaptiveList(columns, rowsPerColumn, columnGap, rowGap), depth);
    }

    /** A single horizontal arc whose panels face along the cylindrical surface. */
    public static FloatingMenuLayout arc(double radius, double degrees) {
        validateArc(radius, degrees);
        return context -> {
            double fraction = context.count() == 1
                    ? 0.5 : context.index() / (double) (context.count() - 1);
            double angle = Math.toRadians((fraction - 0.5) * degrees);
            return cylindricalPose(radius, angle, 0.0);
        };
    }

    /** Multiple rows wrapped around one horizontal cylinder. */
    public static FloatingMenuLayout cylindricalGrid(int columns, double radius,
                                                      double degrees, double verticalGap) {
        if (columns < 1 || !positiveFinite(verticalGap)) throw new IllegalArgumentException();
        validateArc(radius, degrees);
        return context -> {
            int rows = (context.count() + columns - 1) / columns;
            int column = context.index() % columns;
            int row = context.index() / columns;
            int columnsInRow = Math.min(columns, context.count() - row * columns);
            double step = columns <= 1 ? 0.0 : Math.toRadians(degrees) / (columns - 1.0);
            double angle = (column - (columnsInRow - 1) / 2.0) * step;
            double up = ((rows - 1) / 2.0 - row) * verticalGap;
            return cylindricalPose(radius, angle, up);
        };
    }

    /** A grid distributed across a shallow spherical surface. */
    public static FloatingMenuLayout sphericalGrid(int columns, double radius,
                                                    double horizontalDegrees,
                                                    double verticalDegrees) {
        if (columns < 1 || !positiveFinite(radius)
                || !validSweep(horizontalDegrees) || !validSweep(verticalDegrees)) {
            throw new IllegalArgumentException();
        }
        return context -> {
            int rows = (context.count() + columns - 1) / columns;
            int column = context.index() % columns;
            int row = context.index() / columns;
            int columnsInRow = Math.min(columns, context.count() - row * columns);
            double horizontalStep = columns <= 1
                    ? 0.0 : Math.toRadians(horizontalDegrees) / (columns - 1.0);
            double yaw = (column - (columnsInRow - 1) / 2.0) * horizontalStep;
            double pitch = rows <= 1 ? 0.0
                    : Math.toRadians(((rows - 1) / 2.0 - row)
                    * verticalDegrees / (rows - 1.0));
            double cosPitch = Math.cos(pitch);
            double right = Math.sin(yaw) * cosPitch * radius;
            double up = Math.sin(pitch) * radius;
            double forward = radius - Math.cos(yaw) * cosPitch * radius;
            return oriented(right, up, forward,
                    Math.toDegrees(yaw), Math.toDegrees(pitch));
        };
    }

    /** A flat radial arrangement; useful when every node should share one facing plane. */
    public static FloatingMenuLayout ring(double radius) {
        if (!positiveFinite(radius)) throw new IllegalArgumentException();
        return context -> {
            double angle = Math.PI * 2 * context.index() / context.count();
            return at(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0);
        };
    }

    /** Places semantic elements at exact three-dimensional points with no local rotation. */
    public static FloatingMenuLayout fixed(Map<String, FloatingMenuPoint> points) {
        Map<String, FloatingMenuPoint> copy = Map.copyOf(points);
        return context -> {
            FloatingMenuPoint point = copy.get(context.elementId());
            if (point == null) throw new IllegalArgumentException(
                    "No fixed position for element '" + context.elementId() + "'");
            return FloatingMenuPose.at(point);
        };
    }

    /** Places semantic elements at exact spatial poses. */
    public static FloatingMenuLayout fixedPoses(Map<String, FloatingMenuPose> poses) {
        Map<String, FloatingMenuPose> copy = Map.copyOf(poses);
        return context -> {
            FloatingMenuPose pose = copy.get(context.elementId());
            if (pose == null) throw new IllegalArgumentException(
                    "No fixed pose for element '" + context.elementId() + "'");
            return pose;
        };
    }

    public static FloatingMenuLayout offset(FloatingMenuLayout layout,
                                            double right, double up, double forward) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward)) {
            throw new IllegalArgumentException("Layout offset must be finite");
        }
        return context -> layout.pose(context).offset(right, up, forward);
    }

    public static FloatingMenuLayout orient(FloatingMenuLayout layout,
                                            double yawDegrees, double pitchDegrees) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Layout orientation must be finite");
        }
        return context -> layout.pose(context).rotate(yawDegrees, pitchDegrees);
    }

    /** Composes independently laid-out subsets selected from element metadata. */
    public static FloatingMenuLayout choose(Predicate<FloatingMenuLayout.Context> selector,
                                            FloatingMenuLayout selected,
                                            FloatingMenuLayout fallback) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(fallback, "fallback");
        return context -> (selector.test(context) ? selected : fallback).pose(context.inRegion());
    }

    /** Assigns an independent layout to each named region. */
    public static FloatingMenuLayout regions(Map<String, FloatingMenuLayout> layouts) {
        return regions(layouts, null);
    }

    public static FloatingMenuLayout regions(Map<String, FloatingMenuLayout> layouts,
                                             FloatingMenuLayout fallback) {
        Map<String, FloatingMenuLayout> copy = Map.copyOf(layouts);
        return context -> {
            FloatingMenuLayout layout = copy.get(context.region());
            if (layout == null) layout = fallback;
            if (layout == null) {
                throw new IllegalArgumentException("No layout for region '" + context.region() + "'");
            }
            return layout.pose(context.inRegion());
        };
    }

    /** Declares one named region for automatic region composition. */
    public static Region region(String id, FloatingMenuLayout layout) {
        return new Region(id, layout);
    }

    /** Stacks present regions from top to bottom using their measured outer bounds. */
    public static FloatingMenuLayout verticalRegions(double gap, Region... regions) {
        return stackedRegions(Axis.VERTICAL, gap, regions);
    }

    /** Places present regions from left to right using their measured outer bounds. */
    public static FloatingMenuLayout horizontalRegions(double gap, Region... regions) {
        return stackedRegions(Axis.HORIZONTAL, gap, regions);
    }

    /** Declares a measured panel composed from one or more semantic regions. */
    public static Panel panel(String id, FloatingMenuLayout layout, String... regions) {
        return new Panel(id, Set.of(regions), layout);
    }

    /**
     * Places independently composed panels from left to right. A panel can use
     * {@link #verticalRegions(double, Region...)} internally, allowing a scene
     * to express browser/detail or content/inspector structures without offsets.
     */
    public static FloatingMenuLayout horizontalPanels(double gap, Panel... panels) {
        return stackedPanels(Axis.HORIZONTAL, gap, panels);
    }

    /** Places independently composed panels from top to bottom. */
    public static FloatingMenuLayout verticalPanels(double gap, Panel... panels) {
        return stackedPanels(Axis.VERTICAL, gap, panels);
    }

    /**
     * Attaches a secondary panel to one edge of a primary panel without moving
     * the primary panel's local origin. The sidecar still participates in scene
     * bounds, collision probing and automatic scale calculation.
     */
    public static FloatingMenuLayout sidecar(Panel primary, Panel secondary,
                                             Side side, double gap) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(secondary, "secondary");
        Objects.requireNonNull(side, "side");
        if (!nonNegativeFinite(gap)) throw new IllegalArgumentException("Sidecar gap must be finite");
        Set<String> overlap = new HashSet<>(primary.regions());
        overlap.retainAll(secondary.regions());
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Sidecar panels share regions " + overlap);
        }
        return context -> {
            boolean inPrimary = primary.regions().contains(context.region());
            boolean inSecondary = secondary.regions().contains(context.region());
            if (!inPrimary && !inSecondary) {
                throw new IllegalArgumentException(
                        "No sidecar panel for region '" + context.region() + "'");
            }
            if (inPrimary) {
                return primary.layout().pose(context.inRegions(primary.regions()));
            }
            int primaryCount = Math.toIntExact(context.nodes().stream()
                    .filter(node -> primary.regions().contains(node.region())).count());
            int secondaryCount = Math.toIntExact(context.nodes().stream()
                    .filter(node -> secondary.regions().contains(node.region())).count());
            if (primaryCount == 0) {
                throw new IllegalArgumentException("A sidecar requires a present primary panel");
            }
            MeasuredPanel primaryBounds = measurePanel(context, primary, primaryCount);
            MeasuredPanel secondaryBounds = measurePanel(context, secondary, secondaryCount);
            FloatingMenuPose local = secondary.layout()
                    .pose(context.inRegions(secondary.regions()));
            Bounds main = primaryBounds.bounds();
            Bounds attached = secondaryBounds.bounds();
            return switch (side) {
                case LEFT -> local.offset(
                        main.minimumRight() - gap - attached.maximumRight(), 0.0, 0.0);
                case RIGHT -> local.offset(
                        main.maximumRight() + gap - attached.minimumRight(), 0.0, 0.0);
                case ABOVE -> local.offset(0.0,
                        main.maximumUp() + gap - attached.minimumUp(), 0.0);
                case BELOW -> local.offset(0.0,
                        main.minimumUp() - gap - attached.maximumUp(), 0.0);
            };
        };
    }

    private static FloatingMenuLayout stackedRegions(Axis axis, double gap, Region... regions) {
        Objects.requireNonNull(axis, "axis");
        if (!nonNegativeFinite(gap)) throw new IllegalArgumentException("Region gap must be finite");
        List<Region> declared = List.of(regions.clone());
        if (declared.isEmpty()) throw new IllegalArgumentException("At least one region is required");
        Set<String> ids = new HashSet<>();
        for (Region region : declared) {
            if (!ids.add(region.id())) {
                throw new IllegalArgumentException("Duplicate region '" + region.id() + "'");
            }
        }
        return context -> {
            List<MeasuredRegion> present = new ArrayList<>();
            for (Region region : declared) {
                long count = context.nodes().stream()
                        .filter(node -> node.region().equals(region.id())).count();
                if (count > 0) present.add(measureRegion(context, region, Math.toIntExact(count)));
            }
            MeasuredRegion target = present.stream()
                    .filter(region -> region.definition().id().equals(context.region()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "No composed layout for region '" + context.region() + "'"));
            FloatingMenuPose local = target.definition().layout().pose(context.inRegion());
            double total = present.stream().mapToDouble(region -> axis.extent(region.bounds())).sum()
                    + gap * Math.max(0, present.size() - 1);
            double cursor = -total * 0.5;
            double shift = 0.0;
            for (MeasuredRegion region : present) {
                double leading = axis.minimum(region.bounds());
                if (region == target) shift = cursor - leading;
                cursor += axis.extent(region.bounds()) + gap;
            }
            return axis == Axis.HORIZONTAL
                    ? local.offset(shift, 0.0, 0.0)
                    : local.offset(0.0, -shift, 0.0);
        };
    }

    private static FloatingMenuLayout stackedPanels(Axis axis, double gap, Panel... panels) {
        Objects.requireNonNull(axis, "axis");
        if (!nonNegativeFinite(gap)) throw new IllegalArgumentException("Panel gap must be finite");
        List<Panel> declared = List.of(panels.clone());
        if (declared.isEmpty()) throw new IllegalArgumentException("At least one panel is required");
        Set<String> panelIds = new HashSet<>();
        Set<String> ownedRegions = new HashSet<>();
        for (Panel panel : declared) {
            if (!panelIds.add(panel.id())) {
                throw new IllegalArgumentException("Duplicate panel '" + panel.id() + "'");
            }
            for (String region : panel.regions()) {
                if (!ownedRegions.add(region)) {
                    throw new IllegalArgumentException("Region '" + region
                            + "' belongs to more than one panel");
                }
            }
        }
        return context -> {
            List<MeasuredPanel> present = new ArrayList<>();
            for (Panel panel : declared) {
                int count = Math.toIntExact(context.nodes().stream()
                        .filter(node -> panel.regions().contains(node.region())).count());
                if (count > 0) present.add(measurePanel(context, panel, count));
            }
            MeasuredPanel target = present.stream()
                    .filter(panel -> panel.definition().regions().contains(context.region()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "No composed panel for region '" + context.region() + "'"));
            FloatingMenuPose local = target.definition().layout()
                    .pose(context.inRegions(target.definition().regions()));
            double total = present.stream().mapToDouble(panel -> axis.extent(panel.bounds())).sum()
                    + gap * Math.max(0, present.size() - 1);
            double cursor = -total * 0.5;
            double shift = 0.0;
            for (MeasuredPanel panel : present) {
                double leading = axis.minimum(panel.bounds());
                if (panel == target) shift = cursor - leading;
                cursor += axis.extent(panel.bounds()) + gap;
            }
            return axis == Axis.HORIZONTAL
                    ? local.offset(shift, 0.0, 0.0)
                    : local.offset(0.0, -shift, 0.0);
        };
    }

    private static MeasuredRegion measureRegion(FloatingMenuLayout.Context context,
                                                Region region, int count) {
        double minimumRight = Double.POSITIVE_INFINITY;
        double maximumRight = Double.NEGATIVE_INFINITY;
        double minimumUp = Double.POSITIVE_INFINITY;
        double maximumUp = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            FloatingMenuLayout.Context localContext = context.region(region.id(), index);
            FloatingMenuPose pose = region.layout().pose(localContext);
            FloatingMenuSize size = localContext.size();
            minimumRight = Math.min(minimumRight, pose.right() - size.width() * 0.5);
            maximumRight = Math.max(maximumRight, pose.right() + size.width() * 0.5);
            minimumUp = Math.min(minimumUp, pose.up() - size.height() * 0.5);
            maximumUp = Math.max(maximumUp, pose.up() + size.height() * 0.5);
        }
        return new MeasuredRegion(region,
                new Bounds(minimumRight, maximumRight, minimumUp, maximumUp));
    }

    private static MeasuredPanel measurePanel(FloatingMenuLayout.Context context,
                                              Panel panel, int count) {
        double minimumRight = Double.POSITIVE_INFINITY;
        double maximumRight = Double.NEGATIVE_INFINITY;
        double minimumUp = Double.POSITIVE_INFINITY;
        double maximumUp = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            FloatingMenuLayout.Context localContext = context.regions(panel.regions(), index);
            FloatingMenuPose pose = panel.layout().pose(localContext);
            FloatingMenuSize size = localContext.size();
            minimumRight = Math.min(minimumRight, pose.right() - size.width() * 0.5);
            maximumRight = Math.max(maximumRight, pose.right() + size.width() * 0.5);
            minimumUp = Math.min(minimumUp, pose.up() - size.height() * 0.5);
            maximumUp = Math.max(maximumUp, pose.up() + size.height() * 0.5);
        }
        return new MeasuredPanel(panel,
                new Bounds(minimumRight, maximumRight, minimumUp, maximumUp));
    }

    private static FloatingMenuPose at(double right, double up, double forward) {
        return FloatingMenuPose.at(new FloatingMenuPoint(right, up, forward));
    }

    private static FloatingMenuPose oriented(double right, double up, double forward,
                                             double yawDegrees, double pitchDegrees) {
        return FloatingMenuPose.oriented(new FloatingMenuPoint(right, up, forward),
                yawDegrees, pitchDegrees);
    }

    private static FloatingMenuPose cylindricalPose(double radius, double angle, double up) {
        return oriented(Math.sin(angle) * radius, up,
                radius - Math.cos(angle) * radius,
                Math.toDegrees(angle), 0.0);
    }

    private static void requireCapacity(FloatingMenuLayout.Context context,
                                        int columns, int rowsPerColumn) {
        if (context.count() > columns * rowsPerColumn) {
            throw new IllegalArgumentException("List layout capacity exceeded: "
                    + context.count() + " > " + (columns * rowsPerColumn));
        }
    }

    private static void validateArc(double radius, double degrees) {
        if (!positiveFinite(radius) || !validSweep(degrees)) throw new IllegalArgumentException();
    }

    private static boolean validSweep(double degrees) {
        return positiveFinite(degrees) && degrees <= 180.0;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean nonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static double horizontalCenter(List<Double> sizes, double gap, int index) {
        double total = sizes.stream().mapToDouble(Double::doubleValue).sum()
                + gap * Math.max(0, sizes.size() - 1);
        double cursor = -total * 0.5;
        for (int current = 0; current < index; current++) cursor += sizes.get(current) + gap;
        return cursor + sizes.get(index) * 0.5;
    }

    private static double verticalCenter(List<Double> sizes, double gap, int index) {
        double total = sizes.stream().mapToDouble(Double::doubleValue).sum()
                + gap * Math.max(0, sizes.size() - 1);
        double cursor = total * 0.5;
        for (int current = 0; current < index; current++) cursor -= sizes.get(current) + gap;
        return cursor - sizes.get(index) * 0.5;
    }

    public record Region(String id, FloatingMenuLayout layout) {
        public Region {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Region id must not be blank");
            layout = Objects.requireNonNull(layout, "layout");
        }
    }

    public record Panel(String id, Set<String> regions, FloatingMenuLayout layout) {
        public Panel {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Panel id must not be blank");
            regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
            if (regions.isEmpty() || regions.stream().anyMatch(region -> region == null || region.isBlank())) {
                throw new IllegalArgumentException("Panel regions must not be empty or blank");
            }
            layout = Objects.requireNonNull(layout, "layout");
        }
    }

    public enum Side {
        LEFT,
        RIGHT,
        ABOVE,
        BELOW
    }

    private enum Axis {
        HORIZONTAL {
            @Override double minimum(Bounds bounds) { return bounds.minimumRight(); }
            @Override double extent(Bounds bounds) { return bounds.width(); }
        },
        VERTICAL {
            @Override double minimum(Bounds bounds) { return bounds.minimumUp(); }
            @Override double extent(Bounds bounds) { return bounds.height(); }
        };

        abstract double minimum(Bounds bounds);
        abstract double extent(Bounds bounds);
    }

    private record Bounds(double minimumRight, double maximumRight,
                          double minimumUp, double maximumUp) {
        double width() { return maximumRight - minimumRight; }
        double height() { return maximumUp - minimumUp; }
    }

    private record MeasuredRegion(Region definition, Bounds bounds) {
    }

    private record MeasuredPanel(Panel definition, Bounds bounds) {
    }
}
