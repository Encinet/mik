package org.encinet.mik.module.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.i18n.TextArg;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.util.PlayerDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SimpleFeaturesModule implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String SPAWN_WORLD = "world";
    private static final double SPAWN_X = 33.5;
    private static final double SPAWN_Y = 65.5;
    private static final double SPAWN_Z = 82.5;
    private static final float SPAWN_YAW = -90.0f;
    private static final float SPAWN_PITCH = 0.0f;
    private static final int MAX_SELF_KICK_REASON_LENGTH = 200;
    private static final int TRASH_ITEMS_PER_PAGE = 9;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final SelfKickMessageService selfKickMessages;
    private final Map<UUID, SelfKickMessageService.Request> pendingSelfKicks = new HashMap<>();
    private final FloatingMenuScreen<Integer> trashScreen;

    public SimpleFeaturesModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.selfKickMessages = new SelfKickMessageService(languageService);
        this.trashScreen = new FloatingMenuScreen<>("trash", ignored -> 0, this::buildTrashMenu);
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
                    }).build(), languageService.t(Language.DEFAULT, Message.HAT_COMMAND_DESCRIPTION), List.of("head"));

            // Register /tpany command
            commands.register(
                    Commands.literal("tpany")
                            .requires(source -> {
                                var sender = source.getSender();
                                return sender instanceof Player && sender.hasPermission("group.helper");
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
                                                            RichArg.component("player", PlayerDisplay.name(online, NamedTextColor.YELLOW), online.getName())));
                                                    return 1;
                                                }

                                                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                                                Location loc = target != null ? target.getLocation() : null;
                                                if (loc == null) {
                                                    self.sendMessage(languageService.rich(self, Message.TPANY_NOT_FOUND_RICH, NamedTextColor.RED,
                                                            RichArg.component("player", Component.text(targetName, NamedTextColor.YELLOW), targetName)));
                                                    return 0;
                                                }

                                                self.teleportAsync(loc);
                                                self.sendMessage(languageService.rich(self, Message.TPANY_DONE_OFFLINE_RICH, NamedTextColor.GREEN,
                                                        RichArg.component("player", Component.text(targetName, NamedTextColor.YELLOW), targetName),
                                                        RichArg.component("world", Component.text(loc.getWorld().getName(), NamedTextColor.GRAY), loc.getWorld().getName()),
                                                        RichArg.component("location", Component.text(String.format("(%.1f, %.1f, %.1f)",
                                                                loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY), "")));
                                                return 1;
                                            })
                            )
                            .build(),
                    languageService.t(Language.DEFAULT, Message.TPANY_COMMAND_DESCRIPTION)
            );

            // Register /trash command
            commands.register(Commands.literal("trash")
                    .executes(ctx -> {
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            trashScreen.open(player, 0);
                            return Command.SINGLE_SUCCESS;
                        }
                        ctx.getSource().getSender().sendMessage(
                                playerOnlyMessage()
                        );
                        return 0;
                    }).build(), languageService.t(Language.DEFAULT, Message.TRASH_COMMAND_DESCRIPTION), List.of("trashcan", "garbage"));

            // Register /removeitems command
            commands.register(Commands.literal("removeitems")
                    .requires(source -> source.getSender().hasPermission("mik.command.removeitems"))
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

    private FloatingMenuDefinition buildTrashMenu(FloatingMenuContext<Integer> context) {
        Player player = context.player();
        List<TrashEntry> carried = carriedItems(player);
        FloatingMenuPage page = new FloatingMenuPage(context.state(), carried.size(),
                TRASH_ITEMS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "trash",
                        Component.text(languageService.t(player, Message.TRASH_TITLE),
                                NamedTextColor.RED, TextDecoration.BOLD))
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.cards("items", 3, 3),
                        FloatingMenuLayouts.navigation("controls")));

        if (carried.isEmpty()) {
            menu.information("empty", Component.text("0", NamedTextColor.GRAY)
                            .append(Component.newline())
                            .append(Component.text(languageService.t(player,
                                    Message.TRASH_TITLE_HINT), NamedTextColor.DARK_GRAY)))
                    .region("items");
        } else {
            for (TrashEntry entry : page.slice(carried)) {
                ItemStack snapshot = entry.item().clone();
                menu.item("inventory:" + entry.slot(), snapshot, snapshot.effectiveName())
                        .region("items")
                        .primary((p, handle) -> handle.feedback(
                                Component.text(languageService.t(p, Message.TRASH_TITLE_HINT),
                                        NamedTextColor.YELLOW), FloatingMenuFeedbackKind.INFO))
                        .hotkey((p, handle) -> deleteCarriedItem(
                                context, entry.slot(), snapshot));
            }
        }

        menu.pagination("controls", page, context::setState);
        menu.dismiss(
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("controls");
        return menu.build();
    }

    private void deleteCarriedItem(FloatingMenuContext<Integer> context, int slot,
                                   ItemStack expected) {
        Player player = context.player();
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir() || !current.isSimilar(expected)
                || current.getAmount() != expected.getAmount()) {
            context.feedback(Component.text(languageService.t(player, Message.TRASH_TITLE_HINT),
                    NamedTextColor.RED), FloatingMenuFeedbackKind.ERROR);
            context.redraw();
            return;
        }
        Component itemName = expected.effectiveName();
        int amount = expected.getAmount();
        player.getInventory().setItem(slot, null);
        context.feedback(Component.text("× ", NamedTextColor.RED)
                        .append(itemName)
                        .append(Component.text(" ×" + amount, NamedTextColor.GRAY)),
                FloatingMenuFeedbackKind.SUCCESS);
        context.redraw();
    }

    private static List<TrashEntry> carriedItems(Player player) {
        List<TrashEntry> result = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                result.add(new TrashEntry(slot, item.clone()));
            }
        }
        return List.copyOf(result);
    }

    private record TrashEntry(int slot, ItemStack item) { }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingSelfKicks.remove(playerId);
        trashScreen.forget(playerId);
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
                    RichArg.component("count", removed, Integer.toString(count)),
                    RichArg.component("world", world, worldName),
                    RichArg.component("location", location, "[" + posInfo + "]"),
                    RichArg.component("radius", radiusText, t(sender, Message.REMOVEITEMS_RADIUS, radius)));
        }
        return Component.text(languageService.format(Language.DEFAULT, Message.REMOVEITEMS_DONE_RICH,
                TextArg.of("count", count),
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
