package org.encinet.mik.shared.menu.ui;

/** Semantic identity and preset material opacity; custom themes snapshot both RGB and opacity. */
public enum UiColorRole {
    STATUS(96.0 / 255), WINDOW(.84), SIDEBAR(.86), TOOLBAR(.90), INPUT(.90), TOOLTIP(.90),
    BUTTON(.80), BUTTON_HOVERED(.86), BUTTON_SELECTED(.88), BUTTON_PRESSED(.90), BUTTON_DISABLED(.84),
    TEXT(1), ACCENT(1), SUCCESS(1), WARNING(1), DANGER(1), INFO(1), MUSIC_ACCENT(1), RHYTHM_ACCENT(1);

    private final double alpha;
    UiColorRole(double alpha) { this.alpha = alpha; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
    public double alpha() { return alpha; }
}
