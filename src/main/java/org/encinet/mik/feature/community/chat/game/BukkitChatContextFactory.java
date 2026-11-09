package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatItemSnapshot;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.IdentityHashMap;

/** Maintains primary-thread Bukkit snapshots consumed lock-free by async chat. */
public final class BukkitChatContextFactory {
    private final Plugin plugin;
    private final IdentityBindingManager identityBindings;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private List<IdentityBinding> boundBindings = List.of();
    private BukkitTask refreshTask;

    public BukkitChatContextFactory(
            Plugin plugin,
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
        snapshot = Snapshot.EMPTY;
        boundBindings = List.of();
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
                ? Bukkit.getPlayer(checked) : snapshot.playersById().get(checked);
    }

    public Set<Player> staffPlayers() {
        if (Bukkit.isPrimaryThread()) {
            Set<Player> current = Collections.newSetFromMap(new IdentityHashMap<>());
            Bukkit.getOnlinePlayers().stream()
                    .filter(player -> RolePermissions.canModerate(player))
                    .forEach(current::add);
            return Set.copyOf(current);
        }
        return snapshot.staffPlayers();
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
        Snapshot current = snapshot;
        Set<UUID> allowed = new HashSet<>();
        if (Bukkit.isPrimaryThread()) {
            List<Target> live = new ArrayList<>();
            for (Player player : players) {
                if (player.isOnline()) {
                    Target target = new Target(player.getUniqueId(), player.getName());
                    live.add(target);
                    if (!target.id().equals(sender.getUniqueId())) {
                        allowed.add(target.id());
                    }
                }
            }
            PlayerMentionIndex online = current.onlineMentions().including(live);
            if (online != current.onlineMentions()) {
                current = new Snapshot(current.playersByIdentity(), current.playersById(),
                        current.staffPlayers(), online, current.boundMentions());
                snapshot = current;
            }
            return snapshotPlayer(sender).context(mentions(
                    current, allowed, sender.getUniqueId(), includeBoundOfflinePlayers));
        }
        CachedPlayer cachedSender = current.playersByIdentity().get(sender);
        if (cachedSender == null) {
            return ChatProcessingContext.external();
        }
        for (Player player : players) {
            CachedPlayer cached = current.playersByIdentity().get(player);
            if (cached != null && !cached.id().equals(cachedSender.id())) {
                allowed.add(cached.id());
            }
        }
        return cachedSender.snapshot().context(mentions(
                current, allowed, cachedSender.id(), includeBoundOfflinePlayers));
    }

    private PlayerMentions mentions(
            Snapshot current, Set<UUID> allowed, UUID senderId, boolean includeBoundOfflinePlayers
    ) {
        return new PlayerMentions(current.onlineMentions(),
                includeBoundOfflinePlayers ? current.boundMentions() : PlayerMentionIndex.empty(),
                allowed, senderId);
    }

    private void refreshAll() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit chat snapshots require primary thread");
        }
        IdentityHashMap<Player, CachedPlayer> byIdentity = new IdentityHashMap<>();
        Map<UUID, Player> byId = new HashMap<>();
        Set<Player> staff = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Target> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            online.add(new Target(playerId, player.getName()));
            byIdentity.put(player, new CachedPlayer(playerId, snapshotPlayer(player)));
            byId.put(playerId, player);
            if (RolePermissions.canModerate(player)) {
                staff.add(player);
            }
        }
        Snapshot previous = snapshot;
        snapshot = new Snapshot(Collections.unmodifiableMap(byIdentity), Map.copyOf(byId), Set.copyOf(staff),
                previous.onlineMentions().withTargets(online), refreshBoundPlayers(previous.boundMentions()));
    }

    private PlayerMentionIndex refreshBoundPlayers(PlayerMentionIndex previous) {
        if (identityBindings == null) {
            return PlayerMentionIndex.empty();
        }
        try {
            List<IdentityBinding> bindings = List.copyOf(identityBindings.bindings());
            if (boundBindings.equals(bindings)) {
                return previous;
            }
            Map<UUID, Target> byPlayer = new LinkedHashMap<>();
            for (IdentityBinding binding : bindings) {
                byPlayer.put(binding.playerId(), new Target(binding.playerId(), binding.playerName()));
            }
            Map<String, Target> unique = new LinkedHashMap<>();
            Set<String> ambiguous = new HashSet<>();
            for (Target target : byPlayer.values()) {
                String name = PlayerMentionIndex.nameKey(target.name());
                if (unique.putIfAbsent(name, target) != null) {
                    ambiguous.add(name);
                }
            }
            ambiguous.forEach(unique::remove);
            PlayerMentionIndex refreshed = previous.withTargets(unique.values());
            boundBindings = bindings;
            return refreshed;
        } catch (RuntimeException error) {
            plugin.getLogger().warning(
                    "Could not refresh bound chat mention identities: "
                            + error.getMessage());
            return previous;
        }
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
        if (RolePermissions.isMember(sender)) {
            capabilities.add(ChatCapability.MINI_MESSAGE);
        }
        if (RolePermissions.isCustodian(sender)) {
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
                PlayerMentions mentions
        ) {
            return new ChatProcessingContext(
                    capabilities, mentions, mainHand, inventory);
        }
    }

    private record CachedPlayer(
            UUID id,
            PlayerSnapshot snapshot
    ) {
    }

    private record Snapshot(
            Map<Player, CachedPlayer> playersByIdentity,
            Map<UUID, Player> playersById,
            Set<Player> staffPlayers,
            PlayerMentionIndex onlineMentions,
            PlayerMentionIndex boundMentions
    ) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Set.of(),
                PlayerMentionIndex.empty(), PlayerMentionIndex.empty());
    }
}
