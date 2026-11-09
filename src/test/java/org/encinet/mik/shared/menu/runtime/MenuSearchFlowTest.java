package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuSearchFlowTest {
    private List<MenuPlayerSearch.Candidate> players(int count) {
        var players = new ArrayList<MenuPlayerSearch.Candidate>();
        for (int i = 0; i < count; i++) players.add(new MenuPlayerSearch.Candidate(new UUID(0,i),
                i % 2 == 0 ? "P" + i : "Player_Name_" + i));
        return MenuPlayerSearch.matching("",players);
    }

    private MenuSearch<MenuPlayerSearch.Candidate> definition(Component title, List<UUID> selected) {
        return MenuPlayerSearch.builder(title,Component.text("Player name"))
                .flowResults(3,UiLength.ui(480)).find(_ -> CompletableFuture.completedFuture(List.of()))
                .selected((_,value) -> selected.add(value.id())).build();
    }

    private MenuSearchWindow.Labels labels() {
        return new MenuSearchWindow.Labels(Component.text("Loading"),Component.text("Empty"),Component.text("Failed"),
                Component.text("Retry"),Component.text("Back"),(shown,total) -> Component.text(shown + " / " + total));
    }

    private MenuScene scene(MenuSearch<MenuPlayerSearch.Candidate> definition, MenuSearchState<MenuPlayerSearch.Candidate> state,
                            MenuSearchWindow.Labels labels, UiNode query, double width) {
        return MenuSearchWindow.scene("player-flow", definition,query,state,labels,
                page -> state.page=page,(_,_) -> {},(_,_) -> {},definition.selected())
                .toBuilder().viewport(new UiSize(width,560)).build();
    }

    private List<UiFrame.Element> cards(UiFrame frame) {
        String collection = frame.pagination().getFirst().path() + "/items/";
        return frame.elements().stream().filter(element -> element.path().startsWith(collection)).toList();
    }

    @Test void aSinglePlayerDoesNotReserveTwoEmptyRowsBelowTheResult() {
        var definition = definition(Component.text("Players"), new ArrayList<>());
        var state = new MenuSearchState<MenuPlayerSearch.Candidate>();
        state.complete(state.begin(""), players(1), null);
        for (var scale : FloatingMenuTextScale.values()) {
            var source = scene(definition, state, labels(), Ui.text(Component.text("Search")).build(), 640);
            var frame = SceneGeometryTestSupport.measure(source, scale.factor());
            var collection = frame.pagination().getFirst();
            assertEquals(1, collection.page().count());
            assertEquals(cards(frame).getFirst().size().height(), collection.size().height(), 1e-6, scale.toString());
            assertDisjoint(frame);
        }
    }

    @Test void playersPackWithinThreeRowsAndTheReadingHeightAndEveryPlayerKeepsItsClickTarget() {
        var selected = new ArrayList<UUID>();
        var definition = definition(Component.text("Players"),selected);
        var state = new MenuSearchState<MenuPlayerSearch.Candidate>();
        var players = players(53);
        state.complete(state.begin(""),players,null);
        for (double scale : new double[] {.7,1,1.4,1.8}) {
            var query = Ui.text(Component.text("Search")).build();
            state.page=0;
            var narrow = SceneGeometryTestSupport.measure(scene(definition,state,labels(),query,240),scale);
            var wide = SceneGeometryTestSupport.measure(scene(definition,state,labels(),query,640),scale);
            var veryWide = SceneGeometryTestSupport.measure(scene(definition,state,labels(),query,960),scale);
            assertTrue(cards(wide).size()>cards(narrow).size());
            assertEquals(cards(wide).size(),cards(veryWide).size(),"The list stops growing at its maximum width");
            var seen = new LinkedHashSet<UUID>();
            int count = wide.pagination().getFirst().page().count();
            for (int page=0;page<count;page++) {
                state.page=page;
                var source = scene(definition,state,labels(),query,640);
                var frame = SceneGeometryTestSupport.measure(source,scale);
                var measured=source.measured(frame);
                assertDisjoint(frame);
                assertTrue(cards(frame).stream().map(UiFrame.Element::y).distinct().count() <= 3,
                        "three rows is a maximum; the finite reading height may allow fewer");
                assertTrue(frame.size().width()<=504+1e-6,"Only the collection and window padding determine maximum width");
                for (var card : cards(frame)) {
                    UUID id=UUID.fromString(card.node().key().value());
                    assertTrue(seen.add(id),"No duplicated player across pages");
                    assertEquals(1,SceneGeometryTestSupport.textLines(source,scale,card.node().key().value()));
                    var target=measured.entries().get(card.path());
                    assertNotNull(target,"Every visible player has a click target");
                    target.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(id,selected.getLast());
                }
            }
            assertEquals(players.stream().map(MenuPlayerSearch.Candidate::id).toList(),List.copyOf(seen));
        }
    }

    @Test void localizedPlayerManagementKeepsSearchAndThreeRowsWithinTheViewport() throws Exception {
        for (var language : Language.values()) {
            var bundle=FluentTestSupport.bundle(language);
            var definition=definition(Component.text(FluentTestSupport.text(bundle,Message.GOVERNANCE_MENU_PLAYERS)),new ArrayList<>());
            var labels=new MenuSearchWindow.Labels(Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_LOADING)),
                    Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_EMPTY)),Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_FAILED)),
                    Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_RETRY)),Component.text(FluentTestSupport.text(bundle,Message.BACK)),
                    (shown,total) -> Component.text(FluentTestSupport.text(bundle,Message.MENU_SEARCH_LIMIT,shown,total)));
            var state=new MenuSearchState<MenuPlayerSearch.Candidate>();
            state.complete(state.begin(""),players(300),null);
            for (var phase : List.of(MenuQueryViewTestSupport.Phase.IDLE,MenuQueryViewTestSupport.Phase.EDITING)) {
                var query=MenuQueryViewTestSupport.content("",phase,definition.placeholder(),
                        FluentTestSupport.rich(bundle,Message.MENU_QUERY_HINT_RICH,RichArg.component("chatKey",Component.keybind("key.chat"))),
                        Component.text(FluentTestSupport.text(bundle,Message.MENU_INPUT_CLEAR)),
                        Component.text(FluentTestSupport.text(bundle,Message.MENU_QUERY_CANCEL)));
                for (double width : new double[] {360,640}) for (double scale : new double[] {.7,1,1.4,1.8}) for (int page : new int[] {0,999}) {
                    state.page=page;
                    var source=scene(definition,state,labels,query,width);
                    var frame=assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(source,scale),language+" width="+width+" scale="+scale);
                    assertDisjoint(frame);
                    assertNotNull(entry(source,"limit"));
                    assertTrue(frame.size().width()<=Math.min(504,width)+1e-6);
                    assertTrue(frame.size().height()<=560);
                    assertTrue(cards(frame).stream().map(UiFrame.Element::y).distinct().count()<=3);
                    MenuQueryViewTestSupport.assertCompactCentered(source,frame,scale);
                }
            }
        }
    }

    @Test void loadingFailureAndEmptyStatesDoNotExposeStalePlayerActions() {
        var selected=new ArrayList<UUID>();
        var definition=definition(Component.text("Players"),selected);
        var state=new MenuSearchState<MenuPlayerSearch.Candidate>();
        long request=state.begin("");
        for (int phase=0;phase<3;phase++) {
            if (phase==1) state.complete(request,null,new IllegalStateException("Directory failed"));
            if (phase==2) state.complete(state.begin("nobody"),List.of(),null);
            var source=scene(definition,state,labels(),Ui.text(Component.text("Search")).build(),640);
            var frame=SceneGeometryTestSupport.measure(source,1.8);
            assertTrue(cards(frame).isEmpty());
            assertNotNull(entry(source,"status"));
            assertEquals(phase==1,entry(source,"retry")!=null);
            source.measured(frame).triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
            assertTrue(selected.isEmpty());
        }
    }
}
