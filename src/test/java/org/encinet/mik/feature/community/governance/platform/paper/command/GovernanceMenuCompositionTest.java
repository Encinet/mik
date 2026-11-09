package org.encinet.mik.feature.community.governance.platform.paper.command;

import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class GovernanceMenuCompositionTest {
    @Test void allGovernanceViewsOwnTheirOptionalAndRequiredContent() {
        var player = fixture(GovernanceMenuComposition.player(new java.util.UUID(0,1),reader()), "heading", "report", "actions", "navigation");
        var status = fixture(GovernanceMenuComposition.status(new java.util.UUID(0,1)), "heading", "status", "navigation");
        for (var scene : java.util.List.of(player, status)) {
            assertDisjoint(frame(scene));
            assertFalse(scene.entries().isEmpty());
        }
    }

    @Test void sparseViewsCollapseEmptyGroupsAndRejectUnownedContent() {
        var sparse = fixture(GovernanceMenuComposition.status(new java.util.UUID(0,1)), "heading", "navigation", "status");
        assertEquals(3, frame(sparse).elements().size());
        assertDisjoint(frame(sparse));
        assertThrows(IllegalArgumentException.class, () -> fixture(GovernanceMenuComposition.status(new java.util.UUID(0,1)), "unknown"));
    }

    @Test void governanceCompositionsUseTheSharedContentSizingRuleInBothDirections() {
        record Page(UiNode content, String... ports) { }
        var pages = java.util.List.of(
                new Page(GovernanceMenuComposition.player(new java.util.UUID(0,1),reader()), "heading", "report", "actions", "navigation"),
                new Page(GovernanceMenuComposition.status(new java.util.UUID(0,1)), "heading", "status", "navigation"));
        for (var page : pages) for (var scale : FloatingMenuTextScale.values()) {
            var large = MenuScene.screen("governance-fixture").content(page.content());
            var small = MenuScene.screen("governance-fixture").content(page.content());
            for (String port : page.ports()) {
                large.information(port, Component.text("Long_Player_Name999 · Appointment\nEligible voters: 16 · Quorum: 4\nCurrent vote and participation record")).region(port);
                small.information(port, Component.text("None")).region(port);
            }
            var session = new SceneGeometryTestSupport.LayoutSession();
            var expanded = large.build();
            var collapsed = small.build();
            var before = session.naturalFrame(expanded, scale.factor());
            var after = session.naturalFrame(collapsed, scale.factor());
            var restored = session.naturalFrame(expanded, scale.factor());
            assertSameLayout(SceneGeometryTestSupport.naturalFrame(collapsed, scale.factor()), after, page.content().key() + " @ " + scale);
            assertTrue(after.size().height() < before.size().height());
            assertEquals(before.panels(), restored.panels());
            assertDisjoint(after);
        }
    }

    private static UiNode reader() {
        return UiTemplates.information("report").document(UiReadingPosition.START,_ -> {}).readingBody();
    }
}
