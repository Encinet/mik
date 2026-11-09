package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class MusicQueueTableTest {
    @Test void sourceOccurrenceBookmarksSurviveReorderingAndExposeTheCorrectPartitionAndSelection() {
        var track = StoredTrack.of(MusicWorkspaceStateTest.track("Repeated song"));
        var rows = java.util.stream.IntStream.range(0,300).mapToObj(index -> new MusicQueueTable.Row(
                QueueEntry.create(track.id(),UUID.randomUUID()),track,index < 100 ? MusicQueueRows.Section.REQUESTS : MusicQueueRows.Section.CONTINUATION)).toList();
        UUID target = rows.get(211).entry().id();
        var reverse = new ArrayList<>(rows); Collections.reverse(reverse);
        for (var order : List.of(rows,reverse)) for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
            var calls = new ArrayList<UUID>();
            var selection = MusicTableSelection.<MusicQueueTable.Row>selecting(row -> row.entry().id().equals(target),
                    row -> (_,_) -> calls.add(row.entry().id()));
            var scene = MenuScene.screen("queue-source").content(MusicQueueTable.content(order,selection,Component::text,0,_ -> {},target,true)).build();
            var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),1.3);
            assertDisjoint(frame);
            assertTrue(frame.readingRegion().path().endsWith("/pages"),"Both representations keep the same body focus");
            assertTrue(frame.size().height() > frame.readingRegion().size().height(),"The persistent header stays outside the body budget");
            var published = scene.measured(frame);
            for (String key : List.of("title","select")) {
                var cell = frame.elements().stream().filter(node -> node.path().contains(UiKey.item(target.toString()).value())
                        && node.node().key().value().equals(key)).findFirst().orElseThrow();
                var action = published.entries().get(cell.path()); assertTrue(action.selected());
                action.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
            }
            assertEquals(List.of(target,target),calls);
            var partition = frame.elements().stream().filter(node -> node.path().contains(UiKey.item(target.toString()).value())
                    && node.node().key().value().equals("section")).findFirst();
            if (partition.isPresent()) assertEquals(Component.text("shuffle-candidates"),partition.orElseThrow().node().input().tooltip().orElseThrow().text());
            else {
                var title = frame.elements().stream().filter(node -> node.path().contains(UiKey.item(target.toString()).value())
                        && node.node().key().value().equals("title")).findFirst().orElseThrow();
                var help = PlainTextComponentSerializer.plainText().serialize(title.node().input().tooltip().orElseThrow().text());
                assertTrue(help.contains("shuffle-candidates")); assertTrue(help.contains("artist")); assertTrue(help.contains("duration"));
            }
            long visible = frame.elements().stream().filter(node -> node.node().key().value().equals("title")).count();
            assertTrue(visible < rows.size());
        }
    }

    @Test void repeatedSongsBindSelectionToTheirOccurrencesAfterReordering() {
        var track = StoredTrack.of(MusicWorkspaceStateTest.track("song"));
        var first = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
        var second = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
        var calls = new ArrayList<UUID>();
        var original = scene(List.of(first, second), Set.of(second.entry().id()), false, Component::text, 1280, calls);
        var reordered = scene(List.of(second, first), Set.of(second.entry().id()), false, Component::text, 1280, calls);
        assertEquals(original.entries().keySet(), reordered.entries().keySet());
        for (String key : List.of("select", "title")) {
            var source = rowEntry(reordered, first.entry().id(), key);
            assertFalse(source.selected());
            source.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
        assertEquals(List.of(first.entry().id(), first.entry().id()), calls);
        assertTrue(rowEntry(reordered, second.entry().id(), "title").selected());
    }

    @Test void movingReusesTheRowControlsAndLocksTheSelectedOccurrencesInsteadOfShowingMoreButtons() {
        var track = StoredTrack.of(MusicWorkspaceStateTest.track("same song"));
        var target = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
        var source = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
        var calls = new ArrayList<UUID>();
        var scene = scene(List.of(target, source), Set.of(source.entry().id()), true, Component::text, 1280, calls);
        for (String key : List.of("select", "title")) {
            if (key.equals("select")) assertFalse(rowEntry(scene, source.entry().id(), key).interactive());
            else assertFalse(rowEntry(scene, source.entry().id(), key).enabled());
            assertNull(rowEntry(scene, source.entry().id(), key).trigger(FloatingMenuInteraction.PRIMARY));
            rowEntry(scene, target.entry().id(), key).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
        assertEquals(List.of(target.entry().id(), target.entry().id()), calls);
        assertNull(entry(scene, "before"));
        assertEquals("place-before", PlainTextComponentSerializer.plainText().serialize(rowEntry(scene, target.entry().id(), "select").label()));
    }

    @Test void readOnlyQueuesHaveNoSelectionOrMutationControlsAndUnknownMetadataIsSafe() {
        var row = new MusicQueueTable.Row(QueueEntry.create("missing", UUID.randomUUID()), null);
        var scene = scene(List.of(row), null, false, Component::text, 360, new ArrayList<>());
        assertNull(entry(scene, "select"));
        assertTrue(scene.entries().values().stream().noneMatch(MenuScene.Entry::interactive));
        for (String key : List.of("title", "artist", "duration"))
            assertEquals("—", PlainTextComponentSerializer.plainText().serialize(entry(scene, key).label()));
        assertDisjoint(SceneGeometryTestSupport.measure(scene, 1.8));
    }

    @Test void shortMetadataStaysCloseWithoutAnExtraMovementColumn() {
        var track = StoredTrack.of(MusicWorkspaceStateTest.track("A song title"));
        var row = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
        var scene = scene(List.of(row), Set.of(), false, Component::text, 1280, new ArrayList<>());
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var title = element(frame, "title"); var artist = element(frame, "artist"); var duration = element(frame, "duration");
        assertEquals(6, artist.x() - title.x() - title.size().width(), 1e-6);
        assertTrue(duration.x() >= artist.x() + artist.size().width());
        assertEquals(1, SceneGeometryTestSupport.textLines(scene, 1, "title"));
        assertNull(entry(scene, "before"));
        assertDisjoint(frame);
    }

    @Test void selectionAndPlacementKeepTheSameBoundsWithLongNamesNarrowSpaceAndLargeFonts() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            var track = StoredTrack.of(MusicWorkspaceStateTest.track("月光奏鸣曲 A long song title ".repeat(4)));
            var first = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
            var second = new MusicQueueTable.Row(QueueEntry.create(track.id(), UUID.randomUUID()), track);
            for (double scale : new double[]{.7, 1, 1.8}) for (double width : new double[]{360, 1280}) {
                var normal = scene(List.of(first, second), Set.of(second.entry().id()), false, label, width, new ArrayList<>());
                var placing = scene(List.of(first, second), Set.of(second.entry().id()), true, label, width, new ArrayList<>());
                var before = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(normal, scale), language + " @ " + scale + " / " + width);
                var after = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(placing, scale), language + " @ " + scale + " / " + width);
                assertDisjoint(before); assertDisjoint(after);
                assertEquals(before.size(), after.size(), language + " @ " + scale + " / " + width);
                assertEquals(normal.entries().keySet(), placing.entries().keySet());
                var checkbox = element(after, "select"); var title = element(after, "title");
                assertTrue(checkbox.x() + checkbox.size().width() <= title.x());
            }
        }
    }

    private MenuScene scene(List<MusicQueueTable.Row> rows, Set<UUID> selected, boolean moving,
                            Function<String, Component> label, double width, List<UUID> calls) {
        MusicTableSelection<MusicQueueTable.Row> selection = null;
        if (selected != null) {
            var move = selected.isEmpty() ? null : new MusicMove(UUID.randomUUID(), 0, rows.stream().map(row -> row.entry().id()).toList(), selected);
            selection = moving ? MusicTableSelection.moving(row -> selected.contains(row.entry().id()),
                    row -> move.canPlace(EntryEdits.before(row.entry().id())) ? (_, _) -> calls.add(row.entry().id()) : null)
                    : MusicTableSelection.selecting(row -> selected.contains(row.entry().id()), row -> (_, _) -> calls.add(row.entry().id()));
        }
        var scene = MenuScene.screen("queue").viewport(new UiSize(width,4096))
                .content(MusicQueueTable.content(rows,selection,label,0,_ -> {},null,false)).build();
        return scene.measured(SceneGeometryTestSupport.measure(scene,1));
    }

    private MenuScene.Entry rowEntry(MenuScene scene, UUID row, String key) {
        var frame = SceneGeometryTestSupport.measure(scene,1);
        var element = frame.elements().stream().filter(node -> node.path().contains(UiKey.item(row.toString()).value())
                && node.node().key().value().equals(key)).findFirst().orElseThrow();
        return scene.entries().get(element.path());
    }

    private MenuScene.Entry entry(MenuScene scene, String key) {
        return UiSceneTestSupport.entry(scene,SceneGeometryTestSupport.measure(scene,1),key);
    }
}
