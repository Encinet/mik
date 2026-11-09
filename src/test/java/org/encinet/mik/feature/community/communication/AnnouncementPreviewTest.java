package org.encinet.mik.feature.community.communication;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementPreviewTest {
    @Test void shortAndExactlyEightLineNoticesKeepTheirFullTextWithoutAnExpansionAction() {
        for (String body : List.of("服务器已完成维护。\n欢迎回来。", String.join("\n", java.util.Collections.nCopies(8, "短句")))) {
            var scene = preview(body, new AtomicInteger());
            for (var scale : FloatingMenuTextScale.values()) {
                var frame = SceneGeometryTestSupport.measure(scene, scale.factor());
                assertEquals(body, plain(element(frame, "announcement-body").node().content().label()));
                assertTrue(frame.elements().stream().noneMatch(element -> element.node().key().value().equals("announcement-read")));
                assertEquals(AnnouncementReading.date(1), plain(entry(scene, "announcement-date").label()));
                assertFalse(entry(scene, "announcement-body").interactive());
                assertDisjoint(frame);
                SceneGeometryTestSupport.assertVisualContentsFit(scene, scale.factor());
            }
        }
    }

    @Test void multilineWideGlyphAndUnbrokenNoticesAreClampedAtTheirMeasuredWidth() {
        var opened = new AtomicInteger();
        for (String body : List.of("公告正文\n".repeat(9), "社区花园建设🌼欢迎参与".repeat(50), "W".repeat(2000))) {
            var scene = preview(body, opened);
            for (var scale : FloatingMenuTextScale.values()) {
                var frame = SceneGeometryTestSupport.measure(scene, scale.factor());
                String excerpt = plain(element(frame, "announcement-body").node().content().label());
                if (excerpt.equals(body)) {
                    assertTrue(frame.elements().stream().noneMatch(element -> element.node().key().value().equals("announcement-read")));
                } else {
                    assertTrue(excerpt.endsWith("…"), scale + ": " + excerpt);
                    assertTrue(body.startsWith(excerpt.substring(0, excerpt.length() - 1)));
                    assertTrue(SceneGeometryTestSupport.textLines(scene, scale.factor(), "announcement-body") <= 8);
                    assertEquals(body,plain(element(frame,"announcement-body").node().input().tooltip().orElseThrow().text()));
                    assertNotNull(element(frame, "announcement-read"));
                }
                assertDisjoint(frame);
                SceneGeometryTestSupport.assertVisualContentsFit(scene, scale.factor());
            }
            entry(scene, "announcement-read").trigger(FloatingMenuInteraction.PRIMARY)
                    .execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
        assertEquals(3, opened.get());
    }

    @Test void absentOrBlankLatestNoticesHaveNoContentOrPanel() {
        for (var latest : new AnnouncementCatalog.Announcement[]{null,
                new AnnouncementCatalog.Announcement(1, ""), new AnnouncementCatalog.Announcement(1, " \n ")}) {
            var scene = MenuScene.screen("empty").content(UiTemplates.sidebar("announcements",
                    AnnouncementPreview.content(latest, Component.text("Latest"), Component.text("Read"), (_, _) -> {}))).build();
            assertTrue(scene.entries().isEmpty());
            assertTrue(SceneGeometryTestSupport.measure(scene, 1).panels().isEmpty());
        }
    }

    private static MenuScene preview(String body, AtomicInteger opened) {
        return MenuScene.screen("preview").viewport(new UiSize(640, 2048)).content(UiTemplates.split(UiTemplates.sidebar("announcements",
                AnnouncementPreview.content(new AnnouncementCatalog.Announcement(1, body), Component.text("Latest announcement"),
                        Component.text("View all announcements"), (_, _) -> opened.incrementAndGet())), Ui.text(Component.text("Main reading region")).build())).build();
    }

    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
}
