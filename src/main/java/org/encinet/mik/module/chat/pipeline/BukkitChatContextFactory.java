package org.encinet.mik.module.chat.pipeline;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatItemSnapshot;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.IdentityHashMap;

/** Maintains primary-thread Bukkit snapshots consumed lock-free by async chat. */
public final class BukkitChatContextFactory {
    private static final String MEMBER_PERMISSION = "group." + Mik.GROUP_MEMBER;
    private static final String MANAGER_PERMISSION = "group." + Mik.GROUP_MANAGER;
    private static final String STAFF_PERMISSION = "group." + Mik.GROUP_HELPER;

    private final JavaPlugin plugin;
    private final IdentityBindingManager identityBindings;
    private volatile Map<Player, CachedPlayer> playersByIdentity = Map.of();
    private volatile Map<UUID, Player> playersById = Map.of();
    private volatile Set<Player> staffPlayers = Set.of();
    private volatile List<ChatProcessingContext.PlayerReference> boundPlayers = List.of();
    private BukkitTask refreshTask;

    public BukkitChatContextFactory(JavaPlugin plugin) {
        this(plugin, null);
    }

    public BukkitChatContextFactory(
            JavaPlugin plugin,
            IdentityBindingManager identityBindings
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.identityBindings = identityBindings;
    }

    public void enable() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Bukkit chat snapshot cache must start on the primary thread");
        }
        refreshAll();
        // One-second refresh avoids any async Bukkit access while keeping item
        // placeholders current without serializing every inventory every tick.
        refreshTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::refreshAll, 20L, 20L);
    }

    public void close() {
        BukkitTask task = refreshTask;
        refreshTask = null;
        if (task != null) {
            task.cancel();
        }
        playersByIdentity = Map.of();
        playersById = Map.of();
        staffPlayers = Set.of();
        boundPlayers = List.of();
    }

    public void forget(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit chat snapshots require primary thread");
        }
        refreshAll();
    }

    public Player onlinePlayer(UUID playerId) {
        UUID checked = java.util.Objects.requireNonNull(playerId, "playerId");
        return Bukkit.isPrimaryThread()
                ? Bukkit.getPlayer(checked) : playersById.get(checked);
    }

    public Set<Player> staffPlayers() {
        if (Bukkit.isPrimaryThread()) {
            Set<Player> current = Collections.newSetFromMap(new IdentityHashMap<>());
            Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission(STAFF_PERMISSION))
                    .forEach(current::add);
            return Set.copyOf(current);
        }
        return staffPlayers;
    }

    public ChatProcessingContext capture(
            Player sender,
            Collection<? extends Player> mentionablePlayers
    ) {
        return capture(sender, mentionablePlayers, false);
    }

    public ChatProcessingContext capture(
            Player sender,
            Collection<? extends Player> mentionablePlayers,
            boolean includeBoundOfflinePlayers
    ) {
        java.util.Objects.requireNonNull(sender, "sender");
        List<Player> players = List.copyOf(mentionablePlayers);
        if (Bukkit.isPrimaryThread()) {
            PlayerSnapshot senderSnapshot = snapshotPlayer(sender);
            return senderSnapshot.context(mergeBoundPlayers(
                    sender.getUniqueId(), referencesOnPrimaryThread(
                            sender.getUniqueId(), players),
                    includeBoundOfflinePlayers));
        }
        Map<Player, CachedPlayer> snapshot = playersByIdentity;
        CachedPlayer cachedSender = snapshot.get(sender);
        if (cachedSender == null) {
            return ChatProcessingContext.external();
        }
        List<ChatProcessingContext.PlayerReference> onlineReferences = players.stream()
                .map(snapshot::get)
                .filter(java.util.Objects::nonNull)
                .filter(player -> !player.id().equals(cachedSender.id()))
                .map(CachedPlayer::reference)
                .sorted(Comparator.comparingInt(
                        (ChatProcessingContext.PlayerReference player) ->
                                player.name().length()).reversed())
                .toList();
        return cachedSender.snapshot().context(mergeBoundPlayers(
                cachedSender.id(), onlineReferences,
                includeBoundOfflinePlayers));
    }

    private List<ChatProcessingContext.PlayerReference> referencesOnPrimaryThread(
            UUID senderId,
            List<Player> mentionablePlayers
    ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit chat context requires primary thread");
        }
        List<ChatProcessingContext.PlayerReference> players = new ArrayList<>();
        mentionablePlayers.stream()
                .filter(Player::isOnline)
                .filter(player -> !player.getUniqueId().equals(senderId))
                .sorted(Comparator.comparingInt((Player player) ->
                        player.getName().length()).reversed())
                .forEach(player -> players.add(
                        new ChatProcessingContext.PlayerReference(
                                player.getUniqueId(), player.getName())));
        return List.copyOf(players);
    }

    private void refreshAll() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit chat snapshots require primary thread");
        }
        IdentityHashMap<Player, CachedPlayer> byIdentity = new IdentityHashMap<>();
        Map<UUID, Player> byId = new HashMap<>();
        Set<Player> staff = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            ChatProcessingContext.PlayerReference reference =
                    new ChatProcessingContext.PlayerReference(
                            playerId, player.getName());
            byIdentity.put(player, new CachedPlayer(
                    playerId, reference, snapshotPlayer(player)));
            byId.put(playerId, player);
            if (player.hasPermission(STAFF_PERMISSION)) {
                staff.add(player);
            }
        }
        playersByIdentity = Collections.unmodifiableMap(byIdentity);
        playersById = Map.copyOf(byId);
        staffPlayers = Set.copyOf(staff);
        refreshBoundPlayers();
    }

    private void refreshBoundPlayers() {
        if (identityBindings == null) {
            boundPlayers = List.of();
            return;
        }
        try {
            Map<UUID, ChatProcessingContext.PlayerReference> byPlayer =
                    new java.util.LinkedHashMap<>();
            for (IdentityBinding binding : identityBindings.bindings()) {
                byPlayer.put(binding.playerId(),
                        new ChatProcessingContext.PlayerReference(
                                binding.playerId(), binding.playerName()));
            }
            Map<String, Integer> nameCounts = new HashMap<>();
            byPlayer.values().forEach(player -> nameCounts.merge(
                    player.name().toLowerCase(java.util.Locale.ROOT), 1,
                    Integer::sum));
            boundPlayers = byPlayer.values().stream()
                    .filter(player -> nameCounts.getOrDefault(
                            player.name().toLowerCase(java.util.Locale.ROOT), 0) == 1)
                    .sorted(Comparator.comparingInt(
                            (ChatProcessingContext.PlayerReference player) ->
                                    player.name().length()).reversed())
                    .toList();
        } catch (RuntimeException error) {
            plugin.getLogger().warning(
                    "Could not refresh bound chat mention identities: "
                            + error.getMessage());
        }
    }

    private List<ChatProcessingContext.PlayerReference> mergeBoundPlayers(
            UUID senderId,
            List<ChatProcessingContext.PlayerReference> onlinePlayers,
            boolean includeBoundOfflinePlayers
    ) {
        if (!includeBoundOfflinePlayers || boundPlayers.isEmpty()) {
            return onlinePlayers;
        }
        Map<String, ChatProcessingContext.PlayerReference> unique =
                new java.util.LinkedHashMap<>();
        for (ChatProcessingContext.PlayerReference player : onlinePlayers) {
            unique.put(player.name().toLowerCase(java.util.Locale.ROOT), player);
        }
        for (ChatProcessingContext.PlayerReference player : boundPlayers) {
            if (!player.id().equals(senderId)) {
                unique.putIfAbsent(player.name().toLowerCase(
                        java.util.Locale.ROOT), player);
            }
        }
        return unique.values().stream().sorted(Comparator.comparingInt(
                (ChatProcessingContext.PlayerReference player) ->
                        player.name().length()).reversed()).toList();
    }

    private PlayerSnapshot snapshotPlayer(Player sender) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit chat snapshots require primary thread");
        }
        EnumSet<ChatCapability> capabilities = EnumSet.of(
                ChatCapability.URL,
                ChatCapability.PLAYER_MENTION,
                ChatCapability.ITEM,
                ChatCapability.INVENTORY);
        if (sender.hasPermission(MEMBER_PERMISSION)) {
            capabilities.add(ChatCapability.MINI_MESSAGE);
        }
        if (sender.hasPermission(MANAGER_PERMISSION)) {
            capabilities.add(ChatCapability.BROADCAST_MENTION);
        }

        Optional<ChatItemSnapshot> mainHand = snapshot(
                sender.getInventory().getItemInMainHand());
        Map<Integer, ChatItemSnapshot> inventory = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            int index = slot;
            snapshot(sender.getInventory().getItem(slot))
                    .ifPresent(item -> inventory.put(index, item));
        }
        return new PlayerSnapshot(Set.copyOf(capabilities), mainHand,
                Collections.unmodifiableMap(inventory));
    }

    private Optional<ChatItemSnapshot> snapshot(ItemStack value) {
        if (value == null || value.getType() == Material.AIR || value.isEmpty()) {
            return Optional.empty();
        }
        ItemStack item = value.clone();
        String name = PlainTextComponentSerializer.plainText().serialize(
                item.effectiveName());
        return Optional.of(new ChatItemSnapshot(
                item.getType().getKey().asString(), name,
                item.getAmount(), item.serializeAsBytes()));
    }

    private record PlayerSnapshot(
            Set<ChatCapability> capabilities,
            Optional<ChatItemSnapshot> mainHand,
            Map<Integer, ChatItemSnapshot> inventory
    ) {
        private ChatProcessingContext context(
                List<ChatProcessingContext.PlayerReference> players
        ) {
            return new ChatProcessingContext(
                    capabilities, players, mainHand, inventory);
        }
    }

    private record CachedPlayer(
            UUID id,
            ChatProcessingContext.PlayerReference reference,
            PlayerSnapshot snapshot
    ) {
    }
}
