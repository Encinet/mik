package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** Content-sized room information, a shared player table and personal preparation. */
final class MusicRhythmComposition {
    record DeviceRow(UUID device, Component name, Component position, Component coordinates, boolean existing, Component restriction) { }
    record Participant(UUID player, Component name, Component status) { }

    private MusicRhythmComposition() { }

    static UiNode directory(List<DeviceRow> source, Function<String, Component> label, Function<UUID, FloatingMenuCommand> enter,
                            MusicSourcePosition position, java.util.function.Consumer<MusicSourcePosition> browse) {
        List<DeviceRow> rows = List.copyOf(source);
        Component name = label.apply("name"), location = label.apply("device-location");
        Component join = label.apply("join-room"), create = label.apply("create-room");
        boolean widerJoin = UiTextMetrics.measure(join, Integer.MAX_VALUE).widthPixels()
                >= UiTextMetrics.measure(create, Integer.MAX_VALUE).widthPixels();
        var prototype = new DeviceRow(new UUID(0, 0), Component.text("M"), Component.text("M"), Component.empty(), widerJoin, null);
        Function<DeviceRow, UiNode> action = row -> {
            Component title = row.existing() ? join : create;
            var entry = Ui.button(title).key("enter-room").singleLine().role(UiRole.NAVIGATION)
                    .tooltip(title.append(Component.newline()).append(details(row))).primary(enter.apply(row.device()));
            if (row.restriction() != null) entry.disabled(row.restriction());
            return entry.build();
        };
        var full = Ui.<DeviceRow>table().key(UiKey.named("rhythm-devices")).gaps(UiLength.ui(8), UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(960, 260)))
                .column(new UiTable.Column<>(UiKey.named("name"), heading(name), UiTrack.fraction(1),
                        row -> Ui.text(row.name()).key("device").singleLine().tooltip(row.name())
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .column(new UiTable.Column<>(UiKey.named("location"), heading(location), UiTrack.fraction(2),
                        row -> Ui.text(row.position()).key("position").singleLine().tooltip(details(row))
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .column(new UiTable.Column<>(UiKey.named("entry"), Ui.text(Component.empty()).build(), UiTrack.content(), action));
        var compact = Ui.<DeviceRow>table().key(UiKey.named("rhythm-devices")).gaps(UiLength.ui(4), UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(960, 260)))
                .column(new UiTable.Column<>(UiKey.named("card"), heading(name), UiTrack.fraction(1), row ->
                        Ui.column().gap(UiLength.ui(4))
                                .child(Ui.text(row.name()).key("device").singleLine().tooltip(row.name()).build())
                                .child(Ui.text(row.position()).key("position").singleLine().tooltip(details(row)).build())
                                .child(action.apply(row)).build()));
        return Ui.alternative(window(full, rows, prototype, DeviceRow::device, position, browse),
                window(compact, rows, prototype, DeviceRow::device, position, browse));
    }

    static UiNode participants(List<Participant> source, Function<String, Component> label,
                               MusicSourcePosition position, java.util.function.Consumer<MusicSourcePosition> browse) {
        List<Participant> rows = List.copyOf(source);
        Component name = label.apply("room-players"), status = label.apply("room-player-status");
        var prototype = new Participant(new UUID(0, 0), Component.text("M"), Component.text("M"));
        var full = Ui.<Participant>table().key(UiKey.named("room-players")).gaps(UiLength.ui(12), UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(480, 220)))
                .column(new UiTable.Column<>(UiKey.named("name"), heading(name), UiTrack.fraction(1),
                        row -> Ui.text(row.name()).key("player").singleLine().tooltip(row.name())
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build()))
                .column(new UiTable.Column<>(UiKey.named("status"), heading(status), UiTrack.fraction(1),
                        row -> Ui.text(row.status()).key("player-status").singleLine().tooltip(row.status())
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build()));
        var compact = Ui.<Participant>table().key(UiKey.named("room-players")).gaps(UiLength.ui(4), UiLength.ui(4))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(480, 220)))
                .column(new UiTable.Column<>(UiKey.named("card"), heading(name), UiTrack.fraction(1), row ->
                        Ui.column().gap(UiLength.ui(4))
                                .child(Ui.text(row.name()).key("player").singleLine().tooltip(row.name()).build())
                                .child(Ui.text(row.status()).key("player-status").singleLine().tooltip(row.status()).build()).build()));
        return Ui.alternative(window(full, rows, prototype, Participant::player, position, browse),
                window(compact, rows, prototype, Participant::player, position, browse));
    }

    private static <T> UiNode window(UiTable<T> table, List<T> rows, T prototype, Function<T, UUID> identity,
                                     MusicSourcePosition position, java.util.function.Consumer<MusicSourcePosition> browse) {
        if (rows.isEmpty()) return table.rows(List.of(), row -> UiKey.item(identity.apply(row).toString())).build();
        return table.windowed(rows.size(), prototype, rows::get, row -> UiKey.item(identity.apply(row).toString()))
                .containing(position.resolve(rows, row -> UiKey.item(identity.apply(row).toString()))).pager((page, _) -> UiTemplates.pager(page, next -> {
                    int index = new org.encinet.mik.shared.menu.FloatingMenuPage(next, rows.size(), page.pageSize()).fromIndex();
                    browse.accept(MusicSourcePosition.at(rows, index, row -> UiKey.item(identity.apply(row).toString())));
                }));
    }

    private static Component details(DeviceRow row) {
        return row.name().append(Component.newline()).append(row.position()).append(Component.newline()).append(row.coordinates());
    }

    static UiNode summary(Component label, Component song, Component readiness) {
        return Ui.column().key("room-summary").gap(UiLength.ui(4))
                .child(Ui.text(label).key("label").singleLine().build())
                .child(Ui.row().key("status").gap(UiLength.ui(10))
                        .child(Ui.text(song).key("room-song").singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(),
                                new LinearSlot(2,LinearSlot.Alignment.START))
                        .child(Ui.text(readiness).key("readiness").singleLine().build(),new LinearSlot(1,LinearSlot.Alignment.END)).build()).build();
    }

    static UiNode preparation(Function<String, Component> label, UiElement mode, UiElement difficulty, UiNode actions) {
        return Ui.column().key("room-preparation").gap(UiLength.ui(8))
                .child(heading(label.apply("room-preparation")), new LinearSlot(0, LinearSlot.Alignment.START))
                .child(Ui.row().key("preferences").gap(UiLength.ui(12))
                        .child(Ui.column().key("mode").gap(UiLength.ui(4))
                                .child(Ui.text(label.apply("room-mode")).singleLine().build()).child(mode.withSummaryLines(1)).build(),new LinearSlot(0,LinearSlot.Alignment.START))
                        .child(Ui.column().key("difficulty").gap(UiLength.ui(4))
                                .child(Ui.text(label.apply("room-difficulty")).singleLine().build()).child(difficulty.withSummaryLines(1)).build(),new LinearSlot(0,LinearSlot.Alignment.START)).build())
                .child(actions).build();
    }

    static UiNode content(UiNode heading, UiNode summary, UiNode roster, UiNode preparation, List<UiElement> actions) {
        var body = Ui.layout(UiLayouts.contentColumn(UiKey.named("room-body"),UiLength.ui(8))).key("room")
                .child(heading, LinearSlot.stretch()).child(summary, LinearSlot.stretch())
                .child(UiKey.named("room-body"),preparation == null ? roster : Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(16)),
                        UiLayouts.contentColumn(UiKey.named("roster"),UiLength.ui(8))))
                        .child(UiKey.named("roster"),roster,new LinearSlot(0,LinearSlot.Alignment.START))
                        .child(UiKey.named("preparation"),preparation,new LinearSlot(0,LinearSlot.Alignment.START)).build(), LinearSlot.stretch());
        if (!actions.isEmpty()) body.child(Ui.layout(UiLayouts.adaptiveGrid(2,UiLength.ui(8),UiLength.ui(8))).key("room-actions")
                .children(actions.stream().map(action -> action.withSummaryLines(1)).toList()).build(),LinearSlot.fit());
        return body.build();
    }

    private static UiNode heading(Component text) {
        return Ui.heading(text).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
