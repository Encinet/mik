package org.encinet.mik.module.safety;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.safety.UnsafeItemPacketSanitizer.NetworkAudit;
import org.encinet.mik.module.safety.UnsafeItemPolicy.BookPacketCheck;
import org.encinet.mik.module.safety.UnsafeItemPolicy.CheckResult;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Removes items with oversized entity data or a network representation too large to handle safely.
 *
 * <p>Encoding failures are treated as unsafe. Audit messages intentionally
 * omit item metadata so malicious book pages, lore, or NBT cannot inflate logs.
 */
public final class BanItemGuardModule implements Listener {

    private static final long SCAN_PERIOD_TICKS = 100L;
    private static final int MAX_DETAILED_LOGS_PER_PURGE = 5;
    private static final int HOTBAR_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final int BOOK_OFF_HAND_SLOT = 40;
    private static final String NETWORK_HANDLER_NAME = "mik_ban_item_guard";
    private static final long NETWORK_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final JavaPlugin plugin;
    private final UnsafeItemPolicy itemPolicy;
    private final UnsafeItemPacketSanitizer packetSanitizer;
    private final NetworkConnectionListener networkConnectionListener =
            new NetworkConnectionListener();
    private final Set<Channel> guardedChannels = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPurges = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingBookRemovals = ConcurrentHashMap.newKeySet();

    private EventManager packetEventManager;
    private BukkitTask scanTask;
    private volatile boolean enabled;

    public BanItemGuardModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemPolicy = new UnsafeItemPolicy(MinecraftServer.getServer().registryAccess());
        this.packetSanitizer = new UnsafeItemPacketSanitizer(itemPolicy);
    }

    public void enable() {
        if (enabled) {
            return;
        }

        enabled = true;
        packetEventManager = PacketEvents.getAPI().getEventManager();
        packetEventManager.registerListener(networkConnectionListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        scanTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::scanOnlinePlayers,
                1L,
                SCAN_PERIOD_TICKS);

        for (Player player : Bukkit.getOnlinePlayers()) {
            installNetworkGuard(player);
        }

        plugin.getLogger().info(String.format(
                Locale.ROOT,
                "BanItemGuardModule enabled (networkLimit=%d KiB, scanPeriod=%d ticks)",
                UnsafeItemPolicy.MAX_ITEM_SIZE_KIB,
                SCAN_PERIOD_TICKS));
    }

    public void disable() {
        if (!enabled) {
            return;
        }

        enabled = false;

        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (packetEventManager != null) {
            packetEventManager.unregisterListener(networkConnectionListener);
            packetEventManager = null;
        }

        HandlerList.unregisterAll(this);
        for (Channel channel : List.copyOf(guardedChannels)) {
            removeNetworkGuard(channel);
        }
        guardedChannels.clear();
        pendingPurges.clear();
        pendingBookRemovals.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = event.getWhoClicked() instanceof Player target ? target : null;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        CheckResult cursorResult = itemPolicy.check(cursor);
        CheckResult currentResult = itemPolicy.check(current);
        InventorySwapTarget swapTarget = inventorySwapTarget(event, player, current);
        CheckResult swapResult = swapTarget == null
                ? UnsafeItemPolicy.SAFE
                : itemPolicy.check(swapTarget.item());

        if (!cursorResult.blocked()
                && !currentResult.blocked()
                && !swapResult.blocked()) {
            return;
        }

        event.setCancelled(true);
        if (cursorResult.blocked()) {
            logRemoval(player, "inventory-cursor", cursor, cursorResult);
            event.getView().setCursor(emptyItem());
        }
        if (currentResult.blocked()) {
            logRemoval(player, "inventory-slot", current, currentResult);
            event.setCurrentItem(emptyItem());
        }
        if (player != null && swapTarget != null && swapResult.blocked()) {
            logRemoval(player, swapTarget.source(), swapTarget.item(), swapResult);
            clearSwapTarget(player.getInventory(), swapTarget);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack item = event.getOldCursor();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        event.getView().setCursor(emptyItem());
        Player player = event.getWhoClicked() instanceof Player target ? target : null;
        logRemoval(player, "inventory-drag", item, result);
    }

    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        ItemStack candidate = new ItemStack(
                event.isSigning() ? Material.WRITTEN_BOOK : Material.WRITABLE_BOOK);
        if (!candidate.setItemMeta(event.getNewBookMeta())) {
            return;
        }

        CheckResult result = itemPolicy.check(candidate);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        int slot = event.getSlot();
        ItemStack original;

        if (slot == -1) {
            original = inventory.getItemInOffHand();
            inventory.setItemInOffHand(emptyItem());
        } else {
            original = inventory.getItem(slot);
            inventory.clear(slot);
        }

        logRemoval(
                player,
                "book-edit",
                isEmpty(original) ? candidate : original,
                result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        removeFromHandIfUnsafe(
                event.getPlayer(),
                event.getHand(),
                event.getItem(),
                "item-interact",
                event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        removeFromHandIfUnsafe(
                event.getPlayer(),
                event.getHand(),
                event.getItemInHand(),
                "block-place",
                event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        CheckResult result = itemPolicy.check(event.getItem());
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        purgeAutomationSource(event.getSource(), "inventory-transfer");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDispense(BlockDispenseEvent event) {
        CheckResult result = itemPolicy.check(event.getItem());
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        if (event.getBlock().getState(false) instanceof InventoryHolder holder) {
            purgeAutomationSource(holder.getInventory(), "block-dispense");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();
        logRemoval(null, "inventory-item-pickup", item, result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        logRemoval(null, "item-spawn", item, result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();
        Player player = event.getEntity() instanceof Player target ? target : null;
        logRemoval(player, "item-pickup", item, result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory openedInventory = event.getInventory();
        Inventory playerInventory = player.getInventory();
        purgeInventory(openedInventory, player, "opened-inventory");
        if (openedInventory != playerInventory) {
            purgeInventory(playerInventory, player, "player-inventory");
        }
        purgeCursor(player, "inventory-cursor");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Fallback for connections that existed before this module was enabled.
        installNetworkGuard(event.getPlayer());
        scanPlayer(event.getPlayer(), "join", false);
    }

    /** Covers Ctrl+middle-click pick-block and pick-entity item creation. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickItem(PlayerPickItemEvent event) {
        ItemStack item = event.getItem();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        logRemoval(
                event.getPlayer(),
                event.isIncludeData() ? "pick-item/with-data" : "pick-item",
                item,
                result);
    }

    private void removeFromHandIfUnsafe(
            Player player,
            EquipmentSlot hand,
            ItemStack item,
            String source,
            Cancellable event
    ) {
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        event.setCancelled(true);
        clearHand(player, hand);
        logRemoval(player, source, item, result);
    }

    private InventorySwapTarget inventorySwapTarget(
            InventoryClickEvent event,
            Player player,
            ItemStack current
    ) {
        if (player == null) {
            return null;
        }

        PlayerInventory inventory = player.getInventory();
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int slot = event.getHotbarButton();
            if (slot < 0) {
                return null;
            }

            ItemStack item = inventory.getItem(slot);
            return item == current
                    ? null
                    : new InventorySwapTarget(
                            slot,
                            item,
                            "inventory-hotbar-swap/slot=" + slot);
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack item = inventory.getItemInOffHand();
            return item == current
                    ? null
                    : new InventorySwapTarget(
                            -1,
                            item,
                            "inventory-offhand-swap");
        }

        return null;
    }

    private void clearSwapTarget(
            PlayerInventory inventory,
            InventorySwapTarget target
    ) {
        if (target.slot() == -1) {
            inventory.setItemInOffHand(emptyItem());
        } else {
            inventory.clear(target.slot());
        }
    }

    private void purgeAutomationSource(Inventory inventory, String source) {
        // InventoryMoveItemEvent may restore its item after listeners return.
        Bukkit.getScheduler().runTask(
                plugin,
                () -> {
                    Player owner = inventory.getHolder(false) instanceof Player player
                            ? player
                            : null;
                    purgeInventory(inventory, owner, source);
                });
    }

    private void blockOversizedBookEdit(
            PacketReceiveEvent event,
            WrapperPlayClientEditBook packet,
            BookPacketCheck result
    ) {
        event.setCancelled(true);

        User user = event.getUser();
        UUID playerId = user.getUUID();
        if (!pendingBookRemovals.add(playerId)) {
            return;
        }

        String sizeInfo = result.bytes() >= 0L
                ? String.format(Locale.ROOT, "%.2f KiB", result.bytes() / 1024.0D)
                : "unavailable (validationError=" + result.error() + ")";
        plugin.getLogger().warning(
                "Blocked oversized inbound book edit: player="
                        + (user.getName() == null ? "unknown" : user.getName())
                        + "/" + playerId
                        + ", size=" + sizeInfo
                        + ", allowed="
                        + String.format(Locale.ROOT, "%.2f KiB", result.allowed() / 1024.0D)
                        + ", pages=" + result.pages()
                        + ", slot=" + packet.getSlot());

        try {
            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> removeEditedBook(playerId, packet.getSlot()));
        } catch (RuntimeException exception) {
            pendingBookRemovals.remove(playerId);
        }
    }

    private void blockMalformedBookEdit(
            PacketReceiveEvent event,
            Throwable exception
    ) {
        event.setCancelled(true);
        User user = event.getUser();
        plugin.getLogger().warning(
                "Blocked malformed inbound book edit: player="
                        + (user.getName() == null ? "unknown" : user.getName())
                        + "/" + (user.getUUID() == null ? "unknown" : user.getUUID())
                        + ", error=" + exception.getClass().getSimpleName());
    }

    private void removeEditedBook(UUID playerId, int slot) {
        pendingBookRemovals.remove(playerId);
        if (!enabled) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack item;
        if (slot == BOOK_OFF_HAND_SLOT) {
            item = inventory.getItemInOffHand();
        } else if (slot >= HOTBAR_MIN_SLOT && slot <= HOTBAR_MAX_SLOT) {
            item = inventory.getItem(slot);
        } else {
            return;
        }

        if (item.getType() != Material.WRITABLE_BOOK
                && item.getType() != Material.WRITTEN_BOOK) {
            return;
        }

        if (slot == BOOK_OFF_HAND_SLOT) {
            inventory.setItemInOffHand(emptyItem());
        } else {
            inventory.clear(slot);
        }
    }

    private void installNetworkGuard(Player player) {
        Object rawChannel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (!(rawChannel instanceof Channel channel)) {
            return;
        }

        User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        installNetworkGuard(channel, user, player.getUniqueId(), player.getName());
    }

    private void installNetworkGuard(User user) {
        Object rawChannel = user.getChannel();
        if (rawChannel instanceof Channel channel) {
            installNetworkGuard(channel, user, null, null);
        }
    }

    private void installNetworkGuard(
            Channel channel,
            User user,
            UUID fallbackPlayerId,
            String fallbackPlayerName
    ) {
        if (!enabled || !channel.isOpen()) {
            return;
        }

        synchronized (channel) {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(NETWORK_HANDLER_NAME) != null) {
                guardedChannels.add(channel);
                return;
            }

            String encoderName;
            if (pipeline.get(HandlerNames.ENCODER) != null) {
                encoderName = HandlerNames.ENCODER;
            } else if (pipeline.get(HandlerNames.OUTBOUND_CONFIG) != null) {
                encoderName = HandlerNames.OUTBOUND_CONFIG;
            } else {
                plugin.getLogger().warning(
                        "BanItemGuard could not find the Minecraft encoder; "
                                + "the connection was not guarded");
                return;
            }

            pipeline.addAfter(
                    encoderName,
                    NETWORK_HANDLER_NAME,
                    new UnsafeItemOutboundHandler(
                            user,
                            fallbackPlayerId,
                            fallbackPlayerName));
            guardedChannels.add(channel);
        }
    }

    private void removeNetworkGuard(Channel channel) {
        try {
            synchronized (channel) {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(NETWORK_HANDLER_NAME) != null) {
                    pipeline.remove(NETWORK_HANDLER_NAME);
                }
            }
        } catch (RuntimeException ignored) {
            // A concurrently closing channel may already have destroyed its pipeline.
        } finally {
            guardedChannels.remove(channel);
        }
    }

    private void scheduleServerPurge(UUID playerId) {
        if (!enabled || playerId == null || !pendingPurges.add(playerId)) {
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingPurges.remove(playerId);
                if (!enabled) {
                    return;
                }

                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    scanPlayer(player, "network", true);
                }
            });
        } catch (RuntimeException exception) {
            pendingPurges.remove(playerId);
        }
    }

    private void scanOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scanPlayer(player, "periodic", false);
        }
    }

    private void scanPlayer(
            Player player,
            String source,
            boolean includeOpenInventory
    ) {
        Inventory playerInventory = player.getInventory();
        Inventory enderChest = player.getEnderChest();

        purgeInventory(playerInventory, player, source + "/player-inventory");
        purgeInventory(enderChest, player, source + "/ender-chest");
        if (includeOpenInventory) {
            Inventory openInventory = player.getOpenInventory().getTopInventory();
            if (openInventory != playerInventory && openInventory != enderChest) {
                purgeInventory(openInventory, player, source + "/opened-inventory");
            }
        }
        purgeCursor(player, source + "/inventory-cursor");
    }

    private void purgeInventory(Inventory inventory, Player player, String source) {
        int removed = 0;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            CheckResult result = itemPolicy.check(item);
            if (!result.blocked()) {
                continue;
            }

            if (removed < MAX_DETAILED_LOGS_PER_PURGE) {
                logRemoval(player, source + "/slot=" + slot, item, result);
            }
            inventory.clear(slot);
            removed++;
        }

        if (removed > MAX_DETAILED_LOGS_PER_PURGE) {
            plugin.getLogger().warning(
                    "Unsafe item purge completed: player=" + playerInfo(player)
                            + ", source=" + source
                            + ", removed=" + removed
                            + ", detailedLogs=" + MAX_DETAILED_LOGS_PER_PURGE);
        }
    }

    private void purgeCursor(Player player, String source) {
        ItemStack item = player.getItemOnCursor();
        CheckResult result = itemPolicy.check(item);
        if (!result.blocked()) {
            return;
        }

        logRemoval(player, source, item, result);
        player.setItemOnCursor(emptyItem());
    }

    private void clearHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(emptyItem());
        } else {
            player.getInventory().setItemInMainHand(emptyItem());
        }
    }

    private void logRemoval(
            Player player,
            String source,
            ItemStack item,
            CheckResult result
    ) {
        String sizeInfo = result.bytes() >= 0L
                ? String.format(Locale.ROOT, "%.2f KiB", result.bytes() / 1024.0)
                : "unavailable (encodingError=" + result.error() + ")";

        plugin.getLogger().warning(
                "Removed unsafe item: player=" + playerInfo(player)
                        + ", source=" + source
                        + ", type=" + item.getType()
                        + ", size=" + sizeInfo
                        + ", limit=" + UnsafeItemPolicy.MAX_ITEM_SIZE_KIB + " KiB");
    }

    private void logNetworkRemoval(
            String playerName,
            UUID playerId,
            String packetType,
            NetworkAudit audit,
            int suppressedPackets
    ) {
        String sizeInfo = audit.largestBytes() >= 0L
                ? String.format(Locale.ROOT, "%.2f KiB", audit.largestBytes() / 1024.0)
                : "unavailable (encodingError=" + audit.firstError() + ")";
        String playerInfo = (playerName == null ? "unknown" : playerName)
                + "/" + (playerId == null ? "unknown" : playerId);

        plugin.getLogger().warning(
                "Sanitized unsafe outbound item packet: player=" + playerInfo
                        + ", packet=" + packetType
                        + ", removed=" + audit.removed()
                        + ", largestItem=" + sizeInfo
                        + ", limit=" + UnsafeItemPolicy.MAX_ITEM_SIZE_KIB + " KiB"
                        + (suppressedPackets == 0
                                ? ""
                                : ", suppressedPacketLogs=" + suppressedPackets));
    }

    private static String playerInfo(Player player) {
        return player == null
                ? "none"
                : player.getName() + "/" + player.getUniqueId();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static ItemStack emptyItem() {
        return new ItemStack(Material.AIR);
    }

    private final class NetworkConnectionListener extends PacketListenerAbstract {

        private NetworkConnectionListener() {
            super(PacketListenerPriority.LOWEST);
        }

        @Override
        public void onUserConnect(UserConnectEvent event) {
            installNetworkGuard(event.getUser());
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() != PacketType.Play.Client.EDIT_BOOK) {
                return;
            }

            try {
                WrapperPlayClientEditBook packet = new WrapperPlayClientEditBook(event);
                BookPacketCheck result = itemPolicy.checkBookPages(packet.getPages());
                if (result.blocked()) {
                    blockOversizedBookEdit(event, packet, result);
                }
            } catch (RuntimeException | StackOverflowError exception) {
                blockMalformedBookEdit(event, exception);
            }
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            Object rawChannel = event.getUser().getChannel();
            if (rawChannel instanceof Channel channel) {
                removeNetworkGuard(channel);
            }
        }
    }

    private final class UnsafeItemOutboundHandler extends ChannelOutboundHandlerAdapter {

        private final User user;
        private final UUID fallbackPlayerId;
        private final String fallbackPlayerName;
        private long lastLogNanos;
        private int suppressedPacketLogs;

        private UnsafeItemOutboundHandler(
                User user,
                UUID fallbackPlayerId,
                String fallbackPlayerName
        ) {
            this.user = user;
            this.fallbackPlayerId = fallbackPlayerId;
            this.fallbackPlayerName = fallbackPlayerName;
        }

        @Override
        public void write(
                ChannelHandlerContext context,
                Object message,
                ChannelPromise promise
        ) throws Exception {
            if (!enabled
                    || !(message instanceof Packet<?> packet)
                    || !UnsafeItemPacketSanitizer.canContainItem(packet)) {
                super.write(context, message, promise);
                return;
            }

            NetworkAudit audit = new NetworkAudit();
            Packet<?> sanitized = packetSanitizer.sanitizePacket(packet, audit);
            if (audit.removed() > 0) {
                UUID playerId = playerId();
                logNetworkAudit(packet.getClass().getSimpleName(), audit, playerId);
                scheduleServerPurge(playerId);
            }

            super.write(context, sanitized, promise);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) {
            guardedChannels.remove(context.channel());
        }

        private void logNetworkAudit(
                String packetType,
                NetworkAudit audit,
                UUID playerId
        ) {
            long now = System.nanoTime();
            if (lastLogNanos != 0L
                    && now - lastLogNanos < NETWORK_LOG_INTERVAL_NANOS) {
                suppressedPacketLogs++;
                return;
            }

            logNetworkRemoval(
                    playerName(),
                    playerId,
                    packetType,
                    audit,
                    suppressedPacketLogs);
            lastLogNanos = now;
            suppressedPacketLogs = 0;
        }

        private UUID playerId() {
            UUID playerId = user == null ? null : user.getUUID();
            return playerId == null ? fallbackPlayerId : playerId;
        }

        private String playerName() {
            String playerName = user == null ? null : user.getName();
            return playerName == null ? fallbackPlayerName : playerName;
        }
    }

    private record InventorySwapTarget(int slot, ItemStack item, String source) {
    }

}
