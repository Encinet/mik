package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.util.PlayerDisplay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvpModule implements Listener {

    private static final int SETTINGS_MENU_SIZE = 9;
    private static final int ADMIN_MENU_SIZE = 54;
    private static final int ADMIN_PAGE_SIZE = 45;
    private static final long AUTO_ENABLE_WINDOW_MILLIS = 8_000L;
    private static final long COMBAT_TAG_DURATION_MILLIS = 15_000L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String ACTION_BACK_ADMIN = "back:admin";
    private static final String ACTION_SETTING_PREFIX = "setting:";
    private static final String ACTION_ADMIN_PLAYER_PREFIX = "admin:player:";
    private static final String ACTION_ADMIN_PAGE_PREFIX = "admin:page:";

    private static final boolean DEFAULT_PVP_ENABLED = false;
    private static final boolean DEFAULT_PROTECT_MOBS = true;
    private static final boolean DEFAULT_ALLOW_MOUNTED_DAMAGE = true;
    private static final boolean DEFAULT_ENABLE_ON_DEATH = true;

    private final JavaPlugin plugin;
    private final MenuNavigation menuNavigation;
    private final LanguageService languageService;
    private final NamespacedKey actionKey;
    private final Map<UUID, PvpSettings> settingsCache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAttack> pendingAutoEnable = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatTaggedUntil = new ConcurrentHashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public PvpModule(JavaPlugin plugin, MenuNavigation menuNavigation, LanguageService languageService) {
        this.plugin = plugin;
        this.menuNavigation = menuNavigation;
        this.languageService = languageService;
        this.actionKey = new NamespacedKey(plugin, "pvp_action");
    }

    public void enable() {
        settingsFile = new File(plugin.getDataFolder(), "pvp-settings.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("pvp-settings.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create pvp-settings.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredCombatTags, 20L * 60L, 20L * 60L);
        plugin.getLogger().info("PvpModule enabled");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("pvp")
                .executes(ctx -> {
                    Player player = requirePlayer(ctx.getSource().getSender());
                    if (player != null) {
                        togglePvp(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                togglePvp(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                setPvp(player, true);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                setPvp(player, false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                sendStatus(ctx.getSource().getSender(), player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                openMenu(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("settings")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                openMenu(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            sendHelp(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("admin")
                        .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource().getSender());
                            if (player != null) {
                                openAdminMenu(player, 0);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Player target = onlineTarget(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            if (target != null) {
                                sendStatus(ctx.getSource().getSender(), target);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    Player target = onlineTarget(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                                    if (target != null) {
                                        sendStatus(ctx.getSource().getSender(), target);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("on")
                                .executes(ctx -> {
                                    setTargetPvp(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"), true);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("off")
                                .executes(ctx -> {
                                    setTargetPvp(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"), false);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> {
                                    Player target = onlineTarget(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                                    if (target != null) {
                                        setTargetPvp(ctx.getSource().getSender(), target, !settings(target.getUniqueId()).enabled());
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("gui")
                                .executes(ctx -> {
                                    Player viewer = requirePlayer(ctx.getSource().getSender());
                                    if (viewer != null) {
                                        Player target = onlineTarget(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                                        if (target != null) {
                                            openMenu(viewer, target);
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build(), languageService.t(Language.DEFAULT, Message.PVP_COMMAND_DESCRIPTION)));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isPvpMenuTitle(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, actionKey);
        if (action == null) return;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(viewer);
            return;
        }
        if (ACTION_BACK_ADMIN.equals(action)) {
            openAdminMenu(viewer, 0);
            return;
        }
        if (action.startsWith(ACTION_ADMIN_PAGE_PREFIX)) {
            if (!canManageOthers(viewer)) {
                denyManageOthers(viewer);
                return;
            }
            openAdminMenu(viewer, parseInt(action.substring(ACTION_ADMIN_PAGE_PREFIX.length()), 0));
            return;
        }
        if (action.startsWith(ACTION_ADMIN_PLAYER_PREFIX)) {
            if (!canManageOthers(viewer)) {
                denyManageOthers(viewer);
                return;
            }
            UUID targetId = parseUuid(action.substring(ACTION_ADMIN_PLAYER_PREFIX.length()));
            Player target = targetId != null ? Bukkit.getPlayer(targetId) : null;
            if (target == null) {
                viewer.sendMessage(mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM));
                openAdminMenu(viewer, 0);
                return;
            }
            if (event.isRightClick()) {
                openMenu(viewer, target);
            } else {
                setTargetPvp(viewer, target, !settings(target.getUniqueId()).enabled());
                openAdminMenu(viewer, currentAdminPage(title));
            }
            return;
        }
        if (!action.startsWith(ACTION_SETTING_PREFIX)) return;

        String[] parts = action.substring(ACTION_SETTING_PREFIX.length()).split(":", 2);
        if (parts.length != 2) return;

        SettingKey key = SettingKey.fromId(parts[0]);
        if (key == null) return;

        UUID targetId = parseUuid(parts[1]);
        Player target = targetId != null ? Bukkit.getPlayer(targetId) : null;
        if (target == null) {
            viewer.closeInventory();
            viewer.sendMessage(mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM));
            return;
        }
        if (!viewer.getUniqueId().equals(target.getUniqueId()) && !canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }

        toggleSetting(viewer, target, key);
        openMenu(viewer, target);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) return;

        Entity target = event.getEntity();
        if (target instanceof Player victim) {
            handlePlayerDamage(event, attacker, victim);
            return;
        }

        if (isProtectedMob(attacker, target)) {
            event.setCancelled(true);
            attacker.sendActionBar(mm(attacker, Message.PVP_MOB_PROTECTED_ACTIONBAR_MM));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConfirmedPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0) return;

        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!settings(attacker.getUniqueId()).enabled() || !settings(victim.getUniqueId()).enabled()) {
            return;
        }

        markCombat(attacker);
        markCombat(victim);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        settingsCache.remove(playerId);
        pendingAutoEnable.remove(playerId);
        pendingAutoEnable.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        combatTaggedUntil.remove(playerId);
        PvpSettings settings = settings(playerId);
        if (settings.enableOnDeath() && !settings.enabled()) {
            saveSettings(playerId, settings.withEnabled(true));
            pendingAutoEnable.remove(playerId);
            player.sendMessage(mm(player, Message.PVP_ENABLED_ON_DEATH_MM));
        }
    }

    public void openMenu(Player player) {
        openMenu(player, player);
    }

    public void openMenu(Player viewer, Player target) {
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (!self && !canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        PvpSettings settings = settings(target.getUniqueId());
        Component title = Component.text(self
                ? languageService.t(viewer, Message.PVP_MENU_TITLE)
                : languageService.t(viewer, Message.PVP_TARGET_MENU_TITLE_PREFIX) + " " + target.getName(), MenuItems.TITLE_COLOR);

        MenuBuilder.create(SETTINGS_MENU_SIZE, title)
                .item(0, sectionItem(viewer, target, self))
                .item(3, toggleItem(viewer, target, SettingKey.ENABLED, settings.enabled()))
                .item(4, toggleItem(viewer, target, SettingKey.PROTECT_MOBS, settings.protectMobs()))
                .item(5, toggleItem(viewer, target, SettingKey.ALLOW_MOUNTED_DAMAGE, settings.allowMountedMobDamage()))
                .item(6, toggleItem(viewer, target, SettingKey.ENABLE_ON_DEATH, settings.enableOnDeath()))
                .item(8, self ? backToMainItem(viewer) : backToAdminItem(viewer))
                .open(viewer);
    }

    public void openAdminMenu(Player viewer, int requestedPage) {
        if (!canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(players.size() / (double) ADMIN_PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        MenuBuilder builder = MenuBuilder.create(ADMIN_MENU_SIZE,
                Component.text(languageService.t(viewer, Message.PVP_ADMIN_MENU_TITLE, page + 1, totalPages), MenuItems.TITLE_COLOR));
        int from = page * ADMIN_PAGE_SIZE;
        int to = Math.min(players.size(), from + ADMIN_PAGE_SIZE);
        for (int index = from; index < to; index++) {
            builder.item(index - from, adminPlayerItem(viewer, players.get(index)));
        }
        if (page > 0) {
            builder.item(45, pageItem(viewer, Material.ARROW, Message.PVP_PREV_PAGE, page - 1));
        }
        builder.item(49, backToMainItem(viewer));
        if (page < totalPages - 1) {
            builder.item(53, pageItem(viewer, Material.ARROW, Message.PVP_NEXT_PAGE, page + 1));
        }
        builder.open(viewer);
    }

    public void togglePvp(Player player) {
        setPvp(player, !settings(player.getUniqueId()).enabled());
    }

    public void setPvp(Player player, boolean enabled) {
        if (!enabled && isCombatTagged(player.getUniqueId())) {
            player.sendMessage(mm(player, Message.PVP_COMBAT_LOCKED_MM, combatTagRemainingSeconds(player.getUniqueId())));
            return;
        }
        PvpSettings current = settings(player.getUniqueId());
        saveSettings(player.getUniqueId(), current.withEnabled(enabled));
        pendingAutoEnable.remove(player.getUniqueId());
        if (!enabled) {
            combatTaggedUntil.remove(player.getUniqueId());
        }
        player.sendMessage(mm(player, enabled ? Message.PVP_TOGGLED_ON_MM : Message.PVP_TOGGLED_OFF_MM));
    }

    public boolean isEnabled(Player player) {
        return settings(player.getUniqueId()).enabled();
    }

    public String summary(Player player) {
        return languageService.t(player, settings(player.getUniqueId()).enabled()
                ? Message.PVP_SUMMARY_ENABLED
                : Message.PVP_SUMMARY_DISABLED);
    }

    public Component stateLine(Player player, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(languageService.t(player, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();
    }

    private void handlePlayerDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        PvpSettings attackerSettings = settings(attacker.getUniqueId());
        PvpSettings victimSettings = settings(victim.getUniqueId());
        if (!victimSettings.enabled()) {
            event.setCancelled(true);
            attacker.sendActionBar(mm(attacker, Message.PVP_TARGET_DISABLED_ACTIONBAR_MM));
            return;
        }

        if (attackerSettings.enabled()) {
            return;
        }

        PendingAttack pending = pendingAutoEnable.get(attacker.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending != null && pending.targetId().equals(victim.getUniqueId()) && pending.expiresAt() >= now) {
            saveSettings(attacker.getUniqueId(), attackerSettings.withEnabled(true));
            pendingAutoEnable.remove(attacker.getUniqueId());
            attacker.sendActionBar(mm(attacker, Message.PVP_AUTO_ENABLED_ACTIONBAR_MM));
            return;
        }

        pendingAutoEnable.put(attacker.getUniqueId(), new PendingAttack(victim.getUniqueId(), now + AUTO_ENABLE_WINDOW_MILLIS));
        event.setCancelled(true);
        attacker.sendActionBar(mm(attacker, Message.PVP_AUTO_ENABLE_WARNING_ACTIONBAR_MM));
    }

    private boolean isProtectedMob(Player attacker, Entity target) {
        if (target instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null) {
            UUID ownerId = tameable.getOwner().getUniqueId();
            if (!ownerId.equals(attacker.getUniqueId())) {
                PvpSettings ownerSettings = settings(ownerId);
                if (ownerSettings.enabled() && ownerSettings.protectMobs()) {
                    return true;
                }
            }
        }

        for (Entity passenger : target.getPassengers()) {
            if (!(passenger instanceof Player rider)) {
                continue;
            }
            if (rider.getUniqueId().equals(attacker.getUniqueId())) {
                continue;
            }
            PvpSettings riderSettings = settings(rider.getUniqueId());
            PvpSettings attackerSettings = settings(attacker.getUniqueId());
            boolean mountedDamageAllowed = riderSettings.enabled()
                    && attackerSettings.enabled()
                    && riderSettings.allowMountedMobDamage();
            if (riderSettings.protectMobs() && !mountedDamageAllowed) {
                return true;
            }
        }
        return false;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof AreaEffectCloud cloud) {
            ProjectileSource source = cloud.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private void setTargetPvp(CommandSender sender, String targetName, boolean enabled) {
        Player target = onlineTarget(sender, targetName);
        if (target != null) {
            setTargetPvp(sender, target, enabled);
        }
    }

    private void setTargetPvp(CommandSender sender, Player target, boolean enabled) {
        if (!enabled && sender instanceof Player viewer && viewer.getUniqueId().equals(target.getUniqueId())
                && isCombatTagged(target.getUniqueId())) {
            viewer.sendMessage(mm(viewer, Message.PVP_COMBAT_LOCKED_MM, combatTagRemainingSeconds(target.getUniqueId())));
            return;
        }

        PvpSettings current = settings(target.getUniqueId());
        saveSettings(target.getUniqueId(), current.withEnabled(enabled));
        pendingAutoEnable.remove(target.getUniqueId());
        if (!enabled) {
            combatTaggedUntil.remove(target.getUniqueId());
        }

        if (sender instanceof Player viewer) {
            viewer.sendMessage(languageService.rich(viewer, Message.PVP_SET_OTHER_RICH, NamedTextColor.GREEN,
                    RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW), target.getName()),
                    RichArg.component("state", Component.text(languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY), languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF))));
        } else {
            sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PVP_SET_OTHER,
                    target.getName(), languageService.t(Language.DEFAULT, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)), NamedTextColor.GREEN));
        }

        target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                languageService.t(target, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
    }

    private Player onlineTarget(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(mm(sender, Message.PVP_PLAYER_NOT_FOUND_MM));
        }
        return target;
    }

    private void sendStatus(CommandSender sender, Player target) {
        PvpSettings settings = settings(target.getUniqueId());
        sender.sendMessage(mm(sender, Message.PVP_STATUS_TITLE_MM));
        if (sender instanceof Player viewer) {
            viewer.sendMessage(languageService.rich(viewer, Message.PVP_STATUS_PLAYER_RICH, NamedTextColor.GRAY,
                    RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW), target.getName())));
        } else {
            sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PVP_STATUS_PLAYER, target.getName()), NamedTextColor.GRAY));
        }
        sender.sendMessage(statusLine(sender, Message.PVP_STATE_LABEL, settings.enabled()));
        sender.sendMessage(statusLine(sender, Message.PVP_MOB_PROTECTION_LABEL, settings.protectMobs()));
        sender.sendMessage(statusLine(sender, Message.PVP_MOUNTED_DAMAGE_LABEL, settings.allowMountedMobDamage()));
        sender.sendMessage(statusLine(sender, Message.PVP_ENABLE_ON_DEATH_LABEL, settings.enableOnDeath()));
        if (isCombatTagged(target.getUniqueId())) {
            sender.sendMessage(combatLine(sender, target.getUniqueId()));
        }
        sender.sendMessage(Component.text()
                .append(Component.text(t(sender, Message.PVP_AUTO_ENABLE_LABEL) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.PVP_AUTO_ENABLE_DESC), NamedTextColor.AQUA))
                .build());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm(sender, Message.PVP_HELP_TITLE_MM));
        sender.sendMessage(mm(sender, Message.PVP_HELP_TOGGLE_MM));
        sender.sendMessage(mm(sender, Message.PVP_HELP_GUI_MM));
        sender.sendMessage(mm(sender, Message.PVP_HELP_STATUS_MM));
        if (sender.hasPermission("group." + Mik.GROUP_HELPER)) {
            sender.sendMessage(mm(sender, Message.PVP_HELP_ADMIN_MM));
            sender.sendMessage(mm(sender, Message.PVP_HELP_STAFF_CONTROL_MM));
        }
    }

    private Component statusLine(CommandSender sender, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(t(sender, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();
    }

    private ItemStack sectionItem(Player viewer, Player target, boolean self) {
        List<Component> lore = new ArrayList<>();
        lore.add(stateLine(viewer, Message.PVP_STATE_LABEL, settings(target.getUniqueId()).enabled()));
        lore.add(stateLine(viewer, Message.PVP_MOB_PROTECTION_LABEL, settings(target.getUniqueId()).protectMobs()));
        lore.add(stateLine(viewer, Message.PVP_MOUNTED_DAMAGE_LABEL, settings(target.getUniqueId()).allowMountedMobDamage()));
        lore.add(stateLine(viewer, Message.PVP_ENABLE_ON_DEATH_LABEL, settings(target.getUniqueId()).enableOnDeath()));
        if (isCombatTagged(target.getUniqueId())) {
            lore.add(combatLine(viewer, target.getUniqueId()));
        }
        ItemStack item = self
                ? new ItemStack(Material.IRON_SWORD)
                : playerHead(target);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(self ? languageService.t(viewer, Message.PVP_MENU_TITLE) : target.getName(), NamedTextColor.GOLD));
        meta.lore(lore);
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleItem(Player viewer, Player target, SettingKey settingKey, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(viewer, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(viewer, settingKey.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(viewer, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? settingKey.enabledMaterial() : settingKey.disabledMaterial(),
                Component.text(languageService.t(viewer, settingKey.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, actionKey, ACTION_SETTING_PREFIX + settingKey.id() + ":" + target.getUniqueId());
    }

    private ItemStack adminPlayerItem(Player viewer, Player target) {
        ItemStack item = playerHead(target);
        PvpSettings settings = settings(target.getUniqueId());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(PlayerDisplay.name(target, settings.enabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_PLAYER_LORE), NamedTextColor.GRAY));
        lore.add(stateLine(viewer, Message.PVP_STATE_LABEL, settings.enabled()));
        lore.add(stateLine(viewer, Message.PVP_MOB_PROTECTION_LABEL, settings.protectMobs()));
        lore.add(stateLine(viewer, Message.PVP_MOUNTED_DAMAGE_LABEL, settings.allowMountedMobDamage()));
        lore.add(stateLine(viewer, Message.PVP_ENABLE_ON_DEATH_LABEL, settings.enableOnDeath()));
        if (isCombatTagged(target.getUniqueId())) {
            lore.add(combatLine(viewer, target.getUniqueId()));
        }
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_LEFT_CLICK), NamedTextColor.YELLOW));
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_RIGHT_CLICK), NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, org.bukkit.persistence.PersistentDataType.STRING,
                ACTION_ADMIN_PLAYER_PREFIX + target.getUniqueId());
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pageItem(Player viewer, Material material, Message label, int page) {
        return MenuItems.action(material,
                Component.text(languageService.t(viewer, label), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(viewer, Message.CLICK_OPEN), NamedTextColor.YELLOW)),
                actionKey, ACTION_ADMIN_PAGE_PREFIX + page);
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW, Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)), actionKey, ACTION_BACK_MAIN);
    }

    private ItemStack backToAdminItem(Player player) {
        return MenuItems.action(Material.ARROW, Component.text(languageService.t(player, Message.PVP_BACK_ADMIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)), actionKey, ACTION_BACK_ADMIN);
    }

    private void toggleSetting(Player viewer, Player target, SettingKey key) {
        PvpSettings current = settings(target.getUniqueId());
        PvpSettings next = current.toggle(key);
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (key == SettingKey.ENABLED && !next.enabled() && self && isCombatTagged(target.getUniqueId())) {
            viewer.sendMessage(mm(viewer, Message.PVP_COMBAT_LOCKED_MM, combatTagRemainingSeconds(target.getUniqueId())));
            return;
        }
        saveSettings(target.getUniqueId(), next);
        if (key == SettingKey.ENABLED) {
            pendingAutoEnable.remove(target.getUniqueId());
            if (!next.enabled()) {
                combatTaggedUntil.remove(target.getUniqueId());
            }
            if (!viewer.getUniqueId().equals(target.getUniqueId())) {
                target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                        languageService.t(target, next.enabled() ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
            }
        }
    }

    private boolean isPvpMenuTitle(String title) {
        return languageService.titleMatches(Message.PVP_MENU_TITLE, title)
                || languageService.titleStartsWith(Message.PVP_TARGET_MENU_TITLE_PREFIX, title)
                || languageService.titleStartsWith(Message.PVP_ADMIN_MENU_TITLE_PREFIX, title);
    }

    private int currentAdminPage(String title) {
        for (Language language : Language.values()) {
            String prefix = languageService.t(language, Message.PVP_ADMIN_MENU_TITLE_PREFIX);
            if (!title.startsWith(prefix)) {
                continue;
            }
            String suffix = title.substring(prefix.length()).trim();
            int slash = suffix.indexOf('/');
            if (slash <= 0) {
                return 0;
            }
            return Math.max(0, parseInt(suffix.substring(0, slash).trim(), 1) - 1);
        }
        return 0;
    }

    private PvpSettings settings(UUID playerId) {
        return settingsCache.computeIfAbsent(playerId, this::loadSettings);
    }

    private PvpSettings loadSettings(UUID playerId) {
        String path = playerId.toString();
        return new PvpSettings(
                loadBoolean(path, "enabled", DEFAULT_PVP_ENABLED),
                loadBoolean(path, "protect-mobs", DEFAULT_PROTECT_MOBS),
                loadBoolean(path, "allow-mounted-mob-damage", DEFAULT_ALLOW_MOUNTED_DAMAGE),
                loadBoolean(path, "enable-on-death", DEFAULT_ENABLE_ON_DEATH)
        );
    }

    private boolean loadBoolean(String path, String key, boolean fallback) {
        String fullPath = path + "." + key;
        if (settingsData.contains(fullPath)) {
            return settingsData.getBoolean(fullPath, fallback);
        }
        return fallback;
    }

    private void saveSettings(UUID playerId, PvpSettings settings) {
        settingsCache.put(playerId, settings);
        String path = playerId.toString();
        settingsData.set(path + ".enabled", settings.enabled());
        settingsData.set(path + ".protect-mobs", settings.protectMobs());
        settingsData.set(path + ".allow-mounted-mob-damage", settings.allowMountedMobDamage());
        settingsData.set(path + ".enable-on-death", settings.enableOnDeath());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save PVP settings for " + playerId + ": " + e.getMessage());
        }
    }

    private void markCombat(Player player) {
        combatTaggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + COMBAT_TAG_DURATION_MILLIS);
    }

    private boolean isCombatTagged(UUID playerId) {
        Long expiresAt = combatTaggedUntil.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            combatTaggedUntil.remove(playerId, expiresAt);
            return false;
        }
        return true;
    }

    private long combatTagRemainingSeconds(UUID playerId) {
        Long expiresAt = combatTaggedUntil.get(playerId);
        if (expiresAt == null) {
            return 0;
        }
        long remainingMillis = Math.max(0, expiresAt - System.currentTimeMillis());
        return Math.max(1, (remainingMillis + 999L) / 1_000L);
    }

    private Component combatLine(CommandSender sender, UUID playerId) {
        return Component.text()
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_LABEL) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_VALUE, combatTagRemainingSeconds(playerId)), NamedTextColor.RED))
                .build();
    }

    private void cleanupExpiredCombatTags() {
        long now = System.currentTimeMillis();
        combatTaggedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private Language senderLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageService.language(player);
        }
        return Language.DEFAULT;
    }

    private String t(CommandSender sender, Message message, Object... args) {
        return languageService.t(senderLanguage(sender), message, args);
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private Component mm(CommandSender sender, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(t(sender, message, args));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean canManageOthers(Player player) {
        return player.hasPermission("group." + Mik.GROUP_HELPER);
    }

    private void denyManageOthers(Player player) {
        player.closeInventory();
        player.sendMessage(mm(player, Message.PVP_NO_PERMISSION_MM));
    }

    private record PvpSettings(
            boolean enabled,
            boolean protectMobs,
            boolean allowMountedMobDamage,
            boolean enableOnDeath
    ) {
        PvpSettings withEnabled(boolean enabled) {
            return new PvpSettings(enabled, protectMobs, allowMountedMobDamage, enableOnDeath);
        }

        PvpSettings toggle(SettingKey key) {
            return switch (key) {
                case ENABLED -> new PvpSettings(!enabled, protectMobs, allowMountedMobDamage, enableOnDeath);
                case PROTECT_MOBS -> new PvpSettings(enabled, !protectMobs, allowMountedMobDamage, enableOnDeath);
                case ALLOW_MOUNTED_DAMAGE -> new PvpSettings(enabled, protectMobs, !allowMountedMobDamage, enableOnDeath);
                case ENABLE_ON_DEATH -> new PvpSettings(enabled, protectMobs, allowMountedMobDamage, !enableOnDeath);
            };
        }
    }

    private record PendingAttack(UUID targetId, long expiresAt) {
    }

    private enum SettingKey {
        ENABLED("enabled", Message.PVP_ENABLE, Message.PVP_ENABLE_DESC, Material.IRON_SWORD, Material.WOODEN_SWORD),
        PROTECT_MOBS("protect-mobs", Message.PVP_PROTECT_MOBS, Message.PVP_PROTECT_MOBS_DESC, Material.SHIELD, Material.GRAY_DYE),
        ALLOW_MOUNTED_DAMAGE("allow-mounted-mob-damage", Message.PVP_ALLOW_MOUNTED_DAMAGE, Message.PVP_ALLOW_MOUNTED_DAMAGE_DESC, Material.SADDLE, Material.GRAY_DYE),
        ENABLE_ON_DEATH("enable-on-death", Message.PVP_ENABLE_ON_DEATH, Message.PVP_ENABLE_ON_DEATH_DESC, Material.TOTEM_OF_UNDYING, Material.GRAY_DYE);

        private final String id;
        private final Message label;
        private final Message description;
        private final Material enabledMaterial;
        private final Material disabledMaterial;

        SettingKey(String id, Message label, Message description, Material enabledMaterial, Material disabledMaterial) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.enabledMaterial = enabledMaterial;
            this.disabledMaterial = disabledMaterial;
        }

        String id() {
            return id;
        }

        Message label() {
            return label;
        }

        Message description() {
            return description;
        }

        Material enabledMaterial() {
            return enabledMaterial;
        }

        Material disabledMaterial() {
            return disabledMaterial;
        }

        static SettingKey fromId(String id) {
            for (SettingKey key : values()) {
                if (key.id.equals(id)) {
                    return key;
                }
            }
            return null;
        }
    }
}
