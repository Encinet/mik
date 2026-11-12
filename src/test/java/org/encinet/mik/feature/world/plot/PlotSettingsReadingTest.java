package org.encinet.mik.feature.world.plot;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotSettingsReadingTest {
    @Test void generalAndOwnershipOnlyTasksStayReachableWithoutEmptyFocusOrMixedGroups() throws Exception {
        var generalTasks = List.of(Message.PLOT_MENU_RENAME,Message.PLOT_NOTICE_EDIT,Message.PLOT_MENU_ATMOSPHERE);
        var ownership = List.of(Message.PLOT_MENU_TRANSFER,Message.PLOT_MENU_DELETE);
        for (var locale : Language.values()) {
            var text = FluentTestSupport.bundle(locale);
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3})
                for (int fov : new int[]{30,110}) for (boolean general : new boolean[]{true,false}) {
                    var read = new AtomicInteger(); var clicked = new java.util.ArrayList<Message>();
                    var seen = new HashSet<Message>();
                    for (int pass = 0; pass < 5; pass++) {
                        var builder = PlotMenuLayouts.screen("settings",FloatingMenuAppearance.DEFAULT,
                                PlotMenuLayouts.settings(general,read.get(),read::set));
                        builder.information("context",Component.text("庭院与地下铁路 A long project name")).region("context");
                        builder.information("heading",Component.text(FluentTestSupport.text(text,Message.PLOT_MENU_SETTINGS))).region("heading");
                        if (general) for (var task : generalTasks) builder.control(task.name(),Component.text(FluentTestSupport.text(text,task)))
                                .region("settings").primary((_,_) -> clicked.add(task));
                        builder.information("ownership-heading",Component.text(FluentTestSupport.text(text,Message.PLOT_WORKSPACE_OWNERSHIP))).region("ownership-heading");
                        builder.information("owner",Component.text("Long_Player_Name")).region("owner");
                        for (var task : ownership) builder.control(task.name(),Component.text(FluentTestSupport.text(text,task)))
                                .region("ownership").primary((_,_) -> clicked.add(task));
                        builder.information("identifier",Component.text("12345678-1234-1234-1234-123456789012")).region("identifier");
                        builder.back(Component.text(FluentTestSupport.text(text,Message.BACK))).region("navigation");
                        var frame = SceneGeometryTestSupport.readableFrame(builder.build(),font,new FloatingMenuFieldOfView(fov),scale);
                        assertDisjoint(frame);
                        var expected = general ? generalTasks : ownership;
                        for (var task : expected) for (var item : frame.elements()) if (item.node().key().value().equals(task.name())) {
                            assertTrue(seen.add(task));
                            item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                            assertEquals(task,clicked.getLast());
                        }
                        var paging = frame.pagination().stream().filter(page -> page.path().endsWith(general ? "/settings" : "/ownership")).findFirst().orElseThrow();
                        assertTrue(frame.readingRegion().size().height() > 0);
                        if (general) assertTrue(element(frame,"owner").y() > element(frame,generalTasks.stream().filter(seen::contains).reduce((_,b) -> b).orElseThrow().name()).y());
                        if (!paging.page().hasNext()) break;
                        paging.selectPage().accept(paging.page().next().index());
                    }
                    assertEquals(new HashSet<>(general ? generalTasks : ownership),seen,locale + " " + font + " " + fov);
                }
        }
    }
}
