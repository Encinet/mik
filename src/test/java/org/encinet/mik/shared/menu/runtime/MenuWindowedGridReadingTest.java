package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuWindowedGridReadingTest {
    @Test void customWindowNavigationPublishesTheSameSourceMappingForButtonsAndScrolling() {
        for (int columns : new int[]{1,2}) {
            var selected = new AtomicInteger(-1);
            var list = Ui.windowedList(300,UiLength.ui(32),index -> Ui.button(Component.text(index)).key("row:" + index).singleLine().build())
                    .page(1,_ -> fail("The owner supplies its source mapping through the custom pager"))
                    .pager(page -> UiTemplates.pager(new FloatingMenuPage(page.index() + 10,page.count() + 10,1),index -> selected.set(index - 10))).build();
            UiNode content = columns == 1 ? list : Ui.windowedGrid(list,columns,UiLength.ui(120),UiLength.ui(12));
            var frame = SceneGeometryTestSupport.measure(MenuScene.screen("custom-window").viewport(new UiSize(420,200)).content(content).build(),1);
            var paging = frame.pagination().getFirst();
            assertEquals(11,paging.page().index());
            paging.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
            assertEquals(2,selected.get());
            element(frame,"previous").node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
            assertEquals(0,selected.get());
        }
    }

    @Test void responsiveColumnsKeepSourceIdentityAnchorsAndBoundedWorkAtReadingExtremes() {
        for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3})
            for (int fov : new int[]{30,110}) {
                var built = new ArrayList<Integer>(); var selected = new AtomicInteger(-1);
                var list = Ui.windowedList(Integer.MAX_VALUE,Ui.button(Component.text("Template")).singleLine().build(),index -> {
                    built.add(index);
                    return Ui.button(Component.text("Object " + index)).key("object:" + index).singleLine()
                            .primary((_,_) -> selected.set(index)).build();
                }).key("catalogue").containing(173).build();
                var scene = MenuScene.screen("grid").content(Ui.windowedGrid(list,2,UiLength.ui(180),UiLength.ui(12))).build();
                assertTrue(built.isEmpty());
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                var page = frame.pagination().getFirst().page();
                assertTrue(page.fromIndex() <= 173 && 173 < page.toIndex());
                assertTrue(page.pageSize() <= UiWindowedList.MAX_VISIBLE_ITEMS);
                assertEquals(page.toIndex() - page.fromIndex(),new java.util.HashSet<>(built).size());
                assertTrue(built.stream().allMatch(index -> index >= page.fromIndex() && index < page.toIndex()));
                assertDisjoint(frame);
                var target = element(frame,"object:173");
                assertFalse(frame.elements().stream().anyMatch(item -> item.node().content().label().equals(Component.text("Template"))));
                target.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                assertEquals(173,selected.get());
                var first = element(frame,"object:" + page.fromIndex());
                if (page.pageSize() > 1 && frame.readingRegion().size().width() >= 372)
                    assertTrue(element(frame,"object:" + (page.fromIndex() + 1)).x() > first.x());
            }
    }

    @Test void lastAndEmptyPagesDoNotConstructInvisibleCardsOrShrinkThePageBody() {
        var built = new ArrayList<Integer>();
        var first = frame(101,0,built); var last = frame(101,Integer.MAX_VALUE,built);
        assertEquals(first.pagination().getFirst().size(),last.pagination().getFirst().size());
        assertFalse(last.pagination().getFirst().page().hasNext());
        assertDisjoint(last);
        built.clear(); var empty = frame(0,0,built);
        assertTrue(built.isEmpty()); assertTrue(empty.elements().isEmpty());
        assertEquals(0,empty.pagination().getFirst().page().itemCount());
    }

    private static UiFrame frame(int count,int page,ArrayList<Integer> built) {
        var list = Ui.windowedList(count,UiLength.ui(32),index -> {
            built.add(index); return Ui.button(Component.text(index)).key("object:" + index).singleLine().build();
        }).page(page,_ -> {}).build();
        return SceneGeometryTestSupport.measure(MenuScene.screen("grid").viewport(new UiSize(480,200))
                .content(Ui.windowedGrid(list,2,UiLength.ui(180),UiLength.ui(12))).build(),1);
    }
}
