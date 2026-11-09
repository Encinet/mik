package org.encinet.mik.shared.menu.ui;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiFormViewportTest {
    @Test void theBoundedDirectoryReachesEveryFieldAndOnlyTheDisplayedControlHasAnEditingTarget() {
        for (var axis : UiLayouts.Axis.values()) for (double typography : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) for (double scale : new double[]{.7,1.3}) {
            var viewed = new AtomicReference<>("field-0");
            var edited = new AtomicInteger(-1);
            var seen = new HashSet<String>();
            int count = 1;
            for (int page = 0; page < count; page++) {
                var form = Ui.form().key("form").navigationAxis(axis);
                for (int index = 0; index < 18; index++) {
                    int target = index;
                    form.field("field-" + index,Component.text("字段 Long field name " + index),
                            Ui.textField(Component.text("Value " + index)).key("value").primary((_,_) -> edited.set(target)).build());
                }
                form.viewedField(viewed.get(),viewed::set).actions(Ui.button(Component.text("Save")).key("save").build());
                var scene = MenuScene.screen("form").content(form.build()).build();
                var frame = SceneGeometryTestSupport.readableFrame(scene,typography,new FloatingMenuFieldOfView(fov),scale);
                assertDisjoint(frame);
                assertTrue(frame.readingRegion().path().contains("/field:" + viewed.get() + ":control"));
                assertEquals(1,frame.elements().stream().filter(e -> e.node().key().value().equals("preview")).count());
                assertNotNull(element(frame,"save"));
                var paging = frame.pagination().getFirst();
                count = paging.page().count();
                assertEquals(page,paging.page().index());
                for (var row : frame.elements()) if (row.node().key().value().startsWith("select-field:")) {
                    seen.add(row.node().key().value());
                    row.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                }
                assertEquals(-1,edited.get(),"Directory navigation does not invoke a field's editing action");
                var current = element(frame,"preview");
                int target = Integer.parseInt(current.path().split("/field:")[1].split(":control")[0].substring("field-".length()));
                current.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                assertEquals(target,edited.get());
                edited.set(-1);
                if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
            }
            assertEquals(18,seen.size());
        }
    }
}
