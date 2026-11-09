package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPopoverGeometryTest {
    @Test void longCompanionTitlesExpandOrKeepFullHelpWithoutLosingTheReadingBody() {
        var title = Component.text("A long translated subject 名称 ".repeat(24));
        var body = Ui.document().key("reading").readingBody()
                .blocks(java.util.List.of(Ui.text(Component.text("Complete condition and explanation ".repeat(80))).key("condition").build()))
                .position(UiReadingPosition.START,_ -> {}).build();
        for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
            var definition = scene(MenuPopover.attached(UiKey.named("person"),title,body,Component.text("Close")));
            var popup = SceneGeometry.measure(definition,font,new FloatingMenuFieldOfView(fov),1).popover().content();
            var frame = popup.frame();
            var heading = UiSceneTestSupport.element(frame,"title");
            UiSceneTestSupport.assertFullTextAvailable(title,heading);
            assertNotNull(UiSceneTestSupport.element(frame,"close"));
            assertTrue(frame.pagination().getFirst().page().count() > 1);
            assertTrue(frame.elements().stream().anyMatch(node -> node.node().key().value().equals("condition")));
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    private MenuScene scene(MenuPopover popover) {
        return MenuScene.screen("workspace").content(UiTemplates.sheet(
                UiTemplates.window("people", Ui.button(Component.text("Person")).key("person").primary((_,_) -> {}).build()),
                UiTemplates.toolbar("tools", Ui.button(Component.text("Close")).key("close").primary((_,h) -> h.close()).build())))
                .popover(popover).build();
    }

    @Test void independentCompanionsDoNotAffectParentFramingAndAllTheirActionsAreScoped() {
        for (var textScale : FloatingMenuTextScale.values()) for (int fov : new int[] {30,70,110}) {
            var base = SceneGeometry.measure(scene(null), textScale.factor(), new FloatingMenuFieldOfView(fov), 1);
            var content = Ui.column().child(Ui.text(Component.text("Responsibilities\nRecent record")).key("details").build())
                    .child(Ui.button(Component.text("Related matters")).key("related").primary((_,_) -> {}).build()).build();
            var measured = SceneGeometry.measure(scene(MenuPopover.attached(UiKey.named("person"), Component.text("Details"), content, Component.text("Close"), (_,_) -> {})),
                    textScale.factor(), new FloatingMenuFieldOfView(fov), 1);
            assertEquals(base.frame().size(), measured.frame().size());
            assertEquals(base.frame().panels(), measured.frame().panels());
            assertEquals(base.frame().readingAnchor(), measured.frame().readingAnchor());
            base.poses().forEach((key, pose) -> assertEquals(pose, measured.poses().get(key)));
            assertEquals(base.bounds(), measured.bounds());
            assertNotNull(measured.popover());
            assertEquals(4, measured.definition().entries().keySet().stream().filter(MenuPopover::owns).count());
            assertTrue(measured.poses().keySet().containsAll(measured.definition().entries().keySet()));
            var popup = measured.popover().content().frame();
            assertTrue(popup.size().width() <= measured.popover().content().definition().viewport().width() + 1e-6);
        }
    }

    @Test void missingSourceRemovesTheCompanionIncludingItsModalInputScope() {
        var popup = MenuPopover.attached(UiKey.named("removed"), Component.text("Details"), Ui.text(Component.text("Details")).build(), Component.text("Close"), (_,_) -> {}).exclusive();
        var measured = SceneGeometry.measure(scene(popup), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertNull(measured.popover());
        assertNull(measured.definition().popover());
        assertFalse(measured.definition().entries().keySet().stream().anyMatch(MenuPopover::owns));
    }

    @Test void measuredReadingPagesOwnTheirWheelActionsWithoutReplacingParentPaging() {
        var parent = new java.util.concurrent.atomic.AtomicInteger();
        var selected = new java.util.concurrent.atomic.AtomicInteger(-1);
        var content = Ui.pagedList().key("reading")
                .header(Ui.heading(Component.text("Details")).build())
                .items(java.util.stream.IntStream.range(0,20).mapToObj(index ->
                        Ui.text(Component.text("Condition "+index)).key("condition:"+index).build()).toList())
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(400,160)))
                .page(0,selected::set).build();
        var definition = scene(MenuPopover.attached(UiKey.named("person"), Component.text("Details"), content, Component.text("Close"))).toBuilder()
                .on(FloatingMenuInteraction.SCROLL_DOWN,(_,_,_) -> parent.incrementAndGet()).build();
        var geometry = SceneGeometry.measure(definition,1,FloatingMenuFieldOfView.DEFAULT,1);
        var popup = geometry.popover().content();
        assertTrue(popup.frame().pagination().getFirst().page().count()>1);
        popup.definition().triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1,selected.get());
        assertEquals(0,parent.get());
        geometry.definition().triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1,parent.get());
        var next = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element(popup.frame(), "next");
        assertTrue(geometry.definition().entries().containsKey(MenuPopover.scoped(next.path())));
    }
}
