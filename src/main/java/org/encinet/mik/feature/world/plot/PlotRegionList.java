package org.encinet.mik.feature.world.plot;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** A finite rectangle window keeps its source anchor independently of the selected rectangle. */
final class PlotRegionList {
    private PlotRegionList() { }

    static UiNode content(PlotEditorMap.Regions regions, IntFunction<UiElement> row, IntConsumer browse) {
        int count = regions.open() && !regions.editing() ? regions.items().size() : 0;
        return Ui.windowedList(count,Ui.button(Component.text("M")).singleLine().build(),row)
                .key("regions").containing(regions.sourceIndex())
                .sizing(UiSizing.width(UiLength.ZERO,UiLength.ui(240)))
                .pager(page -> UiTemplates.pager(page,next -> browse.accept(
                        new org.encinet.mik.shared.menu.FloatingMenuPage(next,count,page.pageSize()).fromIndex()))).build();
    }

    static UiElement row(PlotSelectionRegions.Region region, Component label, boolean selected, FloatingMenuCommand choose) {
        return Ui.button(label).key(UiKey.item("region:" + region.bounds())).singleLine()
                .selected(selected).primary(choose).build();
    }
}
