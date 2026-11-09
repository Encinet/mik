package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayDeque;
import java.util.Deque;

/** Compact collection labels; callers retain the complete component in details or a tooltip. */
public final class UiText {
    private UiText() { }

    public static Component abbreviate(Component content, int maximumCodePoints) {
        return abbreviate(content, maximumCodePoints, Integer.MAX_VALUE);
    }

    public static Component abbreviate(Component content, int maximumCodePoints, int maximumLines) {
        if (maximumCodePoints < 2) throw new IllegalArgumentException("A text preview needs content and an ellipsis");
        if (maximumLines < 1) throw new IllegalArgumentException("A text preview needs at least one line");
        String plain = PlainTextComponentSerializer.plainText().serialize(content);
        int lineLimit = plain.codePointCount(0, plain.length());
        int lines = 1, index = 0;
        for (int offset = 0; offset < plain.length(); index++) {
            int point = plain.codePointAt(offset);
            if (point == '\n' && ++lines > maximumLines) { lineLimit = index; break; }
            offset += Character.charCount(point);
        }
        if (lineLimit == plain.codePointCount(0, plain.length()) && lineLimit <= maximumCodePoints) return content;
        int budget = Math.min(maximumCodePoints - 1, lineLimit);
        var result = Component.text();
        ComponentFlattener.basic().flatten(content, new FlattenerListener() {
            private final Deque<Style> styles = new ArrayDeque<>();
            private int remaining = budget;
            @Override public void pushStyle(Style style) {
                styles.push(styles.isEmpty() ? style : style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
            }
            @Override public void popStyle(Style style) { styles.pop(); }
            @Override public void component(String text) {
                int length = Math.min(remaining, text.codePointCount(0, text.length()));
                if (length == 0) return;
                result.append(Component.text(text.substring(0, text.offsetByCodePoints(0, length)))
                        .style(styles.isEmpty() ? Style.empty() : styles.peek()));
                remaining -= length;
            }
        });
        return result.append(Component.text("…")).build();
    }
}
