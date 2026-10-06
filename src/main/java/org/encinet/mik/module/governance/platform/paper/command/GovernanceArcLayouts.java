package org.encinet.mik.module.governance.platform.paper.command;

import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuLayouts;

final class GovernanceArcLayouts {

    private GovernanceArcLayouts() {
    }

    static FloatingMenuLayout dashboard() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("status", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("status-heading"),
                        FloatingMenuLayouts.information("status")), "status-heading", "status"),
                FloatingMenuLayouts.panel("voting", FloatingMenuLayouts.topAlignedPanels(0.3,
                        FloatingMenuLayouts.panel("results", FloatingMenuLayouts.menu(
                                FloatingMenuLayouts.heading("results-heading"),
                                FloatingMenuLayouts.cards("results", 1, 2),
                                FloatingMenuLayouts.navigation("results-navigation")),
                                "results-heading", "results", "results-navigation"),
                        FloatingMenuLayouts.panel("votes", FloatingMenuLayouts.menu(
                                FloatingMenuLayouts.heading("votes-heading"),
                                FloatingMenuLayouts.cards("open-votes", 1, 3),
                                FloatingMenuLayouts.navigation("vote-pagination")),
                                "votes-heading", "open-votes", "vote-pagination")),
                        "results-heading", "results", "results-navigation",
                        "votes-heading", "open-votes", "vote-pagination"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("vote-detail"),
                        FloatingMenuLayouts.actions("ballot", 3),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", "vote-detail", "ballot", "navigation"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("actions-heading"),
                        FloatingMenuLayouts.actions("actions", 1)), "actions-heading", "actions"));
    }

    static FloatingMenuLayout history() {
        return FloatingMenuLayouts.panoramicPanels("detail", 0.6,
                FloatingMenuLayouts.panel("list", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.cards("votes", 1, 5),
                        FloatingMenuLayouts.navigation("pagination")), "votes", "pagination"),
                FloatingMenuLayouts.panel("detail", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("detail")), "heading", "detail"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.navigation("navigation")), "navigation"));
    }

    static FloatingMenuLayout petitions() {
        return FloatingMenuLayouts.panoramicPanels("focus", 0.6,
                FloatingMenuLayouts.panel("left", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.cards("petitions-left", 1, 3)), "petitions-left"),
                FloatingMenuLayouts.panel("focus", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.navigation("pagination"),
                        FloatingMenuLayouts.navigation("navigation")),
                        "heading", "pagination", "navigation"),
                FloatingMenuLayouts.panel("right", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.cards("petitions-right", 1, 3)), "petitions-right"));
    }

    static FloatingMenuLayout player() {
        return FloatingMenuLayouts.panoramicPanels("profile", 0.6,
                FloatingMenuLayouts.panel("profile", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("report")), "heading", "report"),
                FloatingMenuLayouts.panel("actions", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.actions("actions", 1),
                        FloatingMenuLayouts.navigation("navigation")), "actions", "navigation"));
    }

    static FloatingMenuLayout status() {
        return FloatingMenuLayouts.panoramicPanels("status", 0.6,
                FloatingMenuLayouts.panel("status", FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.information("status"),
                        FloatingMenuLayouts.navigation("navigation")), "heading", "status", "navigation"));
    }
}
