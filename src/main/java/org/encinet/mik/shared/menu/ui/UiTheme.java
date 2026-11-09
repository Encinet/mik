package org.encinet.mik.shared.menu.ui;

/** Shared reading and tool materials. Materials resolve through each player's selected palette. */
public final class UiTheme {
    /** One text-relative drawing canvas; inherited typography scales icons and adjacent text together. */
    public static final UiLength ICON_SIZE = UiLength.ui(16);
    /** Minimum single-line control height, shared by text and icon buttons and scaled with typography. */
    public static final UiLength CONTROL_MIN_HEIGHT = UiLength.ui(21);
    public static final UiStyle INPUT = UiStyle.CONTROL.withBackground(UiColor.themed(UiColorRole.INPUT));
    public static final UiStyle WINDOW = UiStyle.PANEL;
    public static final UiStyle SIDEBAR = UiStyle.PANEL.withBackground(UiColor.themed(UiColorRole.SIDEBAR));
    public static final UiStyle TOOLBAR = UiStyle.PANEL.withBackground(UiColor.themed(UiColorRole.TOOLBAR))
            .withPadding(UiInsets.all(UiLength.ui(8)));
    public static final UiStyle TOOLTIP = UiStyle.PLAIN.withBackground(UiColor.themed(UiColorRole.TOOLTIP));
    public static final UiStyle HEADING = UiStyle.PLAIN.withTextScale(1.12);
    public static final UiStyle TITLE = UiStyle.PLAIN.withTextScale(1.25);

    private UiTheme() { }
}
