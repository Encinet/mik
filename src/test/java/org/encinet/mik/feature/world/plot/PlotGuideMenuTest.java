package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotGuideMenuTest {
    @Test void everyLanguageKeepsTheWholeGuideAndReturnActionAtReadingExtremes() throws Exception {
        var plain = PlainTextComponentSerializer.plainText();
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            var language = new LanguageService(null) {
                @Override public String t(Language selected, Message message, Object... args) { return FluentTestSupport.text(bundle, message, args); }
            };
            String expected = String.join("", List.of(Message.PLOT_AREA_HINT, Message.PLOT_MAP_LEGEND, Message.PLOT_SELECTION_SESSION_HINT).stream()
                    .map(message -> FluentTestSupport.text(bundle, message)).toList());
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                var position = new AtomicReference<>(UiReadingPosition.START); var text = new StringBuilder(); boolean complete = false;
                for (int pass = 0; pass < 128; pass++) {
                    var scene = PlotGuideMenu.compose(language, locale, position.get(), position::set).build();
                    var frame = SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
                    assertDisjoint(frame); assertNotNull(element(frame, "back"));
                    for (var element : frame.elements()) if (element.path().startsWith(frame.readingRegion().path() + "/"))
                        text.append(plain.serialize(element.node().content().label()));
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) { complete = true; break; }
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                }
                assertTrue(complete); assertEquals(withoutSpacing(expected), withoutSpacing(text.toString()), locale + " @ " + font + " FOV=" + fov);
            }
        }
    }

    private static String withoutSpacing(String value) {
        var result = new StringBuilder();
        value.codePoints().filter(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)).forEach(result::appendCodePoint);
        return result.toString();
    }
}
