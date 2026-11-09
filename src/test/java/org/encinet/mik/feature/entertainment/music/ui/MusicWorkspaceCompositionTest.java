package org.encinet.mik.feature.entertainment.music.ui;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.runtime.MenuInputViewTestSupport;
import org.encinet.mik.shared.menu.runtime.MenuQueryViewTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import java.util.*;
import java.time.Duration;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import java.util.function.Function;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicWorkspaceCompositionTest {
    @Test void theSameControlOnAnotherTaskHasANewInputIdentity() {
        var ids = new java.util.HashSet<String>(); var chosen = new java.util.ArrayList<java.util.UUID>();
        for (var task : List.of(new java.util.UUID(1, 1), new java.util.UUID(1, 2))) {
            var content = MusicWorkspaceComposition.task(task, Ui.button(Component.text("Ready")).key("ready")
                    .primary((_, _) -> chosen.add(task)).build());
            var scene = MenuScene.screen("music-workspace").content(content).build();
            var control = entry(scene, "ready"); assertTrue(ids.add(control.id()));
            control.trigger(org.encinet.mik.shared.menu.FloatingMenuInteraction.PRIMARY)
                    .execute(null, null, org.encinet.mik.shared.menu.FloatingMenuInteraction.PRIMARY);
            assertEquals(task, chosen.getLast());
        }
        assertEquals(2, chosen.size());
    }
    @Test void shortCollectionsDoNotExpandToTheSearchFieldsPreferredWidth() {
        var search = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE,
                Component.text("Search songs"), Component.text("Type in chat"), Component.text("Clear"), Component.text("Cancel"));
        var heading = Ui.heading(Component.text("Songs")).key("heading").build();
        var row = Ui.button(Component.text("One")).key("song").build();
        for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{360, 640, 1280}) {
            var scene = MenuScene.screen("collection").viewport(new UiSize(width, 1024))
                    .content(UiTemplates.window("browser", MusicWorkspaceComposition.collection(heading, null, search).child(row).build())).build();
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var naturalContent = SceneGeometryTestSupport.measure(MenuScene.screen("body").content(Ui.column().child(heading).child(row).build()).build(), scale);
            var panel = panelFor(frame, "heading");
            var padding = frame.surfaces().stream().filter(surface -> surface.path().equals(panel.path()))
                    .findFirst().orElseThrow().style().padding();
            assertEquals(Math.max(naturalContent.size().width(), search.sizing().minimum().width()),
                    panel.size().width() - padding.horizontal(), 1e-6);
            MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale);
            assertDisjoint(frame);
        }
    }

    @Test void deviceLocationExpandsForItsPositionAndKeepsTheVisitOnTheSameLine() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            var direction = FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,"direction-front-right");
            var position = Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_DEVICE_DISTANCE,direction,12));
            var coordinates = Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_DEVICE_COORDINATES,"world",-12,64,20));
            var label = Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,"visit-device"));
            for (double typography : new double[]{.7,1,1.8}) {
                var content = MusicWorkspaceComposition.deviceLocation(position,coordinates,
                        Ui.button(label).key("visit").noWrap().primary((_,_) -> {}).build());
                var scene = MenuScene.screen("device-location").viewport(MusicWorkspaceComposition.VIEWPORT).content(content).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.naturalFrame(scene,typography),language+" / "+typography);
                assertDisjoint(frame);
                var reading = element(frame, "position"); var visit = element(frame, "visit");
                assertEquals(reading.y()+reading.size().height()/2, visit.y()+visit.size().height()/2, 1e-6);
                assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, typography, "position"));
                assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, typography, "visit"));
            }
        }
    }

    @Test void completePositionsAndTravelActionsKeepTheirIntrinsicWidthWithoutAddingLines() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            var direction = FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, "direction-back-left");
            var relative = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_DEVICE_DISTANCE, direction, 12345678))
                    .append(Component.text(" · ")).append(Component.text(FluentTestSupport.text(bundle,
                            Message.MUSIC_SPACE_DEVICE_HEIGHT, "below", 319)));
            var coordinates = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_DEVICE_COORDINATES,
                    "world_with_a_long_name", -29999999, -64, 29999999));
            var label = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, "visit-device"));
            for (var position : List.of(relative, Component.text("世界/Community_creative_world_with_a_long_distinguishing_name"))) {
                for (double scale : new double[]{.7, 1, 1.8})
                    for (boolean visiting : new boolean[]{false, true}) {
                        UiNode action = visiting ? Ui.text(Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, "teleporting")))
                                .key("visit").noWrap().build() : Ui.button(label).key("visit").noWrap().primary((_,_) -> {}).build();
                        var scene = MenuScene.screen("location").viewport(MusicWorkspaceComposition.VIEWPORT)
                                .content(MusicWorkspaceComposition.deviceLocation(position, coordinates, action)).build();
                        var frame = SceneGeometryTestSupport.naturalFrame(scene, scale);
                        var natural = SceneGeometryTestSupport.naturalFrame(MenuScene.screen("intrinsic-position").viewport(MusicWorkspaceComposition.VIEWPORT)
                                .content(Ui.text(position).noWrap().build()).build(), scale);
                        assertEquals(position, element(frame, "position").node().content().label(),
                                language + " @ " + scale + ": location must remain complete");
                        assertEquals(natural.size().width(), element(frame, "position").size().width(), 1e-6);
                        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, scale, "position"));
                        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, scale, "visit"));
                        var reading = element(frame, "position"); var visit = element(frame, "visit");
                        assertEquals(reading.y()+reading.size().height()/2, visit.y()+visit.size().height()/2, 1e-6);
                        assertEquals(coordinates, element(frame, "position").node().input().tooltip().orElseThrow().text());
                        assertEquals(FloatingMenuDecoration.Alignment.LEFT, element(frame, "position").node().alignment());
                        assertDisjoint(frame);
                        assertTrue(frame.size().width() > natural.size().width(), "the panel expands to include the adjacent action");
                    }
            }
        }
    }
    @Test void auxiliaryHeadingsKeepLocalActionsAndDismissalOnOneRowWhenSpaceAllows() throws Exception {
        for (Language language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (double typography : new double[]{.7, 1, 1.8}) for (int width : new int[]{360, 1280}) for (boolean recording : new boolean[]{false, true}) {
                var close = Ui.button(label.apply("close-panel")).key("close-panel").build();
                UiNode[] actions = recording ? new UiNode[]{Ui.button(label.apply("record")).key("record").build()} : new UiNode[0];
                var heading = MusicWorkspaceComposition.panelHeading(label.apply("playlist-settings"), close, actions);
                var scene = MenuScene.screen("heading").viewport(new UiSize(width, 2048)).content(heading).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.naturalFrame(scene, typography), language + " @ " + typography + " width=" + width);
                assertDisjoint(frame);
                assertTrue(frame.size().width() <= width + 1e-6);
                var title = element(frame, "title");
                var dismiss = element(frame, "close-panel");
                assertEquals(frame.size().width()/2, title.x()+title.size().width()/2, 1e-6);
                var tools = frame.elements().stream().filter(element -> element.path().contains("/actions/")).toList();
                double toolsLeft = tools.stream().mapToDouble(UiFrame.Element::x).min().orElseThrow();
                double toolsTop = tools.stream().mapToDouble(UiFrame.Element::y).min().orElseThrow();
                double toolsBottom = tools.stream().mapToDouble(element -> element.y()+element.size().height()).max().orElseThrow();
                boolean inline = Math.abs(title.y()+title.size().height()/2-(toolsTop+toolsBottom)/2) < 1e-6;
                if (inline) assertTrue(toolsLeft >= title.x()+title.size().width()-1e-6);
                else assertTrue(toolsTop >= title.y()+title.size().height()-1e-6);
                if (width == 1280) {
                    if (!recording) {
                        assertEquals(title.y() + title.size().height()/2, dismiss.y() + dismiss.size().height()/2, 1e-6);
                        var old = MusicWorkspace.column("old-heading").child(close)
                                .child(Ui.heading(label.apply("playlist-settings")).key("title").build()).build();
                        var oldScene = MenuScene.screen("old-heading").viewport(new UiSize(width, 2048)).content(old).build();
                        assertTrue(frame.size().height() < SceneGeometryTestSupport.naturalFrame(oldScene, typography).size().height());
                    }
                }
            }
        }
    }

    @Test void backgroundShellsFollowCurrentContentAcrossUpdatesWithinTheSameWorkspace() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (double typography : new double[]{.7, 1, 1.8}) {
                var session = new SceneGeometryTestSupport.LayoutSession();
                var songs = List.of(MusicWorkspaceStateTest.track("first"), MusicWorkspaceStateTest.track("second"),
                        MusicWorkspaceStateTest.track("third"));
                var tracks = MusicTrackTable.builder(songs, null, label, _ -> (_, _) -> {},
                        (_, _) -> {}, _ -> Ui.button(label.apply("add-playlist")).key("add").build()).build();
                var full = UiTemplates.focus(MusicWorkspaceComposition.collection(Ui.heading(label.apply("catalog")).key("heading").build(),
                                null, Ui.text(label.apply("search-tracks")).key("search-value").build())
                        .child(tracks).build(),tracks.key());
                var compact = UiTemplates.focus(MusicWorkspaceComposition.collection(Ui.heading(label.apply("my-playlists")).key("heading").build(),
                                null, Ui.text(label.apply("search-playlists")).key("search-value").build())
                        .child(Ui.text(label.apply("empty")).key("empty").build()).build(),UiKey.named("empty"));
                var task = Ui.column().key("task-pane").child(Ui.heading(label.apply("playlist-settings")).key("task-title").build());
                for (int i = 0; i < 8; i++) task.child(Ui.text(label.apply("description")).key("setting-" + i).build());
                var smallTask = Ui.column().key("task-pane").child(Ui.heading(label.apply("add-playlist")).key("task-title").build()).build();
                var transport = Ui.column().child(Ui.text(label.apply("output")).key("output").build())
                        .child(MusicPlaybackActions.buttons(songs, label, (_, _) -> {})).build();
                var idle = Ui.text(label.apply("output")).key("output").build();
                var navigation = MusicLibraryScenes.navigation(MusicWorkspaceState.Source.CATALOG, label, _ -> (_, _) -> {});
                var tools = Ui.button(label.apply("close")).key("close").build();
                var fullPage = workspace(navigation, full, null, transport, tools);
                var compactPage = workspace(navigation, compact, null, idle, tools);
                var largeTask = workspace(navigation, compact, task.build(), transport, tools);
                var smallTaskPage = workspace(navigation, compact, smallTask, idle, tools);
                UiFrame initial = null, reduced = null, expandedTask = null, reducedTask = null;
                for (var scene : List.of(fullPage, compactPage, largeTask, smallTaskPage, compactPage, fullPage)) {
                    var frame = session.measure(scene, typography);
                    String detail = language + " @ " + typography;
                    assertSameLayout(SceneGeometryTestSupport.measure(scene,typography),frame,detail);
                    assertDisjoint(frame);
                    if (scene == fullPage) initial = frame;
                    if (scene == compactPage) reduced = frame;
                    if (scene == largeTask) expandedTask = frame;
                    if (scene == smallTaskPage) reducedTask = frame;
                }
                assertTrue(panelFor(reduced, "heading").size().width() < panelFor(initial, "heading").size().width());
                assertTrue(panelFor(reduced, "heading").size().height() < panelFor(initial, "heading").size().height());
                assertTrue(panelFor(reducedTask, "task-title").size().height() < panelFor(expandedTask, "task-title").size().height());
                assertTrue(panelFor(reduced, "output").size().height() < panelFor(initial, "output").size().height());
            }
        }
    }

    private static MenuScene workspace(UiNode navigation, UiNode browser, UiNode auxiliary, UiNode transport, UiNode tools) {
        return MenuScene.screen("music-workspace").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                .content(MusicWorkspaceComposition.content(navigation, browser, auxiliary, transport, tools)).build();
    }

    private static UiFrame.Panel panelFor(UiFrame frame, String key) {
        String path = element(frame, key).path();
        return frame.panels().stream().filter(panel -> path.startsWith(panel.path() + "/"))
                .max(Comparator.comparingInt(panel -> panel.path().length())).orElseThrow();
    }

    @Test void anOpenTaskOwnsThePrimarySurfaceAndClosingItRestoresTheBrowser() {
        UiNode navigation = Ui.button(Component.text("Collections")).key("collections").build();
        UiNode browser = Ui.button(Component.text("Songs")).key("songs").build();
        UiNode auxiliary = Ui.button(Component.text("Add to playlist")).key("destination").build();
        UiNode transport = Ui.button(Component.text("Pause")).key("pause").build();
        UiNode tools = Ui.button(Component.text("Close")).build();
        var scene = MenuScene.screen("music").viewport(MusicWorkspaceComposition.VIEWPORT).content(MusicWorkspaceComposition.content(navigation, browser, auxiliary, transport, tools)).build();
        assertTrue(SceneGeometryTestSupport.measure(scene,1).elements().stream().noneMatch(node -> node.node().key().value().equals("songs")));
        assertEquals(0, SceneGeometryTestSupport.pose(scene, 1, "destination").pitchDegrees(), 1e-8);
        assertTrue(SceneGeometryTestSupport.pose(scene, 1, "collections").yawDegrees() < 0);
        assertEquals(0,SceneGeometryTestSupport.pose(scene,1,"destination").yawDegrees(),1e-8);
        var restored = scene.toBuilder().content(MusicWorkspaceComposition.content(navigation,browser,null,transport,tools)).build();
        assertNotNull(element(SceneGeometryTestSupport.measure(restored,1),"songs"));
        assertTrue(SceneGeometryTestSupport.measure(restored,1).elements().stream().noneMatch(node -> node.node().key().value().equals("destination")));
        assertTrue(SceneGeometryTestSupport.pose(scene, 1, "pause").pitchDegrees() < 0);
        assertEquals(0, SceneGeometryTestSupport.pose(scene, 1, "pause").yawDegrees(), 1e-8);
    }

    @Test void translatedWorkbenchKeepsTransportBelowContentWithAndWithoutAnAuxiliaryTask() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (var typography : FloatingMenuTextScale.values()) for (int auxiliary : new int[] {0, 1, 2}) {
                double scale = typography.factor();
                var nav = MusicLibraryScenes.navigation(MusicWorkspaceState.Source.CATALOG, label,
                        _ -> (_, _) -> { });
                var first = MusicWorkspaceStateTest.track("first");
                var songs = new ArrayList<>(List.of(new MusicTrack(first.id(),
                        new TrackDetails("这是很长的歌曲名称 A very long song title", "很长的歌手名字 A long artist name", null, "FLAC",
                                new AudioProperties(null, null, Duration.ofSeconds(3723))), first.target())));
                for (int i = 1; i < (scale >= 1.3 ? 3 : 5); i++) songs.add(MusicWorkspaceStateTest.track("song-" + i));
                var tracks = MusicTrackTable.builder(songs, MusicTableSelection.selecting(_ -> false, _ -> (_, _) -> {}), label,
                        _ -> (_, _) -> {}, (_, _) -> {}, _ -> Ui.button(label.apply("add-playlist")).key("add").build()).build();
                Function<Message, Component> inputLabel = message -> message == Message.MENU_QUERY_HINT_RICH
                        ? FluentTestSupport.rich(bundle, message, RichArg.component("chatKey", Component.keybind("key.chat")))
                        : Component.text(FluentTestSupport.text(bundle, message));
                UiNode search = MenuQueryViewTestSupport.content(
                        auxiliary == 0 ? "" : auxiliary >= 3 ? "搜索 😀 long query ".repeat(8) : "曲名 song title",
                        auxiliary == 3 ? MenuQueryViewTestSupport.Phase.EDITING
                                : auxiliary == 4 ? MenuQueryViewTestSupport.Phase.SUBMITTED : MenuQueryViewTestSupport.Phase.IDLE,
                        label.apply("search-tracks"), inputLabel.apply(Message.MENU_QUERY_HINT_RICH),
                        inputLabel.apply(Message.MENU_INPUT_CLEAR), inputLabel.apply(Message.MENU_QUERY_CANCEL));
                var browser = UiTemplates.reading(MusicWorkspaceComposition.collection(Ui.heading(label.apply("catalog")).build(), null, search)
                        .child(MusicWorkspace.tools("selection-tools")
                                .child(Ui.text(Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_SELECTED_TRACKS,1))).key("selected-count").build())
                                                                .child(Ui.button(label.apply("select-all")).key("select-all").build())
                                .child(Ui.button(label.apply("clear-tracks")).key("clear-tracks").build())
                                .child(Ui.button(label.apply("cancel-selection")).key("cancel-selection").build()).build())
                        .child(MusicPlaybackActions.buttons(List.of(songs.getFirst()), label, (_, _) -> {})).child(tracks).build(),tracks.key(),UiLength.ui(8));
                var task = Ui.column().gap(UiLength.ui(8)).child(Ui.heading(label.apply("add-playlist")).build())
                        .child(Ui.button(Component.text("长名称播放列表 Playlist with a long name")).key("destination").build())
                        .child(Ui.button(label.apply("new-playlist")).build()).child(Ui.button(label.apply("close-panel")).key("close-panel").build()).build();
                var form = MenuInputForm.builder(label.apply("playlist-settings")).description(Component.text("Playlist name 曲单"))
                        .field(MenuInputForm.Field.text("name", label.apply("name"), "曲单名称 Playlist name", 64))
                        .field(MenuInputForm.Field.text("description", label.apply("description"), "多语言描述 Long description\n".repeat(20), 512).multiline(true))
                        .cancel(label.apply("cancel")).submit(label.apply("apply"), (_, _) -> {}).build();
                var editor = MenuInputViewTestSupport.content(form,
                        inputLabel.apply(Message.MENU_INPUT_EMPTY), inputLabel.apply(Message.MENU_INPUT_CLEAR));
                var transport = MusicPlayerComposition.content(
                        Ui.button(label.apply("output")).key("output").singleLine().build(),
                        MusicPlayerComposition.playback(Ui.button(UiIcons.PREVIOUS,label.apply("previous")).build(),
                                Ui.button(UiIcons.PAUSE,label.apply("pause")).build(),Ui.button(UiIcons.NEXT,label.apply("skip-next")).build()),
                        MusicPlayerComposition.song(Ui.icon(UiIcons.PLAY,label.apply("play")).build(),
                                Ui.text(Component.text("Moonlight Sonata 月光奏鸣曲")).key("playing").singleLine().build(),
                                Ui.button(UiIcons.ADD,label.apply("add-playlist")).build()),
                        MusicPlayerComposition.actions(Ui.button(UiIcons.VOLUME,label.apply("volume-control")).build(),
                                Ui.button(UiIcons.QUEUE,label.apply("queue")).build(),Ui.button(UiIcons.MORE,label.apply("more")).build()),null);
                var tools = Ui.button(label.apply("close")).key("close").build();
                var scene = MenuScene.screen("music-layout").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                        .content(MusicWorkspaceComposition.content(nav, browser, auxiliary == 0 ? null : auxiliary == 2 ? editor : task, transport, tools)).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale), language + " @ " + scale + " auxiliary=" + auxiliary);
                assertDisjoint(frame);
                if (auxiliary == 0) {
                MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale);
                var searchValue = frame.elements().stream().filter(element -> element.path().contains("/search/")
                        && element.node().key().value().equals("preview")).findFirst().orElseThrow();
                assertTrue(element(frame, "output").y() > searchValue.y());
                } else assertTrue(frame.elements().stream().noneMatch(node -> node.path().contains("/search/")));
                if (auxiliary == 1) assertTrue(element(frame, "output").y() > element(frame, "destination").y());
                if (auxiliary == 2) { assertNotNull(element(frame, "confirm")); assertTrue(element(frame, "output").y() > element(frame, "confirm").y()); }
                assertTrue(frame.elements().stream().noneMatch(element -> element.node().key().value().equals("jukebox-model")));
            }
        }
    }
}
