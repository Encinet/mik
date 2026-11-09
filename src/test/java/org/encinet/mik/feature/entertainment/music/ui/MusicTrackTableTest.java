package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicTrackTableTest {
    @Test void compactWindowsKeepAllDirectActionsAndTrackIdentityAfterReordering() {
        var tracks = java.util.stream.IntStream.range(0,60).mapToObj(index -> {
            var base = MusicWorkspaceStateTest.track("song:" + index);
            return new MusicTrack(base.id(),new TrackDetails("较长名称 Long song " + index,"Composer",null,"mp3",
                    new AudioProperties(null,null,Duration.ofSeconds(225))),base.target());
        }).toList();
        var reversed = new ArrayList<>(tracks); Collections.reverse(reversed);
        var target = tracks.get(43);
        for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
            Map<String,String> previous = null;
            for (var order : List.of(tracks,reversed)) {
                var calls = new ArrayList<String>(); var source = new ArrayList<>(order);
                var content = MusicTrackTable.builder(source,null,Component::text,
                        track -> (_,_) -> calls.add("details:" + track.id()),
                        (songs,placement) -> calls.add(placement + ":" + songs.stream().map(MusicTrack::id).toList()),
                        track -> Ui.button(Component.text("Save")).key("add").primary((_,_) -> calls.add("add:" + track.id())).build())
                        .containing(target.id()).build();
                source.clear();
                var scene = MenuScene.screen("track-object").viewport(new UiSize(360,600)).content(content).build();
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),1.3);
                UiSceneTestSupport.assertDisjoint(frame); assertTrue(frame.readingRegion().path().endsWith("/pages"));
                var published = scene.measured(frame); var paths = new HashMap<String,String>();
                for (String key : List.of("details","insert","append","add")) {
                    var element = rowElement(frame,target,key);
                    paths.put(key,element.path());
                    published.entries().get(element.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    if (font == 1.8 && fov == 110 && !key.equals("details")) assertInstanceOf(UiContent.Icon.class,element.node().content());
                }
                assertEquals(List.of("details:" + target.id(),"INSERT:[" + target.id() + "]","APPEND:[" + target.id() + "]","add:" + target.id()),calls);
                if (previous != null) assertEquals(previous,paths); previous = paths;
                if (font == 1.8 && fov == 110) {
                    String help = PlainTextComponentSerializer.plainText().serialize(rowElement(frame,target,"details").node().input().tooltip().orElseThrow().text());
                    for (String value : List.of("3:45","MP3","Composer",target.details().title())) assertTrue(help.contains(value));
                }
            }
        }
    }

    @Test void selectPageChoosesOnlyTheFinalPublishedWindowAndRetainsEarlierSelections() throws Exception {
        var tracks = java.util.stream.IntStream.range(0,12).mapToObj(index -> MusicWorkspaceStateTest.track("song:" + index)).toList();
        for (var language : org.encinet.mik.shared.i18n.Language.values()) {
            var bundle = org.encinet.mik.shared.i18n.FluentTestSupport.bundle(language);
            java.util.function.Function<String,Component> label = key -> Component.text(org.encinet.mik.shared.i18n.FluentTestSupport.text(bundle,
                    org.encinet.mik.shared.i18n.Message.MUSIC_SPACE_LABEL,key));
            for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
                var page = new java.util.concurrent.atomic.AtomicInteger(); var chosen = new HashSet<String>(); int pages = 1;
                for (int index = 0; index < pages; index++) {
                    var snapshot = Set.copyOf(chosen);
                    var content = selectionTable(tracks,snapshot,label,page,visible -> visible.forEach(track -> chosen.add(track.id())));
                    var scene = MenuScene.screen("select-measured-page").viewport(new UiSize(360,200)).content(content).build();
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),1.3),
                            language + " font=" + font + " FOV=" + fov);
                    UiSceneTestSupport.assertDisjoint(frame);
                    var paging = frame.pagination().getFirst(); pages = paging.page().count(); assertEquals(index,paging.page().index());
                    var visible = tracks.stream().filter(track -> frame.elements().stream().anyMatch(node -> inRow(node,track) && node.node().key().value().equals("details")))
                            .map(MusicTrack::id).collect(java.util.stream.Collectors.toSet());
                    assertEquals(paging.page().toIndex() - paging.page().fromIndex(),visible.size());
                    var expected = new HashSet<>(snapshot); expected.addAll(visible);
                    var published = scene.measured(frame);
                    UiSceneTestSupport.entry(published,frame,"select-page").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(expected,chosen);
                    for (String key : List.of("insert","append","add")) assertNull(UiSceneTestSupport.entry(published,frame,key));
                    var refreshed = MenuScene.screen("select-measured-page").viewport(new UiSize(360,200)).content(selectionTable(tracks,chosen,label,page,
                            _ -> fail("An already selected page cannot execute again"))).build();
                    var selectedFrame = SceneGeometryTestSupport.readableFrame(refreshed,font,new FloatingMenuFieldOfView(fov),1.3);
                    var currentPage = selectedFrame.pagination().getFirst(); assertEquals(paging.page(),currentPage.page());
                    var done = UiSceneTestSupport.entry(refreshed.measured(selectedFrame),selectedFrame,"select-page");
                    assertFalse(done.enabled()); assertNull(done.trigger(FloatingMenuInteraction.PRIMARY));
                    if (currentPage.page().hasNext()) currentPage.selectPage().accept(currentPage.page().next().index());
                }
                assertEquals(tracks.size(),chosen.size()); assertTrue(pages > 1);
            }
        }
    }

    private static UiNode selectionTable(List<MusicTrack> tracks, Set<String> selected, java.util.function.Function<String,Component> label,
                                         java.util.concurrent.atomic.AtomicInteger page, java.util.function.Consumer<List<MusicTrack>> choose) {
        var snapshot = Set.copyOf(selected);
        var selection = MusicTableSelection.<MusicTrack>selecting(track -> snapshot.contains(track.id()),_ -> (_,_) -> {});
        return MusicTrackTable.builder(tracks,selection,label,_ -> (_,_) -> fail("Selection cannot inspect"),
                        (_,_) -> fail("Selection cannot play"),_ -> { fail("Selection cannot save"); return null; })
                .page(page.get(),page::set).pager(MusicTrackTable.selectionControls(tracks,snapshot,label,choose)).build();
    }

    @Test void normalRowActionsHaveDistinctStableBindingsAfterReorder() {
        var first = MusicWorkspaceStateTest.track("local:a/b%.mp3"); var second = MusicWorkspaceStateTest.track("local:a%2Fb%.mp3");
        var calls = new ArrayList<String>();
        var scene = scene(List.of(first, second), null, calls); var reordered = scene(List.of(second, first), null, calls);
        assertEquals(scene.entries().keySet(), reordered.entries().keySet()); assertTrue(calls.isEmpty());
        for (String action : List.of("details", "insert", "append", "add")) {
            String path = SceneGeometryTestSupport.measure(scene,1).elements().stream()
                    .filter(entry -> entry.node().key().value().equals(action) && entry.path().contains(UiKey.item(first.id()).value()))
                    .map(org.encinet.mik.shared.menu.ui.UiFrame.Element::path).findFirst().orElseThrow();
            reordered.entries().get(path).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
        assertEquals(List.of("inspect:" + first.id(), "INSERT:" + first.id(), "APPEND:" + first.id(), "add:" + first.id()), calls);
        UiSceneTestSupport.assertDisjoint(SceneGeometryTestSupport.measure(scene, 1.8));
    }

    @Test void selectionRowsToggleByNameOrLeadingCheckboxAndCannotTriggerSingleSongActions() {
        var first = MusicWorkspaceStateTest.track("a"); var second = MusicWorkspaceStateTest.track("b");
        var calls = new ArrayList<String>();
        var scene = scene(List.of(first,second),Set.of(first.id()),calls);
        for (String action : List.of("insert","append","add")) assertNull(entry(scene,action));
        var name = trackEntry(scene,first,"details"); var checkbox = trackEntry(scene,first,"select");
        assertEquals(UiRole.CHOICE,name.role());
        assertTrue(name.selected()); assertTrue(checkbox.selected());
        assertEquals(Component.text("deselect"),checkbox.tooltip().text(),"an icon-only toggle describes the next action");
        name.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        checkbox.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of("select:a","select:a"),calls);
        var frame = SceneGeometryTestSupport.measure(scene,1.8);
        UiSceneTestSupport.assertDisjoint(frame);
        var select = UiSceneTestSupport.element(frame,"select"); var title = UiSceneTestSupport.element(frame,"details");
        assertTrue(select.x()+select.size().width()<=title.x());
    }

    @Test void emptySelectionHasNoBatchActionsAndCannotPlayTheWholeCollection() {
        var calls = new ArrayList<String>();
        var scene = MenuScene.screen("selection").content(MusicPlaybackActions.buttons(List.of(),Component::text,
                (_,placement) -> calls.add(placement.name()))).build();
        assertTrue(scene.entries().isEmpty());
        assertTrue(calls.isEmpty());
    }
    @Test void ordinaryBrowsingHasNoImplicitSelectionOrHiddenRowAction() {
        var scene = scene(List.of(MusicWorkspaceStateTest.track("a")), null, new ArrayList<>());
        var keys = SceneGeometryTestSupport.measure(scene,1).elements().stream().map(node -> node.node().key().value()).toList();
        assertFalse(keys.contains("select")); assertTrue(keys.containsAll(List.of("details", "insert", "append", "add")));
    }
    @Test void placementUsesTheExistingCellsWithoutSingleSongActionsOrMutableSourceSelection() {
        var target = MusicWorkspaceStateTest.track("target"); var source = MusicWorkspaceStateTest.track("source");
        var calls = new ArrayList<String>();
        var placement = MusicTableSelection.<MusicTrack>moving(track -> track.id().equals(source.id()),
                track -> track.id().equals(target.id()) ? (_, _) -> calls.add(track.id()) : null);
        var scene = MenuScene.screen("placing").viewport(MusicWorkspaceComposition.VIEWPORT)
                .content(MusicTrackTable.builder(List.of(target, source), placement, Component::text,
                        _ -> (_, _) -> fail("Placement cannot open details"), (_, _) -> fail("Placement cannot play"),
                        _ -> Ui.button(Component.text("Save")).key("add").build()).build()).build();
        for (String key : List.of("select", "details")) {
            if (key.equals("select")) assertFalse(trackEntry(scene, source, key).interactive());
            else assertFalse(trackEntry(scene, source, key).enabled());
            assertNull(trackEntry(scene, source, key).trigger(FloatingMenuInteraction.PRIMARY));
            trackEntry(scene, target, key).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        }
        assertEquals(List.of(target.id(), target.id()), calls);
        for (String key : List.of("insert", "append", "add", "before")) assertNull(entry(scene, key));
        UiSceneTestSupport.assertDisjoint(SceneGeometryTestSupport.measure(scene, 1.8));
    }

    @Test void wideTablesFitTheTitleButtonAndPlaceMetadataImmediatelyAfterItsColumn() {
        var scene = scene(List.of(titled("A song title", null)), null, new ArrayList<>());
        var frame = SceneGeometryTestSupport.naturalFrame(scene,1);
        var title = UiSceneTestSupport.element(frame,"details");
        var artist = UiSceneTestSupport.element(frame, "artist");
        assertEquals(6,artist.x() - title.x() - title.size().width(),1e-6);
        assertNaturalTitleBounds(title,1);
        assertEquals(FloatingMenuDecoration.Alignment.LEFT, title.node().alignment());
    }

    @Test void ordinaryLongTitlesRemainVisibleAsFontSizeChanges() {
        String title = "长".repeat(24);
        var scene = scene(List.of(titled(title, "Artist")), null, new ArrayList<>());
        for (var scale : FloatingMenuTextScale.values()) {
            var frame = SceneGeometryTestSupport.measure(scene, scale.factor());
            UiSceneTestSupport.assertDisjoint(frame);
            var name = UiSceneTestSupport.element(frame,"details");
            UiSceneTestSupport.assertFullTextAvailable(Component.text(title),name);
            assertNaturalTitleBounds(name,scale.factor());
            assertEquals(1,SceneGeometryTestSupport.textLines(scene,scale.factor(),"details"),scale.toString());
            var natural = SceneGeometryTestSupport.naturalFrame(scene,scale.factor());
            assertEquals(Component.text(title),UiSceneTestSupport.element(natural,"details").node().content().label(),scale.toString());
            var artist = frame.elements().stream().filter(element -> List.of("artist","metadata").contains(element.node().key().value())).findFirst().orElseThrow();
            UiSceneTestSupport.assertFullTextAvailable(Component.text("Artist"),artist);
        }
        assertEquals(title, value(scene, "details"));
        assertEquals("Artist", value(scene, "artist"));
    }

    @Test void metadataColumnsShowArtistTimeAndFormatAsReadOnlyValues() {
        var scene = scene(List.of(described("Music", "Composer", "flac", Duration.ofSeconds(225))), null, new ArrayList<>());
        assertEquals("Music", value(scene, "details"));
        assertEquals("Composer", value(scene, "artist"));
        assertEquals("3:45", value(scene, "duration"));
        assertEquals("FLAC", value(scene, "type"));
        for (String key : List.of("details","artist","duration","type")) assertNotNull(entry(scene,key).tooltip(),
                "single-line metadata keeps its source in help: " + key);
        for (String key : List.of("artist", "duration", "type")) assertFalse(entry(scene, key).interactive());
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var previous = UiSceneTestSupport.element(frame, "details");
        for (String key : List.of("artist", "duration", "type", "insert", "append", "add")) {
            var current = UiSceneTestSupport.element(frame, key);
            assertTrue(current.x() >= previous.x() + previous.size().width());
            previous = current;
        }
    }

    @Test void durationsSupportSecondsMinutesHoursAndUnknownValues() {
        for (var expected : Map.of(1L, "0:01", 59L, "0:59", 60L, "1:00", 3599L, "59:59", 3600L, "1:00:00", 3661L, "1:01:01").entrySet()) {
            var scene = scene(List.of(described("Music", null, "nbs", Duration.ofSeconds(expected.getKey()))), null, new ArrayList<>());
            assertEquals(expected.getValue(), value(scene, "duration"));
            assertEquals("NBS", value(scene, "type"));
        }
        var missing = scene(List.of(titled("Music", null)), null, new ArrayList<>());
        assertEquals("—", value(missing, "artist"));
        assertEquals("—", value(missing, "duration"));
    }

    @Test void onlineTracksShowTheirKindWithoutClaimingAnUndeterminedAudioFormat() {
        var source = described("Online song", "Artist", "LX/WY", Duration.ofSeconds(60));
        var online = new MusicTrack(source.id(), source.details(), new TrackTarget.Lx("wy", "1", List.of(), "{}"));
        var scene = scene(List.of(online), null, new ArrayList<>());
        assertEquals("type-online", value(scene, "type"));
    }

    @Test void narrowListsKeepEveryActionAndTheFullNameInHelp() {
        var scene = scene(List.of(titled("长".repeat(24), "Artist")), null, new ArrayList<>())
                .toBuilder().viewport(new UiSize(360, 2048)).build();
        UiSceneTestSupport.assertDisjoint(SceneGeometryTestSupport.measure(scene, 1));
        assertEquals(1,SceneGeometryTestSupport.textLines(scene,1,"details"));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(entry(scene,"details").tooltip().text()).startsWith("长".repeat(24)));
        assertEquals(4,scene.measured(SceneGeometryTestSupport.measure(scene,1)).entries().values().stream().filter(MenuScene.Entry::interactive).count());
    }

    @Test void aShortFirstSongDoesNotLimitLaterNamesOrChangePaginationAfterReordering() throws Exception {
        var shortSong = MusicWorkspaceStateTest.track("short");
        var named = described("月光奏鸣曲与遥远星空中的一段旋律现场演奏珍藏版本","Composer","flac",Duration.ofSeconds(225));
        var tracks = new ArrayList<MusicTrack>(); tracks.add(shortSong); tracks.add(named);
        for (int index = 2; index < 36; index++) tracks.add(MusicWorkspaceStateTest.track("song:" + index));
        for (var language : org.encinet.mik.shared.i18n.Language.values()) {
            var bundle = org.encinet.mik.shared.i18n.FluentTestSupport.bundle(language);
            java.util.function.Function<String,Component> label = key -> Component.text(org.encinet.mik.shared.i18n.FluentTestSupport.text(bundle,
                    org.encinet.mik.shared.i18n.Message.MUSIC_SPACE_LABEL,key));
            for (double font : new double[]{.7,1,1.8}) for (int width : new int[]{360,960}) {
                UiFrame previous = null;
                for (var order : List.of(tracks,tracks.reversed())) {
                    var table = MusicTrackTable.builder(order,null,label,_ -> (_,_) -> {},(_,_) -> {},
                            _ -> Ui.button(label.apply("add-playlist")).key("add").primary((_,_) -> {}).build()).containing(named.id()).build();
                    var scene = MenuScene.screen("long-titles").viewport(new UiSize(width,260)).content(table).build();
                    var frame = SceneGeometryTestSupport.naturalFrame(scene,font);
                    String detail = language + " font=" + font + " width=" + width;
                    UiSceneTestSupport.assertDisjoint(frame);
                    var title = rowElement(frame,named,"details");
                    UiSceneTestSupport.assertFullTextAvailable(Component.text(named.details().title()),title);
                    assertNaturalTitleBounds(title,font);
                    if (width == 960 || font == .7) assertEquals(Component.text(named.details().title()),title.node().content().label(),detail);
                    if (!Component.text(named.details().title()).equals(title.node().content().label()))
                        assertTrue(title.size().width() > width * .8,detail + ": long titles use the available width");
                    for (String key : List.of("insert","append","add")) assertNotNull(rowElement(frame,named,key),detail);
                    if (width == 360) {
                        assertEquals(Component.text("Composer"),rowElement(frame,named,"metadata").node().content().label(),detail);
                        assertEquals(Component.text("3:45"),rowElement(frame,named,"duration").node().content().label(),detail);
                    }
                    if (previous != null) {
                        assertEquals(previous.pagination().getFirst().page().pageSize(),frame.pagination().getFirst().page().pageSize(),detail);
                        assertEquals(rowElement(previous,named,"details").path(),title.path(),detail);
                    }
                    previous = frame;
                }
            }
        }
    }

    @Test void exceptionallyLongTitlesFillTheAvailableLineAndRetainTheirSourceAndDetailsAction() {
        var title = "星空与音乐🎵".repeat(60);
        var song = titled(title,"Composer");
        var calls = new ArrayList<String>();
        var scene = scene(List.of(song),null,calls).toBuilder().viewport(new UiSize(360,260)).build();
        var frame = SceneGeometryTestSupport.naturalFrame(scene,1);
        var name = rowElement(frame,song,"details");
        assertNotEquals(Component.text(title),name.node().content().label());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(name.node().content().label()).endsWith("…"));
        assertEquals(1,SceneGeometryTestSupport.naturalTextLines(scene,1,"details"));
        assertTrue(name.size().width() > 340);
        assertNaturalTitleBounds(name,1);
        UiSceneTestSupport.assertFullTextAvailable(Component.text(title),name);
        scene.measured(frame).entries().get(name.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of("inspect:" + song.id()),calls);
    }

    @Test void libraryShellKeepsLongTitlesReadableBesideNavigationAndAboveThePlayer() {
        var song = titled("月光奏鸣曲与遥远星空中的一段旋律现场演奏珍藏版本","Composer");
        var table = MusicTrackTable.builder(List.of(song),null,Component::text,_ -> (_,_) -> {},(_,_) -> {},
                _ -> Ui.button(Component.text("Save")).key("add").primary((_,_) -> {}).build()).build();
        var browser = UiTemplates.reading(MusicWorkspaceComposition.collection(Ui.heading(Component.text("Catalog")).key("heading").build(),
                null,Ui.text(Component.text("Search songs")).key("query").build()).child(table).build(),table.key(),UiLength.ui(8));
        var scene = MenuScene.screen("library-shell").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                .content(MusicWorkspaceComposition.content(
                        MusicLibraryScenes.navigation(MusicWorkspaceState.Source.CATALOG,Component::text,_ -> (_,_) -> {}),browser,null,
                        Ui.button(Component.text("Playback target")).key("output").build(),Ui.button(Component.text("Close")).key("close").build())).build();
        for (double font : new double[]{.7,1,1.8}) {
            var frame = SceneGeometryTestSupport.readableFrame(scene,font,FloatingMenuFieldOfView.DEFAULT,1);
            UiSceneTestSupport.assertDisjoint(frame);
            var title = rowElement(frame,song,"details");
            UiSceneTestSupport.assertFullTextAvailable(Component.text(song.details().title()),title);
            assertNaturalTitleBounds(title,font);
            assertTrue(UiSceneTestSupport.element(frame,"output").y() >= rowElement(frame,song,"details").y());
        }
    }

    @Test void shortSongButtonsKeepTheirNaturalBoundsEvenBesideLongSongsInCompactAndWideLists() {
        var shortSong = titled("A",null);
        var described = described("长".repeat(60),"Composer","flac",Duration.ofSeconds(225));
        var longSong = new MusicTrack("long",described.details(),described.target());
        for (double font : new double[]{.7,1,1.8}) for (int width : new int[]{360,1280}) {
            for (var songs : List.of(List.of(shortSong),List.of(shortSong,longSong))) for (int mode = 0; mode < 3; mode++) {
                MusicTableSelection<MusicTrack> selection = switch (mode) {
                    case 1 -> MusicTableSelection.selecting(_ -> false,_ -> (_,_) -> {});
                    case 2 -> MusicTableSelection.moving(_ -> false,_ -> (_,_) -> {});
                    default -> null;
                };
                var table = MusicTrackTable.builder(songs,selection,Component::text,_ -> (_,_) -> {},(_,_) -> {},
                        _ -> Ui.button(Component.text("Save")).key("add").primary((_,_) -> {}).build()).build();
                var scene = MenuScene.screen("content-sized-titles").viewport(new UiSize(width,260)).content(table).build();
                var frame = SceneGeometryTestSupport.naturalFrame(scene,font);
                UiSceneTestSupport.assertDisjoint(frame);
                var title = rowElement(frame,shortSong,"details");
                assertEquals(Component.text("A"),title.node().content().label());
                assertNaturalTitleBounds(title,font);
                if (songs.size() > 1) assertEquals(title.x(),rowElement(frame,longSong,"details").x(),1e-6);
            }
        }
    }

    @Test void aLongerVisibleSongExpandsItsColumnAndTheTableBeyondTheOldWidthCap() {
        var shortSong = titled("A",null);
        var described = described("长".repeat(60),"Composer","flac",Duration.ofSeconds(225));
        var longSong = new MusicTrack("long",described.details(),described.target());
        for (double font : new double[]{.7,1}) {
            var shortScene = scene(List.of(shortSong),null,new ArrayList<>());
            var longScene = scene(List.of(shortSong,longSong),null,new ArrayList<>());
            var shortFrame = SceneGeometryTestSupport.naturalFrame(shortScene,font);
            var longFrame = SceneGeometryTestSupport.naturalFrame(longScene,font);
            var title = rowElement(longFrame,longSong,"details");
            assertEquals(Component.text(longSong.details().title()),title.node().content().label());
            assertNaturalTitleBounds(title,font);
            assertTrue(longFrame.size().width() > shortFrame.size().width());
            assertTrue(title.size().width() > rowElement(longFrame,shortSong,"details").size().width());
            if (font == 1) assertTrue(longFrame.size().width() > 960,"content can grow within the host's 1280-unit limit");
        }
    }

    private static void assertNaturalTitleBounds(UiFrame.Element title, double font) {
        var reference = Ui.button(title.node().content().label()).key("natural-title").noWrap()
                .style(title.node().style()).primary((_,_) -> {}).build();
        var frame = SceneGeometryTestSupport.naturalFrame(MenuScene.screen("natural-title").viewport(new UiSize(4096,2048)).content(reference).build(),font);
        var expected = UiSceneTestSupport.element(frame,"natural-title").size();
        assertEquals(expected.width(),title.size().width(),1e-6,title.path() + ": horizontal padding");
        assertEquals(expected.height(),title.size().height(),1e-6,title.path() + ": vertical padding");
    }

    @Test void directPlaybackActionsFreezeTheOrderedSongsAndSubmitWithoutAnExtraPanel() {
        var songs = new ArrayList<>(List.of(MusicWorkspaceStateTest.track("b"), MusicWorkspaceStateTest.track("a")));
        var calls = new ArrayList<String>();
        var scene = MenuScene.screen("actions").content(MusicPlaybackActions.buttons(songs, Component::text,
                (tracks, placement) -> calls.add(placement + ":" + tracks.stream().map(MusicTrack::id).toList()))).build();
        songs.clear();
        assertEquals(2, scene.entries().size());
        for (String action : List.of("insert", "append")) entry(scene,action)
                .trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of("INSERT:[b, a]", "APPEND:[b, a]"), calls);
    }

    private MenuScene scene(List<MusicTrack> tracks, Set<String> selected, List<String> calls) {
        var scene = MenuScene.screen("tracks").viewport(MusicWorkspaceComposition.VIEWPORT).content(MusicTrackTable.builder(tracks, selected == null ? null : MusicTableSelection.selecting(track -> selected.contains(track.id()), track -> (_, _) -> calls.add("select:" + track.id())), Component::text,
                track -> (_, _) -> calls.add("inspect:" + track.id()), (songs, placement) -> calls.add(placement + ":" + songs.getFirst().id()),
                track -> Ui.button(Component.text("add-playlist")).key("add").primary((_, _) -> calls.add("add:" + track.id())).build()).build()).build();
        return scene.measured(SceneGeometryTestSupport.measure(scene,1));
    }

    private MenuScene.Entry trackEntry(MenuScene scene, MusicTrack track, String action) {
        var frame = SceneGeometryTestSupport.measure(scene,1);
        return scene.measured(frame).entries().get(rowElement(frame,track,action).path());
    }

    private static boolean inRow(UiFrame.Element element, MusicTrack track) { return element.path().contains("/" + UiKey.item(track.id()).value() + "/"); }
    private static UiFrame.Element rowElement(UiFrame frame, MusicTrack track, String key) {
        return frame.elements().stream().filter(element -> inRow(element,track) && element.node().key().value().equals(key)).findFirst().orElseThrow();
    }

    private MenuScene.Entry entry(MenuScene scene, String key) {
        var frame = SceneGeometryTestSupport.measure(scene,1);
        return UiSceneTestSupport.entry(scene.measured(frame),frame,key);
    }

    private MusicTrack titled(String title, String artist) {
        return described(title, artist, "mp3", null);
    }

    private MusicTrack described(String title, String artist, String format, Duration duration) {
        var track = MusicWorkspaceStateTest.track("titled");
        return new MusicTrack(track.id(), new TrackDetails(title, artist, null, format, new AudioProperties(null, null, duration)), track.target());
    }

    private String value(MenuScene scene, String key) {
        var entry = entry(scene,key);
        return PlainTextComponentSerializer.plainText().serialize(entry.tooltip() == null ? entry.label()
                : entry.tooltip().fullText().orElse(entry.tooltip().help()));
    }
}
