package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.ui.*;

/** A queue row is one occurrence, even when several rows refer to the same song. */
final class MusicQueueTable {
    record Row(QueueEntry entry, StoredTrack track, MusicQueueRows.Section section) {
        Row(QueueEntry entry, StoredTrack track) { this(entry,track,null); }
    }
    private MusicQueueTable() { }

    static UiNode content(List<Row> rows, MusicTableSelection<Row> selection, Function<String,Component> label,
                          int page, java.util.function.IntConsumer selectPage, java.util.UUID anchor, boolean shuffle) {
        var snapshot = List.copyOf(rows);
        var table = Ui.<Row>table().key(UiKey.named("queue-tracks")).gaps(UiLength.ui(6),UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(640,260)));
        if (snapshot.stream().anyMatch(row -> row.section() != null))
            table.column(new UiTable.Column<>(UiKey.named("section"),Ui.text(Component.empty()).build(),UiTrack.content(),
                    row -> Ui.text(row.section() == null ? Component.empty() : label.apply(row.section() == MusicQueueRows.Section.REQUESTS
                            ? "priority-queue" : shuffle ? "shuffle-candidates" : "base-queue")).key("section").singleLine().build()));
        if (selection != null)
            table.column(new UiTable.Column<>(UiKey.named("selection"), selection.header(label.apply(selection.moving() ? "move-hint" : "select-song")), UiTrack.intrinsic(), row -> selection.marker(row,label)));
        table.column(new UiTable.Column<>(UiKey.named("name"), heading(label.apply("song-title")),
                UiTrack.content(UiLength.ZERO, UiLength.ui(360)), row -> {
                    var title = Component.text(row.track() == null ? "—" : row.track().title());
                    var cell = selection == null ? Ui.text(title) : selection.control(row, title);
                    return cell.key("title").singleLine().textWidth(FloatingMenuTextWidth.CONTENT)
                            .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
                }))
                .column(new UiTable.Column<>(UiKey.named("artist"), heading(label.apply("artist")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(160)), row -> text("artist", row.track() == null ? null : row.track().artist())))
                .column(new UiTable.Column<>(UiKey.named("duration"), heading(label.apply("duration")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(100)), row -> text("duration", row.track() == null || row.track().duration() == null
                                ? "—" : MusicTime.format(row.track().duration()))));
        var compact = Ui.<Row>table().key(UiKey.named("queue-tracks")).gaps(UiLength.ui(6),UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(640,260)));
        if (selection != null) compact.column(new UiTable.Column<>(UiKey.named("selection"),
                selection.header(label.apply(selection.moving() ? "move-hint" : "select-song")),UiTrack.intrinsic(),row -> selection.marker(row,label)));
        compact.column(new UiTable.Column<>(UiKey.named("name"),heading(label.apply("song-title")),UiTrack.content(),row -> {
            var title = Component.text(row.track() == null ? "—" : row.track().title());
            var cell = selection == null ? Ui.text(title) : selection.control(row,title);
            var details = Component.empty();
            if (row.section() != null) details = details.append(label.apply(row.section() == MusicQueueRows.Section.REQUESTS
                    ? "priority-queue" : shuffle ? "shuffle-candidates" : "base-queue")).append(Component.newline());
            details = details.append(label.apply("duration")).append(Component.text(": "))
                    .append(Component.text(row.track() == null || row.track().duration() == null ? "—" : MusicTime.format(row.track().duration())));
            details = details.append(Component.newline()).append(label.apply("artist")).append(Component.text(": "))
                    .append(Component.text(row.track() == null || row.track().artist() == null ? "—" : row.track().artist()))
                    .append(Component.newline()).append(title);
            return cell.key("title").singleLine().tooltip(details).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
        }));
        return Ui.alternative(pages(table,snapshot,page,selectPage,anchor),pages(compact,snapshot,page,selectPage,anchor));
    }

    private static UiNode pages(UiTable<Row> table, List<Row> snapshot, int page, java.util.function.IntConsumer selectPage, java.util.UUID anchor) {
        Function<Row,UiKey> identity = row -> UiKey.item(row.entry().id().toString());
        if (snapshot.isEmpty()) return table.rows(List.of(),identity).paged(page,selectPage);
        var result = table.windowed(snapshot.size(),snapshot.getFirst(),snapshot::get,identity).page(page,selectPage);
        if (anchor != null) for (int index = 0; index < snapshot.size(); index++)
            if (snapshot.get(index).entry().id().equals(anchor)) return result.containing(index);
        return result;
    }

    private static UiNode heading(Component value) { return Ui.heading(value).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(); }
    private static UiNode text(String key, String value) {
        return Ui.text(Component.text(value == null ? "—" : value)).key(key).singleLine()
                .textWidth(FloatingMenuTextWidth.CONTENT).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
