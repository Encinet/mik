package org.encinet.mik.module.communication.tip;

import java.util.List;
import java.util.Objects;

/** Immutable semantic text tree; it deliberately contains no presentation colors. */
public record TipTemplate(List<Node> nodes) {

    public TipTemplate {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.isEmpty()) throw new IllegalArgumentException("Tip content must not be empty");
    }

    public String plainText() {
        return plainText(nodes);
    }

    static String plainText(List<Node> nodes) {
        StringBuilder result = new StringBuilder();
        appendPlainText(nodes, result);
        return result.toString();
    }

    private static void appendPlainText(List<Node> nodes, StringBuilder target) {
        for (Node node : nodes) {
            switch (node) {
                case TextNode text -> target.append(text.text());
                case ElementNode element -> appendPlainText(element.children(), target);
            }
        }
    }

    public sealed interface Node permits TextNode, ElementNode {
    }

    public record TextNode(String text) implements Node {
        public TextNode {
            text = Objects.requireNonNull(text, "text");
        }
    }

    public record ElementNode(String semantic, List<Node> children) implements Node {
        public ElementNode {
            if (semantic == null || semantic.isBlank()) {
                throw new IllegalArgumentException("Semantic tag must not be blank");
            }
            children = List.copyOf(Objects.requireNonNull(children, "children"));
        }
    }
}
