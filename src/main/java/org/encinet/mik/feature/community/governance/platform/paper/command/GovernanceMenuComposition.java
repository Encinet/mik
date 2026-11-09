package org.encinet.mik.feature.community.governance.platform.paper.command;

import org.encinet.mik.shared.menu.ui.*;

/** Governance pages distinguish navigation, the current matter and its available decisions. */
final class GovernanceMenuComposition {
    private GovernanceMenuComposition() { }

    static UiNode player(java.util.UUID target,UiNode reading) {
        return scope(target,UiTemplates.sheet(UiTemplates.window("profile", UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(reading.key(),UiLength.ui(12)))
                        .child(UiTemplates.title("heading")).child(reading).build(),reading.key())),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation")))));
    }

    static UiNode status(java.util.UUID target) {
        return scope(target,UiTemplates.sheet(UiTemplates.window("status", UiTemplates.reading(UiKey.named("status"), UiTemplates.title("heading"),
                        UiTemplates.information("status").withSummaryLines(1))),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation"))));
    }

    private static UiNode scope(java.util.UUID target,UiNode content) {
        return Ui.column().key("player-scope").child(UiKey.item(target),content).build();
    }

}
