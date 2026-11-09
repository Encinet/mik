package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuTitleBarLayoutTest {
    @Test void resolvingASceneKeepsEmptyLayoutSlotsAndOnlyVisibleRegionsReservePanelSpace() {
        UiNode visible = Ui.text(Component.text("Content")).key("visible").size(new UiSize(80, 20)).build();
        UiNode empty = Ui.stack().build();
        for (var regions : java.util.List.of(java.util.List.of(visible, empty), java.util.List.of(empty, visible), java.util.List.of(empty, empty))) {
            var content = UiTemplates.window("panel", Ui.layout(UiLayouts.headerBody(UiLength.ui(8)))
                    .children(regions).build());
            var scene = MenuScene.screen("empty-slots").viewport(new UiSize(104, 44)).content(content).build();
            var frame = SceneGeometryTestSupport.measure(scene, 1);
            boolean hasContent = regions.contains(visible);
            assertEquals(hasContent ? new UiSize(104, 44) : UiSize.ZERO, frame.size());
            assertEquals(hasContent ? 1 : 0, frame.panels().size());
            assertDisjoint(frame);
        }
    }

    @Test void aDeclaredCanvasKeepsItsOwnExtentWhenNestedWithoutElements() {
        var canvas = UiTemplates.window("canvas", Ui.layout(UiLayouts.canvas(new UiSize(80, 20), java.util.Map.of())).build());
        for (UiNode content : java.util.List.of(canvas, Ui.stack().child(canvas).build())) {
            var frame = SceneGeometryTestSupport.measure(MenuScene.screen("canvas").content(content).build(), 1);
            assertEquals(new UiSize(104, 44), frame.size());
            assertEquals(1, frame.panels().size());
        }
    }

    @Test void popoverTitlesCenterOnTheirWholePanelWithOrWithoutRefreshAcrossWidthsAndTypeSizes() {
        for (String title : new String[]{"身份","Identity","Community identity and membership"})
            for (boolean refresh : new boolean[]{false,true}) for (double typography : new double[]{.7,1,1.8})
                for (int width : new int[]{160,240,480}) {
                    var popover = MenuPopover.attached(UiKey.named("source"),Component.text(title),
                            Ui.stack().size(new UiSize(width-24,60)).build(),Component.text("Close"));
                    if (refresh) popover = popover.withActions(Ui.button(UiIcons.REFRESH,Component.text("Refresh")).key("refresh").build());
                    var scene = MenuScene.screen("title-test").viewport(new UiSize(width,2048)).content(popover.contentTree()).build();
                    // This fixture tests panel alignment at an authored width, before a host assigns its reading budget.
                    var frame = SceneGeometryTestSupport.naturalFrame(scene,typography);
                    assertDisjoint(frame);
                    var heading = element(frame,"title");
                    var panel = frame.panels().stream().filter(value -> value.path().endsWith("popover")).findFirst().orElseThrow();
                    assertEquals(panel.x()+panel.size().width()/2,heading.x()+heading.size().width()/2,1e-6);
                    assertTrue(frame.size().width() <= width+1e-6);
                }
    }

    @Test void changingTrailingToolsDoesNotMoveTheTitleWhenTheBodyAlreadyDefinesThePanelWidth() {
        UiFrame original = null;
        for (int tools=0; tools<3; tools++) {
            var actions = Ui.row().gap(UiLength.ui(6));
            if (tools > 0) actions.child(Ui.button(UiIcons.CROSS,Component.text("Close")).key("close").build());
            if (tools > 1) actions.child(Ui.button(UiIcons.REFRESH,Component.text("Refresh")).key("refresh").build());
            var header = UiTemplates.titleBar(Ui.heading(Component.text("身份")).key("title").build(),tools==0 ? null : actions.build());
            var content = UiTemplates.window("panel",Ui.layout(UiLayouts.headerBody(UiLength.ui(8)))
                    .child(header).child(Ui.stack().size(new UiSize(360,60)).build()).build());
            var frame = SceneGeometryTestSupport.measure(MenuScene.screen("tools").content(content).build(),1.8);
            assertDisjoint(frame);
            if (original == null) original = frame;
            assertEquals(original.size().width(),frame.size().width());
            var title = element(frame,"title");
            assertEquals(frame.size().width()/2,title.x()+title.size().width()/2,1e-6);
        }
    }

    @Test void wideToolsUseASecondRowInsteadOfSqueezingOrShiftingTheTitle() {
        var content = UiTemplates.titleBar(Ui.heading(Component.text("身份 Identity")).key("title").build(),
                Ui.button(Component.text("Refresh all membership information")).key("refresh").singleLine().textWidth(4096).build());
        var frame = SceneGeometryTestSupport.measure(MenuScene.screen("narrow").viewport(new UiSize(200,400)).content(content).build(),1);
        assertDisjoint(frame);
        var title = element(frame,"title");
        var tools = element(frame,"refresh");
        assertEquals(frame.size().width()/2,title.x()+title.size().width()/2,1e-6);
        assertTrue(tools.y() >= title.y()+title.size().height());
    }
}
