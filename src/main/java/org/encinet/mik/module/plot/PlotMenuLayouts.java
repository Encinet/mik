package org.encinet.mik.module.plot;

import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuLayouts;

public final class PlotMenuLayouts {

    private PlotMenuLayouts() {
    }

    public static FloatingMenuDefinition.Builder screen(String id, FloatingMenuAppearance appearance,
                                                        FloatingMenuLayout layout) {
        return FloatingMenuDefinition.screen(id).appearance(appearance)
                .stableAnchor().frontArc().framing(FloatingMenuFraming.WIDE_ARC)
                .layout(layout);
    }

    public static FloatingMenuLayout browser(String entries, String summary) {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("list", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards(entries, 3, 4)), entries),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information(summary),
                        FloatingMenuLayouts.actions("here", 1),
                        FloatingMenuLayouts.navigation("pagination"),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", summary, "here", "pagination", "navigation"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards("actions", 2, 8)), "actions"));
    }

    public static FloatingMenuLayout detail() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("building", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("building-heading"),
                        FloatingMenuLayouts.information("selection"),
                        FloatingMenuLayouts.balancedCards("construction", 2, 8)),
                        "building-heading", "selection", "construction"),
                FloatingMenuLayouts.panel("subplots", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("subplots-heading"),
                        FloatingMenuLayouts.balancedCards("subplots", 2, 8)), "subplots-heading", "subplots"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("summary"),
                        FloatingMenuLayouts.information("notice"),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", "summary", "notice", "navigation"),
                FloatingMenuLayouts.panel("settings", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("settings-heading"),
                        FloatingMenuLayouts.balancedCards("settings", 2, 8),
                        FloatingMenuLayouts.actions("ownership", 1)),
                        "settings-heading", "settings", "ownership"),
                FloatingMenuLayouts.panel("arrival", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("arrival-heading"),
                        FloatingMenuLayouts.balancedCards("arrival-actions", 2, 8)),
                        "arrival-heading", "arrival-actions"));
    }

    public static FloatingMenuLayout access() {
        return FloatingMenuLayouts.menu(
                FloatingMenuLayouts.heading("heading"),
                FloatingMenuLayouts.actions("project", 1),
                FloatingMenuLayouts.actions("rows", 1),
                FloatingMenuLayouts.actions("ownership", 1),
                FloatingMenuLayouts.navigation("navigation"));
    }

    public static FloatingMenuLayout permissionTable() {
        return new PlotPermissionMenuLayout();
    }

    public static FloatingMenuLayout help() {
        return FloatingMenuLayouts.menu(
                FloatingMenuLayouts.heading("heading"),
                FloatingMenuLayouts.information("instructions"),
                FloatingMenuLayouts.navigation("navigation"));
    }

    public static FloatingMenuLayout noticeBoard() {
        return FloatingMenuLayouts.menu(
                FloatingMenuLayouts.heading("heading"),
                FloatingMenuLayouts.information("body"),
                FloatingMenuLayouts.navigation("pagination"),
                FloatingMenuLayouts.navigation("navigation"));
    }

    public static FloatingMenuLayout editor() {
        return FloatingMenuLayouts.workbench(3.2, 2.2,
                FloatingMenuLayouts.panel("selection-controls", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards("points", 2, 8),
                        FloatingMenuLayouts.balancedCards("composition", 2, 8),
                        FloatingMenuLayouts.information("region-heading"),
                        FloatingMenuLayouts.balancedCards("regions", 2, 8),
                        FloatingMenuLayouts.navigation("region-pagination")),
                        "points", "composition", "region-heading", "regions", "region-pagination"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("selection"),
                        FloatingMenuLayouts.information("hint")), "heading", "selection", "hint"),
                FloatingMenuLayouts.panel("operations", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("region-selection"),
                        FloatingMenuLayouts.balancedCards("region-actions", 2, 8),
                        FloatingMenuLayouts.balancedCards("operations", 2, 8),
                        FloatingMenuLayouts.balancedCards("view", 2, 8)),
                        "region-selection", "region-actions", "operations", "view"),
                FloatingMenuLayouts.panel("navigation", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.navigation("navigation")), "navigation"));
    }

    public static FloatingMenuLayout atmosphere() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("time", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("time-heading"),
                        FloatingMenuLayouts.balancedCards("times", 3, 4)), "time-heading", "times"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("weather-heading"),
                        FloatingMenuLayouts.navigation("navigation")),
                        "weather-heading", "navigation"),
                FloatingMenuLayouts.panel("weather", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards("weather-options", 2, 8)), "weather-options"));
    }

    public static FloatingMenuLayout member() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.actions("identity", 1),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", "identity", "navigation"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards("actions", 2, 8)), "actions"));
    }

    public static FloatingMenuLayout record() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("metadata", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("metadata")), "metadata"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("detail"),
                        FloatingMenuLayouts.navigation("pagination"),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", "detail", "pagination", "navigation"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.balancedCards("actions", 2, 8)), "actions"));
    }

}
