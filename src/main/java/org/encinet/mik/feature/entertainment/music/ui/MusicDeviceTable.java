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
        var snapshot = List.copyOf(rows);
        var selectedMembers = members == null ? null : Set.copyOf(members);
        var table = Ui.<Row>table().key(UiKey.named("device-list")).gaps(UiLength.ui(6),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(1280,260)));
        if (selectedMembers != null) table.column(new UiTable.Column<>(UiKey.named("selected"),Ui.text(Component.empty()).build(),
                UiTrack.intrinsic(),row -> marker(row,selectedMembers,label,choose.apply(row.id()))));
        table.column(new UiTable.Column<>(UiKey.named("name"), heading(label.apply("name")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(240)), row -> Ui.column().key("device").gap(UiLength.ui(4))
                        .child(button(row, row.name().color(net.kyori.adventure.text.format.TextColor.color(row.color())),
                                selectedMembers == null ? row.id().equals(current) : selectedMembers.contains(row.id()), choose.apply(row.id()))
                                .key("target").singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch())
                        .child(text("kind", row.kind()), LinearSlot.stretch()).build()))
                .column(new UiTable.Column<>(UiKey.named("status"), heading(label.apply("device-status")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(180)), row -> text("status", row.status())))
                .column(new UiTable.Column<>(UiKey.named("location"), heading(label.apply("device-location")),
                        UiTrack.intrinsic(), row -> Ui.text(row.location()).key("location").noWrap()
                        .tooltip(row.coordinates()).alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .column(new UiTable.Column<>(UiKey.named("song"), heading(label.apply("current-track")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(280)), row -> text("song", row.song())));
        var compact = Ui.<Row>table().key(UiKey.named("device-list")).gaps(UiLength.ui(4),UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(1280,260)))
                .column(new UiTable.Column<>(UiKey.named("card"),heading(label.apply("name")),UiTrack.fraction(1),row -> {
                    boolean selected = selectedMembers == null ? row.id().equals(current) : selectedMembers.contains(row.id());
                    var name = button(row,row.name().color(net.kyori.adventure.text.format.TextColor.color(row.color())),selected,choose.apply(row.id()))
                            .key("target").singleLine().tooltip(metadata(row)).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
                    var identity = Ui.row().gap(UiLength.ui(4));
                    if (selectedMembers != null) identity.child(marker(row,selectedMembers,label,choose.apply(row.id())));
                    identity.child(name);
                    return Ui.column().gap(UiLength.ui(2)).child(identity.build())
                            .child(Ui.text(row.location()).key("location").singleLine().tooltip(row.coordinates())
                                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build()).build();
                }));
        Function<Row,UiKey> identity = row -> UiKey.item(row.id().toString());
        if (snapshot.isEmpty()) return table.rows(List.of(),identity).paged(page,selectPage);
        var window = compact.windowed(snapshot.size(),snapshot.getFirst(),snapshot::get,identity).page(page,selectPage);
        // Full-position columns need all bounded rows to establish their intrinsic width.
        return snapshot.size() <= UiTable.MAX_ROWS ? Ui.alternative(table.rows(snapshot,identity).paged(page,selectPage),window) : window;
    }
    private static UiElement marker(Row row, Set<UUID> members, Function<String,Component> label, FloatingMenuCommand action) {
        boolean selected = members.contains(row.id());
        var marker = Ui.button(selected ? UiIcons.CHECKED_BOX : UiIcons.BOX,label.apply(selected ? "deselect" : "select-toggle"))
                .key("select").selected(selected).primary(action);
        if (!selected && row.restriction() != null) marker.disabled(row.restriction());
        return marker.build();
    }
    private static Component metadata(Row row) {
        return row.status().append(Component.newline()).append(row.kind()).append(Component.newline()).append(row.location())
                .append(Component.newline()).append(row.coordinates()).append(Component.newline()).append(row.song()).append(Component.newline()).append(row.name());
    }
    private static Ui.ElementBuilder button(Row row, Component text, boolean selected, FloatingMenuCommand action) {
        var button = Ui.button(text).selected(selected).primary(action);
        if (!selected && row.restriction() != null) button.disabled(row.restriction());
        return button;
    }
    private static UiNode heading(Component value) { return Ui.heading(value).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(); }
    private static UiNode text(String key, Component value) {
        return Ui.text(value).key(key).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
