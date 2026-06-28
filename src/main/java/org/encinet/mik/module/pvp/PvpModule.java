package org.encinet.mik.module.pvp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.util.PlayerDisplay;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PvpModule implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final PvpSettingsStore settingsStore;
    private final PvpCombatController combatController;
    private final PvpMenuController menuController;

    public PvpModule(JavaPlugin plugin, MenuNavigation menuNavigation, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.settingsStore = new PvpSettingsStore(plugin);
        this.combatController = new PvpCombatController(plugin, languageService, settingsStore);
        this.menuController = new PvpMenuController(plugin, menuNavigation, languageService, settingsStore, combatController);
    }

    public void enable() {
        settingsStore.enable();
        combatController.enable();
        menuController.enable();
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
                                        setTargetPvp(ctx.getSource().getSender(), target, !settingsStore.get(target.getUniqueId()).enabled());
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        settingsStore.invalidate(event.getPlayer().getUniqueId());
    }

    public void openMenu(Player player) {
        menuController.openMenu(player);
    }

    public void openMenu(Player viewer, Player target) {
        menuController.openMenu(viewer, target);
    }

    public void openAdminMenu(Player viewer, int requestedPage) {
        menuController.openAdminMenu(viewer, requestedPage);
    }

    public void togglePvp(Player player) {
        setPvp(player, !settingsStore.get(player.getUniqueId()).enabled());
    }

    public void setPvp(Player player, boolean enabled) {
        if (!enabled && isCombatTagged(player.getUniqueId())) {
            player.sendMessage(mm(player, Message.PVP_COMBAT_LOCKED_MM, combatTagRemainingSeconds(player.getUniqueId())));
            return;
        }
        PvpSettings current = settingsStore.get(player.getUniqueId());
        settingsStore.save(player.getUniqueId(), current.withEnabled(enabled));
        combatController.onPvpStateSet(player.getUniqueId(), enabled);
        player.sendMessage(mm(player, enabled ? Message.PVP_TOGGLED_ON_MM : Message.PVP_TOGGLED_OFF_MM));
    }

    public boolean isEnabled(Player player) {
        return settingsStore.get(player.getUniqueId()).enabled();
    }

    public String summary(Player player) {
        return languageService.t(player, settingsStore.get(player.getUniqueId()).enabled()
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

        PvpSettings current = settingsStore.get(target.getUniqueId());
        settingsStore.save(target.getUniqueId(), current.withEnabled(enabled));
        combatController.onPvpStateSet(target.getUniqueId(), enabled);

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
        PvpSettings settings = settingsStore.get(target.getUniqueId());
        Component message = mm(sender, Message.PVP_STATUS_TITLE_MM);
        if (sender instanceof Player viewer) {
            message = message.append(Component.newline())
                    .append(languageService.rich(viewer, Message.PVP_STATUS_PLAYER_RICH, NamedTextColor.GRAY,
                            RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW), target.getName())));
        } else {
            message = message.append(Component.newline())
                    .append(Component.text(languageService.t(Language.DEFAULT, Message.PVP_STATUS_PLAYER, target.getName()), NamedTextColor.GRAY));
        }
        message = message.append(Component.newline())
                .append(statusLine(sender, Message.PVP_STATE_LABEL, settings.enabled()))
                .append(Component.newline())
                .append(statusLine(sender, Message.PVP_MOB_PROTECTION_LABEL, settings.protectMobs()))
                .append(Component.newline())
                .append(statusLine(sender, Message.PVP_MOUNTED_DAMAGE_LABEL, settings.allowMountedMobDamage()))
                .append(Component.newline())
                .append(statusLine(sender, Message.PVP_ENABLE_ON_DEATH_LABEL, settings.enableOnDeath()));
        if (isCombatTagged(target.getUniqueId())) {
            message = message.append(Component.newline())
                    .append(combatLine(sender, target.getUniqueId()));
        }
        sender.sendMessage(message.append(Component.newline())
                .append(Component.text()
                        .append(Component.text(t(sender, Message.PVP_AUTO_ENABLE_LABEL) + ": ", NamedTextColor.GRAY))
                        .append(Component.text(t(sender, Message.PVP_AUTO_ENABLE_DESC), NamedTextColor.AQUA))
                        .build()));
    }

    private void sendHelp(CommandSender sender) {
        Component message = mm(sender, Message.PVP_HELP_TITLE_MM)
                .append(Component.newline())
                .append(mm(sender, Message.PVP_HELP_TOGGLE_MM))
                .append(Component.newline())
                .append(mm(sender, Message.PVP_HELP_GUI_MM))
                .append(Component.newline())
                .append(mm(sender, Message.PVP_HELP_STATUS_MM));
        if (sender.hasPermission("group." + Mik.GROUP_HELPER)) {
            message = message.append(Component.newline())
                    .append(mm(sender, Message.PVP_HELP_ADMIN_MM))
                    .append(Component.newline())
                    .append(mm(sender, Message.PVP_HELP_STAFF_CONTROL_MM));
        }
        sender.sendMessage(message);
    }

    private Component statusLine(CommandSender sender, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(t(sender, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();
    }

    public boolean isCombatTagged(UUID playerId) {
        return combatController.isCombatTagged(playerId);
    }

    private long combatTagRemainingSeconds(UUID playerId) {
        return combatController.combatTagRemainingSeconds(playerId);
    }

    private Component combatLine(CommandSender sender, UUID playerId) {
        return Component.text()
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_LABEL) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_VALUE, combatTagRemainingSeconds(playerId)), NamedTextColor.RED))
                .build();
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

    private boolean canManageOthers(Player player) {
        return player.hasPermission("group." + Mik.GROUP_HELPER);
    }

    private void denyManageOthers(Player player) {
        player.closeInventory();
        player.sendMessage(mm(player, Message.PVP_NO_PERMISSION_MM));
    }

}
