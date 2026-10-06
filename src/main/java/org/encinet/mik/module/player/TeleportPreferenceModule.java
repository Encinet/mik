package org.encinet.mik.module.player;

import org.encinet.mik.module.role.RolePermissions;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.util.PlayerDisplay;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportPreferenceModule implements Listener {

    private static final Set<String> TP_COMMANDS = Set.of("tp", "teleport", "minecraft:tp", "minecraft:teleport");
    private static final long REQUEST_TIMEOUT_TICKS = 20L * 60L;
    private static final boolean DEFAULT_BLOCK_TELEPORTS_WHILE_AFK = false;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final Map<UUID, TeleportSettings> settingsCache = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportInitiator> teleportInitiators = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Set<TeleportAuthorization> authorizations = ConcurrentHashMap.newKeySet();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public TeleportPreferenceModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
    }

    public void enable() {
        settingsFile = new File(plugin.getDataFolder(), "teleport-preferences.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("teleport-preferences.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create teleport-preferences.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("TeleportPreferenceModule enabled");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("tpaccept")
                            .executes(context -> respondToRequest(
                                    context.getSource().getSender(), true))
                            .build(),
                    languageService.t(Language.DEFAULT,
                            Message.TELEPORT_ACCEPT_COMMAND_DESCRIPTION));
            commands.register(Commands.literal("tpdeny")
                            .executes(context -> respondToRequest(
                                    context.getSource().getSender(), false))
                            .build(),
                    languageService.t(Language.DEFAULT,
                            Message.TELEPORT_DENY_COMMAND_DESCRIPTION));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (args.length < 3) return;

        String cmd = args[0].toLowerCase(Locale.ROOT);
        if (!TP_COMMANDS.contains(cmd)) return;

        String victimArg = args[1];
        if (victimArg.startsWith("@")) return;

        Player sender = event.getPlayer();
        Player victim = Bukkit.getPlayerExact(victimArg);
        if (victim != null && !victim.equals(sender)) {
            TeleportAuthorization authorization = new TeleportAuthorization(
                    sender.getUniqueId(), victim.getUniqueId(), event.getMessage());
            if (authorizations.remove(authorization)) {
                rememberTeleportInitiator(victim, sender);
                return;
            }

            if (RolePermissions.canModerate(sender)) {
                rememberTeleportInitiator(victim, sender);
                return;
            }

            TeleportSettings settings = getSettings(victim.getUniqueId());
            if (settings.blockTeleportsWhileAfk()
                    && afkService.isAfk(victim.getUniqueId())) {
                denyTeleport(event, sender, victim);
                return;
            }

            switch (settings.policy()) {
                case ALWAYS_ALLOW -> rememberTeleportInitiator(victim, sender);
                case REQUIRE_CONSENT -> {
                    event.setCancelled(true);
                    requestConsent(sender, victim, event.getMessage());
                }
                case ALWAYS_DENY -> denyTeleport(event, sender, victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        Player targetPlayer = event.getPlayer();
        TeleportInitiator initiator = teleportInitiators.remove(
                targetPlayer.getUniqueId());

        if (initiator != null) {
            targetPlayer.sendActionBar(Component.text(languageService.t(targetPlayer,
                    Message.TELEPORT_MOVED_HERE, initiator.senderName()),
                    NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        settingsCache.remove(playerId);
        teleportInitiators.remove(playerId);
        TeleportRequest incoming = pendingRequests.remove(playerId);
        if (incoming != null) {
            notifyRequestExpired(incoming.requesterId());
        }
        pendingRequests.forEach((targetId, request) -> {
            if (request.requesterId().equals(playerId)
                    && pendingRequests.remove(targetId, request)) {
                notifyRequestExpired(targetId);
            }
        });
        authorizations.removeIf(authorization ->
                authorization.senderId().equals(playerId)
                        || authorization.victimId().equals(playerId));
    }

    public void openMenu(Player player) {
        TeleportSettings settings = getSettings(player.getUniqueId());
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "teleport-settings",
                        Component.text(languageService.t(player, Message.TELEPORT_MENU_TITLE),
                                NamedTextColor.DARK_PURPLE))
                .layout(FloatingMenuLayouts.actions(3));
        menu.item("setting:teleport-policy", policyMaterial(settings.policy()),
                        policyLabel(player, settings.policy()))
                .primary((p, handle) -> cyclePolicy(p));
        menu.toggle("setting:block-teleports-while-afk",
                        settings.blockTeleportsWhileAfk(), Material.SHIELD,
                        Material.GRAY_DYE, afkToggleLabel(player,
                                settings.blockTeleportsWhileAfk()))
                .primary((p, handle) -> toggleAfkBlocking(p));
        menu.back(
                        Component.text(languageService.t(player, Message.BACK_TO_MAIN),
                                NamedTextColor.GREEN));
        FloatingMenus.present(player, menu.build());
    }

    private void cyclePolicy(Player player) {
        TeleportSettings current = getSettings(player.getUniqueId());
        TeleportSettings next = new TeleportSettings(
                current.policy().next(), current.blockTeleportsWhileAfk());
        updateSettings(player, next);
    }

    private void toggleAfkBlocking(Player player) {
        TeleportSettings current = getSettings(player.getUniqueId());
        TeleportSettings next = new TeleportSettings(
                current.policy(), !current.blockTeleportsWhileAfk());
        updateSettings(player, next);
    }

    private void updateSettings(Player player, TeleportSettings next) {
        settingsCache.put(player.getUniqueId(), next);
        saveSettings(player.getUniqueId(), next);
        openMenu(player);
    }

    public String summary(Player player) {
        TeleportSettings settings = getSettings(player.getUniqueId());
        String summary = switch (settings.policy()) {
            case ALWAYS_ALLOW -> languageService.t(player,
                    settings.blockTeleportsWhileAfk()
                            ? Message.TELEPORT_SUMMARY_AFK
                            : Message.TELEPORT_SUMMARY_ALLOW);
            case REQUIRE_CONSENT -> languageService.t(player,
                    Message.TELEPORT_SUMMARY_CONSENT);
            case ALWAYS_DENY -> languageService.t(player,
                    Message.TELEPORT_SUMMARY_DENY);
        };
        if (settings.policy() == TeleportPolicy.REQUIRE_CONSENT
                && settings.blockTeleportsWhileAfk()) {
            return summary + " · "
                    + languageService.t(player, Message.TELEPORT_BLOCK_AFK);
        }
        return summary;
    }

    private Component policyLabel(Player player, TeleportPolicy policy) {
        return Component.text(languageService.t(player, Message.TELEPORT_ALLOW),
                        NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, policyMessage(policy)),
                        policyColor(policy)));
    }

    private Component afkToggleLabel(Player player, boolean enabled) {
        return Component.text(languageService.t(player, Message.TELEPORT_BLOCK_AFK),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
    }

    private Message policyMessage(TeleportPolicy policy) {
        return switch (policy) {
            case ALWAYS_ALLOW -> Message.TELEPORT_POLICY_ALWAYS_ALLOW;
            case REQUIRE_CONSENT -> Message.TELEPORT_POLICY_REQUIRE_CONSENT;
            case ALWAYS_DENY -> Message.TELEPORT_POLICY_ALWAYS_DENY;
        };
    }

    private NamedTextColor policyColor(TeleportPolicy policy) {
        return switch (policy) {
            case ALWAYS_ALLOW -> NamedTextColor.GREEN;
            case REQUIRE_CONSENT -> NamedTextColor.GOLD;
            case ALWAYS_DENY -> NamedTextColor.RED;
        };
    }

    private Material policyMaterial(TeleportPolicy policy) {
        return switch (policy) {
            case ALWAYS_ALLOW -> Material.ENDER_PEARL;
            case REQUIRE_CONSENT -> Material.WRITABLE_BOOK;
            case ALWAYS_DENY -> Material.BARRIER;
        };
    }

    private TeleportSettings getSettings(UUID playerId) {
        return settingsCache.computeIfAbsent(playerId, this::loadSettings);
    }

    private TeleportSettings loadSettings(UUID playerId) {
        String path = playerId.toString();
        return new TeleportSettings(
                TeleportPolicy.fromId(settingsData.getString(
                        path + ".teleport-policy")),
                loadBoolean(path, "block-teleports-while-afk", DEFAULT_BLOCK_TELEPORTS_WHILE_AFK)
        );
    }

    private boolean loadBoolean(String path, String key, boolean fallback) {
        String fullPath = path + "." + key;
        if (settingsData.contains(fullPath)) {
            return settingsData.getBoolean(fullPath, fallback);
        }
        return fallback;
    }

    private void saveSettings(UUID playerId, TeleportSettings settings) {
        String path = playerId.toString();
        settingsData.set(path + ".teleport-policy", settings.policy().id());
        settingsData.set(path + ".block-teleports-while-afk", settings.blockTeleportsWhileAfk());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save teleport preferences for " + playerId + ": " + e.getMessage());
        }
    }

    private record TeleportSettings(
            TeleportPolicy policy,
            boolean blockTeleportsWhileAfk
    ) {}

    private void denyTeleport(PlayerCommandPreprocessEvent event,
                              Player sender, Player victim) {
        event.setCancelled(true);
        sender.sendMessage(languageService.rich(sender, Message.TELEPORT_DENIED,
                NamedTextColor.RED,
                RichArg.component("player",
                        PlayerDisplay.name(victim, NamedTextColor.YELLOW),
                        victim.getName())));
    }

    private void requestConsent(Player sender, Player victim, String command) {
        TeleportRequest request = new TeleportRequest(
                UUID.randomUUID(), sender.getUniqueId(), command);
        TeleportRequest replaced = pendingRequests.put(victim.getUniqueId(), request);
        if (replaced != null) {
            notifyRequestExpired(replaced.requesterId());
        }

        sender.sendMessage(languageService.rich(sender, Message.TELEPORT_REQUEST_SENT,
                NamedTextColor.YELLOW,
                RichArg.component("player",
                        PlayerDisplay.name(victim, NamedTextColor.YELLOW),
                        victim.getName())));

        Component accept = requestButton(victim, Message.TELEPORT_REQUEST_ACCEPT,
                NamedTextColor.GREEN, "/tpaccept");
        Component deny = requestButton(victim, Message.TELEPORT_REQUEST_DENY_ACTION,
                NamedTextColor.RED, "/tpdeny");
        victim.sendMessage(languageService.rich(victim,
                        Message.TELEPORT_REQUEST_RECEIVED, NamedTextColor.YELLOW,
                        RichArg.component("sender",
                                PlayerDisplay.name(sender, NamedTextColor.AQUA),
                                sender.getName()),
                        RichArg.component("command",
                                Component.text(command, NamedTextColor.GRAY), command))
                .append(Component.newline())
                .append(accept)
                .append(Component.space())
                .append(deny));

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> expireRequest(victim.getUniqueId(), request),
                REQUEST_TIMEOUT_TICKS);
    }

    private Component requestButton(Player player, Message label,
                                    NamedTextColor color, String command) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(languageService.t(player, label), color,
                        TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(
                        Component.text(command, NamedTextColor.GRAY)))
                .build();
    }

    private int respondToRequest(CommandSender sender, boolean accept) {
        Player target = requirePlayer(sender);
        if (target == null) {
            return 0;
        }

        TeleportRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(Component.text(languageService.t(target,
                    Message.TELEPORT_REQUEST_NONE), NamedTextColor.YELLOW));
            return 0;
        }

        Player requester = Bukkit.getPlayer(request.requesterId());
        if (!accept) {
            target.sendMessage(Component.text(languageService.t(target,
                    Message.TELEPORT_REQUEST_REJECTED), NamedTextColor.RED));
            if (requester != null) {
                requester.sendMessage(languageService.rich(requester,
                        Message.TELEPORT_DENIED, NamedTextColor.RED,
                        RichArg.component("player",
                                PlayerDisplay.name(target, NamedTextColor.YELLOW),
                                target.getName())));
            }
            return Command.SINGLE_SUCCESS;
        }

        if (requester == null) {
            target.sendMessage(Component.text(languageService.t(target,
                    Message.TELEPORT_REQUEST_FAILED), NamedTextColor.RED));
            return 0;
        }

        TeleportAuthorization authorization = new TeleportAuthorization(
                requester.getUniqueId(), target.getUniqueId(), request.command());
        authorizations.add(authorization);
        boolean executed;
        try {
            executed = requester.performCommand(
                    request.command().substring(1));
        } finally {
            authorizations.remove(authorization);
        }

        if (!executed) {
            teleportInitiators.remove(target.getUniqueId());
            target.sendMessage(Component.text(languageService.t(target,
                    Message.TELEPORT_REQUEST_FAILED), NamedTextColor.RED));
            requester.sendMessage(Component.text(languageService.t(requester,
                    Message.TELEPORT_REQUEST_FAILED), NamedTextColor.RED));
            return 0;
        }

        requester.sendMessage(languageService.rich(requester,
                Message.TELEPORT_REQUEST_APPROVED, NamedTextColor.GREEN,
                RichArg.component("player",
                        PlayerDisplay.name(target, NamedTextColor.YELLOW),
                        target.getName())));
        return Command.SINGLE_SUCCESS;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT,
                Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private void expireRequest(UUID targetId, TeleportRequest request) {
        if (!pendingRequests.remove(targetId, request)) {
            return;
        }
        notifyRequestExpired(targetId);
        notifyRequestExpired(request.requesterId());
    }

    private void notifyRequestExpired(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text(languageService.t(player,
                    Message.TELEPORT_REQUEST_EXPIRED), NamedTextColor.GRAY));
        }
    }

    private void rememberTeleportInitiator(Player victim, Player sender) {
        UUID victimId = victim.getUniqueId();
        TeleportInitiator initiator = new TeleportInitiator(
                UUID.randomUUID(), sender.getName());
        teleportInitiators.put(victimId, initiator);
        Bukkit.getScheduler().runTask(plugin,
                () -> teleportInitiators.remove(victimId, initiator));
    }

    private record TeleportRequest(
            UUID id,
            UUID requesterId,
            String command
    ) {}

    private record TeleportAuthorization(
            UUID senderId,
            UUID victimId,
            String command
    ) {}

    private record TeleportInitiator(
            UUID id,
            String senderName
    ) {}
}
