package org.encinet.mik.feature.world.plot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotCardListTest {
    @Test void largeDirectoriesPublishEverySnapshotCardOnceWithItsOwnActionAtReadingExtremes() {
        for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
            var chosen = new AtomicInteger(-1); var read = new AtomicInteger();
            var cards = new ArrayList<PlotCardList.Card>();
            for (int index = 0; index < 300; index++) {
                int id = index;
                cards.add(new PlotCardList.Card(UiKey.named("plot:" + id),Component.text("庭院项目 " + id + "\nOwner\n128 members"),
                        (_,_) -> chosen.set(id)));
            }
            var seen = new HashSet<Integer>();
            String firstPath = null;
            for (int requested = 0; requested < 300; requested++) {
                var content = PlotCardList.content(cards,Component.text("Empty"),requested,read::set);
                var scene = PlotMenuLayouts.screen("catalogue",FloatingMenuAppearance.DEFAULT,PlotMenuLayouts.browser(content)).build();
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                assertDisjoint(frame);
                var page = frame.pagination().getFirst();
                for (var item : frame.elements()) if (item.node().key().value().startsWith("plot:")) {
                    int id = Integer.parseInt(item.node().key().value().substring(5));
                    assertTrue(seen.add(id),"A card was repeated across pages");
                    item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(id,chosen.get());
                    assertTrue(item.node().input().tooltip().isPresent());
                    if (id == 0) firstPath = item.path();
                }
                if (!page.page().hasNext()) break;
                page.selectPage().accept(page.page().next().index());
                assertEquals(requested + 1,read.get());
            }
            assertEquals(300,seen.size());
            java.util.Collections.reverse(cards);
            var reverse = PlotCardList.content(cards,Component.text("Empty"),Integer.MAX_VALUE,_ -> {});
            cards.clear(); // The declaration owns its source snapshot through every layout pass.
            var scene = PlotMenuLayouts.screen("catalogue",FloatingMenuAppearance.DEFAULT,PlotMenuLayouts.browser(reverse)).build();
            var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
            assertEquals(firstPath,element(frame,"plot:0").path());
            assertDisjoint(frame);
        }
    }
}
