package org.encinet.mik.feature.world.plot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns each player's temporary native-map texture and offhand presentation, never their inventory. */
final class PlotNativeMapViewer implements Listener {
    interface Output {
        void show(Player player, PlotEditorMap.Scene scene, byte[] changedPixels);
        void restore(Player player);
    }
    private static final byte[] BLANK = new byte[PlotMapViewport.COUNT];
    private final PlotPreviewCache worker;
    private final PlotNativeMapRaster raster;
    private final Function<UUID, Player> players;
    private final Map<UUID, View> views = new HashMap<>();
    private final Set<UUID> inventoryOpen = new HashSet<>();
    private Output output;
    private BukkitTask task;
    private long tick;

    PlotNativeMapViewer(PlotPreviewCache worker) {
        this(worker, null, Bukkit::getPlayer, new PlotNativeMapRaster(PlotNativeMapPackets.colors()));
    }
    PlotNativeMapViewer(PlotPreviewCache worker, Output output, Function<UUID, Player> players, PlotNativeMapRaster raster) {
        this.worker = worker; this.output = output; this.players = players;
        this.raster = raster;
    }
    void enable(Plugin plugin) {
        output = new PlotNativeMapPackets();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    void disable() {
        if (task != null) task.cancel();
        task = null;
        for (UUID player : Set.copyOf(views.keySet())) hide(player);
        inventoryOpen.clear();
        HandlerList.unregisterAll(this);
    }

    boolean visible(UUID player) { return views.containsKey(player); }

    java.util.OptionalInt progress(UUID player) {
        View view = views.get(player);
        return view == null || view.surfaceReady ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(view.progress);
    }

    void tick() {
        tick++;
        for (UUID id : Set.copyOf(views.keySet())) {
            Player player = players.apply(id);
            View view = views.get(id);
            if (player == null || tick - view.touched > 30 || !view.world.equals(player.getWorld().getUID())) hide(id);
        }
    }

    void show(Player player, PlotEditorMap.Scene scene) {
        if (output == null || inventoryOpen.contains(player.getUniqueId())) return;
        View previous = views.get(player.getUniqueId());
        if (previous != null && !previous.world.equals(player.getWorld().getUID())) hide(player.getUniqueId());
        View view = views.computeIfAbsent(player.getUniqueId(), _ -> new View());
        view.world = player.getWorld().getUID();
        view.touched = tick;
        var key = new Key(scene.viewport(), scene.layer(), scene.pixels(), scene.terrain().revision(), scene.regions().selected());
        // Publish a completed surface before scheduling its next terrain revision. Otherwise moving/sampling
        // every refresh could discard each finished image without ever showing it.
        if (sameGeometry(view.key, key)) complete(view);
        if (!key.equals(view.key)) {
            cancel(view);
            if (view.key == null || !view.key.viewport().equals(key.viewport())) view.surfaceReady = false;
            if (!sameGeometry(view.key, key)) view.image = null;
            view.key = key;
            view.job = new FutureTask<>(() -> raster.render(scene));
            try { worker.execute(view.job); }
            catch (RejectedExecutionException rejected) { cancel(view); view.key = null; }
        }
        view.surfaceReady |= scene.terrain().ready();
        view.progress = scene.terrain().progress();
        complete(view);
        byte[] pixels = view.image == null ? BLANK : view.image;
        output.show(player, scene, pixels == view.sent ? null : pixels);
        view.sent = pixels;
    }

    private static boolean sameGeometry(Key first, Key second) {
        return first != null && first.viewport().equals(second.viewport()) && first.layer() == second.layer()
                && first.pixels() == second.pixels() && java.util.Objects.equals(first.selected(), second.selected());
    }

    private void complete(View view) {
        if (view.job != null && view.job.isDone()) {
            try { view.image = view.job.get(); }
            catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                view.key = null;
                System.getLogger(PlotNativeMapViewer.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not prepare native plot map", failure);
            }
            view.job = null;
        }
    }

    void hide(UUID player) {
        View view = views.remove(player);
        if (view == null) return;
        cancel(view);
        Player online = players.apply(player);
        if (online != null && output != null) output.restore(online);
    }
    void forget(UUID player) { hide(player); inventoryOpen.remove(player); }
    private void cancel(View view) { if (view.job != null) worker.cancel(view.job); view.job = null; }

    private void openInventory(Player player) { inventoryOpen.add(player.getUniqueId()); hide(player.getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) openInventory(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) { inventoryOpen.remove(event.getPlayer().getUniqueId()); }

    // Opening the personal inventory is client-side; restore real slot/cursor state before its first transfer.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && visible(player.getUniqueId())) {
            if (event.getClickedInventory() == player.getInventory() && event.getSlot() == 40
                    || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) event.setCancelled(true);
            openInventory(player);
            player.updateInventory();
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && visible(player.getUniqueId())) {
            if (event.getRawSlots().stream().anyMatch(slot -> event.getView().getInventory(slot) == player.getInventory()
                    && event.getView().convertSlot(slot) == 40)) event.setCancelled(true);
            openInventory(player);
            player.updateInventory();
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOffhandUse(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND && visible(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) { hide(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { hide(event.getPlayer().getUniqueId()); }

    private record Key(PlotMapViewport viewport, int layer, PlotEditorMap.Pixels pixels, long terrain,
                       PlotSelectionRegions.Region selected) { }
    private static final class View {
        Key key; FutureTask<byte[]> job; byte[] image, sent; UUID world; long touched;
        boolean surfaceReady; int progress;
    }
}
