package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Declares the reading child before measurement, while preserving the parent's layout algorithm. */
public record UiFocusLayout(UiLayout content, UiKey target) implements UiLayout {
    public UiFocusLayout { Objects.requireNonNull(content); Objects.requireNonNull(target); }

    @Override public Result layout(MeasureScope scope, UiConstraints constraints) {
        Result result = content.layout(scope, constraints);
        for (int index = 0; index < scope.count(); index++) if (scope.key(index).equals(target)) {
            UiSize size = scope.measuredSize(index);
            return size.width() > 0 && size.height() > 0 ? result.focusing(target) : result;
        }
        throw new IllegalArgumentException("Missing reading region: " + target);
    }

    /** Only an explicit focus chain can choose between siblings; unrelated sidebars never steal the budget. */
    static String readingPath(UiNode root) {
        return focused(root,"root",0,new int[]{UiTree.MAX_NODES}).orElse("root");
    }

    private static java.util.Optional<String> focused(UiNode node, String path, int depth, int[] remaining) {
        if (depth > UiTree.MAX_DEPTH || --remaining[0] < 0) throw new IllegalArgumentException("UI focus chain exceeds its traversal budget");
        if (node instanceof UiAlternative alternative) {
            if (alternative.preferred() == alternative.fallback()) return focused(alternative.preferred(),path,depth + 1,remaining);
            var preferred = focused(alternative.preferred(),path,depth + 1,remaining);
            var fallback = focused(alternative.fallback(),path,depth + 1,remaining);
            return preferred.equals(fallback) ? preferred : java.util.Optional.empty();
        }
        if (node instanceof UiPagedTable || node instanceof UiWindowedTable) return java.util.Optional.of(path + "/pages");
        if (node instanceof UiDocument document && document.bodyReading()) return java.util.Optional.of(path + "/items");
        if (!(node instanceof UiBranch branch)) return java.util.Optional.empty();
        if (branch.layout() instanceof UiFocusLayout focus) {
            UiChild child = branch.children().stream().filter(value -> value.scope().equals(focus.target()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing reading region: " + focus.target()));
            String next = path + "/" + child.scope().value();
            return java.util.Optional.of(focused(child.node(),next,depth + 1,remaining).orElse(next));
        }
        if (branch.children().size() == 1) {
            UiChild child = branch.children().getFirst();
            return focused(child.node(),path + "/" + child.scope().value(),depth + 1,remaining);
        }
        return java.util.Optional.empty();
    }
}
