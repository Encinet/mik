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
        var body = column("import").child(w.panelHeading(player, w.label(player, "import")));
        if (task.preview() == null) {
            body.child(w.form(player, "import-link", MenuInputForm.builder(w.label(player, "import"))
                    .field(MenuInputForm.Field.text("link", w.label(player, "link"), state.importLink, 512))
                    .validate((viewer, values) -> {
                        try { MusicPlaylistLink.source(values.text("link")); return null; }
                        catch (MusicProblem error) { return w.problem(viewer, error.code()); }
                    }).cancel(w.label(player, "cancel")).submit(w.label(player, "import-preview"),
                            (viewer, values) -> {
                                if (!owned.getAsBoolean() || state.busy(TaskKey.of(Work.IMPORT))) return;
                                state.importLink = values.text("link").strip();
                                load(viewer, MusicPlaylistLink.source(state.importLink), state.importLink);
                            }).build()));
            if (task.page() != ImportPage.PLATFORMS) body.child(w.button(player, "import-by-id", () -> { if (owned.getAsBoolean()) w.panel(player, new Import(null, ImportPage.PLATFORMS)); }));
            else {
                var platforms = tools("platforms");
                String[][] sources = {{"wy", "网易云音乐"}, {"tx", "QQ Music"}, {"kg", "Kugou"}, {"kw", "Kuwo"}, {"mg", "Migu"}};
                for (var source : sources) platforms.child(w.button(player, "platform:" + source[0], w.raw(source[1]),
                        () -> { if (owned.getAsBoolean()) w.input(player, "playlist-id", "", 128, id -> { if (owned.getAsBoolean()) load(player, source[0], id); }); }));
                body.child(platforms.build());
            }
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
                        state.panel(new Import(new Imported(before.source(), before.reference(), name, before.tracks(),
                            before.total(), before.request()), current.page())); w.redraw(player); }));
            if (preview.tracks().size() < preview.total()) body.child(Ui.text(w.label(player,"partial-import"))
                    .key(UiKey.item("partial")).summaryLines(2).textWidth(FloatingMenuTextWidth.CONTENT).build());
            if (task.page() == ImportPage.MERGE) {
                var lists = w.playlists.owned(player.getUniqueId());
                if (lists.isEmpty()) body.child(UiKey.named("import-content"),info("no-playlists", w.label(player, "no-playlists")));
                else body.child(UiKey.named("import-content"),MusicPlaylistTable.content(lists.stream()
                                .map(list -> new MusicPlaylistTable.Row(list, false, state.busy(TaskKey.of(Work.IMPORT)) ? w.label(player, "saving") : null)).toList(),
                        key -> w.label(player, key), count -> w.trackCount(player, count),
                        list -> (_, _) -> { if (active.getAsBoolean()) save(player, task, list.id()); },state.panelPage,
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
                        .primary((_,_) -> { if (owned.getAsBoolean()) w.safe(player,() -> save(player,(Import) state.panel(),null)); });
                var toggle = (task.page() == ImportPage.MERGE
                        ? Ui.button(UiIcons.CHEVRON_LEFT,w.label(player,"import-preview"))
                        : Ui.button(w.label(player,"import-merge"))).key("import-view")
                        .primary((_,_) -> { if (owned.getAsBoolean() && !state.busy(TaskKey.of(Work.IMPORT))) {
                            var current = (Import) state.panel();
                            w.panel(player,new Import(current.preview(),current.page() == ImportPage.MERGE ? ImportPage.SOURCE : ImportPage.MERGE));
                        } });
                var retry = Ui.button(UiIcons.REFRESH,w.label(player,"retry-import")).key("retry-import")
                        .primary((_,_) -> { if (owned.getAsBoolean()) w.safe(player,() -> {
                            var current = ((Import) state.panel()).preview(); load(player,current.source(),current.reference());
                        }); });
                actions.child(create.build()).child(toggle.build()).child(retry.build());
            }
            body.child(actions.children(w.taskStatus(player,state,TaskKey.of(Work.IMPORT))).build());
        }
        if (task.preview() == null) body.children(w.taskStatus(player,state,TaskKey.of(Work.IMPORT)));
        return UiTemplates.reading(body.build(),UiKey.named("import-content"),UiLength.ui(8));
    }
    void load(Player player, String source, String reference) {
        var state = w.state(player);
        if (state.busy(TaskKey.of(Work.IMPORT))) return;
        w.acquire(player);
        w.panel(player, new Import(null, ImportPage.SOURCE));
        long generation = ++state.importGeneration;
        w.run(player, TaskKey.of(Work.IMPORT), () -> w.sources.importPlaylist(source, reference), result -> {
            if (state.importGeneration != generation || !(state.panel() instanceof Import)) return;
            state.panel(new Import(new Imported(result.source(), reference, result.name(), result.tracks(), result.total(), UUID.randomUUID()), ImportPage.SOURCE));
        });
    }
    private void save(Player player, Import task, UUID destination) {
        var state = w.state(player);
        if (!w.active(player,state) || state.panel() != task || state.busy(TaskKey.of(Work.IMPORT))) return;
        var preview = task.preview();
        String name = preview.name();
        w.run(player, TaskKey.of(Work.IMPORT), () -> w.playlists.imported(player.getUniqueId(), destination, name, preview.tracks(),
                preview.source(), preview.reference(), preview.total(), preview.request()), w.library.openSavedPlaylist(player));
    }
}
