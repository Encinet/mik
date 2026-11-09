package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuPaginationLayoutTest {
    @Test void pageNumbersStayOnThePanelsCenterAtBothEndsAndWithUnequalTranslatedButtons() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double typography : List.of(.7, 1.0, 1.8)) for (int width : List.of(160, 320, 640)) {
                for (int index : List.of(0, 9, 11)) {
                    var scene = MenuScene.screen("pagination").viewport(new UiSize(width, 2048))
                            .content(Ui.column().child(Ui.stack().size(new UiSize(width, 40)).build())
                                    .child(UiTemplates.pagination("pages"), LinearSlot.stretch()).build())
                            .pagination("pages", new FloatingMenuPage(index, 12, 1),
                                    Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_PREVIOUS)),
                                    Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_PAGE, index + 1, 12)),
                                    Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_NEXT)), _ -> { }).build();
                    var frame = SceneGeometryTestSupport.naturalFrame(scene, typography);
                    assertDisjoint(frame);
                    var page = element(frame, "page");
                    assertEquals(width / 2.0, page.x() + page.size().width() / 2, 1e-6,
                            language.id() + " scale=" + typography + " width=" + width + " page=" + index);
                }
            }
        }
    }

    @Test void componentPagersKeepTheirCenterAndNavigationTargetsWhenAnEdgeDisappears() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double typography : List.of(.7, 1.0, 1.8)) for (int width : List.of(160, 320, 640)) {
                for (int index : List.of(0, 9, 11)) for (boolean icons : List.of(false, true)) {
                    var page = new FloatingMenuPage(index, 12, 1);
                    var selected = new ArrayList<Integer>();
                    var pager = icons ? UiTemplates.pager(page, selected::add)
                            : UiTemplates.pager(page, Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_PREVIOUS)),
                                    Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_PAGE, index + 1, page.count())),
                                    Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_NEXT)), selected::add);
                    var scene = MenuScene.screen("component-pager").viewport(new UiSize(width, 2048))
                            .content(Ui.column().child(Ui.stack().size(new UiSize(width, 40)).build())
                                    .child(pager, LinearSlot.stretch()).build()).build();
                    var frame = SceneGeometryTestSupport.naturalFrame(scene, typography);
                    assertDisjoint(frame);
                    var number = element(frame, "page");
                    assertEquals(width / 2.0, number.x() + number.size().width() / 2, 1e-6,
                            language.id() + " scale=" + typography + " width=" + width + " page=" + index + " icons=" + icons);
                    assertEquals(page.hasPrevious(), entry(scene, "previous") != null);
                    assertEquals(page.hasNext(), entry(scene, "next") != null);
                    if (page.hasPrevious()) entry(scene, "previous").trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                    if (page.hasNext()) entry(scene, "next").trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                    var expected = new ArrayList<Integer>();
                    if (page.hasPrevious()) expected.add(index - 1);
                    if (page.hasNext()) expected.add(index + 1);
                    assertEquals(expected, selected);
                }
            }
        }
    }

    @Test void aSinglePageCanKeepItsSummaryWithoutAddingInactiveButtons() {
        var page = new FloatingMenuPage(0, 1, 1);
        var pager = UiTemplates.pager(page, Component.text("Previous"), Component.text("1 / 1"),
                Component.text("Next"), _ -> fail("A single page has no navigation"));
        var scene = MenuScene.screen("single").content(pager).build();
        assertNotNull(entry(scene, "page"));
        assertNull(entry(scene, "previous"));
        assertNull(entry(scene, "next"));
        assertTrue(elements(MenuScene.screen("compact-single").content(UiTemplates.pager(page,
                _ -> fail("A single page has no navigation"))).build()).isEmpty());
    }
}
