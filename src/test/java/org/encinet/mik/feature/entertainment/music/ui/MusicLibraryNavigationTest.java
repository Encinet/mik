package org.encinet.mik.feature.entertainment.music.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicLibraryNavigationTest {
    @Test void creationAndImportBelongToMyPlaylistsWhileTheRailOnlySelectsSources() {
        var actions = new ArrayList<String>();
        for (Source source : Source.values()) {
            var navigation = MusicLibraryScenes.navigation(source, Component::text,
                    selected -> (_, _) -> actions.add(selected.name()));
            var controls = MusicLibraryScenes.playlistManagement(source, Component::text,
                    (_, _) -> actions.add("create"), (_, _) -> actions.add("import"));
            var rail = MenuScene.screen("rail").content(navigation).build();
            assertNull(entry(rail, "new-playlist"));
            assertNull(entry(rail, "import"));
            var scene = MenuScene.screen("collection").content(controls).build();
            if (source == Source.OWNED) {
                for (String key : List.of("new-playlist", "import"))
                    entry(scene, key).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
            } else assertTrue(scene.entries().isEmpty(), source.name());
        }
        assertEquals(List.of("create", "import"), actions);
    }

    @Test void anEmptyOrFullMyPlaylistsKeepsBothActionsVisibleAtEveryLanguageAndTextSize() throws Exception {
        for (Language language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) for (boolean empty : new boolean[]{false, true}) {
                UiNode rail = MusicLibraryScenes.navigation(Source.OWNED, label, _ -> (_, _) -> { });
                var heading = MusicWorkspace.tools("collection-heading").child(Ui.heading(label.apply("my-playlists")).key("heading").build())
                        .child(MusicLibraryScenes.playlistManagement(Source.OWNED, label, (_, _) -> { }, (_, _) -> { }));
                var browser = MusicWorkspace.column("collection").child(heading.build())
                        .child(Ui.form().key("search").field("query", label.apply("search"), Ui.textField("")
                                .placeholder(Component.text(FluentTestSupport.text(bundle, Message.MENU_INPUT_EMPTY))).build()).build());
                if (empty) browser.child(Ui.text(label.apply("empty")).key("empty").build());
                else {
                    var rows = new ArrayList<MusicPlaylistTable.Row>();
                    for (int i = 0; i < 4; i++) rows.add(new MusicPlaylistTable.Row(new Playlist(UUID.randomUUID(), UUID.randomUUID(),
                            "长名称歌单 A playlist with a long name " + i, "", "MUSIC_DISC_CAT", i % 2 == 0, 0, List.of()), false, null));
                    browser.child(MusicPlaylistTable.content(rows, label,
                            count -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_TRACK_COUNT, count)),_ -> (_,_) -> {},0,_ -> {}));
                }
                var scene = MenuScene.screen("music").viewport(MusicWorkspaceComposition.VIEWPORT).content(MusicWorkspaceComposition.content(rail,
                        UiTemplates.focus(browser.build(),UiKey.named(empty ? "empty" : "playlists")),null,
                        Ui.button(label.apply("output")).key("output").build(), Ui.button(label.apply("close")).key("close").build())).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " @ " + scale);
                assertDisjoint(frame);
                var collections = frame.panels().stream().filter(panel -> panel.path().endsWith("/sidebar")).findFirst().orElseThrow();
                for (String key : List.of("new-playlist", "import")) {
                    var action = element(frame, key);
                    assertTrue(action.x() >= collections.x() + collections.size().width());
                    assertTrue(action.y() < element(frame, "preview").y());
                }
                assertTrue(entry(scene, "source:OWNED").selected());
            }
        }
    }

    @Test void creationOrImportOpensTheSavedSongsAndReturnsToMyPlaylists() {
        Playlist saved = new Playlist(UUID.randomUUID(), UUID.randomUUID(), "New playlist", "", "MUSIC_DISC_CAT", false, 0, List.of());
        for (Panel task : List.of(new NoPanel(), new Track(List.of(MusicWorkspaceStateTest.track("a"))), new Import(null, ImportPage.SOURCE))) {
            var state = new MusicWorkspaceState();
            state.panel(task);
            var finished = MusicLibraryScenes.openSavedPlaylist(state, id -> state.browsePlaylist(id, Source.OWNED));
            finished.accept(saved);
            assertEquals(saved.id(), state.browser.collection.playlist());
            assertEquals(Source.OWNED, state.browser.navigationSource());
            assertEquals(Collection.of(Source.OWNED), state.browser.parent);
            assertInstanceOf(NoPanel.class, state.panel());
        }
    }

    @Test void lateSaveCompletionDoesNotReplaceANewerCollectionTaskOrSearch() {
        Playlist saved = new Playlist(UUID.randomUUID(), UUID.randomUUID(), "Saved", "", "MUSIC_DISC_CAT", false, 0, List.of());
        for (int change = 0; change < 6; change++) {
            var state = new MusicWorkspaceState();
            if (change == 3) state.browsePlaylist(UUID.randomUUID(), Source.OWNED);
            if (change >= 4) {
                UUID target = UUID.randomUUID();
                state.target(target);
                state.panel(new DevicePanel(target, DeviceSection.QUEUE));
            }
            var opened = new ArrayList<UUID>();
            var finished = MusicLibraryScenes.openSavedPlaylist(state, opened::add);
            if (change == 0) {
                state.browse(Collection.of(Source.OWNED));
                state.browse(Collection.of(Source.CATALOG));
            } else if (change == 1) state.panel(new Track(List.of(MusicWorkspaceStateTest.track("other"))));
            else if (change == 2) state.browser.query("another search");
            else if (change == 3) {
                UUID first = UUID.randomUUID(), last = UUID.randomUUID();
                state.startPlaylistMove(new MusicMove(state.browser.collection.playlist(), 0, List.of(first, last), Set.of(first)));
            } else {
                state.devices();
                if (change == 5) state.browse(Collection.of(Source.CATALOG));
                assertInstanceOf(NoPanel.class,state.panel(), "the devices entry opens the directory");
            }
            finished.accept(saved);
            assertTrue(opened.isEmpty(), "a late completion must leave the newer view alone");
        }
    }
}
