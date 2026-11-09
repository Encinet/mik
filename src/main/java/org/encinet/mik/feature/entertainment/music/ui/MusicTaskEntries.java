package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.ui.*;

/** Loaded task summaries; only visible entries build controls, and reading never submits the task. */
final class MusicTaskEntries {
    record Row<T>(UiKey key, T value, Component label, Component help, boolean selected, Component restriction) {
        Row { Objects.requireNonNull(key); Objects.requireNonNull(value); Objects.requireNonNull(label); Objects.requireNonNull(help); }
    }
    private MusicTaskEntries() { }

    /** A null action factory declares a read-only preview. */
    static <T> UiNode content(String key, List<Row<T>> source, Component empty, MusicSourcePosition position,
                              Consumer<MusicSourcePosition> browse, Function<T, FloatingMenuCommand> action) {
        List<Row<T>> rows = List.copyOf(source);
        if (rows.isEmpty()) return Ui.text(empty).key("empty").singleLine().build();
        var prototype = (action == null ? Ui.text(Component.text("M")) : Ui.button(Component.text("M"))).singleLine().build();
        var window = Ui.windowedList(rows.size(), prototype, index -> {
            Row<T> row = rows.get(index);
            var node = (action == null ? Ui.text(row.label()) : Ui.button(row.label())).key(row.key()).singleLine().tooltip(row.help());
            if (action != null) {
                node.primary(action.apply(row.value())).selected(row.selected());
                if (row.restriction() != null) node.disabled(row.restriction());
            }
            return node.build();
        }).key(key).gap(UiLength.ui(6)).containing(position.resolve(rows, Row::key))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(640, 240)))
                .pager(page -> UiTemplates.pager(page, next -> browse.accept(MusicSourcePosition.at(rows,
                        new FloatingMenuPage(next, rows.size(), page.pageSize()).fromIndex(), Row::key))));
        return window.build();
    }
}
