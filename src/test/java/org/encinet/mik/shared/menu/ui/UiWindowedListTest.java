package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiWindowedListTest {
    private static final UiCompiler.ElementMeasurer MEASURER = (_, _) ->
            new UiCompiler.ElementMeasurement(new UiSize(40, 20), 160);

    private static UiFrame frame(UiNode list, double height) {
        var frame = UiCompiler.compile(list, UiConstraints.bounded(320, height), MEASURER);
        UiSceneTestSupport.assertDisjoint(frame);
        return frame;
    }

    private static UiWindowedList window(int count, int page, List<Integer> created, java.util.function.IntConsumer selected) {
        return Ui.windowedList(count, UiLength.ui(20), index -> {
            created.add(index);
            return Ui.button(Component.text("Item " + index)).key(UiKey.item(index)).primary((_, _) -> {}).build();
        }).key("catalogue").header(Ui.text(Component.text("Fixed header")).key("heading").build())
                .page(page, selected).build();
    }

    @Test void aHugeCollectionOnlyCreatesItsVisibleWindowAndKeepsHeaderAndLastPageDimensions() {
        var created = new ArrayList<Integer>();
        var declaration = window(Integer.MAX_VALUE, 0, created, _ -> {});
        var scene = MenuScene.screen("catalogue").content(declaration).build();
        assertTrue(created.isEmpty(), "Building, resolving and indexing declarations cannot scan a collection");
        assertNull(UiSceneTestSupport.entry(scene, "0"));
        var first = frame(scene.content(), 160);
        assertEquals(List.of(0, 1, 2, 3), created);
        assertEquals(4, first.pagination().getFirst().page().pageSize());
        assertEquals(536870912, first.pagination().getFirst().page().count());
        created.clear();
        var last = frame(window(Integer.MAX_VALUE, Integer.MAX_VALUE, created, _ -> {}), 160);
        assertEquals(List.of(2147483644, 2147483645, 2147483646), created);
        assertEquals(first.size(), last.size());
        assertEquals(UiSceneTestSupport.element(first, "heading").y(), UiSceneTestSupport.element(last, "heading").y());
        assertTrue(last.elements().stream().allMatch(element -> element.y() + element.size().height() <= 160 + 1e-6));
    }

    @Test void onlyRealizedDomainObjectsOwnActionsAndAReorderRebindsTheCurrentSnapshot() {
        var selected = new AtomicInteger(-1);
        var activated = new AtomicInteger(-1);
        var objects = List.of(80, 90, 10, 20, 30, 40);
        java.util.function.Function<List<Integer>, UiWindowedList> list = snapshot -> Ui.windowedList(snapshot.size(), UiLength.ui(20), index -> {
            int id = snapshot.get(index);
            return Ui.button(Component.text(id)).key(UiKey.item(id)).primary((_, _) -> activated.set(id)).build();
        }).header(Ui.text(Component.text("Header")).build()).page(0, selected::set).build();
        var declaration = list.apply(objects);
        var measured = frame(declaration, 160);
        var scene = MenuScene.screen("objects").content(declaration).build().measured(measured);
        assertNotNull(entry(scene, measured, "80"));
        assertNull(entry(scene, measured, "30"));
        var next = list.apply(List.of(10, 20, 30, 40, 80, 90));
        var nextFrame = frame(next, 160);
        var nextScene = MenuScene.screen("objects").content(next).build().measured(nextFrame);
        assertNull(entry(nextScene, nextFrame, "80"), "An old row's input is not recycled for the next object");
        entry(nextScene, nextFrame, "10").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(10, activated.get());
        scene.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1, selected.get());
    }

    private static MenuScene.Entry entry(MenuScene scene, UiFrame frame, String key) {
        return frame.elements().stream().filter(element -> element.node().key().value().equals(key))
                .map(element -> scene.entries().get(element.path())).findFirst().orElse(null);
    }

    @Test void visibleWindowCostIsCappedEvenWithVeryShortRows() {
        var created = new AtomicInteger();
        var list = Ui.windowedList(1000000, UiLength.ui(1), index -> {
            created.incrementAndGet();
            return Ui.stack().key(UiKey.item(index)).size(new UiSize(1, 1)).build();
        }).gap(UiLength.ZERO).build();
        var measured = UiCompiler.compile(list, UiConstraints.bounded(320, 4096), MEASURER);
        assertEquals(UiWindowedList.MAX_VISIBLE_ITEMS, created.get());
        assertEquals(256, measured.pagination().getFirst().page().pageSize());
    }

    @Test void aMeasuredTemplateDefinesRowHeightWithoutPublishingItsContentOrInput() {
        var prototype = Ui.button(Component.text("Metrics only")).key("prototype").size(new UiSize(40, 30))
                .primary((_, _) -> fail("A row template has no published action")).build();
        var list = Ui.windowedList(50, prototype, index -> Ui.button(Component.text(index)).key(UiKey.item(index))
                .primary((_, _) -> {}).build()).build();
        var measured = frame(list, 160);
        assertEquals(3, measured.pagination().getFirst().page().pageSize());
        assertTrue(measured.elements().stream().noneMatch(element -> element.node().key().equals(prototype.key())));
        var first = UiSceneTestSupport.element(measured, "0");
        var second = UiSceneTestSupport.element(measured, "1");
        assertEquals(38, second.y() - first.y(), 1e-6);
    }

    @Test void emptyCollectionsNeverReadAnItemAndStillKeepTheirHeaderFooterGap() {
        var list = Ui.windowedList(0, UiLength.ui(20), _ -> { fail("No empty row may be created"); return null; })
                .header(Ui.text(Component.text("Empty")).build())
                .pager(_ -> Ui.text(Component.text("Help")).build()).build();
        var measured = frame(list, 48);
        assertEquals(48, measured.size().height());
        assertEquals(0, measured.pagination().getFirst().page().itemCount());
        assertThrows(UiOverflowException.class, () -> frame(list, 47));
    }

    @Test void oversizedRowsAndMissingStableIdentitiesFailWithoutClippingOrDroppingActions() {
        assertThrows(UiOverflowException.class, () -> frame(Ui.windowedList(1, UiLength.ui(10),
                _ -> Ui.text(Component.text("Too tall")).key("object").build()).build(), 160));
        assertThrows(IllegalArgumentException.class, () -> frame(Ui.windowedList(2, UiLength.ui(20),
                _ -> Ui.text(Component.text("Anonymous")).build()).build(), 160));
        assertThrows(IllegalArgumentException.class, () -> frame(Ui.windowedList(2, UiLength.ui(20),
                _ -> Ui.text(Component.text("Duplicate")).key("same").build()).build(), 160));
        assertThrows(UiOverflowException.class, () -> frame(window(10, 0, new ArrayList<>(), _ -> {}), 60));
    }
}
