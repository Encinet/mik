package org.encinet.mik.module.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@FunctionalInterface
public interface FloatingMenuLayout {
    FloatingMenuPose pose(Context context);

    /** Compatibility view for callers that only need coordinates. */
    default FloatingMenuPoint position(Context context) {
        return pose(context).point();
    }

    /** Immutable measured node supplied to layouts without exposing renderer internals. */
    record Node(String elementId, FloatingMenuElementStyle style,
                FloatingMenuNodeRole role, String region, FloatingMenuSize size) {
        public Node {
            if (elementId == null || elementId.isBlank()) {
                throw new IllegalArgumentException("Invalid element id");
            }
            style = Objects.requireNonNull(style, "style");
            role = Objects.requireNonNull(role, "role");
            if (region == null || region.isBlank()) {
                throw new IllegalArgumentException("Invalid region");
            }
            size = Objects.requireNonNull(size, "size");
        }
    }

    /**
     * Complete measured scene plus the current node and its region-local view.
     * Custom layouts can therefore account for every sibling's footprint.
     */
    final class Context {
        private final int index;
        private final List<Node> nodes;
        private final int regionIndex;
        private final List<Node> regionNodes;

        public Context(int index, List<Node> nodes) {
            this(index, immutableNodes(nodes), regionIndex(index, nodes),
                    regionNodes(index, nodes));
        }

        private Context(int index, List<Node> nodes,
                        int regionIndex, List<Node> regionNodes) {
            if (index < 0 || index >= nodes.size()) {
                throw new IllegalArgumentException("Invalid layout index");
            }
            if (regionIndex < 0 || regionIndex >= regionNodes.size()) {
                throw new IllegalArgumentException("Invalid region layout index");
            }
            this.index = index;
            this.nodes = nodes;
            this.regionIndex = regionIndex;
            this.regionNodes = regionNodes;
        }

        public int index() { return index; }
        public int count() { return nodes.size(); }
        public Node node() { return nodes.get(index); }
        public String elementId() { return node().elementId(); }
        public FloatingMenuElementStyle style() { return node().style(); }
        public FloatingMenuNodeRole role() { return node().role(); }
        public String region() { return node().region(); }
        public FloatingMenuSize size() { return node().size(); }
        public List<Node> nodes() { return nodes; }
        public int regionIndex() { return regionIndex; }
        public int regionCount() { return regionNodes.size(); }
        public List<Node> regionNodes() { return regionNodes; }

        /** Returns the same measured scene focused on another global node. */
        public Context at(int nextIndex) {
            return new Context(nextIndex, nodes);
        }

        /** Returns a region-local context suitable for an independently composed layout. */
        public Context inRegion() {
            return new Context(regionIndex, regionNodes, regionIndex, regionNodes);
        }

        /** Returns a region-local context focused on a specific sibling. */
        public Context inRegion(int nextRegionIndex) {
            return new Context(nextRegionIndex, regionNodes,
                    nextRegionIndex, regionNodes);
        }

        /** Finds a named region in the complete scene and focuses one of its nodes. */
        public Context region(String region, int nextRegionIndex) {
            List<Node> selected = nodes.stream()
                    .filter(node -> node.region().equals(region)).toList();
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("Unknown or empty region '" + region + "'");
            }
            return new Context(nextRegionIndex, selected, nextRegionIndex, selected);
        }

        /**
         * Returns a panel-local scene containing several semantic regions while
         * retaining each node's region identity for nested composition.
         */
        public Context inRegions(Set<String> regions) {
            List<Node> selected = nodesInRegions(regions);
            int selectedIndex = selected.indexOf(node());
            if (selectedIndex < 0) {
                throw new IllegalArgumentException("Current node is outside the requested regions");
            }
            return new Context(selectedIndex, selected);
        }

        /** Focuses one node inside a multi-region panel-local scene. */
        public Context regions(Set<String> regions, int nextIndex) {
            List<Node> selected = nodesInRegions(regions);
            return new Context(nextIndex, selected);
        }

        private List<Node> nodesInRegions(Set<String> regions) {
            Set<String> requested = Set.copyOf(Objects.requireNonNull(regions, "regions"));
            if (requested.isEmpty()) {
                throw new IllegalArgumentException("At least one region is required");
            }
            List<Node> selected = nodes.stream()
                    .filter(candidate -> requested.contains(candidate.region())).toList();
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("Requested regions are empty");
            }
            return selected;
        }

        private static List<Node> immutableNodes(List<Node> nodes) {
            List<Node> copy = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
            if (copy.isEmpty()) throw new IllegalArgumentException("Layout scene must not be empty");
            return copy;
        }

        private static int regionIndex(int index, List<Node> nodes) {
            validateIndex(index, nodes);
            String region = nodes.get(index).region();
            int local = 0;
            for (int current = 0; current < index; current++) {
                if (nodes.get(current).region().equals(region)) local++;
            }
            return local;
        }

        private static List<Node> regionNodes(int index, List<Node> nodes) {
            validateIndex(index, nodes);
            String region = nodes.get(index).region();
            List<Node> selected = new ArrayList<>();
            for (Node node : nodes) if (node.region().equals(region)) selected.add(node);
            return List.copyOf(selected);
        }

        private static void validateIndex(int index, List<Node> nodes) {
            Objects.requireNonNull(nodes, "nodes");
            if (index < 0 || index >= nodes.size()) {
                throw new IllegalArgumentException("Invalid layout index");
            }
        }
    }
}
