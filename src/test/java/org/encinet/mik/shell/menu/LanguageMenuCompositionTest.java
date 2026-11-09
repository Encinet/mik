package org.encinet.mik.shell.menu;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LanguageMenuCompositionTest {
    @Test void everyNativeNameRemainsReachableWithoutRotatingTheSceneAtBothFontAndSizeExtremes() {
        for (var scale : List.of(FloatingMenuScale.MINIMUM, FloatingMenuScale.MAXIMUM))
            for (var font : List.of(FloatingMenuTextScale.MINIMUM, FloatingMenuTextScale.MAXIMUM))
                for (int fov : new int[]{30,70,110}) {
                    var page = new AtomicInteger();
                    var seen = new HashSet<String>();
                    int count = 1;
                    for (int index = 0; index < count; index++) {
                        var builder = MenuScene.screen("language").content(LanguageMenu.content(page.get(), page::set));
                        builder.information("title", Component.text("Languages")).region("reader");
                        for (var language : Language.values()) builder.control("language:" + language.id(), Component.text(language.displayName()))
                                .region("languages").selected(language == Language.EN_US).primary((_, _) -> { });
                        builder.control("automatic", Component.text("Automatic")).region("navigation").primary((_, _) -> { });
                        var scene = builder.build();
                        var frame = SceneGeometryTestSupport.readableFrame(scene, font.factor(), new FloatingMenuFieldOfView(fov), scale.factor());
                        assertDisjoint(frame);
                        assertNotNull(element(frame, "automatic"));
                        frame.elements().stream().filter(row -> row.node().key().value().startsWith("language:"))
                                .forEach(row -> assertTrue(seen.add(row.node().key().value())));
                        var paging = frame.pagination().getFirst(); count = paging.page().count();
                        assertEquals(index, paging.page().index());
                        if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                    }
                    assertEquals(Language.values().length, seen.size());
                }
    }
}
