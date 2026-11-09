package org.encinet.mik.feature.world.plot.board;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.world.plot.PlotMenuLayouts;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class PlotBoardPresentationTest {
    @Test
    void longMixedLanguageSubjectsDetailsAndRulingsRemainCompleteAtEveryReadingPreference() throws Exception {
        String subject = "庭院与公共入口 👩‍👩‍👧‍👦 e\u0301 · ".repeat(12);
        String body = "建筑😀".repeat(180) + " route and entrance".repeat(18);
        String ruling = "保留通道并完成记录 · ".repeat(90);
        String metadata = "Author\nworld " + "-123, 65, 456".repeat(4);
        var plain = PlainTextComponentSerializer.plainText();
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                var position = new AtomicReference<>(UiReadingPosition.START); var collected = new StringBuilder(); boolean complete = false;
                String formattedRuling = FluentTestSupport.text(bundle, Message.PLOT_BOARD_RULING_DETAIL, ruling);
                for (int pass = 0; pass < 256; pass++) {
                    var reading = PlotBoardRecordContent.reading(Component.text(subject), Component.text(metadata), Component.text(body),
                            Optional.of(Component.text(formattedRuling)), position.get(), position::set);
                    var menu = PlotMenuLayouts.screen("record:1", FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.record(reading));
                    menu.control("feedback", Component.text(FluentTestSupport.text(bundle, Message.PLOT_MENU_FEEDBACK))).region("actions");
                    menu.back(Component.text(FluentTestSupport.text(bundle, Message.PLOT_BACK))).region("navigation");
                    var scene = menu.build();
                    var frame = SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
                    assertDisjoint(frame); assertNotNull(element(frame, "feedback")); assertNotNull(element(frame, "back"));
                    for (var element : frame.elements()) if (element.path().startsWith(frame.readingRegion().path() + "/"))
                        collected.append(plain.serialize(element.node().content().label()));
                    assertTrue(element(frame, "heading").node().input().tooltip().isPresent());
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) { complete = true; break; }
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                }
                assertTrue(complete, language + " @ " + font + " FOV=" + fov);
                assertEquals(withoutSpacing(subject + metadata + body + formattedRuling), withoutSpacing(collected.toString()));
            }
        }
    }

    @Test void refreshingEarlierFieldsKeepsTheCurrentWrittenRulingBlock() {
        var position = new UiReadingPosition(UiKey.named("ruling"), 18, 13);
        for (var subject : List.of("Short subject", "Very long changed subject ".repeat(40))) {
            var reading = PlotBoardRecordContent.reading(Component.text(subject), Component.text("Updated author and location ".repeat(30)),
                    Component.text("Updated description ".repeat(100)), Optional.of(Component.text("裁决全文 · ".repeat(140))), position, _ -> { });
            var scene = PlotMenuLayouts.screen("record", FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.record(reading)).build();
            var frame = SceneGeometryTestSupport.readableFrame(scene, 1.8, new FloatingMenuFieldOfView(30), 1.3);
            assertEquals(UiKey.named("ruling"), frame.pagination().getFirst().readingPosition().orElseThrow().block().orElseThrow());
            assertNotNull(element(frame, "ruling"));
        }
    }

    private static String withoutSpacing(String value) {
        var result = new StringBuilder();
        value.codePoints().filter(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)).forEach(result::appendCodePoint);
        return result.toString();
    }

    @Test
    void alertLinksUseTheRecipientsAvailableWebsiteLocale() {
        String id = "00000000-0000-0000-0000-000000000001";
        assertEquals("https://mcmik.top/zh-CN/community/notices#notice-" + id,
                PlotBoardNotifications.boardUrl(Language.ZH_HK, id));
        assertEquals("https://mcmik.top/en/community/notices#notice-" + id,
                PlotBoardNotifications.boardUrl(Language.JA_JP, id));
    }
}
