package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntConsumer;
import static org.junit.jupiter.api.Assertions.*;

class UiPagedLayoutTest {
    @Test void fullCollectionsRetainNaturalRectangularItems() {
        for (int count = 1; count <= 13; count++) {
            var frame = frame(count, 0, 400, 400, _ -> {});
            var entries = entries(frame);
            assertEquals(count, entries.size());
            for (var entry : entries) assertEquals(new UiSize(40, 20), entry.size());

            if (count == 6) {
                assertEquals(2, entries.stream().map(UiFrame.Element::x).distinct().count());
                assertEquals(3, entries.stream().map(UiFrame.Element::y).distinct().count());
            }
        }
    }

    @Test void everyPageKeepsEveryActionAndTheOuterBodyDimensionsStable() {
        for (int count = 0; count <= 13; count++) for (double width : new double[]{60, 100, 180}) {
            var first = frame(count, 0, width, 130, _ -> {});
            var seen = new HashSet<String>();
            int pages = first.pagination().getFirst().page().count();
            for (int page = 0; page < pages; page++) {
                var current = frame(count, page, width, 130, _ -> {});
                assertEquals(first.size(), current.size(), "Short pages must not shift surrounding panels");
                UiSceneTestSupport.assertDisjoint(current);
                var entries = entries(current);
                for (var entry : entries) assertEquals(new UiSize(40, 20), entry.size());
                for (var entry : entries) {
                    assertTrue(seen.add(entry.node().key().value()));
                    assertTrue(entry.node().input().actions().containsKey(FloatingMenuInteraction.PRIMARY));
                }
            }
            assertEquals(count, seen.size());
            assertEquals(pages - 1, frame(count, 999, width, 130, _ -> {}).pagination().getFirst().page().index());
        }
        var tail = entries(frame(8, 1, 100, 100, _ -> {}));
        assertEquals(2, tail.size());
        assertEquals(tail.getFirst().x(), tail.getLast().x(), 1e-6, "The short page uses the closest measured shape");
    }

    @Test void navigationAndEntryActionsUseThePublishedFrame() {
        var selected = new ArrayList<Integer>();
        var invoked = new java.util.concurrent.atomic.AtomicInteger();
        var items = java.util.stream.IntStream.range(0, 6).mapToObj(index -> UiChild.of(
                Ui.button(Component.text(index)).key("entry:" + index).primary((_, _) -> invoked.incrementAndGet()).build())).toList();
        var grid = new UiPagedLayout(new UiContainer(UiKey.named("grid"), balanced(), items, UiStyle.PLAIN, UiSizing.CONTENT), UiLength.ui(10), 0,
                current -> UiTemplates.pager(current, selected::add), selected::add);
        var scene = MenuScene.screen("grid").content(grid).build();
        var frame = UiCompiler.compile(scene.content(), UiConstraints.bounded(100, 70),
                (node, _) -> new UiCompiler.ElementMeasurement(node.key().value().startsWith("entry:")
                        ? new UiSize(40, 20) : new UiSize(20, 10), node.textWidthPixels()));
        var published = scene.measured(frame);
        published.entries().get(entries(frame).getFirst().path()).trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        published.entries().get(UiSceneTestSupport.element(frame, "next").path()).trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        published.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1, invoked.get());
        assertEquals(List.of(1, 1), selected);
        assertThrows(UiOverflowException.class, () -> frame(1, 0, 30, 30, _ -> {}));
    }

    @Test void paginationReusesOrdinaryColumnAndWrapLayouts() {
        var column = frame(7, 0, 100, 80, _ -> {}, UiLayouts.column(UiLength.ui(10)));
        var wrap = frame(7, 0, 100, 80, _ -> {}, UiLayouts.wrap(UiLength.ui(10), UiLength.ui(10)));
        assertEquals(4, column.pagination().getFirst().page().count());
        assertEquals(2, wrap.pagination().getFirst().page().count());
        assertEquals(2, entries(column).size()); assertEquals(4, entries(wrap).size());
        UiSceneTestSupport.assertDisjoint(column); UiSceneTestSupport.assertDisjoint(wrap);
    }

    @Test void responsiveLayoutFitNeedNotBeMonotonicAndInvalidLayoutsAreNotHidden() {
        UiLayout responsive = (scope, limits) -> {
            if (scope.count() == 2 || scope.count() > 3) throw new UiOverflowException("Use a complete group or one tail entry");
            return UiLayouts.column(UiLength.ui(10)).layout(scope, limits);
        };
        var first = frame(4, 0, 100, 100, _ -> {}, responsive);
        var last = frame(4, 1, 100, 100, _ -> {}, responsive);
        assertEquals(2, first.pagination().getFirst().page().count());
        assertEquals(3, entries(first).size()); assertEquals(1, entries(last).size());
        assertEquals(first.size(), last.size());
        var invalid = assertThrows(IllegalArgumentException.class, () -> frame(4, 0, 100, 100, _ -> {},
                (_, _) -> { throw new IllegalArgumentException("Invalid layout declaration"); }));
        assertFalse(invalid instanceof UiOverflowException);
    }

    private static UiFrame frame(int count, int page, double width, double height, IntConsumer select) {
        return frame(count, page, width, height, select, balanced());
    }
    private static UiFrame frame(int count, int page, double width, double height, IntConsumer select, UiLayout layout) {
        List<UiChild> items = java.util.stream.IntStream.range(0, count).mapToObj(index -> UiChild.of(
                Ui.button(Component.text(index)).key("entry:" + index).primary((_, _) -> {}).build())).toList();
        var grid = new UiPagedLayout(new UiContainer(UiKey.named("grid"), layout, items, UiStyle.PLAIN, UiSizing.CONTENT), UiLength.ui(10), page,
                current -> current.count() == 1 ? Ui.column().key("pager").build()
                        : Ui.text(Component.text(current.count())).key("page-count").build(), select);
        var scene = MenuScene.screen("grid").content(grid).build();
        return UiCompiler.compile(scene.content(), UiConstraints.bounded(width, height), (node, _) ->
                new UiCompiler.ElementMeasurement(node.key().value().equals("page-count") ? new UiSize(20, 10) : new UiSize(40, 20), node.textWidthPixels()));
    }

    private static List<UiFrame.Element> entries(UiFrame frame) {
        return frame.elements().stream().filter(element -> element.node().key().value().startsWith("entry:")).toList();
    }

    private static UiLayout balanced() {
        return UiLayouts.balancedGrid(4, 1, UiLayouts.GridBias.TALLER, UiLength.ui(10), UiLength.ui(10));
    }
}
