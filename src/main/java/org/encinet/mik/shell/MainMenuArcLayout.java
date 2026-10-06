package org.encinet.mik.shell;

import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPose;

final class MainMenuArcLayout implements FloatingMenuLayout {

    private final FloatingMenuLayout panels = FloatingMenuLayouts.panoramicPanels("hub", 0.6,
            FloatingMenuLayouts.panel("profile",
                    FloatingMenuLayouts.menu(
                            FloatingMenuLayouts.heading("profile-heading"),
                            FloatingMenuLayouts.information("profile")),
                    "profile-heading", "profile"),
            FloatingMenuLayouts.panel("plots",
                    FloatingMenuLayouts.menu(
                            FloatingMenuLayouts.actions("plot-actions", 1),
                            FloatingMenuLayouts.actions("plot-notice", 1)),
                    "plot-actions", "plot-notice"),
            FloatingMenuLayouts.panel("hub",
                    FloatingMenuLayouts.menu(
                            FloatingMenuLayouts.heading("heading"),
                            FloatingMenuLayouts.region("destinations", FloatingMenuLayouts.adaptiveGrid(2, 0.3, 0.18)),
                            FloatingMenuLayouts.actions("quick", 2),
                            FloatingMenuLayouts.actions("more", 1),
                            FloatingMenuLayouts.navigation("footer")),
                    "heading", "destinations", "quick", "more", "footer"),
            FloatingMenuLayouts.panel("governance",
                    FloatingMenuLayouts.verticalPanels(0.18,
                            FloatingMenuLayouts.panel("entry",
                                    FloatingMenuLayouts.actions(1), "governance-actions"),
                            FloatingMenuLayouts.panel("voting",
                                    FloatingMenuLayouts.topAlignedPanels(0.3,
                                            FloatingMenuLayouts.panel("votes",
                                                    FloatingMenuLayouts.menu(
                                                            FloatingMenuLayouts.heading("governance-heading"),
                                                            FloatingMenuLayouts.information("governance-vote"),
                                                            FloatingMenuLayouts.navigation("governance-pages"),
                                                            FloatingMenuLayouts.actions("governance-ballot", 3)),
                                                    "governance-heading", "governance-vote", "governance-pages", "governance-ballot"),
                                            FloatingMenuLayouts.panel("results",
                                                    FloatingMenuLayouts.menu(
                                                            FloatingMenuLayouts.heading("governance-results-heading"),
                                                            FloatingMenuLayouts.cards("governance-results", 1, 2)),
                                                    "governance-results-heading", "governance-results")),
                                    "governance-heading", "governance-vote", "governance-pages", "governance-ballot",
                                    "governance-results-heading", "governance-results")),
                    "governance-actions", "governance-heading", "governance-vote", "governance-pages", "governance-ballot",
                    "governance-results-heading", "governance-results"),
            FloatingMenuLayouts.panel("links",
                    FloatingMenuLayouts.menu(
                            FloatingMenuLayouts.heading("links-heading"),
                            FloatingMenuLayouts.actions("links", 1)),
                    "links-heading", "links"));

    @Override
    public FloatingMenuPose pose(Context context) {
        return panels.pose(context);
    }
}
