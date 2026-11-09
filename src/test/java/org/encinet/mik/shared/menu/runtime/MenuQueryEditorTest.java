package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuQueryEditorTest {
    @Test void enterSubmitsOnceAndLeavesTheFieldWhileTheNextMessageIsOrdinaryChat() throws Exception {
        var fixture = new Fixture();
        assertEquals("Search songs, artists…", plain(entry(fixture.scene(), "preview").label()));
        assertEquals(1, fixture.scene().entries().size(), "an empty idle query has no label, tools or footer");
        fixture.click("preview");
        assertNotNull(entry(fixture.scene(), "cancel"));
        assertTrue(entry(fixture.scene(), "preview").selected());
        assertTrue(plain(entry(fixture.scene(), "preview").label()).contains("key.chat"));
        assertTrue(fixture.updates.isEmpty(), "the placeholder and chat prompt are presentation only");
        fixture.inputs.tick();
        assertTrue(entry(fixture.scene(), "preview").selected(), "waiting for Enter has no timeout");

        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertTrue(fixture.inputs.offerText(fixture.id, "  周杰伦  "));
                assertFalse(fixture.inputs.offerText(fixture.id, "ordinary chat before the menu tick"));
            }).get();
        }
        assertEquals("", fixture.value.get());
        fixture.inputs.tick();
        assertEquals(List.of("周杰伦"), fixture.updates);
        assertEquals("周杰伦", plain(entry(fixture.scene(), "preview").label()));
        assertNull(entry(fixture.scene(), "preview").tooltip(), "a short visible query has no duplicate tooltip");
        assertNull(entry(fixture.scene(), "cancel"));
        assertFalse(entry(fixture.scene(), "preview").selected());
        assertNotNull(entry(fixture.scene(), "clear"));
        assertFalse(fixture.inputs.offerText(fixture.id, "ordinary chat"));
        fixture.click("preview");
        assertTrue(fixture.inputs.offerText(fixture.id, "resumed query"));
        fixture.inputs.tick();
        assertEquals("resumed query", fixture.value.get());
        assertNull(entry(fixture.scene(), "cancel"));
    }

    @Test void clearAndCancelDiscardPendingTextButOnlyClearChangesTheCommittedQuery() {
        var fixture = new Fixture();
        fixture.value.set("garden");
        fixture.click("clear");
        assertEquals("", fixture.value.get());
        assertNull(entry(fixture.scene(), "clear"));
        assertFalse(fixture.inputs.offerText(fixture.id, "normal chat"), "idle Clear must not activate private capture");

        fixture.click("preview");
        fixture.value.set("old query");
        fixture.inputs.offerText(fixture.id, "queued before Cancel");
        assertNull(entry(fixture.scene(), "clear"));
        fixture.click("cancel");
        fixture.inputs.tick();
        assertEquals("old query", fixture.value.get());
        assertNull(entry(fixture.scene(), "cancel"));
        assertFalse(entry(fixture.scene(), "preview").selected());
        assertFalse(fixture.inputs.offerText(fixture.id, "normal chat after Clear"));
        fixture.click("preview");
        assertTrue(fixture.inputs.offerText(fixture.id, "next"));
        fixture.inputs.tick();
        assertEquals("next", fixture.value.get());
    }

    @Test void cancellingOnlyAbandonsThePendingInputAndAnEmptySubmissionClearsTheQuery() {
        var fixture = new Fixture();
        fixture.value.set("previous");
        fixture.click("preview");
        fixture.inputs.offerText(fixture.id, "abandoned query");
        fixture.click("cancel");
        fixture.inputs.tick();
        assertEquals("previous", fixture.value.get());
        assertTrue(fixture.updates.isEmpty());
        assertEquals(1, fixture.cancelled.get());
        assertNull(entry(fixture.scene(), "cancel"));
        assertFalse(fixture.inputs.offerText(fixture.id, "ordinary chat"));
        fixture.click("preview");
        fixture.inputs.offerText(fixture.id, "   ");
        fixture.inputs.tick();
        assertEquals("", fixture.value.get());
        assertNull(entry(fixture.scene(), "cancel"));
        assertNull(entry(fixture.scene(), "clear"));
        assertEquals("Search songs, artists…", plain(entry(fixture.scene(), "preview").label()));
        assertEquals(1, fixture.cancelled.get(), "submission is not a cancellation event");
    }

    @Test void rejectionAlsoLeavesTheFieldAndPreservesTheLastQueryForAnExplicitRetry() {
        var fixture = new Fixture();
        fixture.value.set("previous");
        for (String raw : List.of("😀".repeat(129), "first\nsecond", "first\r", "first\n")) {
            fixture.click("preview");
            assertTrue(fixture.inputs.offerText(fixture.id, raw));
            fixture.inputs.tick();
            assertEquals("previous", fixture.value.get());
            assertNotNull(entry(fixture.scene(), "problem"));
            assertNull(entry(fixture.scene(), "cancel"));
            assertFalse(entry(fixture.scene(), "preview").selected());
            assertFalse(fixture.inputs.offerText(fixture.id, "ordinary chat"));
        }
        String full = "😀".repeat(128);
        fixture.click("preview");
        assertTrue(fixture.inputs.offerText(fixture.id, full));
        fixture.inputs.tick();
        assertEquals(full, fixture.value.get());
        assertNull(entry(fixture.scene(), "problem"));
        assertEquals(full, plain(entry(fixture.scene(), "preview").label()));
        assertFullTextAvailable(Component.text(full),element(SceneGeometryTestSupport.measure(fixture.scene(),1),"preview"));
        fixture.click("preview");
        assertTrue(fixture.inputs.offerText(fixture.id, "<red>literal search"));
        fixture.inputs.tick();
        assertEquals("<red>literal search", fixture.value.get());
        assertEquals(Component.text("<red>literal search", net.kyori.adventure.text.format.NamedTextColor.WHITE),
                entry(fixture.scene(), "preview").label());
    }

    @Test void viewLossAndReplacementDiscardOldMessagesAndCannotCloseTheNewOwner() {
        var fixture = new Fixture();
        fixture.click("preview");
        fixture.inputs.offerText(fixture.id, "old query");
        var replacement = new ArrayList<String>();
        var next = fixture.inputs.beginChat(fixture.id, () -> true, replacement::add, () -> { });
        fixture.inputs.tick();
        assertTrue(fixture.updates.isEmpty());
        assertNull(entry(fixture.scene(), "cancel"));
        var staleView = fixture.scene();
        fixture.editor.close();
        fixture.editor.close();
        entry(staleView, "preview").trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertTrue(next.active());
        assertTrue(fixture.inputs.offerText(fixture.id, "next editor"));
        fixture.inputs.tick();
        assertEquals(List.of("next editor"), replacement);

        next.close();
        var suspended = new Fixture();
        suspended.click("preview");
        suspended.inputs.offerText(suspended.id, "queued before suspension");
        suspended.owner.set(false);
        suspended.inputs.tick();
        assertTrue(suspended.updates.isEmpty());
        assertNull(entry(suspended.scene(), "cancel"));
        assertFalse(suspended.inputs.offerText(suspended.id, "ordinary chat"));
    }

    @Test void normalStatesShareOneRowWithoutEmptyActionSpaceAndErrorsAppearOnlyWhenNeeded() {
        for (double typography : List.of(.75, 1.0, 1.4, 1.8)) {
            var fixture = new Fixture();
            Double height = null, width = null, valueX = null;
            for (int state = 0; state < 4; state++) {
                fixture.value.set(state == 1 || state == 3 ? "A long query 😀 ".repeat(8) : "");
                if (state == 2) fixture.click("preview");
                var scene = fixture.scene();
                var frame = SceneGeometryTestSupport.measure(scene, typography);
                assertDisjoint(frame);
                assertNull(entry(scene, "label"));
                assertNull(entry(scene, "hint"));
                assertNull(entry(scene, "confirm"));
                if (state < 2) assertNull(entry(scene, "cancel"));
                else assertNotNull(entry(scene, "cancel"));
                assertNull(entry(scene, "problem"));
                assertEquals(1, SceneGeometryTestSupport.textLines(scene, typography, "preview"));
                if (height == null) height = frame.size().height();
                assertEquals(height, frame.size().height(), 1e-6);
                var preview = element(frame, "preview");
                if (width == null) width = frame.size().width();
                if (valueX == null) valueX = preview.x();
                assertEquals(width, frame.size().width(), 1e-6, "The field keeps its width as tools appear");
                assertEquals(valueX, preview.x(), 1e-6, "The text origin stays stable as tools appear");
                if (state == 0) assertEquals(frame.size().width(), preview.size().width(), 1e-6,
                        "An idle empty query has no unused trailing slot");
                for (String key : List.of("clear", "cancel")) if (entry(scene, key) != null) {
                    var action = element(frame, key);
                    assertTrue(action.x() >= preview.x() + preview.size().width());
                    assertEquals(preview.y() + preview.size().height() / 2, action.y() + action.size().height() / 2, 1e-6);
                }
                assertEquals(frame.size().width(), frame.surfaces().stream().filter(surface -> surface.path().endsWith("/query")).findFirst().orElseThrow().size().width(), 1e-6);
            }
        }
    }

    @Test void rejectionWrapsUnderTheQueryWithoutWideningTheWorkspace() {
        for (double width : new double[] {180, 240, 480}) for (double scale : new double[] {1, 1.8}) {
            var fixture = new Fixture();
            fixture.click("preview");
            var before = SceneGeometryTestSupport.measure(fixture.scene(width), scale);
            fixture.inputs.offerText(fixture.id, "😀".repeat(129));
            fixture.inputs.tick();
            var scene = fixture.scene(width);
            var after = SceneGeometryTestSupport.measure(scene, scale);
            assertTrue(after.size().width() <= before.size().width() + 1e-6);
            assertTrue(after.size().height() > before.size().height());
            assertTrue(element(after, "problem").y() >= element(after, "preview").y() + element(after, "preview").size().height());
            assertDisjoint(after);
        }
    }

    @Test void everyQueryStateFitsNarrowHostsWithoutLosingItsActions() {
        for (double width : new double[] {180, 240, 280, 320, 480}) {
            for (var scale : FloatingMenuTextScale.values()) for (int state = 0; state < 4; state++) {
                var fixture = new Fixture();
                if (state > 1) fixture.value.set("A long search query 😀 ".repeat(5));
                if (state > 0) fixture.click("preview");
                if (state == 3) {
                    fixture.inputs.offerText(fixture.id, fixture.value.get());
                    fixture.inputs.tick();
                }
                var scene = fixture.scene(width);
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()),
                        "width=" + width + ", scale=" + scale + ", state=" + state);
                assertTrue(frame.size().width() <= width + 1e-6);
                assertDisjoint(frame);
                assertEquals(1, SceneGeometryTestSupport.textLines(scene, scale.factor(), "preview"));
                assertEquals(state == 1 || state == 2, entry(scene, "cancel") != null);
                assertEquals(state == 3, entry(scene, "clear") != null);
            }
        }
    }

    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final AtomicBoolean owner = new AtomicBoolean(true);
        final AtomicReference<String> value = new AtomicReference<>("");
        final List<String> updates = new ArrayList<>();
        final AtomicInteger redraws = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();
        final Thread main = Thread.currentThread();
        final MenuInputSessions inputs = new MenuInputSessions((_, _) -> fail("Not a confirmation"));
        final MenuQueryEditor editor = new MenuQueryEditor(Component.text("Search songs, artists…"), 128,
                new MenuQueryEditor.Labels(Component.text("Press ").append(Component.keybind("key.chat")),
                        Component.text("Clear"), Component.text("Cancel"), Component.text("Single line"),
                        length -> Component.text("Length " + length + " / 128. Click the field to retry with a shorter query.")),
                (text, ended) -> inputs.beginChatOnce(id, owner::get, text, ended), query -> {
                    assertSame(main, Thread.currentThread());
                assertFalse(inputs.blocking(id));
                    value.set(query); updates.add(query); redraws.incrementAndGet();
                    assertFalse(entry(scene(), "preview").selected(), "the host receives an already blurred field");
                }, redraws::incrementAndGet, cancelled::incrementAndGet);

        MenuScene scene() {
            return scene(480);
        }
        MenuScene scene(double width) { return MenuScene.screen("query").viewport(new UiSize(width, 512)).content(editor.content(value.get())).build(); }
        void click(String key) {
            entry(scene(), key).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
    }
}
