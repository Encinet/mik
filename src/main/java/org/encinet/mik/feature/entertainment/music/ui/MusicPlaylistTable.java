package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.ui.*;

/** Playlist browsing and destination picking use the same aligned metadata and stable identities. */
final class MusicPlaylistTable {
    record Row(Playlist playlist, boolean selected, Component restriction) { }
    private MusicPlaylistTable() { }

    static UiNode content(List<Row> rows, Function<String, Component> label, IntFunction<Component> count,
                          Function<Playlist, FloatingMenuCommand> choose, int page, java.util.function.IntConsumer selectPage) {
        var snapshot = List.copyOf(rows);
        var table = Ui.<Row>table().key(UiKey.named("playlists")).gaps(UiLength.ui(8), UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO,new UiSize(640,260)))
                .column(new UiTable.Column<>(UiKey.named("name"), heading(label.apply("playlist")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(480)), row -> {
                    var button = Ui.button(Component.text(row.playlist().name())).key("playlist")
                            .selected(row.selected()).singleLine().textWidth(FloatingMenuTextWidth.CONTENT)
                            .alignment(FloatingMenuDecoration.Alignment.LEFT).primary(choose.apply(row.playlist()));
                    if (row.restriction() != null) button.disabled(row.restriction());
                    return button.build();
                }))
                .column(new UiTable.Column<>(UiKey.named("count"), heading(label.apply("track-count")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(160)), row -> text("count", count.apply(row.playlist().entries().size()))))
                .column(new UiTable.Column<>(UiKey.named("visibility"), heading(label.apply("visibility")),
                        UiTrack.content(UiLength.ZERO, UiLength.ui(160)), row -> text("visibility", label.apply(row.playlist().published() ? "public" : "private"))));
        Function<Row,UiKey> identity = row -> UiKey.item(row.playlist().id().toString());
        return snapshot.isEmpty() ? table.rows(List.of(),identity).paged(page,selectPage)
                : table.windowed(snapshot.size(),snapshot.getFirst(),snapshot::get,identity).page(page,selectPage);
    }

    private static UiNode heading(Component value) {
        return Ui.heading(value).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
    private static UiNode text(String key, Component value) {
        return Ui.text(value).key(key).singleLine().textWidth(FloatingMenuTextWidth.CONTENT)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
