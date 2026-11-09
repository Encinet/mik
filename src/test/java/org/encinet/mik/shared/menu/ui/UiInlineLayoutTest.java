package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiInlineLayoutTest {
    @Test void firstAndLastLinesAlignDifferentTextSizesWithoutOverlappingTheirDescenders() {
        for (UiBaseline.Line line : UiBaseline.Line.values()) {
            var root = Ui.layout(UiLayouts.baselineRow(UiLength.ui(6), line, LinearSlot.Alignment.END))
                    .child(text("short")).child(text("paragraph")).build();
            var frame = UiCompiler.compile(root, UiConstraints.bounded(200, 100), (node, _) ->
                    node.key().value().equals("short")
                            ? new UiCompiler.ElementMeasurement(new UiSize(30, 20), 160).withBaseline(new UiBaseline(12, 12))
                            : new UiCompiler.ElementMeasurement(new UiSize(50, 40), 160).withBaseline(new UiBaseline(18, 34)));
            var a = frame.elements().get(0); var b = frame.elements().get(1);
            assertEquals(a.y() + a.baseline().orElseThrow().at(line), b.y() + b.baseline().orElseThrow().at(line), 1e-8);
            for (var element : frame.elements()) assertTrue(element.y() >= 0 && element.y() + element.size().height() <= frame.size().height());
        }
    }

    @Test void aGraphicWithoutABaselineUsesTheExplicitFallback() {
        var root = Ui.layout(UiLayouts.baselineRow(UiLength.ui(6), UiBaseline.Line.FIRST, LinearSlot.Alignment.CENTER))
                .child(text("text")).child(text("graphic")).build();
        var frame = UiCompiler.compile(root, UiConstraints.bounded(100, 100), (node, _) ->
                node.key().value().equals("text")
                        ? new UiCompiler.ElementMeasurement(new UiSize(30, 20), 160).withBaseline(new UiBaseline(14, 14))
                        : new UiCompiler.ElementMeasurement(new UiSize(16, 16), 160));
        var text = frame.elements().get(0); var graphic = frame.elements().get(1);
        assertEquals(text.y() + 14, graphic.y() + graphic.size().height() / 2, 1e-8);
        assertTrue(graphic.baseline().isEmpty());
    }

    @Test void inlineUnitsWrapAsWholeObjectsAndPreserveTheirKeysAndLineSpacing() {
        var root = Ui.layout(UiLayouts.inline(UiLength.ui(6), UiLength.ui(4), UiBaseline.Line.FIRST, LinearSlot.Alignment.END))
                .child(text("a")).child(text("b")).child(text("c")).build();
        var frame = UiCompiler.compile(root, UiConstraints.bounded(100, 100), (_, _) ->
                new UiCompiler.ElementMeasurement(new UiSize(40, 20), 160).withBaseline(new UiBaseline(14, 14)));
        assertEquals(new UiSize(86, 44), frame.size());
        assertEquals(0, frame.elements().get(0).y());
        assertEquals(0, frame.elements().get(1).y());
        assertEquals(24, frame.elements().get(2).y());
        assertEquals(java.util.List.of("a", "b", "c"), frame.elements().stream().map(value -> value.node().key().value()).toList());
        assertThrows(UiOverflowException.class, () -> UiCompiler.compile(root, UiConstraints.bounded(100, 30),
                (_, _) -> new UiCompiler.ElementMeasurement(new UiSize(40, 20), 160)));
    }

    @Test void nestedPaddingPreservesBaselinesAndEmptySlotsDoNotCreateGaps() {
        var nested = Ui.surface(text("nested")).key("panel").style(UiStyle.PLAIN.withPadding(UiInsets.all(UiLength.ui(8)))).build();
        var root = Ui.layout(UiLayouts.baselineRow(UiLength.ui(6), UiBaseline.Line.FIRST, LinearSlot.Alignment.END))
                .child(nested).child(Ui.column().key("empty").build()).child(text("sibling")).build();
        var frame = UiCompiler.compile(root, UiConstraints.bounded(200, 100), (_, _) ->
                new UiCompiler.ElementMeasurement(new UiSize(30, 20), 160).withBaseline(new UiBaseline(14, 14)));
        var first = frame.elements().get(0); var last = frame.elements().get(1);
        assertEquals(first.y() + first.baseline().orElseThrow().first(), last.y() + last.baseline().orElseThrow().first(), 1e-8);
        assertEquals(82, frame.size().width());
    }

    private static UiElement text(String key) { return Ui.text(Component.text(key)).key(key).build(); }
}
