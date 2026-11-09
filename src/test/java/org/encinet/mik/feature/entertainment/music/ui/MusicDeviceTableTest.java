package org.encinet.mik.feature.entertainment.music.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import java.util.*;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

class MusicDeviceTableTest {
    @Test void longDirectoriesKeepStableTargetsMembershipAndRestrictionsAtReadingExtremes() {
        var first = new UUID(0,1); var blocked = new UUID(0,2); var reason = Component.text("No permission");
        var rows = java.util.stream.IntStream.range(0,300).mapToObj(index -> {
            UUID id = new UUID(0,index + 1); var row = row(id,"Device " + index);
            return new MusicDeviceTable.Row(id,row.name(),row.kind(),row.status(),row.song(),index < 2 ? reason : null,
                    row.location(),row.coordinates(),row.color());
        }).toList();
        var reversed = new ArrayList<>(rows); Collections.reverse(reversed);
        for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110})
            for (boolean editing : new boolean[]{false,true}) {
                var calls = new ArrayList<UUID>(); var members = editing ? new HashSet<>(Set.of(first)) : null;
                var source = new ArrayList<>(rows);
                var content = MusicDeviceTable.content(source,first,members,Component::text,id -> (_,_) -> calls.add(id),0,_ -> {});
                source.clear(); if (members != null) { members.clear(); members.add(blocked); }
                var scene = MenuScene.screen("large-device-directory").content(content).build();
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                assertDisjoint(frame); var published = scene.measured(frame);
                var target = rowElement(frame,first,"target"); var action = published.entries().get(target.path());
                assertTrue(action.selected()); assertTrue(action.enabled());
                action.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                if (editing) {
                    var marker = rowElement(frame,first,"select");
                    assertTrue(marker.node().input().selected());
                    published.entries().get(marker.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(List.of(first,first),calls);
                    var firstPage = frame.pagination().getFirst().page();
                    int blockedPage = 1 / firstPage.pageSize();
                    var restricted = MenuScene.screen("large-device-directory").content(MusicDeviceTable.content(rows,first,Set.of(first),Component::text,
                            _ -> (_,_) -> fail("A restricted member cannot be added"),blockedPage,_ -> {})).build();
                    var restrictedFrame = SceneGeometryTestSupport.readableFrame(restricted,font,new FloatingMenuFieldOfView(fov),scale);
                    for (String key : List.of("target","select")) {
                        var cell = rowElement(restrictedFrame,blocked,key);
                        assertFalse(cell.node().input().enabled()); assertEquals(reason,cell.node().input().disabledReason().orElseThrow());
                    }
                } else {
                    assertEquals(List.of(first),calls); assertNull(UiSceneTestSupport.entry(published,frame,"select"));
                }
                var reordered = MenuScene.screen("large-device-directory").content(MusicDeviceTable.content(reversed,first,editing ? Set.of(first) : null,Component::text,
                        id -> (_,_) -> calls.add(id),Integer.MAX_VALUE,_ -> {})).build();
                var reorderedFrame = SceneGeometryTestSupport.readableFrame(reordered,font,new FloatingMenuFieldOfView(fov),scale);
                var moved = rowElement(reorderedFrame,first,"target"); assertEquals(target.path(),moved.path());
                reordered.measured(reorderedFrame).entries().get(moved.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                assertEquals(first,calls.getLast());
                assertEquals(row(first,"unused").coordinates(),rowElement(frame,first,"location").node().input().tooltip().orElseThrow().text());
                assertTrue(frame.elements().stream().filter(node -> node.node().key().value().equals("target")).count() < rows.size());
            }
    }

    @Test void locationColumnsKeepTheFullPositionWhenOtherColumnsCompeteForWidth() throws Exception {
        UUID id = UUID.randomUUID();
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            var position = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_DEVICE_DISTANCE,
                    FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, "direction-back-right"), 12345678))
                    .append(Component.text(" · ")).append(Component.text(FluentTestSupport.text(bundle,
                            Message.MUSIC_SPACE_DEVICE_HEIGHT, "above", 319)));
            for (var location : List.of(position, Component.text("世界/Community_creative_world_with_a_long_distinguishing_name"))) {
                var row = new MusicDeviceTable.Row(id, Component.text("客厅唱片机 Room speaker"), label.apply("single-device"),
                        label.apply("available"), Component.text("Moonlight Sonata 月光"), null, location,
                        Component.text("world · X -29999999 · Y -64 · Z 29999999"), 0x70D7FF);
                for (double scale : new double[]{.7, 1, 1.8})
                    for (boolean editing : new boolean[]{false, true}) {
                        var scene = MenuScene.screen("directory").viewport(MusicWorkspaceComposition.VIEWPORT)
                                .content(MusicDeviceTable.content(List.of(row), id, editing ? Set.of(id) : null, label, _ -> (_,_) -> {},0,_ -> {})).build();
                        var frame = SceneGeometryTestSupport.naturalFrame(scene, scale);
                        var reading = element(frame, "location");
                        var natural = SceneGeometryTestSupport.naturalFrame(MenuScene.screen("intrinsic-position").viewport(MusicWorkspaceComposition.VIEWPORT)
                                .content(Ui.text(location).noWrap().build()).build(), scale);
                        assertEquals(location, reading.node().content().label(), language + " @ " + scale);
                        assertEquals(natural.size().width(), reading.size().width(), 1e-6);
                        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, scale, "location"));
                        assertEquals(row.coordinates(), reading.node().input().tooltip().orElseThrow().text());
                        assertFalse(reading.node().input().interactive());
                        assertDisjoint(frame);
                        assertTrue(frame.size().width() > natural.size().width());
                    }
            }
        }
    }
    @Test void changingToALongerPositionExpandsTheColumnAndItsBackgroundInsteadOfTheRowHeight() {
        UUID id = UUID.randomUUID();
        var before = row(id, "Room");
        var after = new MusicDeviceTable.Row(id, before.name(), before.kind(), before.status(), before.song(), before.restriction(),
                Component.text("世界/Community_creative_world_with_a_long_distinguishing_name"), before.coordinates(), before.color());
        for (double scale : new double[] {.7, 1, 1.8}) {
            UiFrame shortFrame = null;
            for (var row : List.of(before, after)) {
                var scene = MenuScene.screen("directory").viewport(MusicWorkspaceComposition.VIEWPORT)
                        .content(UiTemplates.window("devices", MusicDeviceTable.content(List.of(row), id, null, Component::text,
                                _ -> (_, _) -> {},0,_ -> {}))).build();
                var frame = SceneGeometryTestSupport.naturalFrame(scene, scale);
                assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, scale, "location"));
                assertDisjoint(frame);
                assertEquals(frame.size(), frame.surfaces().getFirst().size());
                if (shortFrame != null) {
                    assertTrue(element(frame, "location").size().width() > element(shortFrame, "location").size().width());
                    assertTrue(frame.size().width() > shortFrame.size().width());
                    assertEquals(shortFrame.size().height(), frame.size().height(), 1e-6);
                }
                shortFrame = frame;
            }
        }
    }
    @Test void locationsAreReadOnlyAndRetainCoordinatesWhileNamesSelectThePlaybackTarget() {
        UUID id = UUID.randomUUID();
        var chosen = new ArrayList<UUID>();
        var scene = MenuScene.screen("location").content(MusicDeviceTable.content(List.of(row(id,"Room")),null,null,Component::text,
                target -> (_,_) -> chosen.add(target),0,_ -> {})).build();
        var location = rowEntry(scene,id,"location");
        assertFalse(location.interactive());
        assertEquals(Component.text("test · X 0 · Y 64 · Z 12"),location.tooltip().text());
        assertTrue(chosen.isEmpty());
        targetEntry(scene,id).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(id),chosen);
    }
    @Test void aNormalDirectoryChoosesExactlyOneStableTargetWithoutMembershipCheckboxes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var calls = new ArrayList<UUID>();
        var rows = List.of(row(a, "A"), row(b, "B"));
        var scene = scene(rows, a, null, calls);
        var reordered = scene(List.of(rows.get(1), rows.get(0)), a, null, calls);
        assertEquals(scene.entries().keySet(), reordered.entries().keySet());
        assertNull(entry(scene, "select"));
        var target = targetEntry(reordered, b);
        target.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(b), calls);
        assertTrue(targetEntry(scene, a).selected());
        assertFalse(targetEntry(scene, b).selected());
    }
    @Test void membershipSelectionIsExplicitAndRestrictedRowsCannotBeAddedButCanBeRemoved() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var restricted = new MusicDeviceTable.Row(a, Component.text("Offline"), Component.text("Jukebox"), Component.text("Offline"), Component.empty(), Component.text("Chunk not loaded"),
                Component.text("Ahead · 12 m"),Component.text("test · X 0 · Y 64 · Z 12"),0x70D7FF);
        var scene = scene(List.of(restricted, row(b, "B")), null, Set.of(), new ArrayList<>());
        assertFalse(targetEntry(scene, a).enabled());
        var calls = new ArrayList<UUID>();
        var selected = scene(List.of(restricted), null, Set.of(a), calls);
        assertTrue(targetEntry(selected, a).enabled());
        assertNotNull(entry(selected, "select"));
        targetEntry(selected, a).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(a), calls);
    }
    @Test void deviceAndGroupDirectoriesFitAllLanguagesAndTextSizesInTheRealWorkbench() throws Exception {
        for (Language language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            UUID active = UUID.randomUUID();
            var rows = new ArrayList<MusicDeviceTable.Row>();
            var position = Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_DEVICE_DISTANCE,
                    FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, "direction-back-right"), 12345678))
                    .append(Component.text(" · ")).append(Component.text(FluentTestSupport.text(bundle,
                            Message.MUSIC_SPACE_DEVICE_HEIGHT, "above", 319)));
            for (int index = 0; index < 5; index++) rows.add(new MusicDeviceTable.Row(index == 0 ? active : UUID.randomUUID(),
                    Component.text("客厅唱片机 Room speaker " + index), label.apply(index % 2 == 0 ? "sync-group" : "single-device"),
                    Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_ONLINE_COUNT, 2, 3)),
                    Component.text("月光 Moonlight Sonata"), null, index % 2 == 0 ? position
                            : Component.text("世界/Community_creative_world_with_a_long_distinguishing_name"), Component.text("X 0 Y 64 Z 12"),0x70D7FF));
            for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) for (boolean editing : new boolean[] {false,true}) {
                var page = new java.util.concurrent.atomic.AtomicInteger(); var seen = new HashSet<UUID>();
                int pages = 1; MenuScene host = null;
                for (int index = 0; index < pages; index++) {
                    var table = MusicDeviceTable.content(rows,active,editing ? Set.of(active) : null,label,_ -> (_,_) -> {},page.get(),page::set);
                    var directory = UiTemplates.focus(MusicWorkspace.column("device-directory").child(Ui.heading(label.apply("devices")).build()).child(table).build(),table.key());
                    var scene = MenuScene.screen("music-devices").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                            .content(MusicWorkspaceComposition.content(Ui.button(label.apply("devices")).build(),directory,null,
                                    Ui.row().child(Ui.button(label.apply("pause")).build()).child(Ui.button(label.apply("devices")).key("picker").build()).build(),
                                    Ui.button(label.apply("close")).build())).build();
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene,scale.factor()),language + " @ " + scale + " editing=" + editing);
                    assertDisjoint(frame); assertLocationsStayOnOneLine(frame); assertPublishedRows(frame,rows,seen);
                    var paging = frame.pagination().getFirst(); pages = paging.page().count(); assertEquals(index,paging.page().index());
                    if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                    host = scene;
                }
                assertEquals(rows.size(),seen.size());
                if (!editing) {
                    var popupPage = new java.util.concurrent.atomic.AtomicInteger(); var popupSeen = new HashSet<UUID>(); int popupPages = 1;
                    for (int index = 0; index < popupPages; index++) {
                        var table = MusicDeviceTable.content(rows,active,null,label,_ -> (_,_) -> {},popupPage.get(),popupPage::set);
                        var picker = UiTemplates.focus(MusicWorkspace.column("device-picker")
                                .child(Ui.button(label.apply("create-group")).key("create-group").build()).child(table).build(),table.key());
                        var popup = host.toBuilder().popover(MenuPopover.attached(UiKey.named("picker"),label.apply("devices"),picker,label.apply("close"))).build();
                        var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.popoverFrame(popup,scale.factor(),FloatingMenuFieldOfView.DEFAULT),
                                language + " @ " + scale + " picker");
                        assertNotNull(frame); assertDisjoint(frame); assertLocationsStayOnOneLine(frame); assertPublishedRows(frame,rows,popupSeen);
                        var paging = frame.pagination().getFirst(); popupPages = paging.page().count(); assertEquals(index,paging.page().index());
                        if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                    }
                    assertEquals(rows.size(),popupSeen.size());
                }
            }
        }
    }
    private static void assertPublishedRows(UiFrame frame, List<MusicDeviceTable.Row> rows, Set<UUID> seen) {
        for (var row : rows) {
            var target = frame.elements().stream().filter(node -> node.path().contains("/" + UiKey.item(row.id().toString()).value() + "/")
                    && node.node().key().value().equals("target")).findFirst();
            if (target.isEmpty()) continue;
            assertTrue(seen.add(row.id()));
            var location = rowElement(frame,row.id(),"location");
            assertFalse(location.node().input().interactive()); assertEquals(row.coordinates(),location.node().input().tooltip().orElseThrow().text());
            if (location.node().content() instanceof UiContent.Text text && !text.wrapping()) assertEquals(row.location(),text.label());
            else {
                var help = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(target.orElseThrow().node().input().tooltip().orElseThrow().text());
                assertTrue(help.contains(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(row.location())));
            }
        }
    }
    private void assertLocationsStayOnOneLine(UiFrame frame) {
        for (var reading : frame.elements().stream().filter(value -> value.node().key().value().equals("location")).toList())
            assertEquals(1, UiTextMetrics.measure(reading.node().content().label(), reading.node().textWidthPixels()).lines());
    }
    private MusicDeviceTable.Row row(UUID id, String name) {
        return new MusicDeviceTable.Row(id, Component.text(name), Component.text("Jukebox"), Component.text("Available"), Component.text("Song"), null,
                Component.text("Ahead · 12 m"),Component.text("test · X 0 · Y 64 · Z 12"),0x70D7FF);
    }
    private MenuScene scene(List<MusicDeviceTable.Row> rows, UUID target, Set<UUID> members, List<UUID> calls) {
        var scene = MenuScene.screen("devices").content(MusicDeviceTable.content(rows, target, members, Component::text,
                id -> (_, _) -> calls.add(id),0,_ -> {})).build();
        return scene.measured(SceneGeometryTestSupport.measure(scene,1));
    }
    private MenuScene.Entry targetEntry(MenuScene scene, UUID id) {
        return rowEntry(scene,id,"target");
    }
    private MenuScene.Entry rowEntry(MenuScene scene, UUID id, String key) {
        var frame = SceneGeometryTestSupport.measure(scene,1);
        return scene.measured(frame).entries().get(rowElement(frame,id,key).path());
    }
    private MenuScene.Entry entry(MenuScene scene, String key) {
        var frame = SceneGeometryTestSupport.measure(scene,1);
        return UiSceneTestSupport.entry(scene.measured(frame),frame,key);
    }
    private static UiFrame.Element rowElement(UiFrame frame, UUID id, String key) {
        return frame.elements().stream().filter(node -> node.path().contains("/" + UiKey.item(id.toString()).value() + "/")
                && node.node().key().value().equals(key)).findFirst().orElseThrow();
    }
}
