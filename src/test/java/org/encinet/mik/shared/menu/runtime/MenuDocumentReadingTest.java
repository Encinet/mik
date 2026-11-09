package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuDocumentReadingTest {
    private static final UiKey BODY = UiKey.named("paragraph");
    private static final Component TEXT = Component.text("前言", NamedTextColor.GOLD)
            .append(Component.text("Ae\u0301界🌿👩‍🔬".repeat(400), NamedTextColor.AQUA));

    private static MenuScene scene(Component text, UiReadingPosition position, java.util.function.Consumer<UiReadingPosition> changed) {
        return MenuScene.screen("document").viewport(new UiSize(640,240)).content(UiTemplates.window("reader", Ui.document().key("document")
                .blocks(List.of(Ui.text(text).key(BODY).textWidth(4096).build())).position(position, changed).build())).build();
    }

    private static SceneGeometry geometry(MenuScene scene, double text, int fov, double scale) {
        return SceneGeometry.measure(scene, text, new FloatingMenuFieldOfView(fov), scale);
    }

    @Test void glyphAlignedReadingWidthsRemainStableWhenLayoutRoundTripsLoseOneUlp() {
        var label = Component.text("e".repeat(63));
        int pixels = UiTextMetrics.measure(label,Integer.MAX_VALUE).widthPixels();
        var natural = FloatingMenuNodeSizing.measureText(label,false,pixels);
        for (double typography : new double[]{.7,1,1.8}) {
            double width = Math.nextDown(natural.size().width() * typography);
            assertEquals(pixels,FloatingMenuNodeSizing.textWidthPixels(width,typography));
            var document = Ui.document().key("document").blocks(List.of(Ui.text(Component.text(
                    ("e".repeat(63) + "\n").repeat(80))).key(BODY).textWidth(4096).build()))
                    .position(UiReadingPosition.START,_ -> {}).build();
            var page = SceneGeometry.measureBase(MenuScene.screen("pixel-boundary")
                    .viewport(new UiSize(width / UiSceneContent.WORLD_PER_UI,600)).content(document).build(),
                    typography,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT);
            assertTrue(page.frame().pagination().getFirst().page().count() > 1);
            UiSceneTestSupport.assertDisjoint(page.frame());
        }
    }

    @Test void everyStyledGraphemeRemainsReachableAcrossAllReadingPreferencesWithoutChangingTheOwnersSnapshot() {
        String original = plain(TEXT);
        var boundaries = new java.util.HashSet<Integer>();
        var matcher = java.util.regex.Pattern.compile("\\X").matcher(original);
        boundaries.add(0);
        while (matcher.find()) boundaries.add(original.codePointCount(0, matcher.end()));
        for (double typography : new double[]{.7,1,1.8}) for (int fov : new int[]{30,70,110}) for (double scale : new double[]{.7,1.3}) {
            var position = new AtomicReference<>(UiReadingPosition.START);
            var received = new StringBuilder();
            UiSize pageSize = null;
            int count = 1;
            for (int page = 0; page < count; page++) {
                var requested = position.get();
                var frame = geometry(scene(TEXT, requested, position::set), typography, fov, scale);
                assertSame(requested, position.get(), "Layout cannot mutate feature-owned reading position");
                var reading = frame.frame().pagination().getFirst();
                count = reading.page().count();
                assertEquals(page, reading.page().index());
                assertTrue(count < 256);
                UiSceneTestSupport.assertDisjoint(frame.frame());
                if (pageSize == null) pageSize = frame.frame().size(); else assertEquals(pageSize, frame.frame().size());
                var actual = reading.readingPosition().orElseThrow();
                assertTrue(boundaries.contains(actual.offset()), "A page must not start inside a Unicode grapheme");
                var body = UiSceneTestSupport.element(frame.frame(), BODY.value());
                received.append(plain(body.node().content().label()));
                if (page + 1 < count) {
                    var next = UiSceneTestSupport.element(frame.frame(), "next");
                    frame.definition().entries().get(next.path()).trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                    assertTrue(position.get().offset() > actual.offset());
                }
            }
            assertEquals(original, received.toString(), typography + " / " + fov + " @ " + scale);
        }
    }

    @Test void repaginationRetainsTheRequestedParagraphAndCharacterRatherThanTheOldNumericPage() {
        var position = new AtomicReference<>(UiReadingPosition.START);
        for (int step = 0; step < 3; step++) {
            var frame = geometry(scene(TEXT, position.get(), position::set), 1,70,1);
            frame.frame().pagination().getFirst().selectPage().accept(step + 1);
        }
        var requested = position.get();
        assertTrue(requested.offset() > 0);
        var frame = geometry(scene(TEXT, requested, position::set), 1.8,110,1.3);
        var actual = frame.frame().pagination().getFirst().readingPosition().orElseThrow();
        assertEquals(requested.block(), actual.block());
        assertTrue(actual.offset() <= requested.offset());
        var visible = plain(UiSceneTestSupport.element(frame.frame(), BODY.value()).node().content().label());
        assertTrue(requested.offset() < actual.offset() + visible.codePointCount(0,visible.length()));
        assertSame(requested, position.get());
    }

    @Test void removedParagraphsHaveANearbyFallbackAndShortenedParagraphsClampAtTheirLastPart() {
        var requested = new UiReadingPosition(BODY, 99999, 1);
        var shortPage = geometry(scene(Component.text("Short"), requested, _ -> {}), 1,70,1);
        assertEquals(0, shortPage.frame().pagination().getFirst().page().index());
        var document = Ui.document().blocks(List.of(Ui.text(Component.text("Intro")).key("intro").build(),
                Ui.text(Component.text("Outro")).key("outro").build())).position(requested, _ -> {}).build();
        var frame = geometry(MenuScene.screen("removed").content(document).build(),1,70,1);
        assertEquals(UiKey.named("intro"), frame.frame().pagination().getFirst().readingPosition().orElseThrow().block().orElseThrow());
        assertEquals(1, frame.frame().pagination().getFirst().page().count());
    }

    @Test void bodyReadingKeepsPageBoundariesWhenTheLastPageAddsAuxiliaryActions() {
        Component source = Component.empty().append(TEXT).append(TEXT).append(TEXT);
        for (double typography : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
            var position = new AtomicReference<>(UiReadingPosition.START);
            var actions = new java.util.concurrent.atomic.AtomicInteger();
            var read = new StringBuilder();
            UiSize body = null;
            int count = 1;
            for (int index = 0; index < count; index++) {
                var document = Ui.document().key("document").readingBody()
                        .header(Ui.heading(Component.text("Persistent title")).key("title").singleLine().build())
                        .blocks(List.of(Ui.text(source).key(BODY).textWidth(4096).build())).position(position.get(),position::set)
                        .pager((page,select) -> {
                            var footer = Ui.column().key("footer").gap(UiLength.ui(8)).child(UiTemplates.pager(page,select));
                            if (!page.hasNext()) footer.child(UiTemplates.toolbar("actions",Ui.column().gap(UiLength.ui(8))
                                    .child(Ui.button(Component.text("Act")).key("final-action").primary((_,_) -> actions.incrementAndGet()).build())
                                    .child(Ui.button(Component.text("Help")).key("help").build())
                                    .child(Ui.button(Component.text("Back")).key("back").build()).build()));
                            return footer.build();
                        }).build();
                var rendered = geometry(MenuScene.screen("contextual-document").viewport(new UiSize(640,600)).content(UiTemplates.window("reader",document)).build(),typography,fov,scale);
                var frame = rendered.frame();
                UiSceneTestSupport.assertDisjoint(frame);
                assertTrue(frame.readingRegion().path().endsWith("/items"));
                var paging = frame.pagination().getFirst();
                if (index == 0) count = paging.page().count(); else assertEquals(count,paging.page().count());
                assertEquals(index,paging.page().index());
                if (body == null) body = frame.readingRegion().size(); else assertEquals(body,frame.readingRegion().size());
                read.append(plain(UiSceneTestSupport.element(frame,BODY.value()).node().content().label()));
                boolean last = !paging.page().hasNext();
                var available = frame.elements().stream().filter(node -> node.node().key().value().equals("final-action")).findFirst();
                assertEquals(last,available.isPresent());
                if (last) {
                    var target = available.orElseThrow();
                    rendered.definition().entries().get(target.path()).trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null,null,FloatingMenuInteraction.PRIMARY);
                } else {
                    assertEquals(0,actions.get());
                    paging.selectPage().accept(paging.page().next().index());
                }
            }
            assertEquals(plain(source),read.toString()); assertEquals(1,actions.get()); assertTrue(count > 1);
        }
        var empty = Ui.document().key("empty").readingBody().position(UiReadingPosition.START,_ -> {}).build();
        assertThrows(UiOverflowException.class,() -> geometry(MenuScene.screen("empty-body").content(empty).build(),1,70,1));
    }

    private static String plain(Component text) { return PlainTextComponentSerializer.plainText().serialize(text); }
}
