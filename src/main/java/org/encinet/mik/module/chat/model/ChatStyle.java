package org.encinet.mik.module.chat.model;

/** Platform-neutral visible text styling. */
public record ChatStyle(
        Integer color,
        Integer gradientEndColor,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough
) {
    public static final ChatStyle EMPTY = new ChatStyle(
            null, null, false, false, false, false);

    public ChatStyle {
        validateColor(color, "color");
        validateColor(gradientEndColor, "gradientEndColor");
        if (gradientEndColor != null && color == null) {
            throw new IllegalArgumentException(
                    "chat gradient requires a start color");
        }
    }

    public ChatStyle withColor(Integer value) {
        return new ChatStyle(value, null, bold, italic, underlined, strikethrough);
    }

    private static void validateColor(Integer value, String name) {
        if (value != null && (value < 0 || value > 0xFFFFFF)) {
            throw new IllegalArgumentException(
                    "chat " + name + " is outside RGB range");
        }
    }
}
