package org.encinet.mik.shared.menu.ui;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Bounded structural traversal is shared by declaration resolution, indexing and compilation. */
public final class UiTree {
    public static final int MAX_DEPTH = 64, MAX_NODES = 4096;
    private UiTree() { }
    public static void check(UiNode root) { visit(root, (_, _) -> { },true); }
    public static void visit(UiNode root, BiConsumer<String, UiNode> visitor) {
        visit(root,visitor,false);
    }
    private static void visit(UiNode root,BiConsumer<String,UiNode> visitor,boolean alternatives) {
        var pending = new ArrayDeque<Visit>();
        pending.add(new Visit(Objects.requireNonNull(root), "root", 0));
        int count = 0;
        while (!pending.isEmpty()) {
            Visit current = pending.removeLast();
            if (current.depth > MAX_DEPTH || ++count > MAX_NODES)
                throw new IllegalArgumentException("UI tree budget exceeded at " + current.path);
            visitor.accept(current.path, current.node);
            if (alternatives && current.node instanceof UiAlternative alternative) {
                pending.addLast(new Visit(alternative.fallback(),current.path + "/$fallback",current.depth + 1));
                pending.addLast(new Visit(alternative.preferred(),current.path + "/$preferred",current.depth + 1));
                continue;
            }
            var children = current.node instanceof UiBranch branch ? branch.children()
                    : current.node instanceof UiExcerpt excerpt ? excerpt.children()
                    : current.node instanceof UiPagedList list ? list.children()
                    : current.node instanceof UiWindowedList list ? list.children()
                    : current.node instanceof UiWindowedGrid grid ? grid.items().children()
                    : current.node instanceof UiDocument document ? document.children()
                    : current.node instanceof UiPagedTable table ? table.children()
                    : current.node instanceof UiWindowedTable table ? table.children()
                    : current.node instanceof UiPagedLayout paged ? paged.content().children()
                    : current.node instanceof UiPagedFlow flow ? flow.items() : java.util.List.<UiChild>of();
            for (int index = children.size() - 1; index >= 0; index--) {
                UiChild child = children.get(index);
                pending.addLast(new Visit(child.node(), current.path + "/" + child.scope().value(), current.depth + 1));
            }
        }
    }
    private record Visit(UiNode node, String path, int depth) { }
}
