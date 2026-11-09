package org.encinet.mik.feature.community.communication;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementMenuTest {
    @Test void directoryActionsAndReadingActionsUseTheirOwnScopeAndRememberTheSourceBookmark() throws Exception {
        var board = new AnnouncementBoard(AnnouncementBrowserStateTest.notices());
        var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL);
        var scene = menu(board, state, Language.ZH_CN, 1, false, () -> {});
        var oldNext = node(scene, 1, "next").input().actions().get(FloatingMenuInteraction.PRIMARY);
        SceneGeometryTestSupport.scroll(scene, 1, "body", FloatingMenuInteraction.SCROLL_DOWN);
        var bookmark = state.get().position();
        assertTrue(bookmark.reading().offset() > 0);
        scene = menu(board, state, Language.ZH_CN, 1, false, () -> {});
        invoke(scene, "notice:" + board.entry(1).id(), FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1, state.get().directoryPage());
        assertEquals(bookmark, state.get().position());
        scene = menu(board, state, Language.ZH_CN, 1, false, () -> {});
        invoke(scene, "notice:" + board.entry(5).id(), FloatingMenuInteraction.PRIMARY);
        assertEquals(5, state.get().position().announcementIndex());
        oldNext.execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(5, state.get().position().announcementIndex(), "an old reader cannot move a newly selected notice");
        state.set(state.get().browse(board, 0));
        invoke(menu(board, state, Language.ZH_CN, 1, false, () -> {}), "notice:" + board.entry(0).id(), FloatingMenuInteraction.PRIMARY);
        assertEquals(bookmark, state.get().position());
        invoke(menu(board, state, Language.ZH_CN, 1, false, () -> {}), "latest", FloatingMenuInteraction.PRIMARY);
        assertEquals(UiReadingPosition.START, state.get().position().reading());
    }

    @Test void pageControlsAndWheelContinueAcrossNoticesAndCaptureBothCatalogBoundaries() throws Exception {
        var board = new AnnouncementBoard(AnnouncementBrowserStateTest.notices());
        var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL);
        var scene = menu(board, state, Language.EN_US, 1, false, () -> {});
        assertFalse(node(scene, 1, "previous").input().enabled());
        assertEquals("Next page", plain(node(scene, 1, "next").content().label()));
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var paging = reading(frame);
        paging.selectPage().accept(paging.page().count() - 2);
        scene = menu(board, state, Language.EN_US, 1, false, () -> {});
        assertEquals("Older notices", plain(node(scene, 1, "next").content().label()));
        SceneGeometryTestSupport.scroll(scene, 1, "body", FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1, state.get().position().announcementIndex());
        scene = menu(board, state, Language.EN_US, 1, false, () -> {});
        assertEquals("Newer notices", plain(node(scene, 1, "previous").content().label()));
        invoke(scene, "previous", FloatingMenuInteraction.PRIMARY);
        assertEquals(0, state.get().position().announcementIndex());
        assertEquals("Older notices", plain(node(menu(board, state, Language.EN_US, 1, false, () -> {}), 1, "next").content().label()));
        for (var notices : List.of(List.<Announcement>of(), List.of(new Announcement(1, "Single")))) {
            board = new AnnouncementBoard(notices);
            state.set(AnnouncementBrowserState.INITIAL);
            scene = menu(board, state, Language.EN_US, 1, false, () -> {});
            for (var direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN)) {
                SceneGeometryTestSupport.scroll(scene, 1, "body", direction);
                assertEquals(AnnouncementBrowserState.INITIAL.position(), state.get().position());
            }
        }
    }

    @Test void reloadIsExplicitAndTheDirectoryConstructsOnlyItsCurrentWindow() throws Exception {
        var board = new AnnouncementBoard(AnnouncementBrowserStateTest.notices());
        var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL);
        var reloaded = new AtomicInteger();
        var scene = menu(board, state, Language.EN_US, 1, false, reloaded::incrementAndGet);
        assertNull(entry(scene, "reload"));
        assertEquals(4, scene.entries().values().stream().filter(value -> value.id().contains("/notice:")).count());
        assertNull(entry(scene,"count").tooltip(), "Complete declarations do not need duplicate full-text help");
        scene.entries().values().stream().filter(value -> value.id().contains("/notice:"))
                .forEach(value -> assertNull(value.tooltip()));
        assertTrue(scene.decorations().isEmpty());
        invoke(menu(board, state, Language.EN_US, 1, true, reloaded::incrementAndGet), "reload", FloatingMenuInteraction.PRIMARY);
        assertEquals(1, reloaded.get());
    }

    @Test void directoryHeadingsAndBoundariesNeverScrollTheReader() throws Exception {
        var board = new AnnouncementBoard(AnnouncementBrowserStateTest.notices());
        var bookmark = new UiReadingPosition(UiKey.named("body"), 123, 0);
        var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL.read(board, bookmark).browse(board, 2));
        var scene = menu(board, state, Language.ZH_CN, 1, false, () -> {});
        SceneGeometryTestSupport.scroll(scene, 1, "count", FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(bookmark, state.get().position().reading());
        assertEquals(2, state.get().directoryPage());
        SceneGeometryTestSupport.scroll(scene, 1, "count", FloatingMenuInteraction.SCROLL_UP);
        assertEquals(bookmark, state.get().position().reading());
        assertEquals(1, state.get().directoryPage());
        SceneGeometryTestSupport.scroll(scene, 1, "body", FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(1, state.get().directoryPage(), "reader pagination must not reset directory browsing");
        assertNotEquals(bookmark, state.get().position().reading());
    }

    @Test void allLanguagesAndTextSizesKeepTheReadingSurfaceAndNavigationStableOnTheShortLastPage() throws Exception {
        var board = new AnnouncementBoard(AnnouncementBrowserStateTest.notices());
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL);
            var first = menu(board, state, language, scale.factor(), true, () -> {});
            var firstFrame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(first, scale.factor()), language + " @ " + scale);
            assertDisjoint(firstFrame);
            SceneGeometryTestSupport.assertVisualContentsFit(first, scale.factor());
            assertTrue(element(firstFrame, "notice:" + board.entry(0).id()).x() < element(firstFrame, "body").x());
            assertEquals(FloatingMenuDecoration.Alignment.LEFT, element(firstFrame, "body").node().alignment());
            assertTrue(element(firstFrame, "body").node().textWidthPixels() <= 320,
                    "320 pixels is a soft line width; the reading budget may wrap it earlier");
            var paging = reading(firstFrame);
            paging.selectPage().accept(paging.page().count() - 2);
            var last = menu(board, state, language, scale.factor(), true, () -> {});
            var lastFrame = SceneGeometryTestSupport.measure(last, scale.factor());
            assertDisjoint(lastFrame);
            SceneGeometryTestSupport.assertVisualContentsFit(last, scale.factor());
            assertEquals(firstFrame.readingRegion().size(), lastFrame.readingRegion().size(), language + " @ " + scale);
            for (String id : List.of("body", "previous", "next", "latest", "reload", "dismiss")) {
                assertEquals(element(firstFrame, id).x(), element(lastFrame, id).x(), 1e-6, language + " horizontal " + id);
                assertEquals(element(firstFrame, id).y(), element(lastFrame, id).y(), 1e-6, language + " vertical " + id);
            }
        }
    }

    @Test void theCompleteUnicodeSourceIsReachableAndFontChangesKeepTheSameSourceLocation() throws Exception {
        String body = "Aé界👩‍🔬".repeat(180);
        var board = new AnnouncementBoard(List.of(new Announcement(1, body)));
        var state = new AtomicReference<>(AnnouncementBrowserState.INITIAL);
        var received = new StringBuilder();
        for (int step = 0; step < 256; step++) {
            var scene = menu(board, state, Language.ZH_CN, 1.8, false, () -> {});
            var frame = SceneGeometryTestSupport.measure(scene, 1.8);
            received.append(plain(element(frame, "body").node().content().label()));
            var paging = reading(frame);
            if (!paging.page().hasNext()) break;
            paging.selectPage().accept(paging.page().next().index());
        }
        assertEquals(body, received.toString());
        var requested = state.get().position().reading();
        var changed = menu(board, state, Language.ZH_CN, .7, false, () -> {});
        var frame = SceneGeometryTestSupport.measure(changed, .7);
        var actual = reading(frame).readingPosition().orElseThrow();
        assertTrue(actual.offset() <= requested.offset());
        assertTrue(actual.offset() + plain(element(frame, "body").node().content().label()).codePointCount(0,
                plain(element(frame, "body").node().content().label()).length()) > requested.offset());
        assertSame(requested, state.get().position().reading());
    }

    private static UiFrame.Pagination reading(UiFrame frame) {
        return frame.pagination().stream().filter(value -> value.readingPosition().isPresent()).findFirst().orElseThrow();
    }
    private static UiElement node(MenuScene scene, double typography, String key) {
        return element(SceneGeometryTestSupport.measure(scene, typography), key).node();
    }
    private static MenuScene menu(AnnouncementBoard board, AtomicReference<AnnouncementBrowserState> state,
                                  Language language, double typography, boolean reload, Runnable onReload) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return AnnouncementMenu.build(board, state.get(), typography, reload, true,
                (message, args) -> FluentTestSupport.text(bundle, message, args),
                change -> state.set(change.apply(state.get())), onReload);
    }
    private static void invoke(MenuScene scene, String id, FloatingMenuInteraction interaction) {
        if (interaction.scroll()) { SceneGeometryTestSupport.scroll(scene, 1, id, interaction); return; }
        node(scene, 1, id).input().actions().get(interaction).execute(null, null, interaction);
    }
    private static String plain(net.kyori.adventure.text.Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }
}
