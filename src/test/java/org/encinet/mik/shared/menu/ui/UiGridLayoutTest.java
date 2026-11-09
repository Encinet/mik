package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.encinet.mik.shared.menu.ui.UiLength.ui;
import static org.junit.jupiter.api.Assertions.*;

class UiGridLayoutTest {
    @Test void spanningCellsUseTheSameTracksRegardlessOfTheirDeclarationOrder() {
        for (boolean vertical : new boolean[]{false, true}) {
            var spanning = cell("spanning", vertical ? new UiSize(10, 100) : new UiSize(80, 10),
                    vertical ? GridSlot.cell(0, 0).withRowSpan(2) : GridSlot.cell(0, 0).withColumnSpan(2));
            var large = cell("large", vertical ? new UiSize(10, 80) : new UiSize(50, 10),
                    vertical ? GridSlot.cell(0, 1) : GridSlot.cell(1, 0));
            var small = cell("small", vertical ? new UiSize(10, 20) : new UiSize(10, 10), GridSlot.cell(1, 1));
            UiFrame reference = null;
            for (var children : List.of(List.of(spanning, large, small), List.of(spanning, small, large),
                    List.of(large, spanning, small), List.of(large, small, spanning),
                    List.of(small, spanning, large), List.of(small, large, spanning))) {
                var grid = new UiContainer(UiKey.AUTO, UiLayouts.grid(2, ui(0), ui(0)), children, UiStyle.PLAIN, UiSizing.CONTENT);
                var frame = measure(grid, 100, 100);
                assertEquals(vertical ? new UiSize(20, 100) : new UiSize(80, 20), frame.size());
                if (reference != null) assertEquals(positions(reference), positions(frame));
                reference = frame;
                UiSceneTestSupport.assertDisjoint(frame);
            }
        }
    }

    @Test void aSpanningCellDoesNotChangeDeclaredFixedColumnWidths() {
        var grid = new UiContainer(UiKey.AUTO,
                UiLayouts.grid(List.of(UiTrack.fixed(ui(30)), UiTrack.fixed(ui(70))), ui(0), ui(0)),
                List.of(cell("span", new UiSize(100, 20), GridSlot.cell(0, 0).withColumnSpan(2)),
                        cell("left", new UiSize(30, 20), GridSlot.cell(1, 0)),
                        cell("right", new UiSize(70, 20), GridSlot.cell(1, 1))), UiStyle.PLAIN, UiSizing.CONTENT);
        var frame = measure(grid, 100, 100);
        assertEquals(new UiSize(100, 40), frame.size());
        assertEquals(30, UiSceneTestSupport.element(frame, "right").x());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void fractionalColumnsReserveControlMinimaBeforeTextColumnsUseTheRemainingWidth() {
        for (boolean nested : new boolean[]{false, true}) {
            UiNode control = Ui.text(Component.text("Action")).key("action").size(new UiSize(40, 20)).build();
            if (nested) control = Ui.row().child(control).build();
            var grid = new UiContainer(UiKey.AUTO,
                    UiLayouts.grid(List.of(UiTrack.content(), UiTrack.fraction(1)), ui(10), ui(0)),
                    List.of(UiChild.grid(Ui.text(Component.text("Long content")).key("text").build(), GridSlot.cell(0, 0)),
                            UiChild.grid(control, GridSlot.cell(0, 1))), UiStyle.PLAIN, UiSizing.CONTENT);
            var frame = measure(grid, 100, 100);
            assertEquals(100, frame.size().width(), 1e-5);
            var text = UiSceneTestSupport.element(frame, "text");
            var action = UiSceneTestSupport.element(frame, "action");
            assertEquals(50, text.size().width(), 1e-5);
            assertEquals(60, action.x(), 1e-5);
            UiSceneTestSupport.assertDisjoint(frame);
            assertThrows(UiOverflowException.class, () -> measure(grid, 39, 100));
        }
    }

    @Test void partiallyOccupiedFixedTracksKeepTheirOwnWidthsAcrossDifferentSpans() {
        var grid = new UiContainer(UiKey.AUTO,
                UiLayouts.grid(List.of(UiTrack.fixed(ui(20)), UiTrack.fixed(ui(30)), UiTrack.fixed(ui(50))), ui(0), ui(0)),
                List.of(cell("first", new UiSize(50, 20), GridSlot.cell(0, 0).withColumnSpan(2)),
                        cell("second", new UiSize(80, 20), GridSlot.cell(1, 1).withColumnSpan(2)),
                        cell("anchor", new UiSize(20, 20), GridSlot.cell(2, 0))), UiStyle.PLAIN, UiSizing.CONTENT);
        var frame = measure(grid, 100, 100);
        assertEquals(new UiSize(100, 60), frame.size());
        assertEquals(20, UiSceneTestSupport.element(frame, "second").x());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void fractionalWeightsRespectControlMinimaAndRedistributeAfterAColumnReachesItsMaximum() {
        var grid = new UiContainer(UiKey.AUTO,
                UiLayouts.grid(List.of(new UiTrack(UiTrack.Kind.FRACTION, 0, 45, 1), UiTrack.fraction(2)), ui(10), ui(0)),
                List.of(cell("first", new UiSize(40, 20), GridSlot.cell(0, 0)),
                        cell("second", new UiSize(40, 20), GridSlot.cell(0, 1))), UiStyle.PLAIN, UiSizing.CONTENT);
        for (int width : new int[]{90, 120, 180}) {
            var frame = measure(grid, width, 100);
            assertEquals(width, frame.size().width());
            assertEquals(Math.min(45, 40 + (width - 90) / 3.0) + 10,
                    UiSceneTestSupport.element(frame, "second").x(), 1e-6);
            UiSceneTestSupport.assertDisjoint(frame);
        }
        assertThrows(UiOverflowException.class, () -> measure(grid, 89, 100));
    }

    @Test void rowsUseTextRemeasuredAtTheFinalColumnWidthBeforeEnforcingTheHeightBudget() {
        var responsive = Ui.layout((scope, limits) -> {
            double height = limits.maxWidth() < 60 ? 40 : 20;
            scope.measure(0, UiConstraints.bounded(50, height));
            return new UiLayout.Result(new UiSize(50, height), List.of(new UiLayout.Position(0, 0)));
        }).key("responsive").child(Ui.text(Component.text("Wrapped text")).key("text").build()).build();
        var grid = new UiContainer(UiKey.AUTO, UiLayouts.grid(2, ui(0), ui(0)),
                List.of(cell("heading", new UiSize(80, 10), GridSlot.cell(0, 0).withColumnSpan(2)),
                        UiChild.grid(responsive, GridSlot.cell(1, 0)),
                        cell("right", new UiSize(10, 10), GridSlot.cell(1, 1))), UiStyle.PLAIN, UiSizing.CONTENT);
        var frame = measure(grid, 100, 30);
        assertEquals(new UiSize(80, 30), frame.size());
        assertEquals(10, UiSceneTestSupport.element(frame, "text").y());
        UiSceneTestSupport.assertDisjoint(frame);
        assertThrows(UiOverflowException.class, () -> measure(grid, 100, 29));
    }

    @Test void stretchAlignsRealControlEdgesAndUniformTracksShareOneRectangleSize() {
        var natural = Map.of("a", new UiSize(20, 10), "b", new UiSize(40, 20),
                "c", new UiSize(30, 30), "d", new UiSize(15, 15));
        for (var sizing : UiLayouts.GridSizing.values()) {
            var grid = Ui.layout(UiLayouts.grid(2, ui(10), ui(10), UiLayouts.GridAlignment.STRETCH,
                    UiLayouts.GridAlignment.STRETCH, sizing));
            for (var key : List.of("a", "b", "c", "d")) grid.child(Ui.button(Component.text(key)).key(key).build());
            var frame = natural(grid.build(), natural, 100, 100);
            var a = UiSceneTestSupport.element(frame, "a"); var b = UiSceneTestSupport.element(frame, "b");
            var c = UiSceneTestSupport.element(frame, "c"); var d = UiSceneTestSupport.element(frame, "d");
            assertEquals(a.x(), c.x()); assertEquals(a.size().width(), c.size().width());
            assertEquals(b.x(), d.x()); assertEquals(b.size().width(), d.size().width());
            assertEquals(a.y(), b.y()); assertEquals(a.size().height(), b.size().height());
            assertEquals(c.y(), d.y()); assertEquals(c.size().height(), d.size().height());
            if (sizing == UiLayouts.GridSizing.UNIFORM) {
                UiSceneTestSupport.assertUniformGrid(frame.elements());
                assertEquals(new UiSize(90, 70), frame.size());
                assertEquals(new UiSize(40, 30), a.size());
            } else assertEquals(new UiSize(30, 20), a.size());
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void stretchingWidthRemeasuresResponsiveHeightBeforeAllocatingRows() {
        var responsive = Ui.layout((scope, limits) -> {
            double height = limits.minWidth() >= 80 ? 40 : 10;
            scope.measure(0, UiConstraints.bounded(20, 10));
            return new UiLayout.Result(new UiSize(Math.max(20, limits.minWidth()), height), List.of(new UiLayout.Position(0, 0)));
        }).key("responsive").child(Ui.text(Component.text("caption")).key("caption").build()).build();
        var grid = Ui.layout(UiLayouts.grid(1, ui(0), ui(10), UiLayouts.GridAlignment.STRETCH, UiLayouts.GridAlignment.STRETCH))
                .child(responsive).child(Ui.button(Component.text("bottom")).key("bottom").build()).build();
        var sizes = Map.of("caption", new UiSize(20, 10), "bottom", new UiSize(80, 20));
        var frame = natural(grid, sizes, 100, 100);
        assertEquals(new UiSize(80, 70), frame.size());
        assertEquals(50, UiSceneTestSupport.element(frame, "bottom").y(), 1e-6);
        assertThrows(UiOverflowException.class, () -> natural(grid, sizes, 100, 60));
    }

    @Test void explicitMaximumSizesRemainCenteredInsideTheSharedTracks() {
        var grid = Ui.layout(UiLayouts.grid(1, ui(0), ui(10), UiLayouts.GridAlignment.STRETCH,
                UiLayouts.GridAlignment.STRETCH, UiLayouts.GridSizing.UNIFORM))
                .child(Ui.button(Component.text("fixed")).key("fixed").size(new UiSize(20, 10)).build())
                .child(Ui.button(Component.text("large")).key("large").build()).build();
        var frame = natural(grid, Map.of("fixed", new UiSize(20, 10), "large", new UiSize(40, 30)), 100, 100);
        var fixed = UiSceneTestSupport.element(frame, "fixed");
        assertEquals(new UiSize(20, 10), fixed.size());
        assertEquals(10, fixed.x(), 1e-6); assertEquals(10, fixed.y(), 1e-6);
        assertEquals(new UiSize(40, 30), UiSceneTestSupport.element(frame, "large").size());
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void stretchedSpansFillAllSharedRowsColumnsAndTheirGaps() {
        var grid = new UiContainer(UiKey.named("grid"), UiLayouts.grid(3, ui(10), ui(10),
                UiLayouts.GridAlignment.STRETCH, UiLayouts.GridAlignment.STRETCH, UiLayouts.GridSizing.UNIFORM),
                List.of(UiChild.grid(Ui.button(Component.text("span")).key("span").build(),
                                GridSlot.cell(0, 0).withRowSpan(2).withColumnSpan(2)),
                        UiChild.grid(Ui.button(Component.text("top")).key("top").build(), GridSlot.cell(0, 2)),
                        UiChild.grid(Ui.button(Component.text("right")).key("right").build(), GridSlot.cell(1, 2)),
                        UiChild.grid(Ui.button(Component.text("bottom")).key("bottom").build(), GridSlot.cell(2, 0))),
                UiStyle.PLAIN, UiSizing.CONTENT);
        var frame = natural(grid, Map.of("span", new UiSize(50, 40), "top", new UiSize(40, 30),
                "right", new UiSize(20, 20), "bottom", new UiSize(10, 10)), 200, 150);
        assertEquals(new UiSize(140, 110), frame.size());
        assertEquals(new UiSize(90, 70), UiSceneTestSupport.element(frame, "span").size());
        for (var key : List.of("top", "right", "bottom"))
            assertEquals(new UiSize(40, 30), UiSceneTestSupport.element(frame, key).size());
        assertEquals(100, UiSceneTestSupport.element(frame, "right").x(), 1e-6);
        assertEquals(80, UiSceneTestSupport.element(frame, "bottom").y(), 1e-6);
        UiSceneTestSupport.assertDisjoint(frame);
    }

    private static UiFrame natural(UiNode node, Map<String, UiSize> sizes, double width, double height) {
        return UiCompiler.compile(node, UiConstraints.bounded(width, height), (element, _) ->
                new UiCompiler.ElementMeasurement(sizes.get(element.key().value()), element.textWidthPixels()));
    }

    private static UiChild cell(String key, UiSize size, GridSlot cell) {
        return UiChild.grid(UiKey.named(key), Ui.text(Component.text(key)).key(key).size(size).build(), cell);
    }

    private static UiFrame measure(UiNode node, double width, double height) {
        return UiCompiler.compile(node, UiConstraints.bounded(width, height), (_, limits) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(80, limits.maxWidth()), Math.min(10, limits.maxHeight())), 100));
    }

    private static Map<String, UiLayout.Position> positions(UiFrame frame) {
        return frame.elements().stream().collect(Collectors.toMap(element -> element.node().key().value(),
                element -> new UiLayout.Position(element.x(), element.y())));
    }
}
