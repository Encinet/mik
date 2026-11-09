package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotMenuLayoutsTest {
    @Test void plotPagesUseAStableViewerCenteredFrame() {
        var scene = PlotMenuLayouts.screen("plot-test", FloatingMenuAppearance.DEFAULT,
                PlotMenuLayouts.browser(UiTemplates.list("cards"))).build();
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION, scene.anchorMode());
        assertEquals(FloatingMenuSpatialFrame.IN_FRONT, scene.spatialFrame());
        assertEquals(42, scene.framing().horizontalHalfAngleDegrees());
    }

    @Test void directoryHasOneCurrentLocationLinkAndIndependentListTools() {
        var full = frame(fixtureBuilder(PlotMenuLayouts.directory(UiTemplates.list("cards")), "heading", "here", "filters", "search", "cards", "actions", "navigation").build());
        assertDisjoint(full);
        assertEquals(2, full.panels().size());
        assertTrue(element(full, "here").y() < element(full, "filters").y());
        assertTrue(element(full, "filters").y() < element(full, "cards").y());
        assertTrue(element(full, "actions").y() > element(full, "cards").y());
    }

    @Test void detailAndSecondaryPagesOwnTheirContentWithoutOverlaps() {
        check(PlotMenuLayouts.workspace(UiReadingPosition.START,_ -> {}), "context", "heading", "summary", "notice", "tasks", "navigation");
        check(PlotMenuLayouts.settings(true,0,_ -> {}), "context", "heading", "settings", "ownership-heading", "owner", "ownership", "identifier", "navigation");
        check(PlotMenuLayouts.members(UiTemplates.list("members")), "context", "heading", "members-heading", "members", "hint", "groups-heading", "groups", "actions", "navigation");
        assertThrows(IllegalArgumentException.class, () -> fixture(PlotMenuLayouts.workspace(UiReadingPosition.START,_ -> {}), "settings"));
        check(PlotMenuLayouts.choices(0, _ -> { }), "heading", "rows", "navigation");
        check(PlotMenuLayouts.help(UiTemplates.information("instructions")), "heading", "instructions", "navigation");
        check(PlotMenuLayouts.noticeBoard(UiTemplates.information("body")), "body", "navigation");
        check(PlotMenuLayouts.atmosphere(0, _ -> { }), "context", "heading", "sections", "options", "navigation");
        check(PlotMenuLayouts.member(), "context", "heading", "identity", "navigation", "actions");
        check(PlotMenuLayouts.record(UiTemplates.information("record-reading")), "record-reading", "navigation", "actions");
    }

    @Test void editorKeepsTheMapBetweenToolsAndInspector() {
        var scene = fixtureBuilder(PlotEditorComposition.create(Ui.stack().key("map").size(new UiSize(240, 160)).build(),UiTemplates.list("regions")), "heading", "selection", "hint", "points", "composition",
                "region-heading", "regions", "region-selection", "region-actions", "commit", "history", "view", "navigation")
                .viewport(PlotEditorComposition.VIEWPORT).build();
        var frame = frame(scene);
        assertDisjoint(frame);
        assertTrue(element(frame, "points").x() + element(frame, "points").size().width() < frame.focus().x() - new UiSize(240, 160).width() / 2);
        assertTrue(element(frame, "region-actions").x() > frame.focus().x() + new UiSize(240, 160).width() / 2);
        assertTrue(element(frame, "heading").y() < frame.focus().y() - new UiSize(240, 160).height() / 2);
        assertTrue(element(frame, "view").y() > frame.focus().y() + new UiSize(240, 160).height() / 2);
        assertTrue(element(frame, "commit").y() > element(frame, "view").y());
    }

    @Test void visitorWorkspaceDoesNotLeaveAnEmptyManagementColumn() {
        var visitor = fixture(PlotMenuLayouts.workspace(UiReadingPosition.START,_ -> {}), "heading", "summary", "navigation");
        assertEquals(2, frame(visitor).panels().size());
    }

    private static void check(UiNode composition, String... ports) {
        var scene = fixture(composition, ports);
        assertEquals(ports.length, scene.entries().size());
        assertDisjoint(frame(scene));
    }

}
