package org.encinet.mik.feature.player.trash;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.player.guidance.Tip;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.guidance.TipTrigger;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrashModule implements Listener {
    static final int RETENTION_SECONDS = 10;
    private static final int DROP_THRESHOLD = 3;
    private static final int DROP_ITEM_THRESHOLD = 8;
    private static final long DROP_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Tip<Void> trashTip;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, DropWindow> recentDrops = new HashMap<>();

    public TrashModule(JavaPlugin plugin, LanguageService languageService, TipScope tips) {
        this.plugin = plugin;
        this.languageService = languageService;
        trashTip = tips.<Void>define("trash-dispose", Message.TRASH_TIP_DISPOSE_MM)
                .on(TipTrigger.event(PlayerDropItemEvent.class).filter(this::frequentDrops))
                .when(player -> {
                    Session session = sessions.get(player.getUniqueId());
                    return session == null || !session.open;
                })
                .build();
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        for (Session session : List.copyOf(sessions.values())) {
            cancelCleanup(session);
            Inventory inventory = session.menu.getInventory();
            inventory.clear();
            for (HumanEntity viewer : List.copyOf(inventory.getViewers())) viewer.closeInventory();
        }
        sessions.clear();
        recentDrops.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("trash")
                                .executes(ctx -> {
                                    if (ctx.getSource().getExecutor() instanceof Player player) {
                                        open(player);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    ctx.getSource().getSender().sendMessage(
                                            languageService.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
                                    return 0;
                                }).build(),
                        languageService.t(Language.DEFAULT, Message.TRASH_COMMAND_DESCRIPTION), List.of("garbage")));
    }

    void open(Player player) {
        UUID playerId = player.getUniqueId();
        Session session = sessions.computeIfAbsent(playerId,
                ignored -> new Session(new TrashInventory(player, languageService)));
        cancelCleanup(session);
        InventoryView view = player.openInventory(session.menu.getInventory());
        if (view != null && view.getTopInventory() == session.menu.getInventory()) {
            // Reopening the current inventory can synchronously fire a close event first.
            cancelCleanup(session);
            session.open = true;
            recentDrops.remove(playerId);
            trashTip.cancel(playerId);
        } else if (!session.open) {
            scheduleCleanup(playerId, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Session session = session(event.getWhoClicked(), event.getInventory());
        if (session == null) return;
        int slot = event.getRawSlot();
        if (slot == TrashInventory.CLEAR_SLOT || slot == TrashInventory.SORT_SLOT) {
            event.setCancelled(true);
            if (event.isLeftClick() || event.isRightClick()) {
                if (slot == TrashInventory.CLEAR_SLOT) session.menu.clearStorage();
                else session.menu.sortStorage();
            }
        } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && session.menu.matchesButton(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (session(event.getWhoClicked(), event.getInventory()) == null) return;
        if (event.getRawSlots().contains(TrashInventory.SORT_SLOT)
                || event.getRawSlots().contains(TrashInventory.CLEAR_SLOT)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = session(event.getPlayer(), event.getInventory());
        if (session != null) scheduleCleanup(event.getPlayer().getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        recentDrops.remove(playerId);
        Session session = sessions.get(playerId);
        if (session != null && session.open) scheduleCleanup(playerId, session);
    }

    private boolean frequentDrops(PlayerDropItemEvent event) {
        return recentDrops.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new DropWindow())
                .record(System.nanoTime(), event.getItemDrop().getItemStack().getAmount());
    }

    private Session session(HumanEntity player, Inventory inventory) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.menu.getInventory() == inventory ? session : null;
    }

    private void scheduleCleanup(UUID playerId, Session session) {
        cancelCleanup(session);
        session.open = false;
        session.cleanup = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.open && sessions.remove(playerId, session)) session.menu.getInventory().clear();
            session.cleanup = null;
        }, RETENTION_SECONDS * 20L);
    }

    private void cancelCleanup(Session session) {
        if (session.cleanup != null) {
            session.cleanup.cancel();
            session.cleanup = null;
        }
    }

    private static final class DropWindow {
        private final long[] timestamps = new long[DROP_THRESHOLD];
        private final int[] amounts = new int[DROP_THRESHOLD];
        private int next;
        private int count;

        boolean record(long now, int amount) {
            timestamps[next] = now;
            amounts[next] = Math.min(amount, DROP_ITEM_THRESHOLD);
            next = (next + 1) % DROP_THRESHOLD;
            if (count < DROP_THRESHOLD) count++;
            int recentCount = 0;
            int recentAmount = 0;
            for (int i = 0; i < count; i++) {
                if (now - timestamps[i] > DROP_WINDOW_NANOS) continue;
                recentCount++;
                recentAmount += amounts[i];
            }
            // Keep offering while disposal continues; guidance owns delivery cooldown and history.
            return recentCount >= DROP_THRESHOLD || recentAmount >= DROP_ITEM_THRESHOLD;
        }
    }

    private static final class Session {
        final TrashInventory menu;
        BukkitTask cleanup;
        boolean open;

        Session(TrashInventory menu) {
            this.menu = menu;
        }
    }
}
