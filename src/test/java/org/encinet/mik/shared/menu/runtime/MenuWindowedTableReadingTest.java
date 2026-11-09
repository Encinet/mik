package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuWindowedTableReadingTest {
    private record Row(int id, String name) { }

    @Test void maximumCollectionsBuildOnlyTheVisibleWindowAndKeepColumnsActionsAndPageSizeStable() {
        for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3})
            for (int fov : new int[]{30,110}) {
                var built = new ArrayList<Integer>(); var chosen = new ArrayList<Integer>(); var page = new AtomicInteger();
                var firstScene = scene(Integer.MAX_VALUE,page,built,chosen,false);
                assertTrue(built.isEmpty(),"Declaring a table does not visit its backing collection");
                var first = SceneGeometryTestSupport.readableFrame(firstScene,font,new FloatingMenuFieldOfView(fov),scale);
                var paging = first.pagination().getFirst();
                assertTrue(paging.page().pageSize() > 0); assertTrue(paging.page().pageSize() <= UiWindowedList.MAX_VISIBLE_ITEMS);
                assertEquals(paging.page().pageSize(),new java.util.HashSet<>(built).size());
                assertTrue(built.stream().allMatch(index -> index >= paging.page().fromIndex() && index < paging.page().toIndex()));
                assertDisjoint(first);
                assertEquals(paging.page().pageSize(),first.elements().stream().filter(value -> value.node().key().value().startsWith("choose:")).count());
                var button = element(first,"choose:0");
                button.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                assertEquals(List.of(0),chosen);
                assertTrue(first.elements().stream().noneMatch(value -> value.node().key().value().equals("choose:-1")),"The prototype publishes no actions");
                double column = element(first,"value-heading").x();
                for (var value : first.elements()) if (value.node().key().value().equals("value")) assertEquals(column,value.x(),1e-6);
                built.clear(); page.set(Integer.MAX_VALUE);
                var last = SceneGeometryTestSupport.readableFrame(scene(Integer.MAX_VALUE,page,built,chosen,false),font,new FloatingMenuFieldOfView(fov),scale);
                var ending = last.pagination().getFirst();
                assertFalse(ending.page().hasNext()); assertEquals(paging.size(),ending.size());
                assertEquals(paging.page().pageSize(),ending.page().pageSize());
                assertEquals(ending.page().toIndex()-ending.page().fromIndex(),new java.util.HashSet<>(built).size());
                assertTrue(built.stream().allMatch(index -> index >= ending.page().fromIndex() && index < ending.page().toIndex()));
                assertEquals(column,element(last,"value-heading").x(),1e-6);
            }
    }

    @Test void reorderingKeepsObjectBindingsAndEmptyTablesNeverRequestRows() {
        var page = new AtomicInteger(); var built = new ArrayList<Integer>(); var chosen = new ArrayList<Integer>();
        var before = SceneGeometryTestSupport.measure(scene(2,page,built,chosen,false),1);
        var after = SceneGeometryTestSupport.measure(scene(2,page,built,chosen,true),1);
        assertEquals(element(before,"choose:0").path(),element(after,"choose:0").path());
        assertTrue(element(before,"choose:0").y() < element(before,"choose:1").y());
        assertTrue(element(after,"choose:0").y() > element(after,"choose:1").y());
        element(after,"choose:1").node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(1),chosen);
        built.clear();
        var empty = SceneGeometryTestSupport.measure(scene(0,page,built,chosen,false),1);
        assertTrue(built.isEmpty()); assertNotNull(element(empty,"value-heading"));
        assertEquals(1,empty.pagination().getFirst().page().count());
        assertTrue(empty.elements().stream().noneMatch(value -> value.node().key().value().startsWith("choose:")));
    }

    @Test void oversizedRowsAndDuplicateVisibleIdentitiesFailRatherThanClippingOrReplacingAnAction() {
        var oversized = Ui.<Integer>table().column(new UiTable.Column<>(UiKey.named("value"),Ui.text(Component.text("Value")).build(),
                        UiTrack.content(),row -> Ui.stack().size(new UiSize(100,row < 0 ? 20 : 100)).build()))
                .windowed(1,-1,index -> index,index -> UiKey.named("row:" + index));
        for (var table : List.of(oversized,oversized.fitVisibleColumns()))
            assertThrows(UiOverflowException.class,() -> SceneGeometryTestSupport.measure(MenuScene.screen("oversized").content(table).build(),1));
        var duplicate = Ui.<Integer>table().column(new UiTable.Column<>(UiKey.named("value"),Ui.text(Component.text("Value")).build(),
                        UiTrack.content(),row -> Ui.button(Component.text(row)).singleLine().primary((_,_) -> {}).build()))
                .windowed(2,-1,index -> index,_ -> UiKey.named("same-row"));
        for (var table : List.of(duplicate,duplicate.fitVisibleColumns()))
            assertThrows(IllegalArgumentException.class,() -> SceneGeometryTestSupport.measure(MenuScene.screen("duplicate").content(table).build(),1));
    }

    @Test void aTightAuxiliaryHostReservesTheCompleteHeaderBeforeComputingItsRowCapacity() {
        var window = scene(256,new AtomicInteger(),new ArrayList<>(),new ArrayList<>(),false).toBuilder()
                .viewport(new UiSize(420,160)).build();
        var paged = Ui.<Integer>table().column(new UiTable.Column<>(UiKey.named("value"),Ui.heading(Component.text("Value")).key("value-heading").build(),
                        UiTrack.content(),value -> Ui.text(Component.text(value)).key("value").build()))
                .rows(java.util.stream.IntStream.range(0,24).boxed().toList(),value -> UiKey.named("row:" + value)).paged(0,_ -> {});
        var bounded = MenuScene.screen("bounded-table").viewport(new UiSize(420,160)).content(UiTemplates.window("main",paged)).build();
        for (var scene : List.of(window,bounded)) {
            var frame = ComponentSceneLayout.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1).frame();
            assertTrue(frame.size().height() <= 160); assertDisjoint(frame);
            assertTrue(frame.pagination().getFirst().page().count() > 1);
            var heading = element(frame,"value-heading");
            for (var cell : frame.elements()) if (cell.node().key().value().equals("value"))
                assertTrue(cell.y() >= heading.y() + heading.size().height());
        }
    }

    @Test void sourceAnchorsFindTheSameItemAfterCapacityChangesAndClampAfterFiltering() {
        for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
            for (int count : new int[]{512,16,0}) {
                var table = Ui.<Integer>table().column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("Name")).singleLine().build(),
                                UiTrack.content(),index -> Ui.button(Component.text("Object " + index)).key("choose:" + index).singleLine().build()))
                        .windowed(count,-1,index -> index,index -> UiKey.named("row:" + index)).containing(173);
                var list = Ui.windowedList(count,Ui.button(Component.text("Template")).singleLine().build(),
                                index -> Ui.button(Component.text("Object " + index)).key("choose:" + index).singleLine().build())
                        .containing(173).build();
                for (var content : List.of(table,list)) {
                    var scene = MenuScene.screen("source-anchor").content(content).build();
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                    var page = frame.pagination().getFirst().page();
                    if (count > 0) {
                        int anchor = Math.min(173,count - 1);
                        assertTrue(page.fromIndex() <= anchor && anchor < page.toIndex());
                        assertNotNull(element(frame,"choose:" + anchor));
                    } else {
                        assertEquals(0,page.index()); assertEquals(0,page.itemCount());
                        assertTrue(frame.elements().stream().noneMatch(node -> node.node().key().value().startsWith("choose:")));
                    }
                }
            }
        }
    }

    @Test void visibleColumnsGrowForLaterNamesAndShrinkAgainWithoutScanningTheBackingCollection() {
        String longName = "A considerably longer name in the second visible row";
        for (double font : new double[]{.7,1,1.8}) {
            var built = new ArrayList<Integer>(); var page = new AtomicInteger();
            java.util.function.Supplier<MenuScene> scene = () -> {
                var table = Ui.<Row>table().gaps(UiLength.ui(6),UiLength.ui(4))
                        .column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("Name")).singleLine().build(),
                                UiTrack.intrinsic(),row -> Ui.button(Component.text(row.name())).key("choose:" + row.id()).singleLine()
                                .textWidth(1024).primary((_,_) -> {}).build()))
                        .column(new UiTable.Column<>(UiKey.named("value"),Ui.heading(Component.text("Value")).key("value-heading").singleLine().build(),
                                UiTrack.content(),_ -> Ui.text(Component.text("OK")).key("value").singleLine().build()))
                        .windowed(Integer.MAX_VALUE,new Row(-1,"A"),index -> {
                            built.add(index); return new Row(index,index == 1 ? longName : "A");
                        },row -> UiKey.named("row:" + row.id())).fitVisibleColumns().page(page.get(),page::set);
                return MenuScene.screen("content-sized-window").viewport(new UiSize(1600,360)).content(table).build();
            };
            var first = SceneGeometryTestSupport.naturalFrame(scene.get(),font);
            var firstPage = first.pagination().getFirst().page();
            assertEquals(Component.text(longName),element(first,"choose:1").node().content().label());
            assertEquals(element(first,"choose:1").x() + element(first,"choose:1").size().width() + 6,
                    element(first,"value-heading").x(),1e-6);
            assertTrue(element(first,"choose:0").size().width() < element(first,"choose:1").size().width());
            assertEquals(firstPage.toIndex() - firstPage.fromIndex(),new java.util.HashSet<>(built).size());
            assertTrue(built.stream().allMatch(index -> index >= firstPage.fromIndex() && index < firstPage.toIndex()));
            assertDisjoint(first);
            built.clear(); page.set(Integer.MAX_VALUE);
            var last = SceneGeometryTestSupport.naturalFrame(scene.get(),font);
            var lastPage = last.pagination().getFirst().page();
            assertEquals(firstPage.pageSize(),lastPage.pageSize());
            assertTrue(element(last,"value-heading").x() < element(first,"value-heading").x());
            assertTrue(last.size().width() < first.size().width());
            assertEquals(lastPage.toIndex() - lastPage.fromIndex(),new java.util.HashSet<>(built).size());
            assertTrue(built.stream().allMatch(index -> index >= lastPage.fromIndex() && index < lastPage.toIndex()));
            assertDisjoint(last);
        }
    }

    @Test void offScreenTemplateTextDoesNotReserveWidthAndEmptyContentNeverRequestsRows() {
        for (int count : new int[]{0,64}) {
            var built = new ArrayList<Integer>();
            var table = Ui.<Integer>table()
                    .column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("Name")).singleLine().build(),
                            UiTrack.intrinsic(),index -> Ui.button(Component.text(index < 0 ? "long template ".repeat(100) : "A"))
                                    .key("choose:" + index).singleLine().textWidth(1024).primary((_,_) -> {}).build()))
                    .windowed(count,-1,index -> { built.add(index); return index; },index -> UiKey.named("row:" + index))
                    .page(0,_ -> {}).fitVisibleColumns();
            var frame = SceneGeometryTestSupport.naturalFrame(MenuScene.screen("template-is-not-content").viewport(new UiSize(1280,260)).content(table).build(),1);
            assertTrue(frame.size().width() < 300,"an off-screen label cannot widen the window");
            assertEquals(count == 0,built.isEmpty());
            assertTrue(built.size() <= UiWindowedList.MAX_VISIBLE_ITEMS);
            assertFalse(frame.elements().stream().anyMatch(element -> element.node().key().value().equals("choose:-1")));
            assertDisjoint(frame);
        }
    }

    @Test void contentSizedColumnsKeepHeadersAlignedInStretchedHostsAndDoNotInventEmptyHeaderSpace() {
        for (boolean header : new boolean[]{true,false}) {
            var table = Ui.<Integer>table().sizing(UiSizing.width(UiLength.ui(900),UiLength.ui(1200)))
                    .gaps(UiLength.ui(6),UiLength.ui(4))
                    .column(new UiTable.Column<>(UiKey.named("name"),header ? Ui.heading(Component.text("Name")).key("name-heading").singleLine().build()
                            : Ui.column().build(),UiTrack.intrinsic(),index -> Ui.button(Component.text("Song " + index))
                            .key("choose:" + index).singleLine().primary((_,_) -> {}).build()))
                    .column(new UiTable.Column<>(UiKey.named("value"),header ? Ui.heading(Component.text("Value")).key("value-heading").singleLine().build()
                            : Ui.column().build(),UiTrack.content(),index -> Ui.text(Component.text(index)).key("value").singleLine().build()))
                    .windowed(100,0,index -> index,index -> UiKey.named("row:" + index)).fitVisibleColumns();
            var frame = SceneGeometryTestSupport.naturalFrame(MenuScene.screen("stretched-content-columns").viewport(new UiSize(1200,260)).content(table).build(),1);
            assertEquals(900,frame.size().width(),1e-6);
            assertTrue(element(frame,"choose:0").size().width() < 100,"the host can stretch while buttons remain content-sized");
            assertEquals(frame.pagination().getFirst().page().pageSize(),frame.elements().stream()
                    .filter(element -> element.node().key().value().startsWith("choose:")).count());
            if (header) {
                assertEquals(element(frame,"name-heading").x(),element(frame,"choose:0").x(),1e-6);
                assertEquals(element(frame,"value-heading").x(),element(frame,"value").x(),1e-6);
            } else assertEquals(0,element(frame,"choose:0").y(),1e-6,"an empty header adds no separator");
            assertDisjoint(frame);
        }
    }

    private static MenuScene scene(int count, AtomicInteger page, List<Integer> built, List<Integer> chosen, boolean reversed) {
        var table = Ui.<Row>table().key(UiKey.named("table"))
                .column(new UiTable.Column<>(UiKey.named("name"),Ui.heading(Component.text("Player")).singleLine().build(),UiTrack.content(),
                        row -> Ui.button(Component.text(row.name())).singleLine().key("choose:" + row.id())
                                .primary((_,_) -> chosen.add(row.id())).build()))
                .column(new UiTable.Column<>(UiKey.named("value"),Ui.heading(Component.text("Value")).key("value-heading").singleLine().build(),UiTrack.content(),
                        row -> Ui.text(Component.text(row.id())).key("value").singleLine().build()))
                .windowed(count,new Row(-1,"A sufficiently wide template"),index -> {
                    int id = reversed ? count - index - 1 : index;
                    built.add(id); return new Row(id,"An actual player name " + id);
                },row -> UiKey.named("row:" + row.id())).page(page.get(),page::set);
        return MenuScene.screen("windowed-table").viewport(new UiSize(640,240)).content(UiTemplates.window("main",table)).build();
    }
}
