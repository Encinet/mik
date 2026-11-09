package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** Content-sized song controls; narrow windows move secondary information below the title. */
final class MusicTrackTable {
    private MusicTrackTable() { }

    private record Paging(int page, java.util.function.IntConsumer select, String anchor, UiPageControls controls) { }

    static Builder builder(List<MusicTrack> tracks, MusicTableSelection<MusicTrack> selection, Function<String,Component> label,
                           Function<MusicTrack,FloatingMenuCommand> inspect,
                           BiConsumer<List<MusicTrack>,PlaybackProgram.Submission> submit, Function<MusicTrack,UiElement> save) {
        return new Builder(tracks,selection,label,inspect,submit,save);
    }

    static UiPageControls selectionControls(List<MusicTrack> tracks, java.util.Set<String> selected, Function<String,Component> label,
                                            java.util.function.Consumer<List<MusicTrack>> choose) {
        var snapshot = List.copyOf(tracks); var chosen = java.util.Set.copyOf(selected);
        return (page,select) -> {
            var visible = page.slice(snapshot);
            var selectPage = Ui.button(UiIcons.CHECKED_BOX,label.apply("select-page")).key("select-page");
            if (visible.stream().allMatch(track -> chosen.contains(track.id()))) selectPage.disabled(label.apply("selected"));
            else selectPage.primary((_,_) -> choose.accept(visible));
            return Ui.column().key("track-pages").gap(UiLength.ui(2)).child(UiTemplates.pager(page,select)).child(selectPage.build()).build();
        };
    }

    static final class Builder {
        private final List<MusicTrack> tracks;
        private final MusicTableSelection<MusicTrack> selection;
        private final Function<String,Component> label;
        private final Function<MusicTrack,FloatingMenuCommand> inspect;
        private final BiConsumer<List<MusicTrack>,PlaybackProgram.Submission> submit;
        private final Function<MusicTrack,UiElement> save;
        private int page;
        private java.util.function.IntConsumer select = _ -> {};
        private String anchor;
        private UiPageControls controls = UiTemplates::pager;

        private Builder(List<MusicTrack> tracks, MusicTableSelection<MusicTrack> selection, Function<String,Component> label,
                        Function<MusicTrack,FloatingMenuCommand> inspect, BiConsumer<List<MusicTrack>,PlaybackProgram.Submission> submit,
                        Function<MusicTrack,UiElement> save) {
            this.tracks = List.copyOf(tracks); this.selection = selection; this.label = label;
            this.inspect = inspect; this.submit = submit; this.save = save;
        }
        Builder page(int index, java.util.function.IntConsumer read) { page = index; select = java.util.Objects.requireNonNull(read); anchor = null; return this; }
        Builder containing(String track) { anchor = track; return this; }
        Builder pager(UiPageControls pager) { controls = java.util.Objects.requireNonNull(pager); return this; }
        UiNode build() { return content(tracks,selection,label,inspect,submit,save,new Paging(page,select,anchor,controls)); }
    }

    private static UiNode content(List<MusicTrack> tracks, MusicTableSelection<MusicTrack> selection, Function<String, Component> label,
                          Function<MusicTrack, FloatingMenuCommand> inspect,
                          BiConsumer<List<MusicTrack>, PlaybackProgram.Submission> submit,
                          Function<MusicTrack, UiElement> save, Paging paging) {
        var table = Ui.<MusicTrack>table().key(UiKey.named("tracks")).gaps(UiLength.ui(6),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(UiSizing.CONTENT.maximum().width(),260)));
        if (selection != null) {
            table.column(new UiTable.Column<>(UiKey.named("selection"), selection.header(label.apply(selection.moving() ? "move-hint" : "select-song")),
                    UiTrack.intrinsic(), row -> selection.marker(row,label)));
        }
        table.column(new UiTable.Column<>(UiKey.named("name"), heading(label.apply("song-title")),
                        UiTrack.intrinsic(), track -> {
                    var name = title(track,selection,inspect,label)
                            .singleLine().key("details")
                            .textWidth(1024).alignment(FloatingMenuDecoration.Alignment.LEFT);
                    return name.build();
                }))
                .column(new UiTable.Column<>(UiKey.named("artist"), heading(label.apply("artist")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(160)), track -> text("artist", track.details().artist())))
                .column(new UiTable.Column<>(UiKey.named("duration"), heading(label.apply("duration")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(100)), track -> text("duration", track.details().audio().duration() == null
                                ? "—" : MusicTime.format(track.details().audio().duration().toMillis()))))
                .column(new UiTable.Column<>(UiKey.named("type"), heading(label.apply("track-type")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(100)), track -> {
                    Component type = track.target() instanceof TrackTarget.Lx
                            ? label.apply("type-online") : Component.text(track.details().format().toUpperCase(Locale.ROOT));
                    return text("type", type);
                }));
        if (selection == null) for (var placement : PlaybackProgram.Submission.values()) {
            String key = MusicPlaybackActions.labelKey(placement);
            table.column(new UiTable.Column<>(UiKey.named(key), Ui.text(Component.empty()).build(),
                    UiTrack.content(UiLength.ui(24), UiLength.ui(144)),
                    track -> completeLabel(MusicPlaybackActions.button(List.of(track),placement,label,submit))));
        }
        if (selection == null) table.column(new UiTable.Column<>(UiKey.named("add"), Ui.text(Component.empty()).build(),
                UiTrack.content(UiLength.ui(24), UiLength.ui(110)),
                track -> completeLabel(save.apply(track))));
        return Ui.alternative(pages(table,tracks,paging),pages(compact(selection,label,inspect,submit,save),tracks,paging));
    }

    private static UiTable<MusicTrack> compact(MusicTableSelection<MusicTrack> selection, Function<String,Component> label,
                                               Function<MusicTrack,FloatingMenuCommand> inspect,
                                               BiConsumer<List<MusicTrack>,PlaybackProgram.Submission> submit,
                                               Function<MusicTrack,UiElement> save) {
        return Ui.<MusicTrack>table().key(UiKey.named("tracks")).gaps(UiLength.ui(4),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(UiSizing.CONTENT.maximum().width(),260)))
                .column(new UiTable.Column<>(UiKey.named("card"),heading(label.apply("song-title")),UiTrack.fraction(1),track -> {
                    var name = title(track,selection,inspect,label).key("details").singleLine()
                            .textWidth(1024).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
                    UiNode title = selection == null ? name : Ui.row().gap(UiLength.ui(4))
                            .child(selection.marker(track,label)).child(name).build();
                    var metadata = Ui.text(artist(track)).key("metadata").singleLine().tooltip(metadata(track,label))
                            .textWidth(1024).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
                    var footer = Ui.row().gap(UiLength.ui(6)).child(metadata,LinearSlot.grow(1))
                            .child(Ui.text(Component.text(duration(track))).key("duration").noWrap().build());
                    var actions = Ui.row().gap(UiLength.ui(4));
                    if (selection == null) {
                        for (var placement : PlaybackProgram.Submission.values()) actions.child(icon(MusicPlaybackActions.button(List.of(track),placement,label,submit),
                                placement == PlaybackProgram.Submission.INSERT ? UiIcons.INSERT_BEFORE : UiIcons.QUEUE));
                        actions.child(icon(save.apply(track),UiIcons.ADD));
                        footer.child(actions.build());
                    }
                    return Ui.column().gap(UiLength.ZERO)
                            .child(title,new LinearSlot(0,LinearSlot.Alignment.START))
                            .child(footer.build(),LinearSlot.stretch()).build();
                }));
    }

    private static Ui.ElementBuilder title(MusicTrack track, MusicTableSelection<MusicTrack> selection,
                                          Function<MusicTrack,FloatingMenuCommand> inspect, Function<String,Component> label) {
        var title = Component.text(track.details().title());
        return (selection == null ? Ui.button(title).role(UiRole.NAVIGATION).primary(inspect.apply(track)) : selection.control(track,title))
                .tooltip(UiTooltip.of(metadata(track,label)).withFullText(title));
    }

    private static UiNode heading(Component label) {
        return Ui.heading(label).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }

    private static UiNode text(String key, String value) {
        return text(key, Component.text(value == null ? "—" : value));
    }

    private static UiNode text(String key, Component value) {
        return Ui.text(value).singleLine().key(key).textWidth(1024)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }

    private static UiNode pages(UiTable<MusicTrack> table, List<MusicTrack> tracks, Paging paging) {
        Function<MusicTrack,UiKey> identity = track -> UiKey.item(track.id());
        if (tracks.isEmpty()) return table.rows(List.of(),identity).paged(paging.page(),paging.select());
        var window = table.windowed(tracks.size(),tracks.getFirst(),tracks::get,identity).fitVisibleColumns()
                .page(paging.page(),paging.select()).pager(paging.controls());
        if (paging.anchor() != null) for (int index = 0; index < tracks.size(); index++)
            if (tracks.get(index).id().equals(paging.anchor())) return window.containing(index);
        return window;
    }

    private static UiElement completeLabel(UiElement control) {
        return control.content() instanceof UiContent.Text text ? representation(control,new UiContent.Text(text.label(),Integer.MAX_VALUE,false)) : control;
    }
    private static UiElement icon(UiElement control, UiIcon icon) { return representation(control,new UiContent.Icon(icon,control.content().label())); }
    private static UiElement representation(UiElement control, UiContent content) {
        var input = control.input();
        if (input.tooltip().isEmpty()) input = new UiInput(input.target(),input.actions(),input.focus(),input.selected(),input.disabledReason(),
                java.util.Optional.of(UiTooltip.of(control.content().label())));
        return new UiElement(control.key(),content,control.role(),input,control.style(),control.sizing(),control.textWidthPixels(),control.alignment(),control.keepAccessible());
    }
    private static Component metadata(MusicTrack track, Function<String,Component> label) {
        var type = track.target() instanceof TrackTarget.Lx ? label.apply("type-online") : Component.text(track.details().format().toUpperCase(Locale.ROOT));
        return label.apply("duration").append(Component.text(": " + duration(track)))
                .append(Component.newline()).append(label.apply("track-type")).append(Component.text(": ")).append(type)
                .append(Component.newline()).append(label.apply("artist")).append(Component.text(": ")).append(artist(track));
    }

    private static Component artist(MusicTrack track) { return Component.text(track.details().artist() == null ? "—" : track.details().artist()); }
    private static String duration(MusicTrack track) {
        return track.details().audio().duration() == null ? "—" : MusicTime.format(track.details().audio().duration().toMillis());
    }

}
