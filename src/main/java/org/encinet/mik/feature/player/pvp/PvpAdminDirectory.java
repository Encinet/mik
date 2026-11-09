package org.encinet.mik.feature.player.pvp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;

/** Already loaded player summaries; deferred card construction never reads the server roster or settings. */
final class PvpAdminDirectory {
    static final String SCREEN_ID = "pvp-admin";
    record Row(UUID id, ItemStack head, Component label, Component settingsLabel) {
        Row {
            Objects.requireNonNull(id); Objects.requireNonNull(head);
            Objects.requireNonNull(label); Objects.requireNonNull(settingsLabel);
            head = head.clone();
        }
        @Override public ItemStack head() { return head.clone(); }
    }

    record Position(Optional<UUID> anchor, int index) {
        static final Position START = new Position(Optional.empty(), 0);
        Position {
            Objects.requireNonNull(anchor);
            if (index < 0) throw new IllegalArgumentException("Negative player directory position");
        }
        int resolve(List<Row> rows) {
            if (anchor.isPresent()) for (int item = 0; item < rows.size(); item++)
                if (anchor.get().equals(rows.get(item).id())) return item;
            return Math.min(index, Math.max(0, rows.size() - 1));
        }
        static Position at(List<Row> rows, int index) {
            if (rows.isEmpty()) return START;
            int actual = Math.clamp(index, 0, rows.size() - 1);
            return new Position(Optional.of(rows.get(actual).id()), actual);
        }
        Position copy() { return new Position(anchor, index); }
    }

    record Actions(IntConsumer read, BiConsumer<Player, UUID> toggle, BiConsumer<Player, UUID> settings) { }

    private PvpAdminDirectory() { }

    static MenuScene compose(Component title, Component empty, Component back, List<Row> source,
                             Position position, Actions actions) {
        List<Row> rows = List.copyOf(source);
        UiNode cards = Ui.alternative(window(rows, empty, position, actions, false),
                window(rows, empty, position, actions, true));
        UiNode body = UiTemplates.focus(Ui.column().key("directory").gap(UiLength.ui(12))
                .child(Ui.heading(title).key("heading").singleLine().build()).child(cards).build(), cards.key());
        return MenuScene.screen(SCREEN_ID, title).stableAnchor().withoutTitle()
                .content(UiTemplates.withBack(UiTemplates.window("directory", body), back)).build();
    }

    private static UiNode window(List<Row> rows, Component empty, Position position, Actions actions, boolean compact) {
        var caption = Component.text(compact ? "M" : "M\nM");
        UiNode mainTemplate = Ui.button(caption).maxLines(compact ? 1 : 2).build();
        if (!compact && !rows.isEmpty()) mainTemplate = summary(Ui.item(rows.getFirst().head(), Component.empty()).build(), mainTemplate);
        UiNode template = card(UiKey.named("row-template"), mainTemplate, Ui.button(UiIcons.INFO, Component.text("M")).build());
        var window = Ui.windowedList(rows.size(), template, index -> {
            Row row = rows.get(index);
            var main = Ui.button(row.label()).maxLines(compact ? 1 : 2).key("player:" + row.id()).tooltip(row.label())
                    .primary((player, _) -> actions.toggle().accept(player, row.id()))
                    .secondary((player, _) -> actions.settings().accept(player, row.id())).build();
            var details = Ui.button(UiIcons.INFO, row.settingsLabel()).key("settings:" + row.id())
                    .primary((player, _) -> actions.settings().accept(player, row.id())).build();
            UiNode content = main;
            if (!compact) content = summary(Ui.item(row.head(), Component.empty()).key("head:" + row.id()).tooltip(row.label())
                    .primary((player, _) -> actions.toggle().accept(player, row.id()))
                    .secondary((player, _) -> actions.settings().accept(player, row.id())).build(), main);
            return card(UiKey.named("player:" + row.id()), content, details);
        }).key("players").containing(position.resolve(rows)).sizing(new UiSizing(UiSize.ZERO, new UiSize(640, 240)))
                .pager(page -> UiTemplates.pager(page, next -> actions.read().accept(
                        new FloatingMenuPage(next, rows.size(), page.pageSize()).fromIndex())));
        if (rows.isEmpty()) window.header(Ui.text(empty).key("empty").singleLine().build());
        return Ui.windowedGrid(window.build(), 2, UiLength.ui(200), UiLength.ui(12));
    }

    private static UiNode summary(UiNode head, UiNode caption) {
        return Ui.row().key("summary").gap(UiLength.ui(8))
                .child(UiKey.named("head"), head, LinearSlot.fit())
                .child(UiKey.named("caption"), caption, LinearSlot.stretch()).build();
    }

    private static UiNode card(UiKey key, UiNode content, UiNode action) {
        return Ui.row().key(key).gap(UiLength.ui(8))
                .child(UiKey.named("content"), content, LinearSlot.stretch())
                .child(UiKey.named("actions"), action, LinearSlot.fit()).build();
    }
}
