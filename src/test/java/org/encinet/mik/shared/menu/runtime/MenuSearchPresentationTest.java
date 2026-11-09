package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.quickwrite.fluent4j.container.FluentBundle;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuSearchPresentationTest {
    private final FloatingMenuTestWorld world = new FloatingMenuTestWorld();
    private final Location eye = new Location(world.world(), 0, 64, 0);
    private static final List<MenuPlayerSearch.Candidate> PLAYERS = IntStream.range(0, 300)
            .mapToObj(index -> new MenuPlayerSearch.Candidate(new UUID(0, index), "Player_Name_" + index)).toList();

    @Test void theInitialPlayerDirectoryUsesItsLoadedFrameAndKeepsItAcrossPagesAndQueries() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            var definition = MenuPlayerSearch.builder(text(bundle, Message.GOVERNANCE_MENU_PLAYERS),
                            text(bundle, Message.GOVERNANCE_MENU_PLAYER_NAME))
                    .flowResults(3, UiLength.ui(480))
                    .find(_ -> CompletableFuture.completedFuture(PLAYERS)).selected((_, _) -> {}).build();
            var labels = new MenuSearchWindow.Labels(text(bundle, Message.MENU_SEARCH_LOADING), text(bundle, Message.MENU_SEARCH_EMPTY),
                    text(bundle, Message.MENU_SEARCH_FAILED), text(bundle, Message.MENU_SEARCH_RETRY), text(bundle, Message.BACK),
                    (shown, total) -> Component.text(FluentTestSupport.text(bundle, Message.MENU_SEARCH_LIMIT, shown, total)));
            var query = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE, definition.placeholder(),
                    Component.text("Type"), text(bundle, Message.MENU_INPUT_CLEAR), text(bundle, Message.MENU_QUERY_CANCEL));
            for (int fov : new int[]{30, 70, 110}) for (double typography : new double[]{.7, 1, 1.8}) {
                String detail = language + " / " + fov + " @ " + typography;
                var fieldOfView = new FloatingMenuFieldOfView(fov);
                var state = new MenuSearchState<MenuPlayerSearch.Candidate>();
                var view = new MenuPresentation();
                var initial = scene(definition, state, labels, query);
                assertTrue(initial.loading(), detail);
                render(view, initial, typography, fieldOfView);
                long request = state.begin("");
                var pending = scene(definition, state, labels, query);
                assertTrue(pending.loading(), detail);
                render(view, pending, typography, fieldOfView);
                state.complete(request, PLAYERS, null);
                var ready = scene(definition, state, labels, query);
                assertFalse(ready.loading(), detail);
                var before = render(view, ready, typography, fieldOfView);
                assertEquals(render(new MenuPresentation(), ready, typography, fieldOfView).anchor(), before.anchor(),
                        "The placeholder must not determine the loaded scale: " + detail);
                assertEquals(MenuViewportLayout.distance(fieldOfView), before.anchor().distance(), 1e-6,
                        "Clear space preserves the nominal reading distance: " + detail);
                state.page = 999;
                assertEquals(before.anchor(), render(view, scene(definition, state, labels, query), typography, fieldOfView).anchor(), detail);

                long narrowed = state.begin("Player_Name_1");
                var searching = scene(definition, state, labels, query);
                assertFalse(searching.loading(), "A later query shows its own loading state: " + detail);
                var waiting = render(view, searching, typography, fieldOfView);
                assertTrue(waiting.geometry().frame().elements().stream().noneMatch(element -> element.path().contains("/results/items/")),
                        "Do not retain clickable candidates from an old query: " + detail);
                state.complete(narrowed, List.of(PLAYERS.get(1)), null);
                assertEquals(before.anchor(), render(view, scene(definition, state, labels, query), typography, fieldOfView).anchor(), detail);
                state.complete(state.begin(""), PLAYERS, null);
                assertEquals(before.anchor(), render(view, scene(definition, state, labels, query), typography, fieldOfView).anchor(), detail);
            }
        }
    }

    @Test void retryingAfterAnInitialFailureCanGrowWithoutMovingTheWindowCloser() {
        var definition = MenuPlayerSearch.builder(Component.text("Players"), Component.text("Name"))
                .flowResults(3, UiLength.ui(480)).find(_ -> CompletableFuture.completedFuture(PLAYERS))
                .selected((_, _) -> {}).build();
        var labels = new MenuSearchWindow.Labels(Component.text("Loading"), Component.text("Empty"), Component.text("Failed"),
                Component.text("Retry"), Component.text("Back"), (shown, total) -> Component.text(shown + " / " + total));
        var query = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE, definition.placeholder(),
                Component.text("Type"), Component.text("Clear"), Component.text("Cancel"));
        var state = new MenuSearchState<MenuPlayerSearch.Candidate>();
        var view = new MenuPresentation();
        state.complete(state.begin(""), null, new IllegalStateException("Directory failed"));
        var failed = render(view, scene(definition, state, labels, query), 1.8, FloatingMenuFieldOfView.DEFAULT);
        state.complete(state.begin(""), PLAYERS, null);
        var ready = render(view, scene(definition, state, labels, query), 1.8, FloatingMenuFieldOfView.DEFAULT);
        assertEquals(failed.anchor().origin(), ready.anchor().origin());
        assertEquals(failed.anchor().distance(), ready.anchor().distance(), 1e-6);
    }

    private static Component text(FluentBundle bundle, Message message) {
        return Component.text(FluentTestSupport.text(bundle, message));
    }

    private static MenuScene scene(MenuSearch<MenuPlayerSearch.Candidate> definition, MenuSearchState<MenuPlayerSearch.Candidate> state,
                                   MenuSearchWindow.Labels labels, UiNode query) {
        return MenuSearchWindow.scene("player-management", definition, query, state, labels,
                page -> state.page = page, (_, _) -> {}, (_, _) -> {}, definition.selected());
    }

    private record Rendered(SceneGeometry geometry, FloatingMenuAnchorResolver.Anchor anchor) { }

    private Rendered render(MenuPresentation view, MenuScene scene, double typography, FloatingMenuFieldOfView fov) {
        var prepared = view.prepare(null, eye, new Vector(0, 0, 1), scene, typography, 1, fov,
                org.encinet.mik.shared.menu.ui.UiPalette.DEFAULT);
        var geometry = prepared.geometry();
        var anchor = prepared.anchor().orElseThrow();
        SceneGeometryTestSupport.assertReadingCone(geometry, anchor, eye, fov, scene.screenId());
        return new Rendered(geometry, anchor);
    }
}
