package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** The same compact device directory in the workspace and the target picker. */
final class MusicDeviceTable {
    record Row(UUID id, Component name, Component kind, Component status, Component song, Component restriction,
               Component location, Component coordinates, int color) { }
    private MusicDeviceTable() { }

    static UiNode content(List<Row> rows, UUID current, Set<UUID> members, Function<String, Component> label,
                          Function<UUID,FloatingMenuCommand> choose, int page, java.util.function.IntConsumer selectPage) {
        return content(rows, current, members, label, choose, page, selectPage, null);
    }

    static UiNode content(List<Row> rows, UUID current, Set<UUID> members, Function<String, Component> label,
                          Function<UUID,FloatingMenuCommand> choose, int page, java.util.function.IntConsumer selectPage,
                          Function<UUID,FloatingMenuCommand> manage) {
        var snapshot = List.copyOf(rows);
        var selectedMembers = members == null ? null : Set.copyOf(members);
        var table = Ui.<Row>table().key(UiKey.named("device-list")).gaps(UiLength.ui(6),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(1280,260)));
        if (selectedMembers != null) table.column(new UiTable.Column<>(UiKey.named("selected"),Ui.text(Component.empty()).build(),
                UiTrack.intrinsic(),row -> marker(row,selectedMembers,label,choose.apply(row.id()))));
        table.column(new UiTable.Column<>(UiKey.named("name"), heading(label.apply("name")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(240)), row -> {
                    var identity = Ui.column().key("device").gap(UiLength.ui(4)).child(identity(row,current,selectedMembers,choose,manage));
                    if (selectedMembers == null) identity.child(text("kind",row.kind()),LinearSlot.stretch());
                    return identity.build();
                }))
                .column(new UiTable.Column<>(UiKey.named("status"), heading(label.apply("device-status")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(180)), row -> status(row, current, selectedMembers, label)))
                .column(new UiTable.Column<>(UiKey.named("location"), heading(label.apply("device-location")),
                        UiTrack.intrinsic(), row -> Ui.text(row.location()).key("location").noWrap()
                        .tooltip(row.coordinates()).alignment(FloatingMenuDecoration.Alignment.LEFT).build()));
        if (selectedMembers == null) table.column(new UiTable.Column<>(UiKey.named("song"), heading(label.apply("current-track")),
                UiTrack.content(UiLength.ZERO, UiLength.ui(280)), row -> text("song", row.song())));
        if (manage != null) table.column(new UiTable.Column<>(UiKey.named("actions"), heading(label.apply("device-actions")),
                UiTrack.intrinsic(), row -> actions(row, current, label, choose, manage)));
        var compact = Ui.<Row>table().key(UiKey.named("device-list")).gaps(UiLength.ui(4),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(1280,260)))
                .column(new UiTable.Column<>(UiKey.named("card"),heading(label.apply("name")),UiTrack.fraction(1),row -> {
                    var name = identity(row, current, selectedMembers, choose, manage);
                    var identity = Ui.row().gap(UiLength.ui(4));
                    if (selectedMembers != null) identity.child(marker(row,selectedMembers,label,choose.apply(row.id())));
                    identity.child(name);
                    var card = Ui.column().gap(UiLength.ui(4)).child(identity.build());
                    if (selectedMembers == null) card.child(text("kind",row.kind()));
                    card.child(status(row, current, selectedMembers, label))
                            .child(Ui.text(row.location()).key("location").singleLine().tooltip(row.coordinates())
                                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build());
                    if (selectedMembers == null) card.child(text("song",row.song()));
                    if (manage != null) card.child(actions(row, current, label, choose, manage));
                    return card.build();
                }));
        Function<Row,UiKey> identity = row -> UiKey.item(row.id().toString());
        if (snapshot.isEmpty()) return table.rows(List.of(),identity).paged(page,selectPage);
        var window = compact.windowed(snapshot.size(),snapshot.getFirst(),snapshot::get,identity).page(page,selectPage);
        // Full-position columns need all bounded rows to establish their intrinsic width.
        return snapshot.size() <= UiTable.MAX_ROWS ? Ui.alternative(table.rows(snapshot,identity).paged(page,selectPage),window) : window;
    }
    private static UiNode identity(Row row, UUID current, Set<UUID> members, Function<UUID,FloatingMenuCommand> choose,
                                   Function<UUID,FloatingMenuCommand> manage) {
        var name = row.name().color(net.kyori.adventure.text.format.TextColor.color(row.color()));
        boolean selected = members == null ? row.id().equals(current) : members.contains(row.id());
        var node = manage == null ? button(row, name, selected, members != null, choose.apply(row.id())) : Ui.text(name);
        return node.key("target").singleLine().tooltip(metadata(row)).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
    private static UiNode status(Row row, UUID current, Set<UUID> members, Function<String,Component> label) {
        var status = members == null && row.id().equals(current)
                ? label.apply("current-device").append(Component.text(" · ")).append(row.status()) : row.status();
        return text("status",status);
    }
    private static UiNode actions(Row row, UUID current, Function<String,Component> label,
                                  Function<UUID,FloatingMenuCommand> choose, Function<UUID,FloatingMenuCommand> manage) {
        return MusicWorkspace.tools("device-actions")
                .child(button(row,label.apply(row.id().equals(current) ? "current-device" : "use-device"),row.id().equals(current),false,choose.apply(row.id()))
                        .key("use").singleLine().build())
                .child(Ui.button(label.apply("manage-device")).key("manage").singleLine().primary(manage.apply(row.id())).build()).build();
    }
    private static UiElement marker(Row row, Set<UUID> members, Function<String,Component> label, FloatingMenuCommand action) {
        boolean selected = members.contains(row.id());
        var marker = Ui.button(selected ? UiIcons.CHECKED_BOX : UiIcons.BOX,label.apply(selected ? "deselect" : "select-toggle"))
                .key("select").selected(selected).primary(action);
        if (!selected && row.restriction() != null) marker.disabled(row.restriction());
        return marker.build();
    }
    private static Component metadata(Row row) {
        return row.name().append(Component.newline()).append(row.kind()).append(Component.newline()).append(row.status())
                .append(Component.newline()).append(row.location()).append(Component.newline()).append(row.coordinates()).append(Component.newline()).append(row.song());
    }
    private static Ui.ElementBuilder button(Row row, Component text, boolean selected, boolean membership, FloatingMenuCommand action) {
        var button = Ui.button(text).selected(selected).primary(action);
        if ((!membership || !selected) && row.restriction() != null) button.disabled(row.restriction());
        return button;
    }
    private static UiNode heading(Component value) { return Ui.heading(value).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(); }
    private static UiNode text(String key, Component value) {
        return Ui.text(value).key(key).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
