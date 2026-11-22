package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.column;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.info;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.tools;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.ui.*;

/** A single import task: link, preview, then create or merge. The browser stays in place. */
final class MusicImportScenes {
    private final MusicWorkspace w;
    MusicImportScenes(MusicWorkspace workspace) { w = workspace; }

    UiNode content(Player player, MusicWorkspaceState state, Import task) {
        var position = state.sourcePosition;
        var scope = state.panelScope;
        java.util.function.BooleanSupplier owned = () -> w.active(player,state) && scope.equals(state.panelScope) && state.panel() instanceof Import;
        java.util.function.BooleanSupplier active = () -> owned.getAsBoolean() && state.currentReading(task,position);
        java.util.function.Consumer<MusicSourcePosition> browse = next -> {
            if (active.getAsBoolean() && state.readPanel(task,position,next)) w.redraw(player);
        };
        var body = column("import").child(w.panelHeading(player,state, w.label(player, "import")));
        if (task.preview() == null) {
            body.child(w.form(player, "import-link", MenuInputForm.builder(w.label(player, "import"))
                    .field(MenuInputForm.Field.text("link", w.label(player, "link"), state.importLink, 512))
                    .validate((viewer, values) -> values.text("link").isBlank() ? w.problem(viewer, MusicProblem.Code.INVALID) : null)
                    .cancel(w.label(player, "cancel")).submit(w.label(player, "import-preview"),
                            (viewer, values) -> {
                                if (!owned.getAsBoolean() || state.busy(TaskKey.of(Work.IMPORT))) return;
                                state.importLink = values.text("link").strip();
                                load(viewer, state, state.importLink);
                            }).build()));
            var snapshot = w.queries.snapshot(player.getUniqueId());
            var receipts = snapshot.imports().values().stream()
                    .filter(receipt -> snapshot.playlists().containsKey(receipt.playlist()))
                    .sorted(Comparator.comparingLong(org.encinet.mik.feature.entertainment.music.storage.MusicState.ImportReceipt::at).reversed()).toList();
            var entries = receipts.stream().map(receipt -> {
                var list = snapshot.playlists().get(receipt.playlist());
                Component name = w.raw(list.name());
                return new MusicTaskEntries.Row<>(UiKey.item("receipt:" + receipt.id()), list, name, name, false, null);
            }).toList();
            body.child(UiKey.named("import-content"),MusicTaskEntries.content("import-history", entries, w.label(player,"empty"), position, browse,
                    list -> (_,_) -> { if (active.getAsBoolean()) w.browse(player,new Collection(Source.PLAYLIST,list.id())); }));
        } else {
            var preview = task.preview();
            if (task.page() != ImportPage.MERGE) body.child(w.field(player, "import-name:" + preview.request(), "name", preview.name(), 64,
                    name -> {
                        if (!owned.getAsBoolean()) return;
                        if (state.busy(TaskKey.of(Work.IMPORT))) throw new MusicProblem(MusicProblem.Code.BUSY);
                        var current = (Import) state.panel(); var before = current.preview();
                        state.panel(new Import(new Imported(before.reference(), name, before.tracks(),
                            before.partial(), before.request()), current.page())); w.redraw(player); }));
            if (preview.partial()) body.child(Ui.text(w.label(player,"partial-import"))
                    .key(UiKey.item("partial")).summaryLines(2).textWidth(FloatingMenuTextWidth.CONTENT).build());
            if (task.page() == ImportPage.MERGE) {
                var lists = w.playlists.owned(player.getUniqueId());
                if (lists.isEmpty()) body.child(UiKey.named("import-content"),info("no-playlists", w.label(player, "no-playlists")));
                else body.child(UiKey.named("import-content"),MusicPlaylistTable.content(lists.stream()
                                .map(list -> new MusicPlaylistTable.Row(list, false, state.busy(TaskKey.of(Work.IMPORT)) ? w.label(player, "saving") : null)).toList(),
                        key -> w.label(player, key), count -> w.trackCount(player, count),
                        list -> (_, _) -> { if (active.getAsBoolean()) save(player,state, task, list.id()); },state.panelPage,
                        page -> { if (active.getAsBoolean()) { state.panelPage = page; w.redraw(player); } }));
            } else {
                var entries = new ArrayList<MusicTaskEntries.Row<org.encinet.mik.feature.entertainment.music.catalog.MusicTrack>>();
                for (int index = 0; index < preview.tracks().size(); index++) {
                    var track = preview.tracks().get(index);
                    Component title = w.raw(track.details().title());
                    entries.add(new MusicTaskEntries.Row<>(UiKey.item("preview:" + index + ":" + track.id()), track, title, title, false, null));
                }
                body.child(UiKey.named("import-content"),MusicTaskEntries.content("import-preview", entries, w.label(player,"empty"), position, browse, null));
            }
            var actions = tools("import-actions");
            if (!state.busy(TaskKey.of(Work.IMPORT))) {
                var create = Ui.button(w.label(player,"import-new")).key("import-new")
                        .primary((_,_) -> { if (owned.getAsBoolean()) w.safe(player,() -> save(player,state,(Import) state.panel(),null)); });
                var toggle = (task.page() == ImportPage.MERGE
                        ? Ui.button(UiIcons.CHEVRON_LEFT,w.label(player,"import-preview"))
                        : Ui.button(w.label(player,"import-merge"))).key("import-view")
                        .primary((_,_) -> { if (owned.getAsBoolean() && !state.busy(TaskKey.of(Work.IMPORT))) {
                            var current = (Import) state.panel();
                            w.panel(player,state,new Import(current.preview(),current.page() == ImportPage.MERGE ? ImportPage.SOURCE : ImportPage.MERGE));
                        } });
                var retry = Ui.button(UiIcons.REFRESH,w.label(player,"retry-import")).key("retry-import")
                        .primary((_,_) -> { if (owned.getAsBoolean()) w.safe(player,() -> {
                            var current = ((Import) state.panel()).preview(); load(player,state,current.reference());
                        }); });
                actions.child(create.build()).child(toggle.build()).child(retry.build());
            }
            body.child(actions.children(w.taskStatus(player,state,TaskKey.of(Work.IMPORT))).build());
        }
        if (task.preview() == null) body.children(w.taskStatus(player,state,TaskKey.of(Work.IMPORT)));
        return UiTemplates.reading(body.build(),UiKey.named("import-content"),UiLength.ui(8));
    }
    void load(Player player, String reference) {
        var state = w.panel(player,w.state(player),new Import(null,ImportPage.SOURCE));
        load(player,state,reference);
    }
    private void load(Player player, MusicWorkspaceState state, String reference) {
        if (state.busy(TaskKey.of(Work.IMPORT))) return;
        w.acquire(player);
        state.panel(new Import(null, ImportPage.SOURCE));
        long generation = ++state.importGeneration;
        w.run(player, TaskKey.of(Work.IMPORT), () -> w.gateway.previewCollection(reference), result -> {
            if (!w.owns(player,state) || state.importGeneration != generation || !(state.panel() instanceof Import)) return;
            state.panel(new Import(new Imported(reference, result.title().isBlank() ? w.text(player, "import-preview") : limitTitle(result.title()),
                    result.tracks(), result.partial(), UUID.randomUUID()), ImportPage.SOURCE));
        });
    }
    private static String limitTitle(String title) {
        return title.substring(0, title.offsetByCodePoints(0, Math.min(64, title.codePointCount(0, title.length()))));
    }

    private void save(Player player, MusicWorkspaceState state, Import task, UUID destination) {
        if (!w.active(player,state) || state.panel() != task || state.busy(TaskKey.of(Work.IMPORT))) return;
        var preview = task.preview();
        String name = preview.name();
        w.run(player, TaskKey.of(Work.IMPORT), () -> w.playlists.imported(player.getUniqueId(), destination, name, preview.tracks(),
                preview.partial(), preview.request()), w.library.openSavedPlaylist(player));
    }
}
