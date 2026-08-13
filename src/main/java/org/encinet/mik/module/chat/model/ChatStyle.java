package org.encinet.mik.module.chat.model;

/** Platform-neutral visible text styling. */
public record ChatStyle(
        Integer color,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough
) {
    public static final ChatStyle EMPTY = new ChatStyle(
            null, false, false, false, false);

    public ChatStyle {
        if (color != null && (color < 0 || color > 0xFFFFFF)) {
            throw new IllegalArgumentException("chat color is outside RGB range");
        }
    }

    public ChatStyle withColor(Integer value) {
        return new ChatStyle(value, bold, italic, underlined, strikethrough);
    }
}
