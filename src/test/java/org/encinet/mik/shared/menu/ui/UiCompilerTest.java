package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiLength.ui;

class UiCompilerTest {
    private static UiNode box(String key, double width, double height) {
        return Ui.text(Component.text(key)).key(key).size(new UiSize(width, height)).build();
    }

    private static UiFrame compile(UiNode node) {
        return UiCompiler.compile(node, UiConstraints.bounded(400, 400), (element, constraints) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(20, constraints.maxWidth()),
                        Math.min(10, constraints.maxHeight())), element.textWidthPixels()));
    }

    @Test void nestedComponentsHaveScopedIdentityAndCanBeReordered() {
        UiNode reusable = Ui.column().key("component").child(box("title", 40, 20)).build();
        UiKey first = UiKey.item(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        UiKey second = UiKey.item(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        UiFrame before = compile(Ui.row().child(first, reusable).child(second, reusable).build());
        UiFrame after = compile(Ui.row().child(second, reusable).child(first, reusable).build());
        assertEquals(before.elements().getFirst().path(), after.elements().getLast().path());
        assertEquals(before.elements().getLast().path(), after.elements().getFirst().path());
        assertNotEquals(before.elements().getFirst().path(), before.elements().getLast().path());
    }

    @Test void gridSharesColumnWidthsAndMeasuresVariableHeightRows() {
        UiFrame frame = compile(Ui.grid(2).gap(ui(8))
                .child(box("first", 40, 20)).child(box("second", 20, 10))
                .child(box("third", 50, 30)).child(box("fourth", 30, 15)).build());
        assertEquals(88, frame.size().width());
        assertEquals(58, frame.size().height());
        assertEquals(58, frame.elements().get(1).x());
        assertEquals(58, frame.elements().get(3).x());
        assertEquals(28, frame.elements().get(2).y());
        assertEquals(28, frame.elements().get(3).y());
    }

    @Test void wrappingCentersMixedHeightChildrenWithinEachRowAndKeepsNaturalWidths() {
        var frame = compile(Ui.layout(UiLayouts.wrap(ui(8), ui(10))).sizing(UiSizing.width(UiLength.ZERO, ui(110)))
                .child(box("text", 60, 20)).child(box("icon", 30, 30))
                .child(box("tall", 50, 40)).child(box("short", 40, 10)).build());
        assertEquals(new UiSize(98,80), frame.size());
        assertEquals(5, UiSceneTestSupport.element(frame, "text").y());
        assertEquals(0, UiSceneTestSupport.element(frame, "icon").y());
        assertEquals(40, UiSceneTestSupport.element(frame, "tall").y());
        assertEquals(55, UiSceneTestSupport.element(frame, "short").y());
        assertEquals(60, UiSceneTestSupport.element(frame, "text").size().width());
        UiSceneTestSupport.assertDisjoint(frame);
        var empty = compile(Ui.layout(UiLayouts.wrap(ui(8), ui(10))).build());
        assertEquals(UiSize.ZERO, empty.size());
    }

    @Test void gridAlignsShorterCellsInsideSharedColumnsWhileKeepingRowTopsAligned() {
        for (var alignment : UiLayouts.GridAlignment.values()) {
            var frame = compile(Ui.layout(UiLayouts.grid(2, ui(8), ui(8), alignment))
                    .child(box("first", 40, 20)).child(box("second", 20, 10))
                    .child(box("third", 50, 30)).child(box("fourth", 30, 15)).build());
            double offset = switch (alignment) { case START -> 0; case CENTER, STRETCH -> 5; case END -> 10; };
            assertEquals(offset, frame.elements().getFirst().x());
            assertEquals(58 + offset, frame.elements().get(1).x());
            assertEquals(0, frame.elements().get(2).x());
            assertEquals(58, frame.elements().get(3).x());
            assertEquals(0, frame.elements().getFirst().y());
            assertEquals(0, frame.elements().get(1).y());
            assertEquals(28, frame.elements().get(2).y());
            assertEquals(28, frame.elements().get(3).y());
        }
    }

    @Test void centeredGridIncludesColumnGapsWhenAligningSpanningChildren() {
        var frame = compile(Ui.grid(2).gap(ui(8)).horizontalAlignment(UiLayouts.GridAlignment.CENTER)
                .child(box("heading", 100, 20), GridSlot.cell(0, 0).withColumnSpan(2))
                .child(box("left", 40, 30), GridSlot.cell(1, 0))
                .child(box("right", 20, 15), GridSlot.cell(1, 1))
                .child(box("footer", 80, 20), GridSlot.cell(2, 0).withColumnSpan(2)).build());
        // Ordinary cells establish 40/20; the spanning heading shares its remaining 32 across both tracks.
        assertEquals(8, frame.elements().get(1).x());
        assertEquals(72, frame.elements().get(2).x());
        assertEquals(10, frame.elements().get(3).x());
        assertEquals(50, frame.elements().getFirst().x() + frame.elements().getFirst().size().width() / 2);
        assertEquals(50, frame.elements().getLast().x() + frame.elements().getLast().size().width() / 2);
    }

    @Test void verticalGridAlignmentIncludesAllRowsAndGapsInASpanningCell() {
        for (var alignment : UiLayouts.GridAlignment.values()) {
            var frame = compile(new UiContainer(UiKey.named("grid"), UiLayouts.grid(2, ui(8), ui(8), UiLayouts.GridAlignment.START, alignment),
                    List.of(UiChild.grid(box("span", 20, 10), GridSlot.cell(0, 0).withRowSpan(2)),
                            UiChild.grid(box("top", 40, 40), GridSlot.cell(0, 1)),
                            UiChild.grid(box("bottom", 40, 20), GridSlot.cell(1, 1))), UiStyle.PLAIN, UiSizing.CONTENT));
            double offset = switch (alignment) { case START -> 0; case CENTER, STRETCH -> 29; case END -> 58; };
            assertEquals(offset, UiSceneTestSupport.element(frame, "span").y(), 1e-8);
            assertEquals(48, UiSceneTestSupport.element(frame, "bottom").y(), 1e-8);
            assertEquals(68, frame.size().height(), 1e-8);
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void adaptiveGridKeepsCenterAlignmentWhenItFallsBackToOneColumn() {
        var frame = compile(Ui.grid(3).gap(ui(8)).adaptiveColumns().horizontalAlignment(UiLayouts.GridAlignment.CENTER)
                .child(box("short", 200, 20)).child(box("long", 250, 30)).build());
        assertEquals(25, frame.elements().getFirst().x());
        assertEquals(0, frame.elements().getLast().x());
        assertEquals(28, frame.elements().getLast().y());
        assertEquals(125, frame.elements().getFirst().x() + frame.elements().getFirst().size().width() / 2);
        assertEquals(125, frame.elements().getLast().x() + frame.elements().getLast().size().width() / 2);
    }

    @Test void spanningContentAndOrdinaryCellsShareOneGrid() {
        UiFrame frame = compile(Ui.grid(2).gap(ui(8))
                .child(box("heading", 100, 20), GridSlot.cell(0, 0).withColumnSpan(2))
                .child(box("left", 40, 30), GridSlot.cell(1, 0))
                .child(box("right", 20, 15), GridSlot.cell(1, 1)).build());
        assertEquals(100, frame.size().width());
        assertEquals(58, frame.size().height());
        assertEquals(64, frame.elements().get(2).x());
        assertEquals(28, frame.elements().get(2).y());
    }

    @Test void weightAllocatesRemainingSpaceAfterFixedContentAndGaps() {
        UiNode node = Ui.row().gap(ui(8)).child(box("cover", 40, 20))
                .child(Ui.text(Component.text("body")).key("body").build(), LinearSlot.grow(1)).build();
        UiFrame frame = UiCompiler.compile(node, UiConstraints.bounded(100, 100), (_, constraints) ->
                new UiCompiler.ElementMeasurement(new UiSize(constraints.maxWidth(), 10), 20));
        assertEquals(100, frame.size().width());
        assertEquals(48, frame.elements().get(1).x());
        assertEquals(52, frame.elements().get(1).size().width());
    }

    @Test void paddingAndBackgroundDoNotMultiplyTextOpacity() {
        UiStyle panel = UiStyle.PANEL.withBackgroundAlpha(0.25).withPadding(UiInsets.all(ui(8)));
        UiFrame frame = compile(Ui.column().style(panel).child(box("text", 40, 20)).build());
        assertEquals(new UiSize(56, 36), frame.size());
        assertEquals(8, frame.elements().getFirst().x());
        assertEquals(8, frame.elements().getFirst().y());
        assertEquals(0.25, frame.surfaces().getFirst().style().backgroundAlpha());
        assertEquals(1, frame.elements().getFirst().node().style().contentAlpha());
    }

    @Test void measuredWrappingIsPublishedWithoutChangingTheSourceNode() {
        UiElement source = Ui.text(Component.text("content")).textWidth(160).build();
        UiFrame frame = UiCompiler.compile(source, UiConstraints.bounded(100, 100), (_, _) ->
                new UiCompiler.ElementMeasurement(new UiSize(80, 30), 24));
        assertEquals(24, frame.elements().getFirst().node().textWidthPixels());
        assertEquals(160, source.textWidthPixels());
    }

    @Test void declaringAndMeasuringAnActionNeverExecutesIt() {
        AtomicInteger calls = new AtomicInteger();
        UiNode node = Ui.button(Component.text("save")).primary((_, _) -> calls.incrementAndGet()).build();
        UiFrame frame = compile(node);
        assertEquals(0, calls.get());
        assertTrue(frame.elements().getFirst().node().input().interactive());
    }

    @Test void duplicateLocalKeysRequireAnExplicitScope() {
        UiNode node = box("same", 20, 10);
        assertThrows(IllegalArgumentException.class, () -> Ui.row().child(node).child(node).build());
    }

    @Test void overlappingCellsAndSizeConflictsReportTheOwningPath() {
        UiNode overlap = Ui.grid(2).key("table")
                .child(box("a", 20, 10), GridSlot.cell(0, 0))
                .child(box("b", 20, 10), GridSlot.cell(0, 0)).build();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(overlap)).getMessage().contains("root"));
        UiNode impossible = Ui.row().child(box("too-wide", 500, 10)).build();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> compile(impossible)).getMessage().contains("too-wide"));
    }

    @Test void customLayoutMustMeasureAndArrangeEveryChild() {
        UiNode invalid = Ui.layout((_, _) -> new UiLayout.Result(UiSize.ZERO, List.of()))
                .child(box("missing", 20, 10)).build();
        assertThrows(IllegalArgumentException.class, () -> compile(invalid));
    }

    @Test void unresolvedPortsAndExcessiveDepthAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> compile(Ui.port("body", UiLayouts.column(ui(8)))));
        UiNode tree = box("deep", 10, 10);
        for (int i = 0; i < UiTree.MAX_DEPTH + 1; i++) tree = Ui.column().child(tree).build();
        UiNode finalTree = tree;
        assertThrows(IllegalArgumentException.class, () -> compile(finalTree));
    }
}
