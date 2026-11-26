package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatItemSnapshot;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.render.ChatMessageFormatter.PlayerView;
import org.encinet.mik.feature.community.chat.render.ChatMessageFormatter.Presentation;
import org.encinet.mik.feature.player.profile.PlayerNameRenderer;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Samples input capabilities and game presentation on the primary thread for native async chat. */
final class GameChatSessions implements AutoCloseable {
    private final Plugin plugin;
    private final GameChatDirectory directory;
    private final LanguageService language;
    private final PlayerNameTagRenderer names;
    private final PlayerSourceRenderer sources;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private BukkitTask sampler;

    GameChatSessions(Plugin plugin, GameChatDirectory directory, LanguageService language,
            PlayerNameTagRenderer names, PlayerSourceRenderer sources) {
        this.plugin = plugin;
        this.directory = directory;
        this.language = language;
        this.names = names;
        this.sources = sources;
    }

    void enable() {
        refresh();
        sampler = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20, 20);
    }

    @Override public void close() {
        if (sampler != null) sampler.cancel();
        sampler = null;
        snapshot = Snapshot.EMPTY;
    }

    private void refresh() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Chat sessions require the primary thread");
        var sessions = new IdentityHashMap<Player, Session>();
        var people = new HashMap<UUID, PlayerView>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var session = sample(player);
            sessions.put(player, session);
            people.put(session.id(), session.person());
        }
        for (var peer : directory.webPeers()) {
            people.computeIfAbsent(peer.id(), id -> describeOffline(id, peer.name()));
        }
        snapshot = new Snapshot(Collections.unmodifiableMap(sessions), Map.copyOf(people));
    }

    void joined(Player player) {
        var current = snapshot;
        var sessions = new IdentityHashMap<>(current.sessions());
        var people = new HashMap<>(current.people());
        var session = sample(player);
        sessions.put(player, session);
        people.put(session.id(), session.person());
        snapshot = new Snapshot(Collections.unmodifiableMap(sessions), Map.copyOf(people));
    }

    void forget(Player player) {
        var current = snapshot;
        var sessions = new IdentityHashMap<>(current.sessions());
        var session = sessions.remove(player);
        if (session == null) return;
        var people = new HashMap<>(current.people());
        people.remove(session.id());
        snapshot = new Snapshot(Collections.unmodifiableMap(sessions), Map.copyOf(people));
    }

    Input input(Player sender) {
        if (Bukkit.isPrimaryThread()) return captureInput(sender);
        var session = snapshot.sessions().get(sender);
        return session == null ? Input.EMPTY : session.input();
    }

    Language viewerLanguage(Audience viewer) {
        if (!(viewer instanceof Player player)) return Language.DEFAULT;
        if (Bukkit.isPrimaryThread()) return language.language(player);
        var session = snapshot.sessions().get(player);
        return session == null ? Language.DEFAULT : session.language();
    }

    Presentation presentation(ChatMessage message, Map<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>> items) {
        boolean primary = Bukkit.isPrimaryThread();
        var people = snapshot.people();
        return new Presentation(
                person(message.sender(), message.origin() == ChatOrigin.Player.MINECRAFT, primary, people),
                person(message.target(), false, primary, people), items::get);
    }

    private PlayerView person(ChatSender identity, boolean game, boolean primary, Map<UUID, PlayerView> people) {
        if (identity == null || identity.minecraftId().isEmpty()) return null;
        var id = identity.minecraftId().orElseThrow();
        if (!primary) {
            var person = people.get(id);
            return person != null ? person : plainName(identity.minecraftName(), NamedTextColor.WHITE);
        }
        var player = Bukkit.getPlayer(id);
        return player == null ? describeOffline(id, identity.minecraftName()) : describeGame(player, id, game);
    }

    private Session sample(Player player) {
        UUID id = player.getUniqueId();
        return new Session(id, language.language(player), describeGame(player, id, true), captureInput(player));
    }

    private PlayerView describeGame(Player player, UUID id, boolean game) {
        return new PlayerView(player.getName(), PlayerNameRenderer.playerName(player),
                names.captureStyle(player, names.current(id)), game && sources.isBedrock(id));
    }

    private PlayerView describeOffline(UUID id, String name) {
        var tag = names.current(id);
        if (tag.prefix().isEmpty() && tag.suffix().isEmpty()) return plainName(name, tag.usernameColor());
        return new PlayerView(name, PlayerNameRenderer.clickablePlayerName(Component.text(name), name),
                names.captureStyle(Bukkit.getOfflinePlayer(id), tag), false);
    }

    private static PlayerView plainName(String name, TextColor color) {
        return new PlayerView(name, PlayerNameRenderer.clickablePlayerName(Component.text(name), name),
                PlayerNameTagRenderer.Style.plain(color), false);
    }

    private static Input captureInput(Player sender) {
        var capabilities = EnumSet.of(ChatCapability.LINK, ChatCapability.PLAYER_MENTION,
                ChatCapability.ITEM, ChatCapability.INVENTORY);
        if (RolePermissions.isMember(sender)) capabilities.add(ChatCapability.MINI_MESSAGE);
        if (RolePermissions.isCustodian(sender)) capabilities.add(ChatCapability.BROADCAST_MENTION);
        // Placeholders with the same label can still have different native hover data.
        var hovers = new IdentityHashMap<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>>();
        var inventory = sender.getInventory();
        var mainHand = item(inventory.getItemInMainHand(), hovers);
        var slots = new HashMap<Integer, ChatItemSnapshot>();
        for (int slot = 0; slot < 36; slot++) {
            var item = item(inventory.getItem(slot), hovers);
            if (item != null) slots.put(slot, item);
        }
        return new Input(Set.copyOf(capabilities), Optional.ofNullable(mainHand), Map.copyOf(slots),
                Collections.unmodifiableMap(hovers));
    }

    private static ChatItemSnapshot item(ItemStack item, Map<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>> hovers) {
        if (item == null || item.getType() == Material.AIR || item.isEmpty()) return null;
        String name = PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
        if (name.isBlank() || name.length() > 512 || name.codePoints().anyMatch(Character::isISOControl))
            name = item.getType().getKey().asString();
        var facts = new ChatItemSnapshot(name, item.getAmount());
        try {
            hovers.put(facts, item.asHoverEvent());
        } catch (RuntimeException unsupported) {
            // The visible item label is still valid if its native data cannot be shown.
        }
        return facts;
    }

    record Input(Set<ChatCapability> capabilities, Optional<ChatItemSnapshot> hand, Map<Integer, ChatItemSnapshot> slots,
                 Map<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>> hovers) {
        static final Input EMPTY = new Input(Set.of(ChatCapability.LINK), Optional.empty(), Map.of(), Map.of());

        ChatProcessingContext context(PlayerMentions mentions) {
            return new ChatProcessingContext(capabilities, mentions, hand, slots);
        }
    }

    private record Session(UUID id, Language language, PlayerView person, Input input) { }
    private record Snapshot(Map<Player, Session> sessions, Map<UUID, PlayerView> people) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }
}
