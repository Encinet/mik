package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.application.MusicGroupService.MAX_MEMBERS;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.application.MusicSubmission;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;

/** View state: library selection, one playback target and a separate membership draft. */
public final class MusicWorkspaceState {
    enum Source { CATALOG, RECENT, OWNED, PUBLIC, FAVORITES, PLAYLIST }
    record Collection(Source source, UUID playlist) {
        Collection {
            Objects.requireNonNull(source);
            if ((source == Source.PLAYLIST) != (playlist != null)) throw new IllegalArgumentException("Playlist identity");
        }
        static Collection of(Source source) { return new Collection(source, null); }
    }
    static final class Browser {
        Collection collection = Collection.of(Source.CATALOG);
        Collection parent = Collection.of(Source.OWNED);
        String query = "";
        String trackFocus;
        MusicMove move;
        int page;
        long revision;
        boolean selecting;
        final Set<String> selected = new HashSet<>();

        void open(Collection next) {
            if (collection.equals(next)) return;
            revision++;
            if (next.source() == Source.PLAYLIST && collection.source() != Source.PLAYLIST) parent = collection;
            collection = next;
            query = "";
            move = null;
            page = 0;
            trackFocus = null;
            selecting = false;
            selected.clear();
        }
        void query(String value) {
            if (query.equals(value)) return;
            revision++;
            query = value;
            page = 0;
            trackFocus = null;
            if (move == null) selected.clear();
        }
        void toggle(String id) { if (!selected.remove(id)) selected.add(id); }
        boolean page(long sourceRevision, int index) {
            if (revision != sourceRevision) return false;
            page = Math.max(0,index); trackFocus = null; return true;
        }
        List<MusicTrack> selected(List<MusicTrack> displayedOrder) {
            return displayedOrder.stream().filter(track -> selected.contains(track.id())).toList();
        }
        List<MusicTrack> playbackTracks(List<MusicTrack> displayedOrder) {
            return selecting ? selected(displayedOrder) : List.copyOf(displayedOrder);
        }
        Source navigationSource() { return collection.source() == Source.PLAYLIST ? parent.source() : collection.source(); }
    }

    enum WorkspacePage { LIBRARY, DEVICES }
    enum DeviceFilter { ALL, RECORDED, NEARBY, GROUPS }
    record PendingPlay(List<MusicTrack> tracks, PlaybackProgram.Submission placement) {
        PendingPlay { tracks = List.copyOf(tracks); Objects.requireNonNull(placement); }
    }
    sealed interface Panel permits NoPanel, Outputs, Track, DevicePanel, MemberOutput, GroupEditor, PlaylistSettings, Import, RoomPicker, Room, Confirmation { }
    record NoPanel() implements Panel { }
    record Outputs(PendingPlay pending) implements Panel { }
    record Track(List<MusicTrack> tracks) implements Panel { Track { tracks = List.copyOf(tracks); } }
    enum DeviceSection { QUEUE, MEMBERS, SETTINGS }
    record DevicePanel(UUID target, DeviceSection section) implements Panel {
        DevicePanel { Objects.requireNonNull(target); Objects.requireNonNull(section); }
    }
    record MemberOutput(UUID group, UUID member, MusicSourcePosition returnPosition) implements Panel {
        MemberOutput { Objects.requireNonNull(group); Objects.requireNonNull(member); Objects.requireNonNull(returnPosition); }
    }
    static final class GroupDraft {
        final UUID group;
        final PendingPlay pending;
        final Set<UUID> members = new LinkedHashSet<>();
        String name = "";
        UUID source;
        boolean pickingInWorld;
        Panel returnTo;
        GroupDraft(UUID group, PendingPlay pending) {
            this.group = group;
            this.pending = pending;
            returnTo = group == null ? new NoPanel() : new DevicePanel(group,DeviceSection.MEMBERS);
        }
        void toggle(UUID device) {
            if (!members.remove(device)) {
                if (members.size() == MAX_MEMBERS) throw new MusicProblem(MusicProblem.Code.LIMIT);
                members.add(device);
            }
            if (!members.contains(source)) source = members.stream().findFirst().orElse(null);
        }
        void pick(UUID device, int existingMembers) {
            if (members.contains(device)) return;
            if (members.size() + existingMembers >= MAX_MEMBERS) throw new MusicProblem(MusicProblem.Code.LIMIT);
            toggle(device);
        }
    }
    record GroupEditor(GroupDraft draft) implements Panel { }
    record PlaylistSettings(UUID playlist) implements Panel { }
    record Imported(String source, String reference, String name, List<MusicTrack> tracks, int total, UUID request) {
        Imported { tracks = List.copyOf(tracks); }
    }
    enum ImportPage { SOURCE, PLATFORMS, MERGE }
    record Import(Imported preview, ImportPage page) implements Panel { }
    record RoomPicker() implements Panel { }
    record Room(UUID device) implements Panel { }
    sealed interface Decision permits DeletePlaylist, ClearRecents, EndRoom { }
    record DeletePlaylist(UUID id, long version, String name) implements Decision { }
    record ClearRecents() implements Decision { }
    record EndRoom(UUID device) implements Decision { }
    record Confirmation(Decision decision, Panel returnTo) implements Panel { }

    enum Work { SEARCH, IMPORT, LIBRARY, QUEUE, DEVICE, PLAYBACK }
    record TaskKey(Work work, UUID subject) { static TaskKey of(Work work) { return new TaskKey(work, null); } }
    record Task(long ticket, boolean busy, MusicProblem.Code error) { }
    final Browser browser = new Browser();
    private UUID target;
    WorkspacePage page = WorkspacePage.LIBRARY;
    int devicePage;
    DeviceFilter deviceFilter = DeviceFilter.ALL;
    private Panel panel = new NoPanel();
    UUID panelScope = UUID.randomUUID();
    int panelPage;
    MusicSourcePosition sourcePosition = MusicSourcePosition.START;
    record DeviceIdentityView(boolean expanded) { }
    DeviceIdentityView deviceIdentity = new DeviceIdentityView(false);
    final Set<UUID> queueSelection = new HashSet<>();
    MusicMove queueMove;
    UUID queueFocus;
    final Map<TaskKey, Task> tasks = new HashMap<>();
    List<MusicTrack> results = List.of();
    long searchGeneration;
    int searchFailures;
    long importGeneration;
    String importLink = "";
    long sequence;
    MusicSubmission lastSubmission;
    UUID target() { return target; }
    Panel panel() { return panel; }
    void startPlaylistMove(MusicMove move) {
        if (page != WorkspacePage.LIBRARY || !new Collection(Source.PLAYLIST, move.owner()).equals(browser.collection))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        browser.revision++;
        browser.move = Objects.requireNonNull(move); queueMove = null;
    }
    void startQueueMove(MusicMove move) {
        if (!(panel instanceof DevicePanel inspector)
                || !inspector.equals(new DevicePanel(move.owner(), DeviceSection.QUEUE)))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        queueMove = Objects.requireNonNull(move); browser.move = null;
    }
    void cancelMoves() { browser.move = null; queueMove = null; }
    void browse(Collection collection) {
        page = WorkspacePage.LIBRARY;
        browser.open(collection);
        boolean dismissTask = panel instanceof GroupEditor || panel instanceof Track || panel instanceof PlaylistSettings || panel instanceof Import
                || panel instanceof Confirmation confirmation && (confirmation.decision() instanceof DeletePlaylist
                        || confirmation.decision() instanceof ClearRecents);
        if (dismissTask) panel(new NoPanel());
    }
    void browsePlaylist(UUID id, Source source) {
        Collection parent = Collection.of(source);
        browse(new Collection(Source.PLAYLIST, id));
        if (!browser.parent.equals(parent)) {
            browser.parent = parent;
            browser.revision++;
        }
    }
    void target(UUID next) {
        if (Objects.equals(target, next)) return;
        target = next;
        lastSubmission = null;
    }
    void devices() {
        if (page != WorkspacePage.DEVICES) browser.revision++;
        page = WorkspacePage.DEVICES;
        browser.move = null;
        panel(new NoPanel());
    }
    void rooms(UUID current) {
        browser.revision++;
        page = WorkspacePage.LIBRARY;
        browser.move = null;
        panel(current == null ? new RoomPicker() : new Room(current));
    }
    void panel(Panel next) {
        if (panel instanceof Import && !(next instanceof Import)) {
            importGeneration++;
            tasks.remove(TaskKey.of(Work.IMPORT));
        }
        boolean sameTask = panel.equals(next)
                || panel instanceof Outputs old && next instanceof Outputs current && Objects.equals(old.pending(), current.pending())
                || panel instanceof Import old && next instanceof Import current && old.page() == current.page()
                        && old.preview() != null && current.preview() != null
                        && old.preview().request().equals(current.preview().request());
        if (!sameTask) {
            panelScope = UUID.randomUUID();
            panelPage = 0;
            sourcePosition = MusicSourcePosition.START;
            deviceIdentity = new DeviceIdentityView(false);
            queueFocus = null;
            if (!(panel instanceof DevicePanel old && next instanceof DevicePanel current
                    && old.target().equals(current.target()) && old.section() == current.section())) {
                queueSelection.clear();
                queueMove = null;
            }
        }
        panel = Objects.requireNonNull(next);
    }
    boolean currentReading(Panel owner, MusicSourcePosition source) { return panel == owner && sourcePosition == source; }
    boolean toggleDeviceIdentity(Panel owner, DeviceIdentityView view) {
        if (panel != owner || deviceIdentity != view) return false;
        deviceIdentity = new DeviceIdentityView(!view.expanded());
        return true;
    }
    boolean readPanel(Panel owner, MusicSourcePosition source, MusicSourcePosition next) {
        if (!currentReading(owner, source)) return false;
        sourcePosition = Objects.requireNonNull(next);
        return true;
    }
    boolean returnToMembers(MemberOutput source) {
        if (panel != source) return false;
        panel(new DevicePanel(source.group(), DeviceSection.MEMBERS));
        sourcePosition = source.returnPosition();
        return true;
    }
    long begin(TaskKey key) {
        Task current = tasks.get(key);
        if (current != null && current.busy()) throw new MusicProblem(MusicProblem.Code.BUSY);
        long ticket = ++sequence;
        tasks.put(key, new Task(ticket, true, null));
        return ticket;
    }
    boolean busy(TaskKey key) { return tasks.containsKey(key) && tasks.get(key).busy(); }
    boolean finish(TaskKey key, long ticket, MusicProblem.Code error) {
        Task current = tasks.get(key);
        if (current == null || current.ticket() != ticket) return false;
        tasks.put(key, new Task(ticket, false, error));
        return true;
    }
}
