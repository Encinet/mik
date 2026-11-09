package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.format.TextColor;

/** A complete, immutable source of colors and opacities, selected independently by each viewer. */
public sealed interface UiThemeColors permits UiPalette, UiCustomTheme {
    String id();
    int rgb(UiColorRole role);
    double alpha(UiColorRole role);
    default TextColor color(UiColorRole role) { return TextColor.color(rgb(role)); }
    default int argb(UiColorRole role) { return UiColor.themed(role).argb(this); }
}
