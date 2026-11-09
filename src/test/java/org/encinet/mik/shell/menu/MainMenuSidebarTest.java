package org.encinet.mik.shell.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuSidebarTest {
    @Test void lowerWidgetsKeepTheirColumnsHorizontalPlaneEvenBelowTheLauncher() {
        for (String side : List.of("left", "right")) {
            try (var sidebar = new MainMenuSidebar(side, () -> {}, List.of(
                    new MainMenuSidebar.Widget("top", () -> MenuScene.screen("top").content(Ui.text(Component.text("Upper card"))
                            .key("upper").size(new UiSize(160, 420)).build()).build()),
                    new MainMenuSidebar.Widget("bottom", () -> MenuScene.screen("bottom").content(Ui.text(Component.text("Plot notice"))
                            .key("lower").size(new UiSize(100, 100)).build()).build())))) {
                UiNode empty = MainMenuSidebar.column(side.equals("left") ? "right" : "left", 0, _ -> {}, List.of());
                var scene = fixture(side.equals("left") ? MainMenuComposition.content(sidebar.render().content(), empty, 0, _ -> {})
                        : MainMenuComposition.content(empty, sidebar.render().content(), 0, _ -> {}), "heading", "destinations", "quick", "footer")
                        .toBuilder().viewport(MenuScene.DEFAULT_VIEWPORT).build();
                for (var scale : FloatingMenuTextScale.values()) {
                    var upper = SceneGeometryTestSupport.pose(scene, scale.factor(), "upper");
                    var lower = SceneGeometryTestSupport.pose(scene, scale.factor(), "lower");
                    assertEquals(0, lower.pitchDegrees(), 1e-8);
                    assertTrue(side.equals("left") ? lower.yawDegrees() < 0 : lower.yawDegrees() > 0);
                    assertRotation(upper.rotation(), lower.rotation());
                }
            }
        }
    }

    @Test void bothSidesScopeReusableWidgetsAndCollapseEmptyWidgetsWithoutLeavingGaps() {
        for (String side : List.of("left", "right")) {
            var first = new MainMenuSidebar.Widget("first", () -> content("Short"));
            var second = new MainMenuSidebar.Widget("second", () -> content("A wider piece of content"));
            var empty = new MainMenuSidebar.Widget("empty", () -> MenuScene.screen("empty").content(Ui.column().build()).build());
            var plain = render(side, List.of(first, second));
            var withEmpty = render(side, List.of(empty, first, emptyWithId("between"), second));
            var reversed = render(side, List.of(second, first));
            assertEquals(plain.entries().keySet(), reversed.entries().keySet(), "ordering must not change action identity");
            assertEquals(2, plain.entries().size(), "the same local key can be reused in separate widgets");
            for (var scale : FloatingMenuTextScale.values()) {
                var before = SceneGeometryTestSupport.measure(plain, scale.factor());
                var after = SceneGeometryTestSupport.measure(withEmpty, scale.factor());
                assertEquals(before.size(), after.size());
                assertEquals(3, after.panels().size(), "two cards share one transparent owning plane");
                for (var panel : before.panels()) {
                    var actual = after.panels().stream().filter(value -> value.path().equals(panel.path())).findFirst().orElseThrow();
                    assertEquals(panel, actual);
                    assertTrue(actual.size().width() <= MainMenuSidebar.MAX_WIDTH + 1e-6);
                }
                var firstCard = before.panels().stream().filter(panel -> panel.path().endsWith("/first")).findFirst().orElseThrow();
                var secondCard = before.panels().stream().filter(panel -> panel.path().endsWith("/second")).findFirst().orElseThrow();
                assertTrue(firstCard.size().width() < secondCard.size().width(),
                        "a neighboring widget must not stretch a smaller widget");
                assertDisjoint(after);
            }
        }
    }

    @Test void widgetRevisionsDrivePollingWithoutRenderingOrChangingPriorSamples() {
        var revision = new AtomicReference<>("first");
        var renders = new AtomicInteger();
        try (var sidebar = new MainMenuSidebar("left", () -> {}, List.of(
                new MainMenuSidebar.Widget("live", () -> { renders.incrementAndGet(); return content(revision.get()); }, revision::get),
                new MainMenuSidebar.Widget("static", () -> content("Fixed"))))) {
            var before = sidebar.revision();
            assertEquals(before, sidebar.revision());
            assertEquals(0, renders.get());
            revision.set("second");
            assertNotEquals(before, sidebar.revision());
            assertEquals("first", before.getFirst());
            assertEquals("second", sidebar.revision().getFirst());
            sidebar.render();
            assertEquals(1, renders.get());
        }
    }

    @Test void forwardsSuspensionAndReleasesAllWidgetsOnceEvenIfOneCleanupFails() {
        var events = new ArrayList<String>();
        var first = panel("first", events, false);
        var second = panel("second", events, true);
        var sidebar = new MainMenuSidebar("right", () -> {}, List.of(
                new MainMenuSidebar.Widget("first", first), new MainMenuSidebar.Widget("second", second)));
        try {
            sidebar.attach(null);
            var scene = sidebar.render();
            scene.lifecycle().changed(null, null, FloatingMenuState.ACTIVE, FloatingMenuState.SUSPENDED, null);
            assertEquals(List.of("first:attach", "second:attach", "first:SUSPENDED", "second:SUSPENDED"), events);
            sidebar.attach(null);
            assertThrows(IllegalStateException.class, sidebar::close);
            assertEquals(List.of("second:close", "first:close"), events.subList(events.size() - 2, events.size()));
            int completed = events.size();
            sidebar.close();
            sidebar.attach(null);
            assertEquals(completed, events.size());
        } finally {
            sidebar.close();
        }
    }

    @Test void widgetPagesAreIndependentAndRetiredCallbacksCannotRedrawTheHost() {
        var redraws = new AtomicInteger();
        var widgets = java.util.stream.IntStream.range(0,6).mapToObj(index -> new MainMenuSidebar.Widget("widget:" + index,
                () -> MenuScene.screen("widget").content(Ui.text(Component.text("Card " + index)).key("value")
                        .size(new UiSize(160,100)).build()).build())).toList();
        var left = new MainMenuSidebar("left",redraws::incrementAndGet,widgets);
        var right = new MainMenuSidebar("right",() -> {},widgets);
        var seen = new java.util.HashSet<String>();
        int count = 1;
        java.util.function.IntConsumer retired = null;
        for (int page = 0; page < count; page++) {
            var frame = SceneGeometryTestSupport.measure(left.render().toBuilder().viewport(new UiSize(300,300)).build(),1);
            assertTrue(frame.size().height() <= 300); assertDisjoint(frame);
            var paging = frame.pagination().getFirst(); count = paging.page().count();
            assertEquals(page,paging.page().index());
            frame.panels().stream().filter(panel -> panel.path().contains("/widget:"))
                    .forEach(panel -> seen.add(panel.path().substring(panel.path().lastIndexOf('/')+1)));
            retired = paging.selectPage();
            if (page+1 < count) retired.accept(page+1);
        }
        assertEquals(6,seen.size()); assertTrue(count > 1); assertEquals(count-1,redraws.get());
        assertEquals(0,SceneGeometryTestSupport.measure(right.render(),1).pagination().getFirst().page().index());
        left.close(); int before = redraws.get(); retired.accept(0); assertEquals(before,redraws.get()); right.close();
    }

    @Test void duplicateWidgetIdsAreRejectedBeforeRendering() {
        var widget = new MainMenuSidebar.Widget("same", () -> content("Value"));
        assertThrows(IllegalArgumentException.class, () -> new MainMenuSidebar("left", () -> {}, List.of(widget, widget)));
    }

    private static MenuScene render(String side, List<MainMenuSidebar.Widget> widgets) {
        try (var sidebar = new MainMenuSidebar(side, () -> {}, widgets)) {
            return sidebar.render();
        }
    }

    private static MainMenuSidebar.Widget emptyWithId(String id) {
        return new MainMenuSidebar.Widget(id, () -> MenuScene.screen(id).content(Ui.column().build()).build());
    }

    private static MenuScene content(String text) {
        return MenuScene.screen("widget").content(Ui.text(Component.text(text)).key("value").build()).build();
    }

    private static MenuPanel panel(String name, List<String> events, boolean failClose) {
        return new MenuPanel() {
            @Override public MenuScene render() {
                return content(name).toBuilder().lifecycle((_, _, _, next, _) -> events.add(name + ":" + next)).build();
            }
            @Override public void attach(FloatingMenuHandle handle) { events.add(name + ":attach"); }
            @Override public void close() {
                events.add(name + ":close");
                if (failClose) throw new IllegalStateException("cleanup failed");
            }
        };
    }
}
