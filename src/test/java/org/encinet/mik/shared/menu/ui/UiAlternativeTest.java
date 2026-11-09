package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiAlternativeTest {
    private static final UiCompiler.ElementMeasurer MEASURER = (_, _) ->
            new UiCompiler.ElementMeasurement(new UiSize(50, 20), 100);

    @Test void compactingANoWrapControlPreservesItsIdentityActionsAndExplicitHelp() {
        var source = Component.text("A complete unwrapped location");
        var help = Component.text("Full destination and current availability");
        var invoked = new AtomicInteger();
        var original = Ui.button(source).key("visit").noWrap().tooltip(help).selected(true)
                .primary((_,_) -> invoked.incrementAndGet()).build();
        var compact = original.withSummaryLines(1);
        assertEquals(original.key(), compact.key()); assertEquals(original.input(), compact.input());
        assertTrue(((UiContent.Text) compact.content()).fullFirst());
        assertEquals(help, compact.input().tooltip().orElseThrow().text());
        compact.input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1, invoked.get());
        assertFalse(((UiContent.Text) original.withSummaryLines(Integer.MAX_VALUE).content()).fullFirst());
        var text = Ui.text(source).noWrap().build().withSummaryLines(2);
        assertTrue(text.input().tooltip().isEmpty(), "Complete text needs no redundant hover help");
        assertEquals(2, ((UiContent.Text) text.content()).maximumLines());
        var scene = MenuScene.screen("adaptive").viewport(new UiSize(400,200)).content(compact).build();
        assertEquals(source,SceneGeometryTestSupport.naturalFrame(scene,1).elements().getFirst().node().content().label());
        var small = SceneGeometryTestSupport.naturalFrame(scene.toBuilder().viewport(new UiSize(80,22)).build(),1);
        var fitted = small.elements().getFirst().node();
        assertNotEquals(source,fitted.content().label());
        assertEquals(source.append(Component.newline()).append(help),fitted.input().tooltip().orElseThrow().text());
        assertEquals(original.input().actions(),fitted.input().actions());
        assertEquals(source,SceneGeometryTestSupport.naturalFrame(scene,1).elements().getFirst().node().content().label());
    }

    @Test void declarationVariantsKeepTheSameOwnerActionAndFullSourceHelp() {
        var opened = new AtomicInteger();
        var preferred = Ui.column().key("object").child(UiTemplates.information("value"))
                .child(Ui.stack().size(new UiSize(100,600)).build()).build();
        var fallback = Ui.column().key("object").child(UiTemplates.information("value").withSummaryLines(1)).build();
        var source = Component.text("Long source\nInherited setting");
        var builder = MenuScene.screen("variants").content(UiTemplates.panel("variants",Ui.alternative(preferred,fallback),"value"));
        builder.control("setting",source).region("value").primary((_,_) -> opened.incrementAndGet());
        var scene = builder.build();
        var frame = UiCompiler.compile(scene.content(),UiConstraints.bounded(120,100),MEASURER);
        var setting = UiSceneTestSupport.element(frame,"setting");
        assertEquals(source,setting.node().content().label());
        assertTrue(setting.node().input().tooltip().isEmpty());
        setting.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,opened.get());
        var mismatch = MenuScene.screen("invalid").content(Ui.alternative(UiTemplates.information("value"),
                new UiPort(UiKey.named("value"),UiLayouts.column(UiLength.ZERO),UiStyle.PLAIN,UiSizing.CONTENT,List.of("other"),UiRole.TEXT)));
        mismatch.control("setting",source).region("value");
        mismatch.control("another",source).region("other");
        assertThrows(IllegalArgumentException.class,mismatch::build);
    }

    @Test void declarationResolutionRejectsExcessivelyNestedVariantsBeforeRecursingIntoThem() {
        UiNode value = Ui.text(Component.text("Value")).key("value").build();
        UiNode tree = value;
        for (int depth = 0; depth < UiTree.MAX_DEPTH + 1; depth++) tree = Ui.alternative(tree,value);
        UiNode invalid = tree;
        assertThrows(IllegalArgumentException.class,() -> MenuScene.screen("too-deep").content(invalid).build());
    }

    @Test void theReservedPagerCanSelectACompactGroupWithoutLosingItsActionOrReexpandingDuringPlacement() {
        var opened = new AtomicInteger();
        var full = Ui.stack().key("figure").size(new UiSize(100, 60))
                .child(Ui.text(Component.text("Figure and caption")).key("caption").build()).build();
        var compact = Ui.button(Component.text("Open full figure")).key("figure").primary((_,_) -> opened.incrementAndGet()).build();
        var group = Ui.alternative(full, compact);
        var tree = Ui.pagedList().items(List.of(group))
                .pager(_ -> Ui.text(Component.text("Persistent help")).key("footer").build()).build();
        var frame = UiCompiler.compile(tree, UiConstraints.bounded(120, 75), MEASURER);
        UiSceneTestSupport.assertDisjoint(frame);
        assertTrue(frame.elements().stream().noneMatch(element -> element.node().key().value().equals("caption")));
        var action = UiSceneTestSupport.element(frame, "figure");
        var scene = MenuScene.screen("document").content(tree).build().measured(frame);
        scene.entries().get(action.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1, opened.get());
        var spacious = UiCompiler.compile(tree, UiConstraints.bounded(120, 120), MEASURER);
        assertTrue(spacious.elements().stream().anyMatch(element -> element.node().key().value().equals("caption")));
        assertTrue(spacious.elements().stream().noneMatch(element -> element.node().key().value().equals("figure")));
    }

    @Test void aFallbackDoesNotHideAnInvalidPreferredLayoutOrInventAnObjectIdentity() {
        var invalid = Ui.layout((_, _) -> { throw new IllegalArgumentException("Invalid application layout"); })
                .key("object").build();
        var fallback = Ui.text(Component.text("Fallback")).key("object").build();
        assertThrows(IllegalArgumentException.class, () -> UiCompiler.compile(Ui.alternative(invalid,fallback),
                UiConstraints.bounded(120,120),MEASURER));
        assertThrows(IllegalArgumentException.class, () -> Ui.alternative(invalid, Ui.text(Component.text("Another object")).key("other").build()));
    }

    @Test void aSharedChildIsRemeasuredWhenItsFallbackInheritsADifferentTextScale() {
        var label = Ui.text(Component.text("Shared content")).key("label").build();
        var preferred = Ui.column().key("object").style(UiStyle.PLAIN.withTextScale(2)).child(label)
                .child(Ui.stack().size(new UiSize(20,600)).build()).build();
        var fallback = Ui.column().key("object").child(label).build();
        var frame = UiCompiler.compile(Ui.alternative(preferred,fallback),UiConstraints.bounded(100,100),
                (node,_) -> new UiCompiler.ElementMeasurement(new UiSize(30,10 * node.style().textScale()),100));
        var result = UiSceneTestSupport.element(frame,"label");
        assertEquals(10,result.size().height());
        assertEquals(1,result.node().style().textScale());
    }
}
