package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

class MusicPlaylistTableTest {
    @Test void browseAndSaveRowsKeepPlaylistIdentityAndOnlyTheNameIsAnAction() {
        var first = playlist("A", false, 2);
        var second = playlist("B", true, 0);
        var calls = new ArrayList<UUID>();
        var scene = scene(List.of(new MusicPlaylistTable.Row(first, false, null), new MusicPlaylistTable.Row(second, false, null)), calls);
        var reordered = scene(List.of(new MusicPlaylistTable.Row(second, true, Component.text("Already added")),
                new MusicPlaylistTable.Row(first, false, null)), calls);
        var frame = SceneGeometryTestSupport.measure(scene,1);
        var updatedFrame = SceneGeometryTestSupport.measure(reordered,1);
        scene = scene.measured(frame); reordered = reordered.measured(updatedFrame);
        assertEquals(scene.entries().keySet(), reordered.entries().keySet());
        assertFalse(rowEntry(reordered,updatedFrame,second.id()).enabled());
        assertTrue(rowEntry(reordered,updatedFrame,second.id()).selected());
        rowEntry(reordered,updatedFrame,first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(first.id()), calls);
        var elements = frame.elements().stream().collect(java.util.stream.Collectors.toMap(UiFrame.Element::path,UiFrame.Element::node));
        assertTrue(scene.entries().entrySet().stream().filter(entry -> entry.getValue().trigger(FloatingMenuInteraction.PRIMARY) != null)
                .allMatch(entry -> elements.get(entry.getKey()).key().value().equals("playlist")));
    }

    @Test void longNamesStayInTheirRowAndMetadataColumnsAlignAcrossLanguagesAndTextSizes() throws Exception {
        for (Language language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            IntFunction<Component> count = value -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_TRACK_COUNT, value));
            var lists = List.of(playlist("短歌单", false, 0), playlist("歌单名 Long playlist ".repeat(3), true, 21));
            for (double typography : new double[]{.7, 1, 1.8}) for (int width : new int[]{360, 1280}) {
                var selected = new java.util.concurrent.atomic.AtomicInteger();
                var seen = new HashMap<UUID,UiFrame.Element>(); var columns = new HashMap<String,Double>();
                int pages = 1;
                for (int page = 0; page < pages; page++) {
                    var table = MusicPlaylistTable.content(lists.stream().map(list -> new MusicPlaylistTable.Row(list,false,null)).toList(),
                            label,count,_ -> (_,_) -> {},selected.get(),selected::set);
                    var scene = MenuScene.screen("playlists").viewport(new UiSize(width,2048)).content(table).build();
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene,typography),language + " @ " + typography + " width=" + width);
                    assertDisjoint(frame); assertTrue(frame.size().width() <= width + 1e-6);
                    var paging = frame.pagination().getFirst(); pages = paging.page().count(); assertEquals(page,paging.page().index());
                    for (var list : lists) {
                        var name = optionalRowElement(frame,list.id(),"playlist");
                        if (name.isEmpty()) continue;
                        var button = name.orElseThrow(); assertNull(seen.put(list.id(),button));
                        assertEquals(Component.text(list.name()),button.node().input().tooltip().orElseThrow().text());
                        for (String key : List.of("count","visibility")) {
                            var cell = rowElement(frame,list.id(),key);
                            if (columns.containsKey(key)) assertEquals(columns.get(key),cell.x(),1e-6);
                            columns.put(key,cell.x());
                            assertTrue(cell.x() >= button.x() + button.size().width() - 1e-6);
                            assertEquals(button.y() + button.size().height()/2,cell.y() + cell.size().height()/2,1e-6);
                        }
                    }
                    if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                }
                assertEquals(lists.size(),seen.size());
                var a = seen.get(lists.getFirst().id()); var b = seen.get(lists.getLast().id());
                assertEquals(a.size().height(),b.size().height(),1e-6); assertEquals(a.x(),b.x(),1e-6);
            }
        }
    }

    private static Playlist playlist(String name, boolean published, int size) {
        var entries = new ArrayList<Playlist.Entry>();
        for (int i = 0; i < size; i++) entries.add(new Playlist.Entry(UUID.randomUUID(), "track:" + i, i));
        return new Playlist(UUID.randomUUID(), UUID.randomUUID(), name, "", "MUSIC_DISC_CAT", published, 0, entries);
    }
    private static MenuScene scene(List<MusicPlaylistTable.Row> rows, List<UUID> calls) {
        return MenuScene.screen("playlists").content(MusicPlaylistTable.content(rows, Component::text, count -> Component.text(count),
                list -> (_, _) -> calls.add(list.id()),0,_ -> {})).build();
    }
    private static MenuScene.Entry rowEntry(MenuScene scene, UiFrame frame, UUID id) {
        return scene.entries().get(rowElement(frame,id,"playlist").path());
    }
    private static UiFrame.Element rowElement(UiFrame frame, UUID id, String key) {
        return optionalRowElement(frame,id,key).orElseThrow();
    }
    private static Optional<UiFrame.Element> optionalRowElement(UiFrame frame, UUID id, String key) {
        return frame.elements().stream().filter(element -> element.path().contains(UiKey.item(id.toString()).value())
                && element.node().key().value().equals(key)).findFirst();
    }
}
