package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.encinet.mik.shared.menu.ui.UiKey;
import static org.junit.jupiter.api.Assertions.*;

class MusicWorkspaceStateTest {
    @Test void deviceIdentityExpansionRejectsRetiredSnapshotsAndResetsWhenTheTaskChanges() {
        var state = new MusicWorkspaceState(); UUID target = UUID.randomUUID();
        var owner = new DevicePanel(target,DeviceSection.SETTINGS); state.panel(owner);
        var closed = state.deviceIdentity;
        assertTrue(state.toggleDeviceIdentity(owner,closed)); var open = state.deviceIdentity;
        assertTrue(open.expanded()); assertFalse(state.toggleDeviceIdentity(owner,closed));
        assertTrue(state.toggleDeviceIdentity(owner,open)); assertFalse(state.deviceIdentity.expanded());
        assertFalse(state.toggleDeviceIdentity(owner,closed),"An old collapsed view cannot toggle a later collapsed view");
        assertTrue(state.toggleDeviceIdentity(owner,state.deviceIdentity));
        state.panel(new DevicePanel(target,DeviceSection.SETTINGS));
        assertTrue(state.deviceIdentity.expanded(),"A same-task refresh retains expansion");
        assertFalse(state.toggleDeviceIdentity(owner,state.deviceIdentity),"A retired panel cannot change the current one");
        state.panel(new DevicePanel(target,DeviceSection.QUEUE)); assertFalse(state.deviceIdentity.expanded());
        state.panel(owner); assertFalse(state.deviceIdentity.expanded());
        state.panel(new DevicePanel(UUID.randomUUID(),DeviceSection.SETTINGS)); assertFalse(state.deviceIdentity.expanded());
    }
    @Test void taskInputNamespacesSurviveDraftEditsButAreRetiredWhenTheirTaskEnds() {
        var state = new MusicWorkspaceState(); UUID room = UUID.randomUUID();
        state.panel(new Room(room)); UUID first = state.panelScope;
        state.panel(new Room(room)); assertEquals(first, state.panelScope);
        state.panel(new Room(UUID.randomUUID())); assertNotEquals(first, state.panelScope);
        UUID second = state.panelScope;
        state.panel(new NoPanel()); assertNotEquals(second, state.panelScope);
        state.panel(new Room(room)); assertNotEquals(first, state.panelScope);
        var request = new Imported("wy", "123", "Original", List.of(track("song")), 1, UUID.randomUUID());
        state.panel(new Import(request, ImportPage.SOURCE)); UUID importing = state.panelScope;
        state.panel(new Import(new Imported(request.source(), request.reference(), "Renamed", request.tracks(), request.total(), request.request()), ImportPage.SOURCE));
        assertEquals(importing, state.panelScope, "Editing a draft must retain its field ownership");
    }
    @Test void sourceReadingBelongsToTheCurrentPaneAndOnlyTheLatestPageCanNavigate() {
        var state = new MusicWorkspaceState(); var owner = new RoomPicker();
        state.panel(owner); var first = state.sourcePosition;
        var next = new MusicSourcePosition(UiKey.item(UUID.randomUUID().toString()), 12);
        assertTrue(state.readPanel(owner, first, next));
        assertFalse(state.readPanel(owner, first, new MusicSourcePosition(UiKey.item(UUID.randomUUID().toString()), 24)));
        assertSame(next, state.sourcePosition);
        state.panel(new RoomPicker());
        assertSame(next, state.sourcePosition, "The same task retains its source bookmark");
        assertFalse(state.readPanel(owner, next, MusicSourcePosition.START), "A retired panel instance cannot navigate");
        var room = new Room(UUID.randomUUID()); state.panel(room);
        assertSame(MusicSourcePosition.START, state.sourcePosition);
        assertFalse(state.readPanel(owner, first, next));
        assertTrue(state.readPanel(room, state.sourcePosition, next));
        state.panel(new NoPanel()); assertSame(MusicSourcePosition.START, state.sourcePosition);
        assertFalse(state.readPanel(room, next, first));
    }

    @Test void sourceBookmarksFollowKeysAcrossReorderAndFallBackWhenTheSourceLeaves() {
        var first = UUID.randomUUID(); var second = UUID.randomUUID(); var third = UUID.randomUUID();
        var rows = new ArrayList<>(List.of(first, second, third).stream().map(id -> UiKey.item(id.toString())).toList());
        var position = MusicSourcePosition.at(rows, 1, Function.identity());
        Collections.swap(rows, 0, 1); assertEquals(0, position.resolve(rows, Function.identity()));
        rows.removeFirst(); assertEquals(1, position.resolve(rows, Function.identity()));
        rows.clear(); assertEquals(0, position.resolve(rows, Function.identity()));
        assertSame(MusicSourcePosition.START, MusicSourcePosition.at(rows, Integer.MAX_VALUE, Function.identity()));
    }
    @Test void memberSoundReturnsToItsOriginalMemberBookmarkAndRetiredReturnsAreRejected() {
        var state = new MusicWorkspaceState(); var group = UUID.randomUUID(); var member = UUID.randomUUID();
        var list = new DevicePanel(group, DeviceSection.MEMBERS); state.panel(list);
        var bookmark = new MusicSourcePosition(UiKey.item(member.toString()), 12);
        assertTrue(state.readPanel(list, state.sourcePosition, bookmark));
        var sound = new MemberOutput(group, member, bookmark); state.panel(sound);
        assertSame(MusicSourcePosition.START, state.sourcePosition);
        assertTrue(state.returnToMembers(sound));
        assertEquals(list, state.panel()); assertSame(bookmark, state.sourcePosition);
        assertFalse(state.returnToMembers(sound));
        state.panel(sound); state.panel(new NoPanel());
        assertFalse(state.returnToMembers(sound));
        assertInstanceOf(NoPanel.class, state.panel());
    }
    @Test void movedTrackBookmarksSurviveAuxiliaryTasksAndRetireOnExplicitBrowsing() {
        var state = new MusicWorkspaceState();
        state.browse(new Collection(Source.PLAYLIST,UUID.randomUUID()));
        state.browser.trackFocus = "moved-song";
        long revision = state.browser.revision;
        state.panel(new Track(List.of(track("details"))));
        assertEquals("moved-song",state.browser.trackFocus);
        assertFalse(state.browser.page(revision - 1,3)); assertEquals("moved-song",state.browser.trackFocus);
        assertTrue(state.browser.page(revision,1)); assertNull(state.browser.trackFocus);
        state.browser.trackFocus = "moved-song"; state.browser.query("filter"); assertNull(state.browser.trackFocus);
        state.browser.trackFocus = "moved-song"; state.browse(Collection.of(Source.OWNED)); assertNull(state.browser.trackFocus);
    }

    @Test void queueOccurrenceBookmarksBelongToTheirDeviceInspector() {
        var state = new MusicWorkspaceState();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), occurrence = UUID.randomUUID();
        state.target(first,false); state.panel(new DevicePanel(first,DeviceSection.QUEUE));
        state.queueFocus = occurrence;
        state.panel(new DevicePanel(first,DeviceSection.QUEUE));
        assertEquals(occurrence,state.queueFocus);
        state.panel(new DevicePanel(first,DeviceSection.SETTINGS)); assertNull(state.queueFocus);
        state.panel(new DevicePanel(first,DeviceSection.QUEUE)); state.queueFocus = occurrence;
        state.target(second,false); assertNull(state.queueFocus);
        state.queueFocus = occurrence; state.panel(new NoPanel()); assertNull(state.queueFocus);
    }

    @Test void measuredPageCallbacksCannotChangeANewerCollectionOrQuery() {
        var state = new MusicWorkspaceState();
        state.browse(Collection.of(Source.OWNED));
        long opened = state.browser.revision;
        assertTrue(state.browser.page(opened,2)); assertEquals(2,state.browser.page);
        state.browser.query("new query");
        assertFalse(state.browser.page(opened,3)); assertEquals(0,state.browser.page);
        long queried = state.browser.revision;
        assertTrue(state.browser.page(queried,1));
        state.browse(Collection.of(Source.PUBLIC));
        assertFalse(state.browser.page(queried,2)); assertEquals(0,state.browser.page);
    }

    static MusicTrack track(String id) { return new MusicTrack(id, new TrackDetails(id, null, null, "mp3", AudioProperties.EMPTY), new TrackTarget.LocalFile(Path.of("/tmp/" + id))); }

    @Test void roomDiscoveryReturnsToTheLibraryWithoutChangingPlaybackOrTheSavedBrowser() {
        var state = new MusicWorkspaceState();
        UUID target = UUID.randomUUID(), room = UUID.randomUUID();
        state.browse(Collection.of(Source.RECENT));
        state.browser.query("piano"); state.browser.page = 2;
        state.browser.selecting = true; state.browser.selected.add("song");
        state.target(target, true);
        state.devices();
        state.rooms(null);
        assertEquals(WorkspacePage.LIBRARY, state.page);
        assertInstanceOf(RoomPicker.class, state.panel());
        assertEquals(target, state.target());
        assertEquals(Collection.of(Source.RECENT), state.browser.collection);
        assertEquals("piano", state.browser.query); assertEquals(2, state.browser.page);
        assertEquals(Set.of("song"), state.browser.selected);
        state.rooms(room);
        assertEquals(new Room(room), state.panel());
        assertEquals(target, state.target(), "a room addresses a physical output independently of ordinary playback");
        state.browse(Collection.of(Source.CATALOG));
        assertEquals(new Room(room), state.panel(), "the room stays available while browsing for its next song");
    }

    @Test void multiSelectPlaybackUsesOnlyChosenIdsInTheCurrentOrderAndNeverFallsBackToAllSongs() {
        var state = new MusicWorkspaceState();
        var songs = List.of(track("a"),track("b"),track("c"));
        assertEquals(songs,state.browser.playbackTracks(songs));
        state.browser.selecting = true;
        assertTrue(state.browser.playbackTracks(songs).isEmpty());
        state.browser.toggle("c"); state.browser.toggle("a");
        assertEquals(List.of(songs.get(0),songs.get(2)),state.browser.playbackTracks(songs));
        assertEquals(List.of(songs.get(2),songs.get(0)),state.browser.playbackTracks(List.of(songs.get(2),songs.get(1),songs.get(0))));
        state.browser.query("missing");
        assertTrue(state.browser.selecting);
        assertTrue(state.browser.playbackTracks(songs).isEmpty());
        state.browse(Collection.of(Source.RECENT));
        assertFalse(state.browser.selecting);
        assertEquals(songs,state.browser.playbackTracks(songs));
    }

    @Test void playlistsKeepTheirSourceSelectedAndNewCopiesReturnToMyPlaylists() {
        for (Source source : List.of(Source.OWNED, Source.PUBLIC, Source.FAVORITES)) {
            var state = new MusicWorkspaceState();
            state.browse(Collection.of(source));
            state.browse(new Collection(Source.PLAYLIST, UUID.randomUUID()));
            assertEquals(source, state.browser.navigationSource());
            state.panel(new PlaylistSettings(state.browser.collection.playlist()));
            state.browsePlaylist(UUID.randomUUID(), Source.OWNED);
            assertEquals(Source.OWNED, state.browser.navigationSource());
            assertEquals(Collection.of(Source.OWNED), state.browser.parent);
            assertInstanceOf(NoPanel.class, state.panel());
            state.browse(state.browser.parent);
            assertEquals(Source.OWNED, state.browser.collection.source());
        }
    }

    @Test void collectionNavigationClosesOnlyLibraryTasksAndInvalidatesAClosingImport() {
        UUID id = UUID.randomUUID();
        for (Panel panel : List.of(new Track(List.of(track("a"))), new PlaylistSettings(id),
                new Import(null, ImportPage.SOURCE), new Confirmation(new DeletePlaylist(id, 0, "Name"), new NoPanel()),
                new Confirmation(new ClearRecents(), new NoPanel()))) {
            var state = new MusicWorkspaceState();
            state.target(id, false);
            state.panel(panel);
            var key = TaskKey.of(Work.IMPORT);
            long ticket = state.begin(key);
            state.browse(Collection.of(Source.OWNED));
            assertInstanceOf(NoPanel.class, state.panel());
            assertEquals(id, state.target());
            if (panel instanceof Import) {
                assertEquals(1, state.importGeneration);
                assertFalse(state.finish(key, ticket, null), "a completed import must not reopen a dismissed task");
            }
        }
        for (Panel panel : List.of(new Outputs(null), new DevicePanel(id, DeviceSection.QUEUE), new DevicePanel(id, DeviceSection.SETTINGS), new Room(id))) {
            var state = new MusicWorkspaceState();
            state.target(id, false);
            state.panel(panel);
            state.browse(Collection.of(Source.OWNED));
            assertSame(panel, state.panel(), "browsing music must retain device controls");
            assertEquals(id, state.target());
        }
    }

    @Test void auxiliaryTasksKeepTheCollectionPositionAndFreezeTheirSongs() {
        var state = new MusicWorkspaceState();
        state.browser.query("piano"); state.browser.page = 4;
        var songs = new ArrayList<>(List.of(track("a")));
        state.panel(new Track(songs));
        songs.add(track("b"));
        assertEquals(List.of(track("a")), ((Track) state.panel()).tracks());
        state.panel(new Outputs(new PendingPlay(songs, PlaybackProgram.Submission.APPEND)));
        state.panel(new NoPanel());
        assertEquals("piano", state.browser.query); assertEquals(4, state.browser.page);
    }
    @Test void editingAnImportNameKeepsItsPageAndReceiptIdentity() {
        var state = new MusicWorkspaceState();
        var request = UUID.randomUUID();
        var tracks = List.of(track("a"));
        state.panel(new Import(new Imported("wy", "123", "Original", tracks, 20, request), ImportPage.SOURCE));
        state.panelPage = 3;
        var bookmark = new MusicSourcePosition(UiKey.item("preview:12:a"), 12);
        assertTrue(state.readPanel(state.panel(), state.sourcePosition, bookmark));
        state.panel(new Import(new Imported("wy", "123", "Renamed", tracks, 20, request), ImportPage.SOURCE));
        assertEquals(3, state.panelPage);
        assertSame(bookmark, state.sourcePosition);
        assertEquals("Renamed", ((Import) state.panel()).preview().name());
        state.panel(new Import(((Import) state.panel()).preview(), ImportPage.MERGE));
        assertSame(MusicSourcePosition.START, state.sourcePosition);
        assertEquals(0, state.panelPage, "changing to destination selection starts at its first page");
        state.panelPage = 2;
        state.panel(new Import(new Imported("wy", "123", "Refetched", tracks, 20, UUID.randomUUID()), ImportPage.MERGE));
        assertEquals(0, state.panelPage, "a new import result must not inherit an unrelated page");
    }
    @Test void selectionFollowsDisplayOrderAcrossPagesAndClearsWhenCollectionChanges() {
        var browser = new Browser();
        browser.selecting = true; browser.toggle("c"); browser.toggle("a"); browser.page = 2;
        assertEquals(List.of(track("a"), track("c")), browser.selected(List.of(track("a"), track("b"), track("c"))));
        assertEquals(List.of(track("c"), track("a")), browser.selected(List.of(track("c"), track("b"), track("a"))));
        browser.open(Collection.of(Source.RECENT));
        assertTrue(browser.selected.isEmpty()); assertFalse(browser.selecting); assertEquals(0, browser.page);
    }
    @Test void membershipDraftNeverChangesTheActiveTargetAndCancelDiscardsOnlyTheDraft() {
        var state = new MusicWorkspaceState();
        UUID target = UUID.randomUUID(), member = UUID.randomUUID();
        state.target(target, false);
        var draft = new GroupDraft(null, null);
        state.panel(new GroupEditor(draft));
        draft.toggle(member);
        assertEquals(target, state.target());
        state.panel(new DevicePanel(target, DeviceSection.QUEUE));
        assertEquals(target, state.target());
    }
    @Test void deviceWorkspacePreservesTheLibraryQueryPageAndSongSelection() {
        var state = new MusicWorkspaceState();
        state.browser.query("piano"); state.browser.page = 4;
        state.browser.selecting = true; state.browser.toggle("a");
        state.devices();
        assertEquals(WorkspacePage.DEVICES, state.page);
        assertEquals("piano", state.browser.query);
        assertEquals(4, state.browser.page);
        assertEquals(Set.of("a"), state.browser.selected);
        state.browse(state.browser.collection);
        assertEquals(WorkspacePage.LIBRARY, state.page);
        assertEquals(4, state.browser.page);
        assertTrue(state.browser.selecting);
    }
    @Test void changingTheSingleTargetRebindsTheQueueWithoutChangingTheLibrary() {
        var state = new MusicWorkspaceState();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        state.target(a, false); state.browser.page = 3;
        state.panel(new DevicePanel(a, DeviceSection.QUEUE));
        state.queueSelection.add(UUID.randomUUID());
        state.target(b, false);
        assertEquals(b, state.target());
        assertEquals(new DevicePanel(b, DeviceSection.QUEUE), state.panel());
        assertTrue(state.queueSelection.isEmpty());
        assertEquals(3, state.browser.page);
    }
    @Test void changingTargetsKeepsTheInspectorSectionAndFallsBackOnlyWhenItDoesNotExist() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        for (var section : DeviceSection.values()) {
            var state = new MusicWorkspaceState();
            state.target(a, true);
            state.panel(new DevicePanel(a, section));
            state.target(b, true);
            assertEquals(new DevicePanel(b, section), state.panel());
            state.target(a, false);
            assertEquals(new DevicePanel(a, section == DeviceSection.MEMBERS ? DeviceSection.QUEUE : section), state.panel());
            state.target(null, false);
            assertInstanceOf(NoPanel.class, state.panel());
        }
        var state = new MusicWorkspaceState();
        state.target(a, true);
        state.panel(new MemberOutput(a, UUID.randomUUID(), MusicSourcePosition.START));
        state.target(b, true);
        assertEquals(new DevicePanel(b, DeviceSection.MEMBERS), state.panel());
    }
    @Test void quickTargetChangesRetainUnrelatedTasksAndTheirDrafts() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var draft = new GroupDraft(null, null);
        draft.name = "Living room";
        draft.toggle(UUID.randomUUID());
        for (var task : List.of(new GroupEditor(draft), new Import(null, ImportPage.SOURCE),
                new PlaylistSettings(UUID.randomUUID()), new Track(List.of(track("a"))))) {
            var state = new MusicWorkspaceState();
            state.target(a, false);
            state.panel(task);
            state.panelPage = 2;
            state.target(b, true);
            assertSame(task, state.panel());
            assertEquals(2, state.panelPage);
            assertEquals(b, state.target());
        }
        assertEquals("Living room", draft.name);
        assertEquals(1, draft.members.size());
    }
    @Test void revisitingDevicesKeepsTheCurrentInspectorPageAndMembershipDraft() {
        UUID id = UUID.randomUUID(), selected = UUID.randomUUID();
        for (var task : List.of(new DevicePanel(id, DeviceSection.SETTINGS), new DevicePanel(id, DeviceSection.MEMBERS),
                new GroupEditor(new GroupDraft(null, null)), new MemberOutput(id, UUID.randomUUID(), MusicSourcePosition.START))) {
            var state = new MusicWorkspaceState();
            state.target(id, true);
            state.devices();
            state.panel(task);
            state.panelPage = 3;
            state.queueSelection.add(selected);
            state.devices();
            assertSame(task, state.panel());
            assertEquals(3, state.panelPage);
            assertEquals(Set.of(selected), state.queueSelection);
        }
        var state = new MusicWorkspaceState();
        state.target(id, true);
        var inspector = new DevicePanel(id, DeviceSection.SETTINGS);
        state.panel(inspector);
        state.browse(Collection.of(Source.OWNED));
        state.devices();
        assertSame(inspector, state.panel());
    }
    @Test void leavingTheDeviceDirectoryDismissesItsUnsubmittedMembershipDraft() {
        var state = new MusicWorkspaceState();
        UUID target = UUID.randomUUID(); state.target(target, false); state.devices();
        var draft = new GroupDraft(null, null); draft.toggle(UUID.randomUUID());
        state.panel(new GroupEditor(draft));
        state.browse(state.browser.collection);
        assertEquals(WorkspacePage.LIBRARY, state.page);
        assertInstanceOf(NoPanel.class, state.panel());
        assertEquals(target, state.target());
    }
    @Test void removingTheChosenSourceClearsItAndMembershipDraftsHaveABoundedSize() {
        var draft = new GroupDraft(null, null);
        UUID first = UUID.randomUUID(); draft.toggle(first); draft.source = first;
        draft.toggle(first); assertNull(draft.source);
        for (int i = 0; i < 16; i++) draft.toggle(UUID.randomUUID());
        assertEquals(MusicProblem.Code.LIMIT, assertThrows(MusicProblem.class, () -> draft.toggle(UUID.randomUUID())).code());
        assertEquals(16, draft.members.size());
    }
    @Test void membershipPicksAValidDefaultSourceWithoutChangingThePlaybackTarget() {
        var state = new MusicWorkspaceState();
        UUID active = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        state.target(active,false);
        var draft = new GroupDraft(null,null);
        draft.toggle(first);
        assertEquals(first,draft.source);
        draft.toggle(second);
        assertEquals(first,draft.source);
        draft.toggle(first);
        assertEquals(second,draft.source);
        assertEquals(active,state.target());
        draft.toggle(second);
        assertNull(draft.source);
    }
    @Test void completedSearchCannotReplaceANewerTaskOrResetOtherWork() {
        var state = new MusicWorkspaceState(); var search = TaskKey.of(Work.SEARCH); var save = new TaskKey(Work.LIBRARY, UUID.randomUUID());
        long old = state.begin(search), saving = state.begin(save);
        state.tasks.remove(search); long newer = state.begin(search);
        assertFalse(state.finish(search, old, null)); assertTrue(state.busy(search));
        assertTrue(state.finish(search, newer, null)); assertTrue(state.busy(save));
        assertTrue(state.finish(save, saving, null));
    }
    @Test void placementKeepsItsSelectionAcrossSearchesAndPagesButEndsWhenTheSourceLeaves() {
        var state = new MusicWorkspaceState();
        UUID list = UUID.randomUUID(), first = UUID.randomUUID(), last = UUID.randomUUID();
        state.browsePlaylist(list, Source.OWNED);
        state.browser.selecting = true;
        state.browser.selected.add("first");
        var move = new MusicMove(list, 3, List.of(first, last), Set.of(first));
        state.startPlaylistMove(move);
        state.browser.page = 4;
        state.browser.query("target");
        assertSame(move, state.browser.move);
        assertEquals(Set.of("first"), state.browser.selected);
        assertEquals(Set.of(first), move.selected());
        state.browse(Collection.of(Source.RECENT));
        assertNull(state.browser.move);
        assertTrue(state.browser.selected.isEmpty());
    }
    @Test void playlistAndQueuePlacementAreExclusiveAndFollowTheirOwningViews() {
        var state = new MusicWorkspaceState();
        UUID list = UUID.randomUUID(), device = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var playlistMove = new MusicMove(list, 0, List.of(a, b), Set.of(a));
        var queueMove = new MusicMove(device, 0, List.of(a, b), Set.of(b));
        state.browsePlaylist(list, Source.OWNED);
        state.target(device, false);
        state.panel(new DevicePanel(device, DeviceSection.QUEUE));
        state.startPlaylistMove(playlistMove);
        state.startQueueMove(queueMove);
        assertNull(state.browser.move);
        assertSame(queueMove, state.queueMove);
        state.startPlaylistMove(playlistMove);
        assertNull(state.queueMove);
        state.devices();
        assertNull(state.browser.move);
        state.startQueueMove(queueMove);
        state.panel(new DevicePanel(device, DeviceSection.SETTINGS));
        assertNull(state.queueMove);
        state.panel(new DevicePanel(device, DeviceSection.QUEUE));
        state.startQueueMove(queueMove);
        state.target(UUID.randomUUID(), false);
        assertNull(state.queueMove);
        state.cancelMoves();
        assertNull(state.browser.move);
    }
    @Test void latePlacementActionsCannotAttachToAnUnrelatedCollectionOrDevice() {
        var state = new MusicWorkspaceState();
        UUID owner = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var move = new MusicMove(owner, 0, List.of(a, b), Set.of(a));
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> state.startPlaylistMove(move)).code());
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> state.startQueueMove(move)).code());
    }
}
