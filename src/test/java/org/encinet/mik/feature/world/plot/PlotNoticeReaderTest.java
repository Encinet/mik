package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotNoticeReaderTest {
    @Test void popupAndStandaloneReadersShareContentAndEachPreservesEverySourceRange() {
        String body = "社区花园公告 🌿\n\n欢迎一起建设。\n".repeat(30);
        var notice = notice(body);
        var redraws = new AtomicInteger();
        try (var reader = new PlotNoticeReader(() -> notice, () -> Component.text("Plot notice"), redraws::incrementAndGet)) {
            var received = new StringBuilder();
            int count = 1;
            for (int index = 0; index < count; index++) {
                var popup = reader.render();
                var popupFrame = SceneGeometryTestSupport.measure(popup, 1);
                var page = popupFrame.pagination().getFirst();
                count = page.page().count();
                assertEquals(index, page.page().index());
                var position = page.readingPosition().orElseThrow();
                var standalone = MenuScene.screen("standalone").content(PlotMenuLayouts.noticeBoard(
                        PlotNoticeContent.reading(notice, position, _ -> {}))).build();
                var standaloneFrame = SceneGeometryTestSupport.measure(standalone, 1);
                for (String key : List.of("plot-name", "notice-body")) {
                    var expected = element(standaloneFrame,key).node();
                    var actual = element(popupFrame,key).node();
                    if (key.equals("plot-name")) assertEquals(expected.content().label(), actual.content().label());
                    assertEquals(expected.style(), actual.style());
                }
                int end = body.codePointCount(0,body.length());
                if (index + 1 < count) {
                    var next = element(popupFrame,"next");
                    popup.measured(popupFrame).entries().get(next.path()).trigger(FloatingMenuInteraction.PRIMARY)
                            .execute(null,null,FloatingMenuInteraction.PRIMARY);
                    end = SceneGeometryTestSupport.measure(reader.render(),1).pagination().getFirst()
                            .readingPosition().orElseThrow().offset();
                }
                String range = body.substring(body.offsetByCodePoints(0,position.offset()), body.offsetByCodePoints(0,end));
                assertEquals(range.stripTrailing(), plain(element(popupFrame,"notice-body").node().content().label()));
                received.append(range);
            }
            assertEquals(body,received.toString());
            assertEquals(count-1,redraws.get());
            var standalonePosition = new AtomicReference<>(UiReadingPosition.START);
            var standaloneText = new StringBuilder();
            int standalonePages = 1;
            for (int index = 0; index < standalonePages; index++) {
                var scene = MenuScene.screen("standalone").content(PlotMenuLayouts.noticeBoard(
                        PlotNoticeContent.reading(notice,standalonePosition.get(),standalonePosition::set))).build();
                var frame = SceneGeometryTestSupport.measure(scene,1);
                var page = frame.pagination().getFirst();
                standalonePages = page.page().count();
                assertEquals(index,page.page().index());
                int start = page.readingPosition().orElseThrow().offset();
                int end = body.codePointCount(0,body.length());
                if (page.page().hasNext()) {
                    page.selectPage().accept(page.page().next().index());
                    end = standalonePosition.get().offset();
                }
                String range = body.substring(body.offsetByCodePoints(0,start),body.offsetByCodePoints(0,end));
                assertEquals(range.stripTrailing(),plain(element(frame,"notice-body").node().content().label()));
                standaloneText.append(range);
            }
            assertEquals(body,standaloneText.toString());
        }
    }

    @Test void changedContentClampsReadingPositionAndClosingInvalidatesOldPageActions() {
        var current = new AtomicReference<>(notice("原始内容\n".repeat(50)));
        var redraws = new AtomicInteger();
        var reads = new AtomicInteger();
        var reader = new PlotNoticeReader(() -> { reads.incrementAndGet(); return current.get(); }, () -> Component.text("Notice"), redraws::incrementAndGet);
        var first = reader.render();
        var frame = SceneGeometryTestSupport.measure(first,1);
        var oldNext = first.measured(frame).entries().get(element(frame,"next").path()).trigger(FloatingMenuInteraction.PRIMARY);
        oldNext.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,SceneGeometryTestSupport.measure(reader.render(),1).pagination().getFirst().page().index());
        current.set(notice("修订后的短公告"));
        var shortPage = SceneGeometryTestSupport.measure(reader.render(),1);
        assertEquals("修订后的短公告",plain(element(shortPage,"notice-body").node().content().label()));
        assertTrue(shortPage.elements().stream().noneMatch(node -> node.node().key().value().equals("next")));
        reader.close();
        int completedReads = reads.get(), completedRedraws = redraws.get();
        oldNext.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertTrue(reader.render().entries().isEmpty());
        assertEquals(completedReads,reads.get());
        assertEquals(completedRedraws,redraws.get());
    }

    @Test void fullNoticePopoversFitAllTextSizesAndFieldsOfViewAndAnchorsBelongToThePlot() {
        var notice = notice("公告正文🌿\n\n".repeat(80));
        var source = PlotNoticeContent.source(notice.plotId());
        assertNotEquals(source,PlotNoticeContent.source(UUID.randomUUID()));
        var preview = PlotNoticeContent.preview(notice,Component.text("查看全文"),source,(_,_)->{});
        var reading = PlotNoticeContent.reading(notice,UiReadingPosition.START,_->{});
        var profile = UiTemplates.sidebar("profile",Ui.text(Component.text("Player")).size(new UiSize(200,240)).build());
        var launcher = UiTemplates.window("hub",Ui.text(Component.text("Main menu"))
                .sizing(UiSizing.width(UiLength.ZERO,UiLength.ui(400))).build());
        var plot = UiTemplates.sidebar("plot",preview).withSizing(UiSizing.width(UiLength.ZERO,UiLength.ui(300)));
        var host = MenuScene.screen("preview").viewport(new UiSize(1440,2048)).content(
                UiTemplates.panoramicPanels("hub",UiLength.ui(16),profile,launcher,plot))
                .popover(MenuPopover.attached(source,Component.text("地段公告板"),reading,Component.text("关闭"))).build();
        for (var scale : FloatingMenuTextScale.values()) for (int fov : new int[]{30,70,110}) {
            var frame = assertDoesNotThrow(()->SceneGeometryTestSupport.popoverFrame(host,scale.factor(),new FloatingMenuFieldOfView(fov)),scale+" FOV "+fov);
            assertNotNull(frame); assertDisjoint(frame);
            assertTrue(frame.pagination().getFirst().page().count()>1);
        }
    }

    private static PlotNoticeBoard notice(String body) {
        return new PlotNoticeBoard(new UUID(0, 1), "社区花园 Garden", new UUID(0, 2), "Owner", body, "");
    }
    private static String plain(Component content) { return PlainTextComponentSerializer.plainText().serialize(content); }
}
