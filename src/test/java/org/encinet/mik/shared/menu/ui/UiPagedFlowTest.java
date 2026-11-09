package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiPagedFlowTest {
    @Test void aFlowNeedsOnlyItemsAndPageStateToGetStandardNavigation() {
        var selected = new ArrayList<Integer>();
        var nodes = java.util.stream.IntStream.range(0, 20).mapToObj(index -> Ui.button(Component.text(30))
                .key("item:" + index).primary((_, _) -> {}).build()).toList();
        var scene = MenuScene.screen("defaults").content(Ui.pagedFlow(3).items(nodes).page(0, selected::add).build()).build();
        var frame = measure(scene, 110);
        assertEquals(4, frame.pagination().getFirst().page().count());
        var measured = scene.measured(frame);
        var next = measured.entries().get(UiSceneTestSupport.element(frame, "next").path());
        assertNotNull(next);
        next.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        measured.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1, 1), selected);

        var one = MenuScene.screen("single").content(Ui.pagedFlow(3).items(List.of(nodes.getFirst())).build()).build();
        var single = measure(one, 110);
        assertEquals(20, single.size().height(), "A default single-page footer is invisible and adds no gap");
        assertTrue(single.elements().stream().noneMatch(element -> element.node().key().value().equals("next")));
        assertDoesNotThrow(() -> one.measured(single).triggers().get(FloatingMenuInteraction.SCROLL_UP)
                .execute(null, null, FloatingMenuInteraction.SCROLL_UP));
    }

    @Test void heightAndNavigationCanReduceThePageBelowItsMaximumRowCount() {
        var sizes = java.util.Collections.nCopies(10, new UiSize(40, 20));
        for (int page : new int[]{0, 1, 999}) {
            var frame = sizedFlow(sizes, page, 100, 80, current -> Ui.text(Component.text(current.count())).key("page-count")
                    .size(new UiSize(80, current.count() <= 2 ? 10 : 20)).build());
            var paging = frame.pagination().getFirst().page();
            assertEquals(3, paging.count());
            assertEquals(Math.min(page, 2), paging.index());
            assertEquals(new UiSize(90, 80), frame.size(), "The body and navigation share one height budget");
            assertEquals(page < 2 ? 4 : 2, items(frame).size());
            assertEquals(Component.text(3), UiSceneTestSupport.element(frame, "page-count").node().content().label());
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void aTallerCardStartsANewPageWhenItWouldOverflowTheCurrentRow() {
        var sizes = List.of(new UiSize(40, 30), new UiSize(40, 30), new UiSize(40, 10), new UiSize(40, 50));
        var first = sizedFlow(sizes, 0, 100, 70);
        var last = sizedFlow(sizes, 999, 100, 70);
        assertEquals(2, first.pagination().getFirst().page().count());
        assertEquals(List.of("item:0", "item:1", "item:2"), items(first));
        assertEquals(List.of("item:3"), items(last));
        assertEquals(new UiSize(90, 50), first.size());
        assertEquals(first.size(), last.size());
        UiSceneTestSupport.assertDisjoint(first);
        UiSceneTestSupport.assertDisjoint(last);
    }

    @Test void anItemThatCannotFitWithItsPagerStillRequestsAnAlternativeLayout() {
        assertThrows(UiOverflowException.class, () -> sizedFlow(List.of(new UiSize(40, 70)), 0, 100, 80,
                _ -> Ui.text(Component.text("Page")).size(new UiSize(80, 20)).build()));
    }

    @Test void boundedPagesKeepEveryMixedSizeItemInOrderAcrossDifferentHostSizes() {
        var sizes = java.util.stream.IntStream.range(0, 23)
                .mapToObj(index -> new UiSize(20 + index * 17 % 50, 12 + index * 13 % 25)).toList();
        var expected = java.util.stream.IntStream.range(0, sizes.size()).mapToObj(index -> "item:" + index).toList();
        for (double width : new double[]{80, 140, 240}) for (double height : new double[]{70, 110, 180}) {
            java.util.function.Function<org.encinet.mik.shared.menu.FloatingMenuPage, UiNode> pager =
                    current -> Ui.text(Component.text(current.count())).size(new UiSize(60, 10)).build();
            var first = sizedFlow(sizes, 0, width, height, pager);
            var seen = new ArrayList<String>();
            for (int page = 0; page < first.pagination().getFirst().page().count(); page++) {
                var frame = sizedFlow(sizes, page, width, height, pager);
                assertEquals(first.size(), frame.size(), width + " x " + height + " / " + page);
                assertTrue(frame.size().width() <= width && frame.size().height() <= height);
                seen.addAll(items(frame));
                UiSceneTestSupport.assertDisjoint(frame);
            }
            assertEquals(expected, seen, "Paging must neither duplicate nor omit an item at " + width + " x " + height);
        }
    }

    @Test void aSinglePageUsesOnlyItsOccupiedRowsAndFitsInAShortViewport() {
        for (int count : new int[]{0, 1, 3, 4}) {
            var sizes = java.util.Collections.nCopies(count, new UiSize(30, 20));
            var frame = sizedFlow(sizes, 0, 110, 55);
            assertEquals(count == 0 ? 0 : count <= 3 ? 20 : 50, frame.size().height(), 1e-6);
            assertEquals(1, frame.pagination().getFirst().page().count());
            assertEquals(count, items(frame).size());
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void mixedHeightCardsShareTheTallestRealPageWithoutReservingThreeTallCards() {
        var sizes = List.of(new UiSize(60, 80), new UiSize(60, 20), new UiSize(60, 30),
                new UiSize(60, 20), new UiSize(60, 20), new UiSize(60, 20));
        var first = sizedFlow(sizes, 0, 100, 200);
        var last = sizedFlow(sizes, 999, 100, 200);
        assertEquals(2, first.pagination().getFirst().page().count());
        assertEquals(new UiSize(60, 150), first.size());
        assertEquals(first.size(), last.size(), "Changing pages must not move neighbouring panels");
        assertEquals(List.of("item:0", "item:1", "item:2"), items(first));
        assertEquals(List.of("item:3", "item:4", "item:5"), items(last));
        UiSceneTestSupport.assertDisjoint(first);
        UiSceneTestSupport.assertDisjoint(last);
    }

    @Test void eachOccupiedRowUsesItsTallestCardAndKeepsShorterCardsCentered() {
        var frame = sizedFlow(List.of(new UiSize(30, 80), new UiSize(30, 20), new UiSize(30, 30), new UiSize(30, 20)),
                0, 110, 115);
        assertEquals(new UiSize(110, 110), frame.size());
        assertEquals(List.of(0.0, 30.0, 25.0, 90.0), frame.elements().stream().map(UiFrame.Element::y).toList());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    private UiFrame sizedFlow(List<UiSize> sizes, int page, double width, double height) {
        return sizedFlow(sizes, page, width, height, _ -> Ui.column().build());
    }

    private UiFrame sizedFlow(List<UiSize> sizes, int page, double width, double height,
                              java.util.function.Function<org.encinet.mik.shared.menu.FloatingMenuPage, UiNode> pager) {
        var nodes = new ArrayList<UiNode>();
        for (int index = 0; index < sizes.size(); index++) nodes.add(Ui.button(Component.text(index)).key("item:" + index)
                .size(sizes.get(index)).primary((_, _) -> {}).build());
        return UiCompiler.compile(Ui.pagedFlow(3).items(nodes).gaps(UiLength.ui(10), UiLength.ui(10))
                        .page(page, _ -> {}).pager(pager).build(),
                UiConstraints.bounded(width, height), (_, limits) -> new UiCompiler.ElementMeasurement(limits.constrain(new UiSize(10, 10)), 100));
    }

    @Test void naturalWidthsFillRowsInOrderAndOnlyThreeRowsEnterEachPage() {
        var selected = new ArrayList<Integer>();
        var scene = scene(List.of(80, 30, 40, 90, 10, 20, 50), 0, selected);
        var frame = measure(scene, 120);
        assertEquals(List.of("item:0", "item:1", "item:2", "item:3", "item:4"), items(frame));
        assertEquals(List.of(0.0, 0.0, 30.0, 60.0, 60.0), frame.elements().stream()
                .filter(e -> e.node().key().value().startsWith("item:")).map(UiFrame.Element::y).toList());
        assertEquals(2, frame.pagination().getFirst().page().count());
        assertTrue(frame.elements().stream().filter(e -> e.node().key().value().equals("page")).findFirst().orElseThrow().y() >= 90);
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void widerHostsCanFitMoreThanTwoItemsPerRow() {
        var scene = scene(java.util.Collections.nCopies(30, 30), 0, new ArrayList<>());
        assertEquals(9, items(measure(scene, 110)).size());
        assertEquals(18, items(measure(scene, 230)).size());
        assertEquals(3, measure(scene, 110).elements().stream().filter(e -> e.node().key().value().startsWith("item:"))
                .map(UiFrame.Element::y).distinct().count());
    }

    @Test void buttonsAndWheelUseTheMeasuredPageAndOffPageItemsHaveNoTargets() {
        var selected = new ArrayList<Integer>();
        var scene = scene(java.util.Collections.nCopies(20, 30), 0, selected);
        var measured = scene.measured(measure(scene, 110));
        assertEquals(9, measured.entries().values().stream().filter(e -> e.label().equals(Component.text(30))).count());
        measured.triggers().get(FloatingMenuInteraction.SCROLL_UP).execute(null, null, FloatingMenuInteraction.SCROLL_UP);
        assertTrue(selected.isEmpty());
        measured.entries().values().stream().filter(entry -> entry.id().endsWith("/page:next")).findFirst().orElseThrow()
                .trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        measured.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1, 1), selected);

        var last = scene(java.util.Collections.nCopies(20, 30), 999, selected);
        var frame = measure(last, 110);
        assertEquals(2, frame.pagination().getFirst().page().index());
        assertEquals(List.of("item:18", "item:19"), items(frame));
        assertTrue(frame.elements().stream().filter(e -> e.node().key().value().equals("page")).findFirst().orElseThrow().y() >= 90,
                "The last page keeps its three-row reading area");
        last.measured(frame).triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1, 1), selected);
    }

    @Test void emptyCollectionsKeepAClampedPagerAndConsumeBothWheelDirections() {
        var selected = new ArrayList<Integer>();
        var scene = scene(List.of(), 50, selected);
        var frame = measure(scene, 120);
        assertEquals(0, frame.pagination().getFirst().page().index());
        assertEquals(1, frame.pagination().getFirst().page().count());
        assertTrue(items(frame).isEmpty());
        var measured = scene.measured(frame);
        for (var interaction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN))
            measured.triggers().get(interaction).execute(null, null, interaction);
        assertTrue(selected.isEmpty());
    }

    @Test void hostRemeasurementUpdatesPagerContentEvenWhenItsOwnWidthStaysTheSame() {
        var nodes = new ArrayList<UiNode>();
        for (int i = 0; i < 30; i++) nodes.add(Ui.button(Component.text(30)).key("item:" + i).build());
        var flow = Ui.pagedFlow(3).key("flow").items(nodes).gaps(UiLength.ui(10), UiLength.ui(10)).page(0, _ -> { })
                .pager(page -> Ui.column().size(new UiSize(80, 20))
                        .child(Ui.text(Component.text(page.count())).key("page-count").build()).build()).build();
        var scene = MenuScene.screen("remeasure").content(Ui.layout((scope, _) -> {
            scope.measure(0, UiConstraints.bounded(110, 500));
            var size = scope.measure(0, UiConstraints.bounded(230, 500));
            return new UiLayout.Result(size, List.of(new UiLayout.Position(0, 0)));
        }).child(flow).build()).build();
        var frame = measure(scene, 230);
        assertEquals(2, frame.pagination().getFirst().page().count());
        assertEquals(Component.text(2), frame.elements().stream().filter(e -> e.node().key().value().equals("page-count"))
                .findFirst().orElseThrow().node().content().label());
    }

    private MenuScene scene(List<Integer> widths, int page, List<Integer> selected) {
        var nodes = new ArrayList<UiNode>();
        for (int i = 0; i < widths.size(); i++) nodes.add(Ui.button(Component.text(widths.get(i))).key("item:" + i).primary((_, _) -> { }).build());
        return MenuScene.screen("flow").content(Ui.pagedFlow(3).key("flow").items(nodes)
                .gaps(UiLength.ui(10), UiLength.ui(10)).page(page, selected::add)
                .pager(state -> MenuScene.screen("pager").content(UiTemplates.pagination("pages"))
                        .pagination("pages", state, selected::add).build().content()).build()).build();
    }

    private UiFrame measure(MenuScene scene, double width) {
        return UiCompiler.compile(scene.content(), UiConstraints.bounded(width, 500), (node, constraints) -> {
            double wanted = node.key().value().startsWith("item:")
                    ? Integer.parseInt(((net.kyori.adventure.text.TextComponent) node.content().label()).content()) : 20;
            return new UiCompiler.ElementMeasurement(new UiSize(Math.min(wanted, constraints.maxWidth()), 20), 100);
        });
    }

    private List<String> items(UiFrame frame) {
        return frame.elements().stream().map(e -> e.node().key().value()).filter(key -> key.startsWith("item:")).toList();
    }
}
