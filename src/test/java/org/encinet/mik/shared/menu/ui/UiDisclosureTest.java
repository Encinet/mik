package org.encinet.mik.shared.menu.ui;

import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiDisclosureTest {
    private static final UiCompiler.ElementMeasurer MEASURE = (_,limits) ->
            new UiCompiler.ElementMeasurement(new UiSize(Math.min(90,limits.maxWidth()),24),160);

    @Test void aCollapsedBodyIsNotBuiltMeasuredOrPublishedAndTheHeaderKeepsItsIdentity() {
        var builds = new AtomicInteger(); var invoked = new AtomicInteger();
        var collapsed = scene(false,builds,invoked);
        var closed = UiCompiler.compile(collapsed.content(),UiConstraints.bounded(300,300),MEASURE);
        assertEquals(0,builds.get()); assertNull(entry(collapsed,"edit")); assertDisjoint(closed);
        var expanded = scene(true,builds,invoked);
        var open = UiCompiler.compile(expanded.content(),UiConstraints.bounded(300,300),MEASURE);
        UiCompiler.compile(expanded.content(),UiConstraints.bounded(200,300),MEASURE);
        assertEquals(1,builds.get(),"Reading or remeasurement must not invoke a lazy application factory");
        assertNotNull(entry(expanded,"edit")); assertDisjoint(open);
        assertEquals(element(closed,"toggle").path(),element(open,"toggle").path());
        assertTrue(open.size().height() > closed.size().height());
        assertEquals(0,invoked.get());
        entry(expanded,"edit").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,invoked.get());
        var folded = scene(false,builds,invoked);
        assertNull(entry(folded,"edit")); assertEquals(1,builds.get());
        assertEquals(closed.size(),UiCompiler.compile(folded.content(),UiConstraints.bounded(300,300),MEASURE).size());
    }

    @Test void aDisabledToggleRetainsItsExplanationAndTheExpandedStateWhileTheBodyRemainsAvailable() {
        var disabled = Component.text("Finish editing first");
        var toggle = Ui.button(UiIcons.CHEVRON_UP,Component.text("Collapse")).key("toggle").selected(true)
                .disabled(disabled).primary((_,_) -> fail("Layout cannot collapse")).build();
        var group = UiTemplates.disclosure(UiKey.named("identity"),Ui.text(Component.text("Name and alias")).key("summary").build(),
                toggle,true,() -> Ui.textField(Component.text("Unsaved draft")).key("edit").build());
        var scene = MenuScene.screen("settings").content(group).build();
        var frame = UiCompiler.compile(scene.content(),UiConstraints.bounded(300,300),MEASURE);
        assertDisjoint(frame);
        var control = element(frame,"toggle"); assertTrue(control.node().input().selected());
        assertEquals(disabled,control.node().input().disabledReason().orElseThrow());
        assertFalse(entry(scene,"toggle").enabled()); assertNotNull(element(frame,"preview"));
    }

    @Test void independentDisclosuresScopeIdenticalFieldKeysAndRejectReadOnlyToggleDeclarations() {
        var group = scene(true,new AtomicInteger(),new AtomicInteger()).content();
        var scene = MenuScene.screen("two-objects").content(Ui.column()
                .child(UiKey.item("first"),group).child(UiKey.item("second"),group).build()).build();
        assertEquals(2,UiSceneContent.elements(scene.content()).values().stream().filter(element -> element.key().value().equals("edit")).count());
        assertEquals(2,scene.entries().keySet().stream().filter(path -> path.endsWith("/body")).count());
        assertThrows(IllegalArgumentException.class,() -> UiTemplates.disclosure(UiKey.named("invalid"),group,
                Ui.text(Component.text("Not an action")).build(),false,() -> group));
    }

    private static MenuScene scene(boolean expanded, AtomicInteger builds, AtomicInteger invoked) {
        return MenuScene.screen("settings").content(UiTemplates.disclosure(UiKey.named("identity"),
                Ui.text(Component.text("Name and alias")).key("summary").build(),
                Ui.button(expanded ? UiIcons.CHEVRON_UP : UiIcons.CHEVRON_DOWN,Component.text(expanded ? "Collapse" : "Expand"))
                        .key("toggle").selected(expanded).primary((_,_) -> {}).build(),expanded,() -> {
                    builds.incrementAndGet();
                    return Ui.button(Component.text("Name")).key("edit").primary((_,_) -> invoked.incrementAndGet()).build();
                })).build();
    }
}
