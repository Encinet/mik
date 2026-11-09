package org.encinet.mik.shared.menu.ui;

import java.util.Locale;

/** All built-in menu RGB values live here. Palettes keep readable text and semantic feedback on dark surfaces. */
public enum UiPalette implements UiThemeColors {
    CHARCOAL("charcoal", 0x202020, 0x181818, 0x2A2A2A, 0x141414,
            0x282828, 0x303030, 0x383838, 0x404040, 0x202020, 0xD8E4E8),
    WARM("warm", 0x27221E, 0x1D1916, 0x342D27, 0x191511,
            0x302922, 0x383027, 0x40372D, 0x483E33, 0x28221C, 0xFFD39B),
    FOREST("forest", 0x1D2621, 0x161D19, 0x27342C, 0x111914,
            0x233028, 0x2A382E, 0x314036, 0x38483D, 0x1D2821, 0xB4E6C5);

    public static final UiPalette DEFAULT = CHARCOAL;
    private final String id;
    private final int window, sidebar, toolbar, input, button, hovered, selected, pressed, disabled, accent;

    UiPalette(String id, int window, int sidebar, int toolbar, int input, int button,
              int hovered, int selected, int pressed, int disabled, int accent) {
        this.id = id; this.window = window; this.sidebar = sidebar; this.toolbar = toolbar; this.input = input;
        this.button = button; this.hovered = hovered; this.selected = selected; this.pressed = pressed;
        this.disabled = disabled; this.accent = accent;
    }

    public String id() { return id; }
    public static UiPalette fromId(String id) {
        if (id != null) for (var value : values()) if (value.id.equals(id.toLowerCase(Locale.ROOT))) return value;
        return DEFAULT;
    }
    public int rgb(UiColorRole role) {
        return switch (role) {
            case STATUS -> 0x000000;
            case WINDOW, TOOLTIP -> window;
            case SIDEBAR -> sidebar;
            case TOOLBAR -> toolbar;
            case INPUT -> input;
            case BUTTON -> button;
            case BUTTON_HOVERED -> hovered;
            case BUTTON_SELECTED -> selected;
            case BUTTON_PRESSED -> pressed;
            case BUTTON_DISABLED -> disabled;
            case TEXT -> 0xFFFFFF;
            case ACCENT -> accent;
            case SUCCESS -> 0xA0E6A8;
            case WARNING -> 0xFFDD88;
            case DANGER -> 0xFFC0C0;
            case INFO -> 0xD0D0FF;
            case MUSIC_ACCENT -> 0xA0E6EC;
            case RHYTHM_ACCENT -> 0xFFC0DA;
        };
    }
    @Override public double alpha(UiColorRole role) { return role.alpha(); }
}
