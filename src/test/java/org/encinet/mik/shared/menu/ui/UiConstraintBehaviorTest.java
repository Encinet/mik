package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiLength.ui;

class UiConstraintBehaviorTest {
    private static UiElement text(String key) { return Ui.text(Component.text(key)).key(key).build(); }
    private static UiFrame measure(UiNode node, double width, double height) {
        return UiCompiler.compile(node, UiConstraints.bounded(width, height), (_, limits) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(80, limits.maxWidth()),
                        Math.min(10, limits.maxHeight())), 100));
    }

    @Test void contentShrinksBeforeWeightedSpaceIsDistributed() {
        var tree = Ui.row().gap(ui(8)).child(text("first")).child(text("second"))
                .child(Ui.text(Component.text("weighted")).key("weighted")
                        .sizing(new UiSizing(new UiSize(20, 0), new UiSize(4096, 4096))).build(),
                        LinearSlot.grow(1)).build();
        var frame = measure(tree, 100, 100);
        assertEquals(100, frame.size().width(), 1e-6);
        assertEquals(32, frame.elements().getFirst().size().width(), 1e-6);
        assertEquals(40, frame.elements().get(1).x(), 1e-6);
        assertEquals(80, frame.elements().getLast().x(), 1e-6);
    }

    @Test void stretchFillsTheCrossAxisButHonorsAFixedChildSize() {
        var frame = measure(Ui.row().child(text("stretch"), LinearSlot.stretch())
                .child(Ui.text(Component.text("fixed")).key("fixed").size(new UiSize(20, 30)).build(),
                        LinearSlot.stretch()).build(), 200, 100);
        assertEquals(30, frame.elements().getFirst().size().height());
        assertEquals(30, frame.elements().getLast().size().height());
    }

    @Test void headerBodyCentersACappedHeaderWithoutViolatingItsOwnWidth() {
        var tree = Ui.layout(UiLayouts.headerBody(ui(8)))
                .child(Ui.text(Component.text("Header")).key("header").size(new UiSize(80, 20)).build())
                .child(Ui.text(Component.text("Body")).key("body").size(new UiSize(160, 30)).build()).build();
        var frame = measure(tree, 200, 100);
        assertEquals(new UiSize(160, 58), frame.size());
        assertEquals(40, UiSceneTestSupport.element(frame, "header").x());
        assertEquals(28, UiSceneTestSupport.element(frame, "body").y());
    }

    @Test void headerBodyUsesTheFinalHeaderHeightForBodyPlacementAndItsOwnExtent() {
        for (double[] heights : new double[][]{{20, 50}, {50, 20}}) {
            var tree = Ui.layout(UiLayouts.headerBody(ui(8)))
                    .child(responsiveHeader(heights[0], heights[1], 120))
                    .child(Ui.text(Component.text("Body")).key("body").size(new UiSize(160, 30)).build()).build();
            var frame = measure(tree, 200, 100);
            assertEquals(new UiSize(160, heights[1] + 38), frame.size());
            assertEquals(heights[1] + 8, UiSceneTestSupport.element(frame, "body").y());
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void aResponsiveHeaderLeavesOnlyItsFinalRemainingHeightForTheBody() {
        var body = Ui.layout((scope, limits) -> {
            var size = new UiSize(160, Math.min(90, limits.maxHeight()));
            scope.measure(0, UiConstraints.bounded(size.width(), size.height()));
            return new UiLayout.Result(size, List.of(new UiLayout.Position(0, 0)));
        }).child(text("body")).build();
        var frame = measure(Ui.layout(UiLayouts.headerBody(ui(8)))
                .child(responsiveHeader(20, 50, 120)).child(body).build(), 200, 100);
        assertEquals(new UiSize(160, 100), frame.size());
        assertEquals(58, UiSceneTestSupport.element(frame, "body").y());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void headerBodyDoesNotReserveASeparatorForAnEmptyRegion() {
        UiNode visible = Ui.text(Component.text("Visible")).size(new UiSize(80, 20)).build();
        UiNode empty = Ui.stack().build();
        for (var regions : List.of(List.of(visible, empty), List.of(empty, visible), List.of(empty, empty))) {
            var tree = Ui.layout(UiLayouts.headerBody(ui(8))).children(regions).build();
            assertEquals(regions.contains(visible) ? new UiSize(80, 20) : UiSize.ZERO, measure(tree, 100, 20).size());
        }
    }

    @Test void aNewBodyPagerCanWidenTheHeaderAndChangeTheRemainingReadingHeight() {
        var items = java.util.stream.IntStream.range(0, 8).mapToObj(index ->
                Ui.text(Component.text("Item")).key("item:" + index).size(new UiSize(40, 15)).build()).toList();
        var body = Ui.pagedFlow(4).items(items).gaps(ui(10), ui(5))
                .pager(page -> page.count() > 1 ? Ui.text(Component.text("Page")).key("page")
                        .size(new UiSize(100, 10)).build() : Ui.stack().build()).build();
        var tree = Ui.layout(UiLayouts.headerBody(ui(8))).child(responsiveHeader(20, 30, 100)).child(body).build();
        var frame = measure(tree, 100, 100);
        assertEquals(new UiSize(100, 88), frame.size());
        assertEquals(2, frame.pagination().getFirst().page().count());
        assertEquals(4, frame.elements().stream().filter(element -> element.node().key().value().startsWith("item:")).count());
        assertEquals(38, UiSceneTestSupport.element(frame, "item:0").y());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void emptySlotsDoNotAddSpacingOrPagesToCollections() {
        var first = Ui.text(Component.text("First")).key("first").size(new UiSize(40, 20)).build();
        var last = Ui.text(Component.text("Last")).key("last").size(new UiSize(40, 20)).build();
        List<UiNode> populated = List.of(first, last);
        List<UiNode> withEmptySlots = List.of(Ui.stack().key("before").build(), first,
                Ui.stack().key("between").build(), last, Ui.stack().key("after").build());
        for (boolean flow : new boolean[]{false, true}) {
            UiFrame reference = null;
            for (var items : List.of(populated, withEmptySlots)) {
                java.util.function.Function<org.encinet.mik.shared.menu.FloatingMenuPage, UiNode> pager = page ->
                        page.count() > 1 ? Ui.text(Component.text("Page")).size(new UiSize(40, 8)).build() : Ui.stack().build();
                var tree = flow ? Ui.pagedFlow(1).items(items).gaps(ui(8), ui(8)).pager(pager).build()
                        : Ui.pagedList().items(items).gap(ui(8)).pager(pager).build();
                var frame = measure(tree, 100, 48);
                if (reference == null) reference = frame;
                assertEquals(reference.size(), frame.size());
                assertEquals(1, frame.pagination().getFirst().page().count());
                for (String key : List.of("first", "last")) {
                    var original = UiSceneTestSupport.element(reference, key);
                    var current = UiSceneTestSupport.element(frame, key);
                    assertEquals(original.x(), current.x());
                    assertEquals(original.y(), current.y());
                }
            }
        }
    }

    private static UiNode responsiveHeader(double naturalHeight, double stretchedHeight, double stretchAt) {
        return Ui.layout((scope, limits) -> {
            double width = Math.max(limits.minWidth(), Math.min(80, limits.maxWidth()));
            double height = width >= stretchAt ? stretchedHeight : naturalHeight;
            scope.measure(0, UiConstraints.bounded(width, height));
            return new UiLayout.Result(new UiSize(width, height), List.of(new UiLayout.Position(0, 0)));
        }).child(text("header")).build();
    }

    @Test void stretchingResponsiveContentRemeasuresItsHeightAndMovesFollowingSiblings() {
        for (boolean horizontal : new boolean[]{false, true}) {
            UiNode responsive = Ui.layout((scope, limits) -> {
                double cross = horizontal ? limits.maxHeight() : limits.maxWidth();
                double main = cross < 100 ? 40 : 20;
                UiSize size = horizontal ? new UiSize(main, Math.min(80, cross)) : new UiSize(Math.min(80, cross), main);
                scope.measure(0, UiConstraints.bounded(size.width(), size.height()));
                return new UiLayout.Result(size, List.of(new UiLayout.Position(0, 0)));
            }).child(text("responsive")).build();
            var tree = (horizontal ? Ui.row() : Ui.column()).gap(ui(8))
                    .child(Ui.text(Component.text("anchor")).size(horizontal ? new UiSize(10, 90) : new UiSize(90, 10)).build())
                    .child(responsive, LinearSlot.stretch())
                    .child(Ui.text(Component.text("after")).key("after").size(new UiSize(10, 10)).build()).build();
            var frame = assertDoesNotThrow(() -> measure(tree, horizontal ? 200 : 100, horizontal ? 100 : 200));
            var after = UiSceneTestSupport.element(frame, "after");
            assertEquals(66, horizontal ? after.x() : after.y(), 1e-6);
            assertEquals(76, horizontal ? frame.size().width() : frame.size().height(), 1e-6);
            assertThrows(UiOverflowException.class, () -> measure(tree, horizontal ? 60 : 100, horizontal ? 100 : 60));
        }
    }

    @Test void overflowingWrapsAndDishonestCustomBoundsAreRejected() {
        var wrap = Ui.layout(UiLayouts.wrap(ui(8), ui(8))).child(text("one")).child(text("two")).build();
        assertThrows(UiOverflowException.class, () -> measure(wrap, 80, 20));
        UiNode bad = Ui.layout((scope, limits) -> {
            scope.measure(0, limits);
            return new UiLayout.Result(new UiSize(20, 20), List.of(new UiLayout.Position(15, 0)));
        }).child(text("outside")).build();
        assertTrue(assertThrows(UiOverflowException.class, () -> measure(bad, 100, 100)).getMessage().contains("outside"));
    }

    @Test void tooltipClampsWithinViewportWhileLeavingThePointerClear() {
        var pointer = new UiLayout.Position(95, 5);
        var placed = UiAttachmentPlacement.place(pointer, new UiSize(40, 20), new UiSize(100, 80), 4).orElseThrow();
        assertEquals(51, placed.x());
        assertEquals(9, placed.y());
        assertTrue(UiAttachmentPlacement.place(new UiLayout.Position(50, 40),
                new UiSize(101, 20), new UiSize(100, 80), 4).isEmpty());
    }

    @Test void structuralBudgetsAreCheckedBeforeRecursiveDeclarationResolution() {
        UiNode tree = text("deep");
        for (int depth = 0; depth <= UiTree.MAX_DEPTH; depth++) tree = Ui.column().child(tree).build();
        UiNode deep = tree;
        assertThrows(IllegalArgumentException.class, () -> org.encinet.mik.shared.menu.MenuScene.builder().content(deep).build());
        var many = Ui.column();
        for (int index = 0; index <= UiTree.MAX_NODES; index++) many.child(text("item:" + index));
        assertThrows(IllegalArgumentException.class, () -> UiTree.check(many.build()));
    }
}
