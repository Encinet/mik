package org.encinet.mik.module.menu;

/**
 * Semantic role of a node in a floating scene.
 *
 * <p>The role is deliberately separate from interaction state. An item or block can be
 * passive scene content, while information is always non-interactive.</p>
 */
public enum FloatingMenuNodeRole {
    INFORMATION(FloatingMenuElementStyle.TEXT, false),
    CONTROL(FloatingMenuElementStyle.TEXT, true),
    NAVIGATION(FloatingMenuElementStyle.TEXT, true),
    ITEM(FloatingMenuElementStyle.ITEM, true),
    BLOCK(FloatingMenuElementStyle.BLOCK, true);

    private final FloatingMenuElementStyle visualStyle;
    private final boolean interactiveByDefault;

    FloatingMenuNodeRole(FloatingMenuElementStyle visualStyle,
                         boolean interactiveByDefault) {
        this.visualStyle = visualStyle;
        this.interactiveByDefault = interactiveByDefault;
    }

    public FloatingMenuElementStyle visualStyle() {
        return visualStyle;
    }

    boolean interactiveByDefault() {
        return interactiveByDefault;
    }

    boolean supportsActions() {
        return this != INFORMATION;
    }

    boolean supportsPassivePresentation() {
        return this == ITEM || this == BLOCK;
    }
}
