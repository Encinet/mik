package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuTableReadingTest {
    private record Row(int id, String name, String amount) { }

    @Test void variableRowsKeepTheHeaderColumnsAndEveryActionAcrossAllPagesAndReadingExtremes() {
        var rows = IntStream.range(0,24).mapToObj(index -> new Row(index,
                index % 3 == 0 ? "较长条目名称 Long translated row name " + index : "Row " + index,
                index == 23 ? "12345" : Integer.toString(index))).toList();
        for (double typography : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3})
            for (int degrees : new int[]{30,70,110}) {
                var page = new AtomicInteger(); var chosen = new ArrayList<Integer>(); var seen = new HashSet<Integer>();
                int count = 1; UiSize previous = null; Double column = null;
                for (int index = 0; index < count; index++) {
                    var scene = scene(rows, page, chosen);
                    var frame = SceneGeometryTestSupport.readableFrame(scene,typography,new FloatingMenuFieldOfView(degrees),scale);
                    assertDisjoint(frame); assertNotNull(element(frame,"back")); assertNotNull(element(frame,"amount-heading"));
                    var paging = frame.pagination().getFirst(); count = paging.page().count();
                    assertEquals(index,paging.page().index());
                    if (previous != null) assertEquals(previous,paging.size(),"Changing pages preserves the allocated table size");
                    previous = paging.size();
                    double x = element(frame,"amount-heading").x();
                    if (column != null) assertEquals(column,x,1e-6,"Unseen rows participate in the shared column demand");
                    column = x;
                    for (var node : frame.elements()) {
                        if (node.node().key().value().equals("amount")) assertEquals(x,node.x(),1e-6);
                        if (!node.node().key().value().startsWith("choose:")) continue;
                        int id = Integer.parseInt(node.node().key().value().substring("choose:".length()));
                        assertTrue(seen.add(id));
                        node.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                        assertEquals(id,chosen.getLast());
                    }
                    var published = scene.measured(frame);
                    assertEquals(frame.elements().size(),published.entries().size(),"Hidden rows publish no actions");
                    if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                }
                assertEquals(rows.size(),seen.size()); assertEquals(rows.size(),chosen.size()); assertTrue(count > 1);
            }
    }

    @Test void reorderingUsesObjectKeysAndTheCurrentRowsRatherThanPositions() {
        var first = new Row(7,"First","7"); var second = new Row(9,"Second","9");
        var chosen = new ArrayList<Integer>(); var page = new AtomicInteger();
        var before = SceneGeometryTestSupport.measure(scene(List.of(first,second),page,chosen),1);
        var after = SceneGeometryTestSupport.measure(scene(List.of(second,first),page,chosen),1);
        assertEquals(element(before,"choose:7").path(),element(after,"choose:7").path());
        assertTrue(element(after,"choose:7").y() > element(after,"choose:9").y());
        var target = element(after,"choose:9").node();
        target.input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(9),chosen);
    }

    @Test void emptyTablesKeepTheirHeaderAndAnOversizedAtomicRowRequestsAnExplicitFallback() {
        var empty = SceneGeometryTestSupport.measure(scene(List.of(),new AtomicInteger(),new ArrayList<>()),1);
        assertNotNull(element(empty,"amount-heading")); assertEquals(1,empty.pagination().getFirst().page().count());
        assertTrue(empty.elements().stream().noneMatch(node -> node.node().key().value().startsWith("choose:")));
        var huge = Ui.<Integer>table().key(UiKey.named("table"))
                .column(new UiTable.Column<>(UiKey.named("large"),Ui.text(Component.text("Header")).build(),UiTrack.content(),
                        _ -> Ui.stack().size(new UiSize(100,600)).build()))
                .rows(List.of(1), value -> UiKey.named("row:" + value)).paged(0,_ -> {});
        var fallback = Ui.pagedList().key("table").items(List.of(Ui.button(Component.text("Open details")).key("details").build()))
                .page(0,_ -> {}).build();
        var scene = MenuScene.screen("fallback").content(Ui.alternative(huge,fallback)).build();
        assertNotNull(element(SceneGeometryTestSupport.measure(scene,1),"details"));
    }

    private static MenuScene scene(List<Row> rows, AtomicInteger page, List<Integer> chosen) {
        var table = Ui.<Row>table().key(UiKey.named("rows")).gaps(UiLength.ui(8),UiLength.ui(6))
                .column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("名称 Name")).singleLine().build(),
                        UiTrack.content(UiLength.ZERO,UiLength.ui(280)), row -> {
                    var label = Component.text(row.name());
                    return Ui.button(label).key("choose:" + row.id()).maxLines(3).tooltip(label)
                            .primary((_,_) -> chosen.add(row.id())).build();
                }))
                .column(new UiTable.Column<>(UiKey.named("amount"),Ui.heading(Component.text("数量")).key("amount-heading").noWrap().build(),
                        UiTrack.intrinsic(), row -> Ui.text(Component.text(row.amount())).key("amount").noWrap().build()))
                .rows(rows,row -> UiKey.named("row:" + row.id())).paged(page.get(),page::set);
        return MenuScene.screen("table").viewport(new UiSize(640,240)).content(UiTemplates.sheet(UiTemplates.window("table",table),
                UiTemplates.toolbar("navigation",Ui.button(Component.text("Back")).key("back").primary((_,_) -> {}).build()))).build();
    }
}
