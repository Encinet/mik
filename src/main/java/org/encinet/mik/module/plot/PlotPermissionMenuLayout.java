package org.encinet.mik.module.plot;

import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlotPermissionMenuLayout implements FloatingMenuLayout {
    private static final String ROW_PREFIX = "permission-";
    private static final String HEADING_SUFFIX = "-group-heading";
    private static final int MAX_ROWS = Arrays.stream(PlotPermission.Category.values())
            .mapToInt(category -> category.permissions().size()).max().orElseThrow();
    private static final Set<String> REGIONS = Set.of("heading", "subject", "section", "categories",
            "operation-heading", "result-heading", "hint", "navigation");
    private List<Node> measured = List.of();
    private Map<String, FloatingMenuPose> poses = Map.of();

    @Override
    public FloatingMenuPose pose(Context context) {
        if (!measured.equals(context.nodes())) measure(context.nodes());
        return poses.get(context.elementId());
    }

    private void measure(List<Node> nodes) {
        Map<String, List<Node>> regions = new HashMap<>();
        Map<String, Node> labels = new LinkedHashMap<>();
        Map<String, Node> values = new LinkedHashMap<>();
        for (Node node : nodes) {
            String region = node.region();
            if (REGIONS.contains(region)) {
                regions.computeIfAbsent(region, ignored -> new ArrayList<>()).add(node);
            } else if (region.startsWith(ROW_PREFIX)) {
                boolean label = region.endsWith(HEADING_SUFFIX);
                String key = label ? region.substring(0, region.length() - HEADING_SUFFIX.length()) : region;
                Map<String, Node> column = label ? labels : values;
                if (column.put(key, node) != null) throw new IllegalArgumentException("Duplicate permission row: " + key);
            } else {
                throw new IllegalArgumentException("Unknown permission layout region: " + region);
            }
        }
        if (!labels.keySet().equals(values.keySet()) || labels.isEmpty() || labels.size() > MAX_ROWS)
            throw new IllegalArgumentException("Permission rows require matching labels and states");
        Node heading = single(regions, "heading");
        Node subject = single(regions, "subject");
        Node section = single(regions, "section");
        Node operationHeading = single(regions, "operation-heading");
        Node resultHeading = single(regions, "result-heading");
        List<Node> categories = regions.getOrDefault("categories", List.of());
        List<Node> navigation = regions.getOrDefault("navigation", List.of());
        if (categories.isEmpty() || navigation.isEmpty()) throw new IllegalArgumentException("Permission navigation is missing");
        double unit = navigation.stream().mapToDouble(node -> node.size().height()).min().orElseThrow();
        double columnGap = unit * 0.5;
        double rowGap = unit * 0.25;
        double sidebarWidth = Math.max(subject.size().width(), width(categories));
        double labelWidth = Math.max(operationHeading.size().width(), width(List.copyOf(labels.values())));
        double valueWidth = Math.max(resultHeading.size().width(), width(List.copyOf(values.values())));
        double rowHeight = Math.max(unit,
                Math.max(height(List.copyOf(labels.values())), height(List.copyOf(values.values()))));
        rowHeight = Math.max(rowHeight, height(categories));
        int rowCount = Math.max(6, labels.size());
        double bodyHeight = Math.max(rowCount * rowHeight + (rowCount - 1) * rowGap,
                categories.size() * rowHeight + (categories.size() - 1) * rowGap);
        double bodyWidth = sidebarWidth + labelWidth + valueWidth + columnGap * 2;
        double left = -bodyWidth / 2;
        double labelLeft = left + sidebarWidth + columnGap;
        double valueLeft = labelLeft + labelWidth + columnGap;
        double tableCenter = (labelLeft + valueLeft + valueWidth) / 2;
        double sectionHeight = Math.max(subject.size().height(), section.size().height());
        double columnHeadingHeight = Math.max(operationHeading.size().height(), resultHeading.size().height());
        List<Node> hints = regions.getOrDefault("hint", List.of());
        double hintHeight = hints.stream().mapToDouble(node -> node.size().height() + rowGap).sum();
        double footerHeight = hintHeight + height(navigation) + unit * 0.5;
        double totalHeight = heading.size().height() + sectionHeight + columnHeadingHeight
                + bodyHeight + footerHeight + unit * 1.1;
        double top = totalHeight / 2;
        Map<String, FloatingMenuPose> next = new HashMap<>();
        centered(next, heading, 0, top);
        top -= heading.size().height() + unit * 0.4;
        aligned(next, subject, left, top);
        aligned(next, section, labelLeft, top);
        top -= sectionHeight + unit * 0.35;
        aligned(next, operationHeading, labelLeft, top);
        aligned(next, resultHeading, valueLeft, top);
        top -= columnHeadingHeight + unit * 0.35;
        double rowTop = top;
        for (String key : labels.keySet()) {
            aligned(next, labels.get(key), labelLeft, rowTop);
            aligned(next, values.get(key), valueLeft, rowTop);
            rowTop -= rowHeight + rowGap;
        }
        rowTop = top;
        for (Node category : categories) {
            aligned(next, category, left, rowTop);
            rowTop -= rowHeight + rowGap;
        }
        top -= bodyHeight + unit * 0.5;
        for (Node hint : hints) {
            centered(next, hint, tableCenter, top);
            top -= hint.size().height() + rowGap;
        }
        double navigationWidth = navigation.stream().mapToDouble(node -> node.size().width()).sum()
                + columnGap * (navigation.size() - 1);
        double cursor = tableCenter - navigationWidth / 2;
        for (Node node : navigation) {
            aligned(next, node, cursor, top);
            cursor += node.size().width() + columnGap;
        }
        if (next.size() != nodes.size()) throw new IllegalArgumentException("Unassigned permission node");
        measured = List.copyOf(nodes);
        poses = Map.copyOf(next);
    }

    private static Node single(Map<String, List<Node>> regions, String region) {
        List<Node> nodes = regions.getOrDefault(region, List.of());
        if (nodes.size() != 1) throw new IllegalArgumentException("Expected one permission node in " + region);
        return nodes.getFirst();
    }

    private static double width(List<Node> nodes) {
        return nodes.stream().mapToDouble(node -> node.size().width()).max().orElse(0);
    }

    private static double height(List<Node> nodes) {
        return nodes.stream().mapToDouble(node -> node.size().height()).max().orElse(0);
    }

    private static void aligned(Map<String, FloatingMenuPose> poses, Node node, double left, double top) {
        centered(poses, node, left + node.size().width() / 2, top);
    }

    private static void centered(Map<String, FloatingMenuPose> poses, Node node, double horizontal, double top) {
        poses.put(node.elementId(), FloatingMenuPose.at(new FloatingMenuPoint(horizontal,
                top - node.size().height() / 2, -0.65)));
    }
}
