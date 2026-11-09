package org.encinet.mik.feature.world.vehicle;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.ui.*;

/** Already loaded editor rows keep their source position when the reading capacity changes. */
final class VehicleEditorList {
    record Item(UiKey key, Component label, FloatingMenuCommand choose) {
        Item { Objects.requireNonNull(key); Objects.requireNonNull(label); Objects.requireNonNull(choose); }
    }

    private VehicleEditorList() { }

    static UiNode content(List<Item> source, Component empty, int anchor, IntConsumer read) {
        List<Item> items = List.copyOf(source);
        var window = Ui.windowedList(items.size(), Ui.button(Component.text("M")).singleLine().build(), index -> {
            Item item = items.get(index);
            return Ui.button(item.label()).key(item.key()).singleLine().tooltip(item.label())
                    .primary(item.choose()).build();
        }).key("items").containing(anchor).sizing(new UiSizing(UiSize.ZERO, new UiSize(560, 240)))
                .pager(page -> UiTemplates.pager(page, next -> read.accept(
                        new FloatingMenuPage(next, items.size(), page.pageSize()).fromIndex())));
        if (items.isEmpty()) window.header(Ui.text(empty).key("empty").singleLine().build());
        return window.build();
    }
}
