package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

class MusicRhythmCompositionTest {
    @Test void playerNamesAndStatusesUseSharedColumnsAndStableRows() {
        var first = new MusicRhythmComposition.Participant(UUID.randomUUID(), Component.text("Player with a long name"), Component.text("Ready"));
        var second = new MusicRhythmComposition.Participant(UUID.randomUUID(), Component.text("Lee"), Component.text("In game"));
        var scene = MenuScene.screen("players").content(MusicRhythmComposition.participants(List.of(first, second), Component::text, MusicSourcePosition.START, _ -> {})).build();
        var reordered = MenuScene.screen("players").content(MusicRhythmComposition.participants(List.of(second, first), Component::text, MusicSourcePosition.START, _ -> {})).build();
        assertEquals(SceneGeometryTestSupport.measure(scene, 1).elements().stream().map(UiFrame.Element::path).collect(java.util.stream.Collectors.toSet()),
                SceneGeometryTestSupport.measure(reordered, 1).elements().stream().map(UiFrame.Element::path).collect(java.util.stream.Collectors.toSet()));
        var frame = SceneGeometryTestSupport.measure(scene, 1.8);
        var statuses = frame.elements().stream().filter(value -> value.node().key().value().equals("player-status")).toList();
        assertEquals(2, statuses.size());
        assertEquals(statuses.getFirst().x(), statuses.getLast().x(), 1e-6);
        assertTrue(statuses.stream().allMatch(value -> !value.node().input().interactive()));
        assertDisjoint(frame);
    }

    @Test void roomSectionsRemainReadableAcrossLanguagesWidthsRolesAndTextSizes() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (var scale : FloatingMenuTextScale.values()) for (int width : new int[] {360, 1280}) for (boolean controller : new boolean[] {false, true}) {
                var content = room(label, controller, 5);
                var scene = MusicSceneTestSupport.auxiliary(content,width);
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " @ " + scale + " / " + width + " controller=" + controller);
                assertDisjoint(frame);
                assertTrue(frame.size().width() <= width + 1e-6);
                assertEquals(element(frame,"room-mode").y(),element(frame,"room-difficulty").y(),1e-6,
                        "the two personal preferences share one row");
                for (var reading : frame.elements().stream().filter(value -> List.of("player-status", "readiness").contains(value.node().key().value())).toList())
                    assertEquals(1, UiTextMetrics.measure(reading.node().content().label(), reading.node().textWidthPixels()).lines());
                if (width == 1280) {
                    var roster = frame.elements().stream().filter(value -> value.path().contains("/room-players/")).toList();
                    double top = roster.stream().mapToDouble(UiFrame.Element::y).min().orElseThrow();
                    var preparationTitle = frame.elements().stream().filter(value -> value.node().role() == UiRole.HEADING
                            && value.node().content().label().equals(label.apply("room-preparation"))).findFirst().orElseThrow();
                    assertEquals(top, preparationTitle.y(), 1e-6, "players and personal preparation align at the top");
                    assertTrue(preparationTitle.x()
                            > roster.stream().mapToDouble(value -> value.x() + value.size().width()).max().orElseThrow());
                }
            }
        }
    }

    @Test void controllerActionsFitTheReadingWidthWithoutMakingTheWholeRoomWider() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (double scale : new double[] {.7, 1, 1.8}) {
                var member = MusicSceneTestSupport.auxiliary(room(label,false,3),(int) MusicWorkspaceComposition.VIEWPORT.width());
                var controller = MusicSceneTestSupport.auxiliary(room(label,true,3),(int) MusicWorkspaceComposition.VIEWPORT.width());
                var before = SceneGeometryTestSupport.measure(member, scale);
                var after = SceneGeometryTestSupport.measure(controller, scale);
                assertEquals(before.size().width(), after.size().width(), 1e-6);
                assertTrue(after.size().height() >= before.size().height());
                assertDisjoint(after);
                var workspace = MenuScene.screen("music").viewport(MusicWorkspaceComposition.VIEWPORT)
                        .content(MusicWorkspaceComposition.content(Ui.button(label.apply("rhythm")).build(),
                                Ui.text(Component.text("Library remains available")).build(), room(label, true, 3),
                                Ui.button(label.apply("pause")).build(), Ui.button(label.apply("close")).build())).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(workspace, scale));
                assertDisjoint(frame);
            }
        }
    }

    @Test void eachExplicitEntryAddressesItsOwnPhysicalDeviceAndDistinguishesCreationFromJoining() {
        var first = row(UUID.randomUUID(), false, null);
        var second = row(UUID.randomUUID(), true, null);
        var calls = new ArrayList<UUID>();
        var scene = scene(List.of(first, second), calls);
        assertTrue(calls.isEmpty(), "browsing cannot create rooms or interrupt playback");
        assertEquals(Component.text("create-room"), action(scene, first.device()).label());
        assertEquals(Component.text("join-room"), action(scene, second.device()).label());
        var reordered = scene(List.of(second, first), calls);
        assertEquals(scene.entries().keySet(), reordered.entries().keySet());
        action(reordered, second.device()).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        action(reordered, first.device()).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(second.device(), first.device()), calls);
        assertNull(entry(scene, "calibration"), "calibration belongs to personal preparation inside a room");
    }

    @Test void aRestrictedCreationExplainsTheReasonWithoutDisablingAnExistingRoom() {
        var restricted = row(UUID.randomUUID(), false, Component.text("No permission to create"));
        var existing = row(UUID.randomUUID(), true, null);
        var scene = scene(List.of(restricted, existing), new ArrayList<>());
        assertFalse(action(scene, restricted.device()).enabled());
        assertEquals(restricted.restriction(), action(scene, restricted.device()).disabledReason());
        assertTrue(action(scene, existing.device()).enabled());
    }

    @Test void nearbyRoomSelectionFitsBesideTheBrowserAcrossLanguagesAndTextSizes() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (var scale : FloatingMenuTextScale.values()) {
                var rows = new ArrayList<MusicRhythmComposition.DeviceRow>();
                for (int index = 0; index < 5; index++) rows.add(row(UUID.randomUUID(), index % 2 == 0, null));
                var picker = MusicWorkspace.column("picker").child(MusicWorkspaceComposition.panelHeading(label.apply("rhythm"),
                        Ui.button(UiIcons.CROSS, label.apply("close-panel")).key("close-panel").build()))
                        .child(MusicRhythmComposition.directory(rows, label, _ -> (_, _) -> {}, MusicSourcePosition.START, _ -> {})).build();
                var scene = MenuScene.screen("music-rhythm").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                        .content(MusicWorkspaceComposition.content(Ui.button(label.apply("rhythm")).build(),
                                Ui.text(Component.text("The library remains available for selecting the room song")).build(), picker,
                                Ui.button(label.apply("pause")).key("pause").build(), Ui.button(label.apply("close")).key("close").build())).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " @ " + scale);
                assertDisjoint(frame);
                assertTrue(frame.elements().stream().anyMatch(element -> element.node().key().value().equals("enter-room")));
                for (var value : frame.elements().stream().filter(element -> List.of("position", "enter-room").contains(element.node().key().value())).toList())
                    assertEquals(1, UiTextMetrics.measure(value.node().content().label(), value.node().textWidthPixels()).lines());
            }
        }
    }

    private MusicRhythmComposition.DeviceRow row(UUID id, boolean existing, Component restriction) {
        return new MusicRhythmComposition.DeviceRow(id, Component.text("Room jukebox 客厅唱片机"), Component.text("右前方 · 8 格"),
                Component.text("world · X 12 · Y 64 · Z 20"), existing, restriction);
    }
    private UiNode room(Function<String, Component> label, boolean controller, int count) {
        var players = new ArrayList<MusicRhythmComposition.Participant>();
        for (int index = 0; index < count; index++) players.add(new MusicRhythmComposition.Participant(UUID.randomUUID(),
                Component.text(index % 2 == 0 ? "Long player name 玩家" : "Lee"), label.apply(index % 2 == 0 ? "ready" : "waiting")));
        var preparation = MusicRhythmComposition.preparation(label,
                Ui.button(label.apply("falling")).key("room-mode").build(), Ui.button(label.apply("normal")).key("room-difficulty").build(),
                MusicWorkspace.tools("personal-actions").child(Ui.button(label.apply("calibration")).key("calibration").singleLine().build())
                        .child(Ui.button(label.apply("room-cancel-ready")).key("ready").singleLine().selected(true).build()).build());
        var actions = new ArrayList<UiElement>();
        if (controller) {
            actions.add(Ui.button(label.apply("select-room-track")).key("select-room-track").build());
            actions.add(Ui.button(label.apply("start-room")).key("start-room").build());
        }
        actions.add(Ui.button(label.apply("leave-room")).key("leave-room").build());
        if (controller) actions.add(Ui.button(label.apply("end-room")).key("end-room").build());
        return MusicRhythmComposition.content(MusicWorkspaceComposition.panelHeading(Component.text("Room jukebox 客厅唱片机"),
                        Ui.button(UiIcons.CROSS, label.apply("close-panel")).key("close-panel").build()),
                MusicRhythmComposition.summary(label.apply("current-track"), Component.text("A long selected song title 月光奏鸣曲".repeat(3)), label.apply("room-score-ready")),
                MusicWorkspace.column("roster").child(MusicRhythmComposition.participants(players, label, MusicSourcePosition.START, _ -> {})).build(), preparation, actions);
    }
    private MenuScene scene(List<MusicRhythmComposition.DeviceRow> rows, List<UUID> calls) {
        var scene = MenuScene.screen("room-picker").content(MusicRhythmComposition.directory(rows, Component::text,
                id -> (_, _) -> calls.add(id), MusicSourcePosition.START, _ -> {})).build();
        return scene.measured(SceneGeometryTestSupport.measure(scene, 1));
    }
    private MenuScene.Entry action(MenuScene scene, UUID device) {
        return scene.entries().entrySet().stream().filter(entry -> entry.getValue().trigger(FloatingMenuInteraction.PRIMARY) != null
                && entry.getKey().contains(UiKey.item(device.toString()).value())).map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
    }
}
