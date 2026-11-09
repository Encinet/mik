package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiBalancedGridLayoutTest {
    @Test void measuredContentAspectDeterminesRectangularTracks() {
        var wide = measure(6, new UiSize(40, 20), 1, UiLayouts.GridBias.TALLER, 400, 400);
        assertEquals(new UiSize(90, 80), wide.size());
        assertEquals(2, wide.elements().stream().map(UiFrame.Element::x).distinct().count());
        var tall = measure(6, new UiSize(20, 40), 1, UiLayouts.GridBias.TALLER, 400, 400);
        assertEquals(new UiSize(80, 90), tall.size());
        assertEquals(3, tall.elements().stream().map(UiFrame.Element::x).distinct().count());
        for (var entry : wide.elements()) assertEquals(new UiSize(40, 20), entry.size());
        for (var entry : tall.elements()) assertEquals(new UiSize(20, 40), entry.size());
    }

    @Test void targetAspectAndDirectionAreCallerChoices() {
        var tall = measure(2, new UiSize(40, 40), 1, UiLayouts.GridBias.TALLER, 400, 400);
        var wide = measure(2, new UiSize(40, 40), 1, UiLayouts.GridBias.WIDER, 400, 400);
        assertEquals(new UiSize(40, 90), tall.size());
        assertEquals(new UiSize(90, 40), wide.size());
        var landscape = measure(6, new UiSize(40, 20), 3, UiLayouts.GridBias.NONE, 400, 400);
        assertEquals(new UiSize(140, 50), landscape.size());
        assertEquals(UiSize.ZERO, measure(0, new UiSize(40, 20), 1, UiLayouts.GridBias.NONE, 100, 100).size());
    }

    @Test void chosenGridHasTheClosestFeasibleMeasuredBoundsAndRestoresItsMeasurements() {
        for (int count = 1; count <= 12; count++) for (double target : new double[]{.5, 1, 2}) {
            var frame = measure(count, new UiSize(40, 20), target, UiLayouts.GridBias.TALLER, 180, 160);
            double actual = error(frame.size(), target);
            for (int columns = 1; columns <= Math.min(6, count); columns++) {
                double width = columns * 50 - 10, height = ((count + columns - 1) / columns) * 30 - 10;
                if (width <= 180 && height <= 160)
                    assertTrue(actual <= error(new UiSize(width, height), target) + 1e-6);
            }
            UiSceneTestSupport.assertDisjoint(frame);
            for (var entry : frame.elements()) assertEquals(new UiSize(40, 20), entry.size());
        }
        assertThrows(UiOverflowException.class, () -> measure(1, new UiSize(40, 20), 1, UiLayouts.GridBias.NONE, 30, 100));
    }

    @Test void explicitSpansAndInvalidDeclarationsKeepOrdinaryGridSemantics() {
        var spanning = Ui.text(Component.text("span")).key("span").size(new UiSize(80, 20)).build();
        var content = new UiContainer(UiKey.named("grid"), balance(1, UiLayouts.GridBias.TALLER),
                List.of(UiChild.grid(spanning, GridSlot.cell(0, 0).withColumnSpan(4))), UiStyle.PLAIN, UiSizing.CONTENT);
        var frame = compile(content, 100, 100);
        assertEquals(new UiSize(80, 20), frame.size());
        var invalid = new UiContainer(UiKey.named("grid"), balance(1, UiLayouts.GridBias.TALLER),
                List.of(UiChild.grid(spanning, GridSlot.cell(0, 0)),
                        UiChild.grid(Ui.text(Component.text("other")).key("other").build(), GridSlot.cell(0, 0))), UiStyle.PLAIN, UiSizing.CONTENT);
        var failure = assertThrows(IllegalArgumentException.class, () -> compile(invalid, 200, 200));
        assertFalse(failure instanceof UiOverflowException);
        assertThrows(IllegalArgumentException.class, () -> balance(Double.NaN, UiLayouts.GridBias.NONE));
        assertThrows(IllegalArgumentException.class, () -> balance(0, UiLayouts.GridBias.NONE));
    }

    @Test void unequalControlsFillEqualRectanglesWithoutChangingTheirContent() {
        var natural = java.util.Map.of("a", new UiSize(20, 10), "b", new UiSize(50, 15),
                "c", new UiSize(30, 30), "d", new UiSize(40, 20));
        var grid = Ui.layout(balance(1, UiLayouts.GridBias.TALLER));
        for (var key : List.of("a", "b", "c", "d")) grid.child(Ui.button(Component.text(key)).key(key).build());
        var frame = UiCompiler.compile(grid.build(), UiConstraints.bounded(200, 200), (element, _) ->
                new UiCompiler.ElementMeasurement(natural.get(element.key().value()), element.textWidthPixels()));
        UiSceneTestSupport.assertUniformGrid(frame.elements()); UiSceneTestSupport.assertDisjoint(frame);
        for (var element : frame.elements()) {
            assertEquals(new UiSize(50, 30), element.size());
            assertEquals(Component.text(element.node().key().value()), element.node().content().label());
        }
    }

    private static double error(UiSize size, double target) { return Math.abs(Math.log(size.width() / size.height() / target)); }
    private static UiLayout balance(double target, UiLayouts.GridBias bias) {
        return UiLayouts.balancedGrid(6, target, bias, UiLength.ui(10), UiLength.ui(10));
    }
    private static UiFrame measure(int count, UiSize cell, double target, UiLayouts.GridBias bias, double width, double height) {
        var content = Ui.layout(balance(target, bias)).key("grid");
        for (int index = 0; index < count; index++) content.child(Ui.text(Component.text(index)).key("entry:" + index).size(cell).build());
        return compile(content.build(), width, height);
    }
    private static UiFrame compile(UiNode node, double width, double height) {
        return UiCompiler.compile(node, UiConstraints.bounded(width, height), (element, limits) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(10, limits.maxWidth()), Math.min(10, limits.maxHeight())), element.textWidthPixels()));
    }
}
