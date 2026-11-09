package org.encinet.mik.shared.menu.ui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiViewportLayoutTest {
    private static final UiCompiler.ElementMeasurer MEASURER = (_, limits) ->
            new UiCompiler.ElementMeasurement(new UiSize(Math.min(60, limits.maxWidth()), 20), 160);

    @Test void theWholeViewportContainsTheReaderSidebarsAndPersistentTools() {
        var items = java.util.stream.IntStream.range(0, 30).mapToObj(index -> Ui.text(Component.text(index)).key("item:" + index).build()).toList();
        var reader = UiTemplates.window("reader", Ui.pagedList().items(items).build());
        var side = UiTemplates.sidebar("side", Ui.stack().size(new UiSize(100, 80)).build());
        var tools = UiTemplates.toolbar("tools", Ui.button(Component.text("Back")).key("back").build());
        var root = UiTemplates.panoramicPanels("sheet", UiLength.ui(16), side, UiTemplates.sheet(reader, tools));
        var frame = UiCompiler.compile(root, UiConstraints.bounded(400, 180), MEASURER);
        assertTrue(frame.size().height() <= 180);
        assertTrue(frame.readingRegion().size().height() <= 180);
        assertTrue(frame.readingRegion().path().endsWith("/body"));
        assertTrue(frame.elements().stream().anyMatch(element -> element.node().key().value().equals("back")));
        assertTrue(frame.pagination().getFirst().page().count() > 1);
        assertTrue(frame.elements().stream().filter(element -> element.node().key().value().startsWith("item:"))
                .allMatch(element -> element.y() >= frame.readingRegion().y()
                        && element.y() + element.size().height() <= frame.readingRegion().y() + frame.readingRegion().size().height() + 1e-6));
    }

    @Test void anUnfocusedPageReceivesTheBudgetAtItsRootAndCannotShrinkBelowAnExplicitMinimum() {
        var root = Ui.column().children(List.of(Ui.text(Component.text("A")).build(), Ui.text(Component.text("B")).build())).build();
        var frame = UiCompiler.compile(root, UiConstraints.bounded(100, 60), MEASURER);
        assertEquals("root", frame.readingRegion().path());
        assertEquals(new UiSize(60, 48), frame.size());
        assertThrows(UiOverflowException.class, () -> UiCompiler.compile(Ui.stack().size(new UiSize(120, 120)).build(),
                UiConstraints.bounded(100, 100), MEASURER));
    }

    @Test void paddedSurfacesDoNotReplaceAnExplicitNestedFocusWithTheWholePanelsCenter() {
        var body = Ui.column().key("body").child(Ui.text(Component.text("Heading")).key("heading").build())
                .child(Ui.stack().key("viewport").size(new UiSize(80, 80)).build()).build();
        var root = UiTemplates.window("window", UiTemplates.focus(body, UiKey.named("viewport")));
        var frame = UiCompiler.compile(root, UiConstraints.bounded(200, 160), MEASURER);
        assertEquals(frame.readingRegion().y() + 40, frame.focus().y(), 1e-6);
        assertEquals(frame.readingRegion().x() + 40, frame.focus().x(), 1e-6);
        assertNotEquals(frame.size().height() / 2, frame.focus().y());
    }
    @Test void persistentChromeIsReservedBeforePagingAndAnEmptyBodyDoesNotReserveAGap() {
        var items = java.util.stream.IntStream.range(0,20).mapToObj(index -> Ui.text(Component.text(index))
                .key("row:"+index).build()).toList();
        var tree = Ui.layout(UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(10)))
                .child(UiKey.named("title"),Ui.text(Component.text("Title")).key("title").size(new UiSize(100,24)).build())
                .child(UiKey.named("body"),Ui.pagedList().items(items).build())
                .child(UiKey.named("tools"),Ui.button(Component.text("Close")).key("close").size(new UiSize(100,50)).build()).build();
        var frame = UiCompiler.compile(tree,UiConstraints.bounded(200,180),MEASURER);
        assertTrue(frame.pagination().getFirst().page().count() > 1);
        assertTrue(frame.size().height() <= 180); UiSceneTestSupport.assertDisjoint(frame);
        var close = UiSceneTestSupport.element(frame,"close");
        assertEquals(50,close.size().height());
        var empty = Ui.layout(UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(10)))
                .child(UiKey.named("body"),Ui.stack().build())
                .child(UiKey.named("tools"),Ui.stack().size(new UiSize(100,80)).build()).build();
        assertEquals(new UiSize(100,80),UiCompiler.compile(empty,UiConstraints.bounded(100,80),MEASURER).size());
    }

}
