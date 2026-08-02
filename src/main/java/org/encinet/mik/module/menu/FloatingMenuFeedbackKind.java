package org.encinet.mik.module.menu;

import net.kyori.adventure.text.format.NamedTextColor;

public enum FloatingMenuFeedbackKind {
    INFO(NamedTextColor.AQUA, 1.25F),
    SUCCESS(NamedTextColor.GREEN, 1.55F),
    WARNING(NamedTextColor.YELLOW, 0.85F),
    ERROR(NamedTextColor.RED, 0.55F);

    private final NamedTextColor color;
    private final float pitch;

    FloatingMenuFeedbackKind(NamedTextColor color, float pitch) {
        this.color = color;
        this.pitch = pitch;
    }

    public NamedTextColor color() { return color; }
    public float pitch() { return pitch; }
}
