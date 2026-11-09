package org.encinet.mik.feature.world.plot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotRegionListTest {
    @Test void measuredWindowsReachEveryRectangleAndKeepTheirSourceAndCommandsAcrossTypographyChanges() {
        var regions = java.util.stream.IntStream.range(0,128).mapToObj(index -> new PlotSelectionRegions.Region(
                new PlotSelection.Bounds(index*100,64,0,index*100+7,67,7))).toList();
        for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) for (double scale : new double[]{.7,1.3}) {
            var source = new AtomicInteger();
            var seen = new LinkedHashSet<PlotSelectionRegions.Region>();
            int pages = 1;
            for (int index = 0; index < pages; index++) {
                var calls = new ArrayList<PlotSelectionRegions.Region>();
                var snapshot = new PlotEditorMap.Regions(regions,regions.get(2),3,true,false,source.get());
                var list = PlotRegionList.content(snapshot,row -> PlotRegionList.row(regions.get(row),
                        Component.text("#" + (row+1) + " · 4096×384×4096"),row==2,(_,_) -> calls.add(regions.get(row))),source::set);
                var scene = MenuScene.screen("regions").viewport(new UiSize(360,200)).content(list).build();
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                assertDisjoint(frame);
                var page = frame.pagination().getFirst();
                pages = page.page().count();
                assertEquals(index,page.page().index());
                assertTrue(calls.isEmpty(),"measurement cannot choose a rectangle");
                for (var row : frame.elements().stream().filter(value -> value.node().key().value().startsWith("region:")).toList())
                    scene.measured(frame).entries().get(row.path()).trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null,null,FloatingMenuInteraction.PRIMARY);
                assertEquals(regions.subList(page.page().fromIndex(),page.page().toIndex()),calls);
                seen.addAll(calls);
                var changed = SceneGeometryTestSupport.readableFrame(scene,font==.7 ? 1.8 : .7,new FloatingMenuFieldOfView(fov),scale);
                var reflowed = changed.pagination().getFirst().page();
                assertTrue(reflowed.fromIndex()<=source.get() && source.get()<reflowed.toIndex());
                if (page.page().hasNext()) page.selectPage().accept(page.page().next().index());
            }
            assertTrue(pages>1);
            assertEquals(regions,List.copyOf(seen));
        }
    }
}
