package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element;
import static org.junit.jupiter.api.Assertions.*;

class MenuNodeFeedbackTest {
    @Test void automaticFullTextHelpDoesNotTurnReadOnlyContentIntoAnAction() {
        var full = Component.text("A long read-only profile value that needs abbreviation");
        var scene = MenuScene.screen("profile").viewport(new UiSize(160,45)).content(Ui.column()
                .child(Ui.text(full).key("value").summaryLines(1).build())
                .child(Ui.text(Component.text("Online")).key("status").singleLine().build()).build()).build();
        var geometry = SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        for (String key : new String[]{"value","status"}) {
            var node = entry(geometry.definition(),key);
            assertFalse(node.interactive());
            assertEquals(UiPointerBehavior.INSPECT,node.pointerBehavior());
            assertQuiet(node);
        }
        var value = entry(geometry.definition(),"value");
        assertEquals(full,MenuTooltipContent.resolve(value,value.label(),_ -> false).text());
        var status = entry(geometry.definition(),"status");
        assertNull(MenuTooltipContent.resolve(status,status.label(),_ -> false));
    }

    @Test void availableActionsReceiveFeedbackWhileDisabledAndUnboundControlsRemainQuiet() {
        var called = new AtomicInteger();
        var reason = Component.text("Choose an item first");
        var scene = MenuScene.screen("actions").content(Ui.column()
                .child(Ui.button(Component.text("Open")).key("open").primary((_,_) -> called.incrementAndGet()).selected(true).build())
                .child(Ui.button(Component.text("Apply")).key("disabled").primary((_,_) -> called.incrementAndGet()).disabled(reason).build())
                .child(Ui.button(Component.text("Unbound")).key("unbound").build()).build()).build();
        var geometry = SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        var open = entry(geometry.definition(),"open");
        assertEquals(UiPointerBehavior.OPERATE,open.pointerBehavior());
        assertEquals(1,MenuNodeFeedback.hover(open.pointerBehavior(),true));
        assertEquals(FloatingMenuElementState.HOVERED,state(open,true,false));
        assertEquals(FloatingMenuElementState.PRESSED,state(open,true,true));
        assertEquals(FloatingMenuElementState.SELECTED,state(open,false,false));
        open.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,called.get());
        var disabled = entry(geometry.definition(),"disabled");
        assertEquals(UiPointerBehavior.INSPECT,disabled.pointerBehavior());
        assertQuiet(disabled);
        assertEquals(reason,MenuTooltipContent.resolve(disabled,disabled.label(),_ -> false).text());
        var unbound = entry(geometry.definition(),"unbound");
        assertTrue(unbound.interactive(),"Semantic control sizing does not imply an available operation");
        assertEquals(UiPointerBehavior.NONE,unbound.pointerBehavior());
        assertQuiet(unbound);
    }

    @Test void focusObserversDoNotAdvertiseAnUnboundClickAction() {
        var builder = MenuScene.screen("preview");
        builder.control("preview",Component.text("Preview")).focus((_,_,_) -> {});
        var node = element(SceneGeometryTestSupport.measure(builder.build(),1),"preview").node();
        assertEquals(UiPointerBehavior.INSPECT,node.input().pointerBehavior());
        assertTrue(node.input().focus().isPresent());
        assertEquals(0,MenuNodeFeedback.hover(node.input().pointerBehavior(),true));
    }

    @Test void sameKeyRemeasureRecomputesCapabilitiesAndCannotRetainAnOldPressOrHover() {
        var state = new MenuLayoutState();
        UiNode[] values = {
                Ui.button(Component.text("Action")).key("same").primary((_,_) -> {}).build(),
                Ui.text(Component.text("Details")).key("same").tooltip(Component.text("Read-only explanation")).build(),
                Ui.text(Component.text("Plain")).key("same").build(),
                Ui.button(Component.text("Action again")).key("same").primary((_,_) -> {}).build()
        };
        var expected = new UiPointerBehavior[]{UiPointerBehavior.OPERATE,UiPointerBehavior.INSPECT,UiPointerBehavior.NONE,UiPointerBehavior.OPERATE};
        for (int index = 0; index < values.length; index++) {
            var geometry = state.measure(MenuScene.screen("changing").content(values[index]).build(),1,FloatingMenuFieldOfView.DEFAULT,1);
            var node = entry(geometry.definition(),"same");
            assertEquals(expected[index],node.pointerBehavior());
            assertEquals(expected[index],geometry.frame().elements().getFirst().node().input().pointerBehavior());
            if (expected[index] != UiPointerBehavior.OPERATE) assertQuiet(node);
        }
    }

    private static FloatingMenuElementState state(MenuScene.Entry node, boolean pointed, boolean pressed) {
        return MenuNodeFeedback.state(node.pointerBehavior(),node.enabled(),node.selected(),pointed,pressed);
    }

    private static void assertQuiet(MenuScene.Entry node) {
        assertEquals(0,MenuNodeFeedback.hover(node.pointerBehavior(),true));
        assertEquals(node.enabled() ? FloatingMenuElementState.NORMAL : FloatingMenuElementState.DISABLED,state(node,true,true));
    }
}
