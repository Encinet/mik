package org.encinet.mik.feature.player.utility;

import org.encinet.mik.shared.role.RolePermissions;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.guidance.TipTriggers;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;
import org.encinet.mik.shared.util.PlayerDisplay;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class SimpleFeaturesModule implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String SPAWN_WORLD = "world";
    private static final Pattern SPAWN_MENTION = Pattern.compile(
            "出生[点點]|主城|回城|大[厅廳]|/(?:spawn|lobby)(?![a-z0-9_])"
                    + "|(?<![\\p{L}\\p{N}_])(?:spawn|lobby)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HAT_MENTION = Pattern.compile(
            "帽子|戴帽|[头頭][饰飾]|(?:戴|穿|放)(?:到|在)?[头頭]上|/hat(?![a-z0-9_])"
                    + "|(?<![\\p{L}\\p{N}_])(?:hats?|headwear)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String REMOVE_ITEMS_PERMISSION = "mik.command.removeitems";
    private static final Pattern REMOVE_ITEMS_MENTION = Pattern.compile(
            "掉落物|(?:地上|地面上?)(?:的)?物品|/(?:rmitems|removeitems)(?![a-z0-9_])"
                    + "|(?<![\\p{L}\\p{N}_])(?:dropped\\s+items?|item\\s+drops?|rmitems|removeitems)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final double SPAWN_X = 33.5;
    private static final double SPAWN_Y = 65.5;
    private static final double SPAWN_Z = 82.5;
    private static final float SPAWN_YAW = -90.0f;
    private static final float SPAWN_PITCH = 0.0f;
    private static final int MAX_SELF_KICK_REASON_LENGTH = 200;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final SelfKickMessageService selfKickMessages;
    private final Map<UUID, SelfKickMessageService.Request> pendingSelfKicks = new HashMap<>();

    public SimpleFeaturesModule(JavaPlugin plugin, LanguageService languageService, TipScope tips) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.selfKickMessages = new SelfKickMessageService(languageService);
        tips.define("spawn-return", Message.SPAWN_TIP_RETURN_MM)
                .ambient()
                .on(TipTriggers.chatMentions(SPAWN_MENTION))
                .when(player -> Bukkit.getWorld(SPAWN_WORLD) != null)
                .build();
        tips.define("hat-wear", Message.HAT_TIP_WEAR_MM)
                .ambient()
                .on(TipTriggers.chatMentions(HAT_MENTION))
                .build();
        tips.define("remove-items", Message.REMOVEITEMS_TIP_USAGE_MM)
                .ambient()
                .on(TipTriggers.chatMentions(REMOVE_ITEMS_MENTION))
                .when(player -> player.hasPermission(REMOVE_ITEMS_PERMISSION))
                .build();
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Register commands
     *
     * @param lifecycleManager the lifecycle event manager
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(Commands.literal("kick")
                            .executes(ctx -> selfKick(ctx.getSource().getSender(), null))
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                    .executes(ctx -> selfKick(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "reason"))))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.SELF_KICK_COMMAND_DESCRIPTION));

            // Register /spawn command
            commands.register(Commands.literal("spawn")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            World spawnWorld = Bukkit.getWorld(SPAWN_WORLD);
                            if (spawnWorld == null) {
                                player.sendMessage(languageService.text(player, Message.SPAWN_WORLD_MISSING, NamedTextColor.RED));
                                return 0;
                            }
                            player.teleport(new Location(
                                    spawnWorld,
                                    SPAWN_X, SPAWN_Y, SPAWN_Z,
                                    SPAWN_YAW, SPAWN_PITCH
                            ));
                            return Command.SINGLE_SUCCESS;
                        } else {
                            sender.sendMessage(playerOnlyMessage());
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT, Message.SPAWN_COMMAND_DESCRIPTION), List.of("lobby"));

            // Register /hat command
            commands.register(Commands.literal("hat")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            return wearHat(player);
                        }
                        sender.sendMessage(playerOnlyMessage());
                        return 0;
                    }).build(), languageService.t(Language.DEFAULT, Message.HAT_COMMAND_DESCRIPTION));

            // Register /tpany command
            commands.register(
                    Commands.literal("tpany")
                            .requires(source -> {
                                var sender = source.getSender();
                                return sender instanceof Player && RolePermissions.canModerate(sender);
                            })
                            .executes(ctx -> {
                                sendTpanyUsage(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(
                                    Commands.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                Player self = (Player) ctx.getSource().getSender();
                                                String targetName = StringArgumentType.getString(ctx, "player");

                                                Player online = Bukkit.getPlayerExact(targetName);
                                                if (online != null) {
                                                    self.teleportAsync(online.getLocation());
                                                    self.sendMessage(languageService.rich(self, Message.TPANY_DONE_ONLINE_RICH, NamedTextColor.GREEN,
                                                            RichArg.component("player", PlayerDisplay.name(online, NamedTextColor.YELLOW))));
                                                    return 1;
                                                }

                                                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                                                Location loc = target != null ? target.getLocation() : null;
                                                if (loc == null) {
                                                    self.sendMessage(languageService.rich(self, Message.TPANY_NOT_FOUND_RICH, NamedTextColor.RED,
                                                            RichArg.component("player", Component.text(targetName, NamedTextColor.YELLOW))));
                                                    return 0;
                                                }

                                                self.teleportAsync(loc);
                                                self.sendMessage(languageService.rich(self, Message.TPANY_DONE_OFFLINE_RICH, NamedTextColor.GREEN,
                                                        RichArg.component("player", Component.text(targetName, NamedTextColor.YELLOW)),
                                                        RichArg.component("world", Component.text(loc.getWorld().getName(), NamedTextColor.GRAY)),
                                                        RichArg.component("location", Component.text(String.format("(%.1f, %.1f, %.1f)",
                                                                loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY))));
                                                return 1;
                                            })
                            )
                            .build(),
                    languageService.t(Language.DEFAULT, Message.TPANY_COMMAND_DESCRIPTION)
            );

            // Register /removeitems command
            commands.register(Commands.literal("removeitems")
                    .requires(source -> source.getSender().hasPermission(REMOVE_ITEMS_PERMISSION))
                    // 带半径参数的分支
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 300))
                            .executes(ctx -> {
                                int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                return removeItems(ctx.getSource(), radius);
                            }))
                    // 无参数分支，使用默认半径50
                    .executes(ctx -> removeItems(ctx.getSource(), 50)).build(),
                    languageService.t(Language.DEFAULT, Message.REMOVEITEMS_COMMAND_DESCRIPTION), List.of("rmitems"));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingSelfKicks.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSelfKick(PlayerKickEvent event) {
        SelfKickMessageService.Request request = pendingSelfKicks.remove(
                event.getPlayer().getUniqueId());
        if (request == null || event.getCause() != PlayerKickEvent.Cause.SELF_INTERACTION) {
            return;
        }

        event.leaveMessage(null);
        broadcastSelfKick(event.getPlayer(), request);
    }

    private int selfKick(CommandSender sender, String rawReason) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(playerOnlyMessage());
            return 0;
        }

        String reason = normalizeSelfKickReason(rawReason);
        if (reason != null && reason.codePointCount(0, reason.length()) > MAX_SELF_KICK_REASON_LENGTH) {
            player.sendMessage(languageService.text(player, Message.SELF_KICK_REASON_TOO_LONG,
                    NamedTextColor.RED, MAX_SELF_KICK_REASON_LENGTH));
            return 0;
        }

        UUID playerId = player.getUniqueId();
        SelfKickMessageService.Request request = selfKickMessages.createRequest(reason);
        pendingSelfKicks.put(playerId, request);
        player.kick(selfKickMessages.message(player, player, request),
                PlayerKickEvent.Cause.SELF_INTERACTION);
        Bukkit.getScheduler().runTask(plugin, () -> pendingSelfKicks.remove(playerId));
        return Command.SINGLE_SUCCESS;
    }

    private String normalizeSelfKickReason(String rawReason) {
        if (rawReason == null) {
            return null;
        }
        String reason = rawReason.replaceAll("\\s+", " ").trim();
        return reason.isEmpty() ? null : reason;
    }

    private void broadcastSelfKick(Player kickedPlayer,
                                   SelfKickMessageService.Request request) {
        Map<Language, Component> localizedMessages = new EnumMap<>(Language.class);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getUniqueId().equals(kickedPlayer.getUniqueId())) continue;
            Language language = languageService.language(recipient);
            recipient.sendMessage(localizedMessages.computeIfAbsent(language,
                    ignored -> selfKickMessages.message(language, kickedPlayer, request)));
        }

        Bukkit.getConsoleSender().sendMessage(selfKickMessages.message(
                Language.DEFAULT, kickedPlayer, request));
    }

    private int removeItems(CommandSourceStack source, int radius) {
        CommandSender sender = source.getSender();

        // 获取执行位置（支持 execute at/positioned）
        Location location;
        World world;

        if (source.getExecutor() != null) {
            // 有实体执行者（玩家或其他实体）
            location = source.getExecutor().getLocation();
        } else {
            source.getLocation();// 通过 execute positioned 等指定了位置
            location = source.getLocation();
        }
        world = location.getWorld();

        if (world == null) {
            sender.sendMessage(Component.text(t(sender, Message.REMOVEITEMS_NO_WORLD), NamedTextColor.RED));
            return 0;
        }

        int count = 0;
        // 获取指定位置周围指定半径内的所有实体
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Item) {
                entity.remove();
                count++;
            }
        }

        // 格式化位置信息
        String posInfo = String.format("%.1f, %.1f, %.1f",
                location.getX(), location.getY(), location.getZ());

        sender.sendMessage(removeItemsMessage(sender, world.getName(), posInfo, count, radius));

        return Command.SINGLE_SUCCESS;
    }

    private int wearHat(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isEmpty(hand)) {
            player.sendMessage(mm(player, Message.HAT_EMPTY_HAND_MM));
            return 0;
        }

        ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(hand.clone());
        player.getInventory().setItemInMainHand(isEmpty(helmet) ? new ItemStack(Material.AIR) : helmet.clone());
        player.sendMessage(mm(player, Message.HAT_SUCCESS_MM));
        return Command.SINGLE_SUCCESS;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private void sendTpanyUsage(CommandSender sender) {
        sender.sendMessage(Component.text()
                .append(Component.text(t(sender, Message.TPANY_USAGE), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text(t(sender, Message.TPANY_USAGE_COMMAND), NamedTextColor.AQUA))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.TPANY_USAGE_DESC), NamedTextColor.GRAY))
                .build());
    }

    private Component removeItemsMessage(CommandSender sender, String worldName, String posInfo, int count, int radius) {
        Component world = Component.text(worldName, NamedTextColor.YELLOW);
        Component location = Component.text("[" + posInfo + "]", NamedTextColor.YELLOW);
        Component removed = Component.text(Integer.toString(count), NamedTextColor.YELLOW);
        Component radiusText = Component.text(t(sender, Message.REMOVEITEMS_RADIUS, radius), NamedTextColor.GRAY);
        if (sender instanceof Player player) {
            return languageService.rich(player, Message.REMOVEITEMS_DONE_RICH, NamedTextColor.GREEN,
                    RichArg.number("count", removed, count),
                    RichArg.component("world", world),
                    RichArg.component("location", location),
                    RichArg.component("radius", radiusText));
        }
        return Component.text(languageService.format(Language.DEFAULT, Message.REMOVEITEMS_DONE_RICH,
                TextArg.of("count", count),
                TextArg.of("countValue", count),
                TextArg.of("world", worldName),
                TextArg.of("location", "[" + posInfo + "]"),
                TextArg.of("radius", t(sender, Message.REMOVEITEMS_RADIUS, radius))), NamedTextColor.GREEN);
    }

    private Component playerOnlyMessage() {
        return Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED);
    }

    private String t(CommandSender sender, Message message, Object... args) {
        if (sender instanceof Player player) {
            return languageService.t(player, message, args);
        }
        return languageService.t(Language.DEFAULT, message, args);
    }

}
