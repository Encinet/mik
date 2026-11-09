package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicRhythmWindowTest {
    private static final Pattern UUID_PATH = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    @Test void everyRoomUsesItsOwnActionAcrossLargeWindowsAndKeepsIdentityAfterReorder() {
        var source = devices(300);
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var reading = new AtomicReference<>(MusicSourcePosition.START); var chosen = new ArrayList<UUID>();
            var seen = new HashMap<UUID, String>();
            for (int pass = 0; pass < source.size(); pass++) {
                var scene = host(MusicRhythmComposition.directory(source, Component::text, id -> (_, _) -> chosen.add(id), reading.get(), reading::set));
                var frame = measure(scene, font, fov, scale); assertDisjoint(frame); assertTrue(chosen.isEmpty());
                for (var item : frame.elements()) if (item.node().key().value().equals("enter-room")) {
                    UUID id = identity(item); assertNull(seen.put(id, item.path()));
                    var row = source.get((int) id.getLeastSignificantBits() - 1);
                    Component details = row.name().append(Component.newline()).append(row.position()).append(Component.newline()).append(row.coordinates());
                    assertEquals(Component.text(row.existing() ? "join-room" : "create-room").append(Component.newline()).append(details),
                            item.node().input().tooltip().orElseThrow().text());
                    item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                    assertEquals(List.of(row.device()), chosen); chosen.clear();
                }
                var page = pagination(frame, "rhythm-devices");
                if (!page.page().hasNext()) break;
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                assertEquals(page.page().toIndex(), reading.get().index());
            }
            assertEquals(source.size(), seen.size());
            var reordered = new ArrayList<>(source); Collections.reverse(reordered);
            var bookmark = reading.get();
            var scene = host(MusicRhythmComposition.directory(reordered, Component::text, _ -> (_, _) -> {}, bookmark, _ -> {}));
            reordered.clear();
            var frame = measure(scene, font, fov, scale);
            assertTrue(frame.elements().stream().filter(item -> item.node().key().value().equals("enter-room"))
                    .anyMatch(item -> UiKey.item(identity(item).toString()).equals(bookmark.anchor())));
            for (var item : frame.elements()) if (item.node().key().value().equals("enter-room")) assertEquals(seen.get(identity(item)), item.path());
        }
    }

    @Test void everyParticipantIsPublishedOnceAndRemainsReadOnlyAcrossLargeWindows() {
        var source = new ArrayList<MusicRhythmComposition.Participant>();
        for (int index = 1; index <= 300; index++) source.add(new MusicRhythmComposition.Participant(new UUID(0, index),
                Component.text("Player_" + index + " 玩家"), Component.text(index % 2 == 0 ? "Playing" : "Ready")));
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var reading = new AtomicReference<>(MusicSourcePosition.START); var seen = new HashSet<UUID>();
            for (int pass = 0; pass < source.size(); pass++) {
                var scene = host(MusicRhythmComposition.participants(source, Component::text, reading.get(), reading::set));
                var frame = measure(scene, font, fov, scale); assertDisjoint(frame);
                for (var item : frame.elements()) {
                    if (!List.of("player", "player-status").contains(item.node().key().value())) continue;
                    assertFalse(item.node().input().interactive()); assertTrue(item.node().input().actions().isEmpty());
                    if (item.node().key().value().equals("player")) assertTrue(seen.add(identity(item)));
                    assertNotNull(item.node().input().tooltip().orElseThrow().text());
                }
                var page = pagination(frame, "room-players");
                if (!page.page().hasNext()) break;
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
            }
            assertEquals(source.size(), seen.size());
        }
    }

    @Test void roomAndParticipantWindowsKeepAllRowsAtLanguageAndReadingBoundaries() throws Exception {
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            var devices = devices(7);
            var participants = devices.stream().map(row -> new MusicRhythmComposition.Participant(row.device(), row.name(), label.apply("room-playing"))).toList();
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                for (boolean directory : new boolean[]{false, true}) {
                    var reading = new AtomicReference<>(MusicSourcePosition.START); var seen = new HashSet<UUID>();
                    for (int pass = 0; pass < devices.size(); pass++) {
                        UiNode content = directory ? MusicRhythmComposition.directory(devices, label, _ -> (_, _) -> {}, reading.get(), reading::set)
                                : MusicRhythmComposition.participants(participants, label, reading.get(), reading::set);
                        var frame = measure(host(content), font, fov, scale); assertDisjoint(frame);
                        for (var item : frame.elements()) if (item.node().key().value().equals(directory ? "enter-room" : "player"))
                            assertTrue(seen.add(identity(item)), locale + " repeated a source row");
                        var page = pagination(frame, directory ? "rhythm-devices" : "room-players");
                        if (!page.page().hasNext()) break;
                        page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                    }
                    assertEquals(devices.size(), seen.size(), locale + " " + font + " " + fov + " " + scale + " " + directory);
                }
            }
        }
    }

    @Test void onlyTheCurrentWindowBuildsEntryActionsAndMeasurementNeverEntersARoom() {
        var made = new HashSet<UUID>(); var entered = new ArrayList<UUID>();
        var declaration = MusicRhythmComposition.directory(devices(300), Component::text, id -> {
            made.add(id); return (_, _) -> entered.add(id);
        }, MusicSourcePosition.START, _ -> fail("Measurement must not navigate"));
        var frame = measure(host(declaration), 1, 70, 1);
        assertTrue(made.size() <= UiWindowedList.MAX_VISIBLE_ITEMS + 1);
        assertFalse(made.contains(new UUID(0, 300))); assertTrue(entered.isEmpty());
        assertTrue(frame.elements().stream().anyMatch(item -> item.node().key().value().equals("enter-room")));
    }

    @Test void aLargeRosterKeepsPersonalPreparationAndControllerActionsInItsActualRoomHost() {
        var rows = new ArrayList<MusicRhythmComposition.Participant>();
        for (int index = 1; index <= 300; index++) rows.add(new MusicRhythmComposition.Participant(new UUID(0, index),
                Component.text("Player_" + index), Component.text("Ready")));
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var reading = new AtomicReference<>(MusicSourcePosition.START); var seen = new HashSet<UUID>();
            for (int pass = 0; pass < rows.size(); pass++) {
                var roster = Ui.column().child(MusicRhythmComposition.participants(rows, Component::text, reading.get(), reading::set)).build();
                var preparation = MusicRhythmComposition.preparation(Component::text,
                        Ui.button(Component.text("Falling")).key("room-mode").build(),
                        Ui.button(Component.text("Normal")).key("room-difficulty").build(),
                        Ui.button(Component.text("Ready")).key("ready").selected(true).build());
                var content = MusicRhythmComposition.content(MusicWorkspaceComposition.panelHeading(Component.text("Room jukebox"),
                                Ui.button(UiIcons.CROSS, Component.text("Close")).key("close-panel").build()),
                        MusicRhythmComposition.summary(Component.text("Current track"), Component.text("Moonlight"), Component.text("Ready")),
                        roster, preparation, List.of(Ui.button(Component.text("Start")).key("start-room").build(),
                        Ui.button(Component.text("Leave")).key("leave-room").build()));
                var frame = measure(host(content), font, fov, scale); assertDisjoint(frame);
                for (String key : List.of("room-mode", "room-difficulty", "ready", "start-room", "leave-room", "close-panel"))
                    assertNotNull(element(frame, key));
                assertTrue(element(frame, "ready").node().input().selected());
                for (var item : frame.elements()) if (item.node().key().value().equals("player")) assertTrue(seen.add(identity(item)));
                var page = pagination(frame, "room-players");
                if (!page.page().hasNext()) break;
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
            }
            assertEquals(rows.size(), seen.size());
        }
    }

    @Test void emptyWindowsRetainTheirHeadersAndDoNotCreateCommandsOrPaging() {
        for (double font : new double[]{.7, 1.8}) for (int fov : new int[]{30, 110}) {
            var directory = MusicRhythmComposition.directory(List.of(), Component::text, _ -> { fail(); return null; },
                    MusicSourcePosition.START, _ -> fail());
            var participants = MusicRhythmComposition.participants(List.of(), Component::text, MusicSourcePosition.START, _ -> fail());
            for (var content : List.of(directory, participants)) {
                var frame = measure(host(content), font, fov, 1.3); assertDisjoint(frame);
                assertTrue(frame.pagination().isEmpty());
                assertTrue(frame.elements().stream().noneMatch(item -> List.of("player", "enter-room").contains(item.node().key().value())));
                assertTrue(frame.size().height() > 0);
            }
        }
    }

    private static List<MusicRhythmComposition.DeviceRow> devices(int count) {
        var rows = new ArrayList<MusicRhythmComposition.DeviceRow>();
        for (int index = 1; index <= count; index++) rows.add(new MusicRhythmComposition.DeviceRow(new UUID(0, index),
                Component.text("Jukebox_" + index + " 客厅"), Component.text("Front right · " + index + " blocks"),
                Component.text("world · X -29999999 · Y -64 · Z 29999999"), index % 2 == 0, null));
        return rows;
    }
    private static MenuScene host(UiNode content) {
        return MusicSceneTestSupport.auxiliary(Ui.column().child(content).build(), (int) MusicWorkspaceComposition.VIEWPORT.width());
    }
    private static UiFrame measure(MenuScene scene, double font, int fov, double scale) {
        return SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
    }
    private static UiFrame.Pagination pagination(UiFrame frame, String key) {
        return frame.pagination().stream().filter(page -> page.path().contains("/" + key)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + key + " pagination in " + frame.pagination().stream().map(UiFrame.Pagination::path).toList()));
    }
    private static UUID identity(UiFrame.Element item) {
        var match = UUID_PATH.matcher(item.path()); UUID row = null;
        while (match.find()) row = UUID.fromString(match.group());
        assertNotNull(row, item.path()); return row;
    }
}
