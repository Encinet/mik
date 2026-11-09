package org.encinet.mik.shell.menu;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsNavigationTest {
    @Test void browsingTheBoundedDirectoryKeepsTheSelectedCategoryAndEveryActionReachable() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.EN_US);
        var selected = new AtomicReference<>(SettingsMenu.Category.INTERFACE);
        var position = new AtomicReference<>(UiReadingPosition.START);
        var seen = new HashSet<String>();
        int count = 1;
        for (int page = 0; page < count; page++) {
            var scene = MenuScene.screen("navigation").viewport(new UiSize(240,400)).content(SettingsMenu.navigation(
                    message -> FluentTestSupport.text(bundle,message),selected.get(),position.get(),position::set,
                    category -> (_,_) -> selected.set(category))).build();
            var frame = SceneGeometryTestSupport.measure(scene,1.8);
            UiSceneTestSupport.assertDisjoint(frame); assertTrue(frame.size().height() <= 400);
            assertNotNull(UiSceneTestSupport.element(frame,"back-to-main"));
            var paging = frame.pagination().getFirst(); count = paging.page().count();
            for (var element : frame.elements()) if (element.node().key().value().startsWith("category:")) {
                seen.add(element.node().key().value()); assertEquals(UiRole.NAVIGATION,element.node().role());
            }
            if (page+1 < count) paging.selectPage().accept(page+1);
            assertEquals(SettingsMenu.Category.INTERFACE,selected.get(),"Browsing must not change the active feature");
        }
        assertEquals(SettingsMenu.Category.values().length,seen.size()); assertTrue(count > 1);
    }
}
