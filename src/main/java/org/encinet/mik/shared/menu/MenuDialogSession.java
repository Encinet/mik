package org.encinet.mik.shared.menu;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** A native dialog's actions share one page-owned lease, without an editing deadline. */
public final class MenuDialogSession implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final Player viewer;
    private final UUID playerId;
    private final String nonce = UUID.randomUUID().toString();
    private final Map<Key, BiConsumer<Player, DialogResponseView>> actions = new HashMap<>();
    private final Runnable invalidated;
    private MenuInputLease lease;
    private boolean responding, ended;

    MenuDialogSession(JavaPlugin plugin, Player viewer) { this(plugin, viewer, () -> { }); }

    MenuDialogSession(JavaPlugin plugin, Player viewer, Runnable invalidated) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.playerId = viewer.getUniqueId();
        this.invalidated = invalidated;
    }

    public DialogAction action(BiConsumer<Player, DialogResponseView> action) {
        if (lease != null || ended) throw new IllegalStateException("Dialog actions must be declared before showing it");
        Key key = Key.key("mik", "menu/" + nonce + "/" + actions.size());
        actions.put(key, java.util.Objects.requireNonNull(action));
        return DialogAction.customClick(key, null);
    }

    public DialogAction cancelAction() { return action((_, _) -> { }); }

    @EventHandler public void onClick(PlayerCustomClickEvent event) {
        Runnable submit = () -> {
            if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)
                    || !playerId.equals(connection.getPlayer().getUniqueId())) return;
            var action = actions.get(event.getIdentifier());
            if (action == null) return;
            if (!plugin.isEnabled() || !viewer.isOnline() || ended || lease == null) return;
            responding = true;
            try {
                if (lease.complete()) action.accept(viewer, event.getDialogResponseView());
            } finally {
                responding = false;
            }
        };
        if (Bukkit.isPrimaryThread()) submit.run();
        else Bukkit.getScheduler().runTask(plugin, submit);
    }

    public void show(Dialog dialog) {
        if (lease != null || ended) throw new IllegalStateException("Dialog session has already been shown");
        lease = FloatingMenus.beginConfirmation(viewer, this::finish);
        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            viewer.showDialog(dialog);
        } catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    private void finish() {
        if (ended) return;
        ended = true;
        HandlerList.unregisterAll(this);
        actions.clear();
        if (!responding) invalidated.run();
    }

    @Override public void close() {
        if (lease == null) { finish(); return; }
        if (!ended && lease.active()) {
            try { viewer.closeDialog(); }
            finally { lease.close(); }
        }
    }
}
