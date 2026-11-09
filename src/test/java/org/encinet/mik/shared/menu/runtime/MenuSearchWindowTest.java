package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuSearchWindowTest {
    @Test void verticalSearchBuildsOnlyTheVisibleCandidatesAndCapturesItsQuerySnapshot() {
        var labelsRead = new AtomicInteger();
        var selected = new java.util.concurrent.atomic.AtomicReference<String>();
        var definition = MenuSearch.<String>builder(Component.text("Objects"), Component.text("Name"))
                .find(_ -> CompletableFuture.completedFuture(List.of())).key(UiKey::item)
                .label(value -> { labelsRead.incrementAndGet(); return Component.text(value); })
                .selected((_, value) -> selected.set(value)).build();
        var state = new MenuSearchState<String>();
        var results = java.util.stream.IntStream.range(0, 256).mapToObj(index -> "Object_" + index).toList();
        state.complete(state.begin(""), results, null);
        var labels = new MenuSearchWindow.Labels(Component.text("Loading"), Component.text("Empty"), Component.text("Failed"),
                Component.text("Retry"), Component.text("Back"), (shown, total) -> Component.text(shown + " / " + total));
        var query = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE, Component.text("Name"),
                Component.text("Type"), Component.text("Clear"), Component.text("Cancel"));
        var scene = MenuSearchWindow.scene("search", definition, query, state, labels, page -> state.page = page,
                (_, _) -> {}, (_, _) -> {}, definition.selected());
        assertEquals(0, labelsRead.get(), "Declaration and indexing must not enumerate candidate labels");
        state.complete(state.begin("replacement"), List.of("Different object"), null);
        var geometry = SceneGeometry.measure(scene, 1.8, FloatingMenuFieldOfView.DEFAULT, 1);
        assertTrue(labelsRead.get() < results.size(), "Only a bounded visible window may be materialized");
        assertTrue(geometry.frame().elements().stream().noneMatch(element -> element.node().key().value().equals("Different object")));
        var row = element(geometry.frame(), "Object_0");
        geometry.definition().entries().get(row.path()).trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals("Object_0", selected.get());
        assertTrue(geometry.frame().elements().stream().noneMatch(element -> element.path().contains("$row-template")));
    }

    @Test void aShortResultListIsNotWidenedByItsQueryPreference() {
        var title = Component.text("P"); var back = Component.text("Back");
        var definition = definition(title, Component.text("Name"), new AtomicInteger());
        var state = new MenuSearchState<String>();
        state.complete(state.begin(""), List.of("Alex"), null);
        var labels = new MenuSearchWindow.Labels(Component.text("Loading"), Component.text("Empty"), Component.text("Failed"),
                Component.text("Retry"), back, (shown,total) -> Component.text(shown + " / " + total));
        var query = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE,
                definition.placeholder(), Component.text("Type in chat"), Component.text("Clear"), Component.text("Cancel"));
        for (double scale : new double[]{.7, 1, 1.8}) {
            var scene = MenuSearchWindow.scene("search", definition, query, state, labels,
                    _ -> {}, (_,_) -> {}, (_,_) -> {}, definition.selected());
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var reading = Ui.column().child(UiTemplates.titleBar(Ui.heading(title).build(), Ui.button(back).build()))
                    .child(Ui.button(Component.text("Alex")).build()).build();
            var natural = SceneGeometryTestSupport.measure(MenuScene.screen("natural").content(reading).build(), scale);
            var panel = frame.panels().getFirst();
            var padding = frame.surfaces().stream().filter(surface -> surface.path().equals(panel.path()))
                    .findFirst().orElseThrow().style().padding();
            assertEquals(Math.max(natural.size().width(), query.sizing().minimum().width()),
                    panel.size().width() - padding.horizontal(), 1e-6);
            MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale);
            assertDisjoint(frame);
        }
    }

    private static MenuSearch<String> definition(Component title, Component placeholder, AtomicInteger selected) {
        return MenuSearch.<String>builder(title,placeholder).find(_ -> CompletableFuture.completedFuture(List.of()))
                .key(UiKey::item).label(Component::text).selected((_,_) -> selected.incrementAndGet()).build();
    }

    @Test void evenOneExactMatchRequiresAResultClickAndPagingNeverSelectsAnObject() {
        var selected = new AtomicInteger();
        var definition = definition(Component.text("Players"),Component.text("Name"),selected);
        var state = new MenuSearchState<String>();
        long request = state.begin("Alex"); state.complete(request,List.of("Alex"),null);
        var labels = new MenuSearchWindow.Labels(Component.text("Loading"),Component.text("Empty"),Component.text("Failed"),Component.text("Retry"),Component.text("Back"),
                (shown,total) -> Component.text(shown+" / "+total));
        var scene = MenuSearchWindow.scene("search",definition,
                MenuQueryViewTestSupport.content("Alex",MenuQueryViewTestSupport.Phase.IDLE,Component.text("Name"),Component.text("Type"),
                        Component.text("Clear"),Component.text("Cancel")),state,labels,page -> state.page = page,
                (_,_) -> {},(_,_) -> {},definition.selected());
        var frame = SceneGeometryTestSupport.measure(scene,1);
        assertEquals(0,selected.get(),"loading and rendering a single exact match are read-only");
        var measured = scene.measured(frame);
        measured.triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(0,selected.get());
        measured.entries().get(element(frame,"Alex").path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,selected.get());
        assertEquals("Alex",state.query);
    }

    @Test void allSearchHostsUseTheSameCompactFieldAndMeasuredPagesAcrossLanguagesAndTextSizes() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            var prompt = FluentTestSupport.rich(bundle,Message.MENU_QUERY_HINT_RICH,RichArg.component("chatKey",Component.keybind("key.chat")));
            var labels = new MenuSearchWindow.Labels(Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_LOADING)),
                    Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_EMPTY)),Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_FAILED)),
                    Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_RETRY)),Component.text(FluentTestSupport.text(bundle,Message.BACK)),
                    (shown,total) -> Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_LIMIT,shown,total)));
            for (var messages : List.of(List.of(Message.GOVERNANCE_MENU_PLAYERS,Message.GOVERNANCE_MENU_PLAYER_NAME),
                    List.of(Message.BAN_MENU_SELECT_TITLE,Message.BAN_MENU_PLAYER_INPUT),List.of(Message.PLOT_MENU_MANAGE,Message.PLOT_MENU_PLAYER),
                    List.of(Message.PLOT_MENU_FIND_RECORD,Message.PLOT_MENU_RECORD_SEARCH))) {
                var definition = definition(Component.text(FluentTestSupport.text(bundle,messages.getFirst())),
                        Component.text(FluentTestSupport.text(bundle,messages.getLast())),new AtomicInteger());
                for (var phase : List.of(MenuQueryViewTestSupport.Phase.IDLE,MenuQueryViewTestSupport.Phase.EDITING)) {
                    var field = MenuQueryViewTestSupport.content("Alex",phase,definition.placeholder(),prompt,
                            Component.text(FluentTestSupport.text(bundle,Message.MENU_INPUT_CLEAR)),Component.text(FluentTestSupport.text(bundle,Message.MENU_QUERY_CANCEL)));
                    for (int page : new int[] {0,999}) {
                        var state = new MenuSearchState<String>();
                        long request = state.begin("Alex");
                        state.complete(request,java.util.stream.IntStream.range(0,24).mapToObj(index -> "Alex_"+index).toList(),null);
                        state.page = page;
                        var scene = MenuSearchWindow.scene("search",definition,field,state,labels,
                                selected -> state.page = selected,(_,_) -> {},(_,_) -> {},definition.selected());
                        assertNotNull(entry(scene,"back"));
                        assertNull(entry(scene,"confirm"));
                        for (var scale : FloatingMenuTextScale.values()) {
                            var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene,scale.factor()),language+" "+messages+" @ "+scale+" page "+page);
                            assertDisjoint(frame);
                            MenuQueryViewTestSupport.assertCompactCentered(scene,frame,scale.factor());
                            assertEquals(1,SceneGeometryTestSupport.textLines(scene,scale.factor(),"preview"));
                            assertTrue(frame.pagination().getFirst().page().count()>1);
                        }
                    }
                }
            }
        }
    }

    @Test void aLargeDirectoryShowsItsLimitAndKeepsTheLastPageAtTheSameSize() throws Exception {
        for (var language : List.of(Language.ZH_CN,Language.RU_RU)) {
            var bundle = FluentTestSupport.bundle(language);
            var definition = definition(Component.text(FluentTestSupport.text(bundle,Message.GOVERNANCE_MENU_PLAYERS)),Component.text("Name"),new AtomicInteger());
            var labels = new MenuSearchWindow.Labels(Component.text("Loading"),Component.text("Empty"),Component.text("Failed"),Component.text("Retry"),
                    Component.text(FluentTestSupport.text(bundle,Message.BACK)),
                    (shown,total) -> Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_LIMIT,shown,total)));
            var state = new MenuSearchState<String>();
            state.complete(state.begin(""),java.util.stream.IntStream.range(0,10_000).mapToObj(index -> "Player_"+index).toList(),null);
            var field = MenuQueryViewTestSupport.content("",MenuQueryViewTestSupport.Phase.IDLE,Component.text("Name"),Component.text("Type"),Component.text("Clear"),Component.text("Cancel"));
            for (var scale : FloatingMenuTextScale.values()) {
                UiSize size = null;
                for (int page : new int[] {0,999}) {
                    state.page = page;
                    var scene = MenuSearchWindow.scene("search",definition,field,state,labels,
                            selected -> state.page = selected,(_,_) -> {},(_,_) -> {},definition.selected());
                    assertNotNull(entry(scene,"limit"));
                    var frame = SceneGeometryTestSupport.measure(scene,scale.factor());
                    assertDisjoint(frame);
                    MenuQueryViewTestSupport.assertCompactCentered(scene,frame,scale.factor());
                    if (size == null) size = frame.size(); else assertEquals(size,frame.size());
                    assertTrue(frame.pagination().getFirst().page().count()>1);
                }
            }
        }
    }
}
