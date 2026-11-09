package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PagedFlowSceneTest {
    @Test void runtimeIndexesOnlyTheVisiblePageAndKeepsTheSourceForLaterReflow() {
        var items = new ArrayList<UiNode>();
        for (int i = 0; i < 700; i++) items.add(Ui.button(Component.text("Style " + i)).singleLine().key("style:" + i)
                .primary((_, _) -> { }).build());
        var selected = new ArrayList<Integer>();
        var source = MenuScene.screen("collection").viewport(new UiSize(320, 2048)).content(
                Ui.pagedFlow(3).key("styles").items(items).page(0, selected::add)
                        .pager(page -> MenuScene.screen("pager").content(UiTemplates.pagination("pages"))
                                .pagination("pages", page, selected::add).build().content()).build()).build();
        var narrow = SceneGeometry.measure(source, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertTrue(narrow.definition().entries().size() < 700, "Off-page controls must not create entities or hitboxes");
        assertEquals(narrow.poses().keySet(), narrow.definition().entries().keySet());
        narrow.definition().triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(java.util.List.of(1), selected);
        var widerSource = narrow.definition().toBuilder().viewport(new UiSize(640, 2048)).build();
        var wide = SceneGeometry.measure(widerSource, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertTrue(wide.definition().entries().size() > narrow.definition().entries().size());
        assertTrue(wide.frame().pagination().getFirst().page().count() < narrow.frame().pagination().getFirst().page().count());
    }
}
