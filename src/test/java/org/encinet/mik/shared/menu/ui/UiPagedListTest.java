package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class UiPagedListTest {
    @Test void anEmptyListDoesNotReserveAGapBelowItsHeader() {
        var list = Ui.pagedList().header(Ui.text(Component.text("No results")).key("heading")
                .size(new UiSize(80, 20)).build()).items(List.of()).build();
        var frame = UiCompiler.compile(list, UiConstraints.bounded(100, 20), (_, limits) ->
                new UiCompiler.ElementMeasurement(limits.constrain(new UiSize(10, 10)), 100));
        assertEquals(new UiSize(80, 20), frame.size());
        assertEquals(1, frame.elements().size());
        assertEquals(1, frame.pagination().size());
        assertDisjoint(frame);
    }

    @Test void emptyListsStillReserveTheGapWhenTheirCustomFooterIsVisible() {
        var list = Ui.pagedList().header(Ui.text(Component.text("No results")).size(new UiSize(80, 20)).build())
                .items(List.of()).pager(_ -> Ui.text(Component.text("Help")).size(new UiSize(80, 20)).build()).build();
        UiCompiler.ElementMeasurer measurer = (_, limits) -> new UiCompiler.ElementMeasurement(limits.constrain(new UiSize(10, 10)), 100);
        var frame = UiCompiler.compile(list, UiConstraints.bounded(100, 48), measurer);
        assertEquals(new UiSize(80, 48), frame.size());
        assertDisjoint(frame);
        assertThrows(UiOverflowException.class, () -> UiCompiler.compile(list, UiConstraints.bounded(100, 47), measurer));
    }

    private UiNode list(int page, java.util.function.IntConsumer selected) {
        return Ui.pagedList().key("reading").header(Ui.text(Component.text("Title")).key("heading").build())
                .items(java.util.stream.IntStream.range(0,7).mapToObj(index -> Ui.text(Component.text("Paragraph "+index)).key("item:"+index).build()).toList())
                .page(page,selected).build();
    }
    private UiFrame frame(int page, double height) {
        return UiCompiler.compile(list(page,_->{}),UiConstraints.bounded(300,height),(node,_) ->
                new UiCompiler.ElementMeasurement(new UiSize(node.key().value().equals("item:0") ? 120 : 40,
                        node.key().value().startsWith("item:") ? 38 : 20),160));
    }
    @Test void variableHeightPagesReserveTheirHeaderAndPagerAndKeepTheLastPageStable() {
        var first = frame(0,165);
        var last = frame(999,165);
        assertDisjoint(first); assertDisjoint(last);
        assertEquals(first.size(),last.size());
        assertEquals(element(first,"heading").y(),element(last,"heading").y());
        assertEquals(4,first.pagination().getFirst().page().count());
        assertEquals(3,last.pagination().getFirst().page().index());
        assertTrue(last.elements().stream().anyMatch(element -> element.node().key().value().equals("item:6")));
        assertTrue(last.elements().stream().anyMatch(element -> element.node().key().value().equals("previous")));
        assertThrows(UiOverflowException.class,() -> frame(0,80));
    }
    @Test void collectionsThatFitHaveNoRedundantPagerAndOnlyMeasuredEntriesReceiveInput() {
        var roomy = frame(0,500);
        assertEquals(1,roomy.pagination().getFirst().page().count());
        assertFalse(roomy.elements().stream().anyMatch(element -> element.node().key().value().equals("next")));
        var selected = new AtomicInteger(-1);
        var content = list(0,selected::set);
        var measured = UiCompiler.compile(content,UiConstraints.bounded(300,165),(node,_) ->
                new UiCompiler.ElementMeasurement(new UiSize(40,node.key().value().startsWith("item:") ? 38 : 20),160));
        var scene = MenuScene.screen("read").content(content).build().measured(measured);
        assertNotNull(entry(scene,"item:0"));
        assertNull(entry(scene,"item:6"));
        scene.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1,selected.get());
    }
}
