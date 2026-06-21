package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class BackModule implements Listener {

    private static final int MAX_BACK_HISTORY = 20;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Map<UUID, Deque<Location>> backHistory = new HashMap<>();
    private final Map<UUID, Deque<BackMove>> undoHistory = new HashMap<>();
    private final Map<UUID, PendingBackTeleport> pendingBackTeleports = new HashMap<>();

    public BackModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("BackModule enabled");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("back")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player == null) {
                                    return Command.SINGLE_SUCCESS;
                                }
                                runBack(player, 1);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("undo")
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        undoBack(player);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .then(Commands.argument("times", IntegerArgumentType.integer(1, MAX_BACK_HISTORY))
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        runBack(player, IntegerArgumentType.getInteger(ctx, "times"));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.BACK_COMMAND_DESCRIPTION)
            );

            commands.register(
                    Commands.literal("reback")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player == null) {
                                    return Command.SINGLE_SUCCESS;
                                }
                                undoBack(player);
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    languageService.t(Language.DEFAULT, Message.REBACK_COMMAND_DESCRIPTION)
            );
        });
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PendingBackTeleport pendingBackTeleport = pendingBackTeleports.get(playerId);
        if (pendingBackTeleport != null && isSamePrecisePosition(event.getTo(), pendingBackTeleport.target())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (isSameBlockPosition(from, to)) {
            return;
        }
        pushBackLocation(playerId, from);
        undoHistory.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        pushBackLocation(player.getUniqueId(), player.getLocation());
        undoHistory.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        backHistory.remove(playerId);
        undoHistory.remove(playerId);
        pendingBackTeleports.remove(playerId);
    }

    private void runBack(Player player, int times) {
        UUID playerId = player.getUniqueId();
        if (pendingBackTeleports.containsKey(playerId)) {
            player.sendMessage(message(player, Message.BACK_PENDING));
            return;
        }

        Deque<Location> history = backHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            player.sendMessage(message(player, Message.BACK_EMPTY));
            return;
        }

        int steps = Math.min(times, history.size());
        List<Location> consumedHistory = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            consumedHistory.add(history.removeFirst());
        }
        Location target = consumedHistory.getLast();
        if (target.getWorld() == null) {
            restoreConsumedBackHistory(playerId, consumedHistory);
            player.sendMessage(message(player, Message.BACK_WORLD_MISSING));
            return;
        }

        BackMove backMove = new BackMove(player.getLocation().clone(), cloneLocations(consumedHistory));
        if (!startPendingBackTeleport(playerId, target)) {
            restoreConsumedBackHistory(playerId, consumedHistory);
            player.sendMessage(message(player, Message.BACK_PENDING));
            return;
        }

        String template = languageService.t(player, steps == 1
                ? Message.BACK_SUCCESS_ONE_MM
                : Message.BACK_SUCCESS_MULTI_MM);
        teleportBack(player, target, MINI_MESSAGE.deserialize(template,
                Placeholder.unparsed("steps", Integer.toString(steps)),
                Placeholder.unparsed("count", Integer.toString(history.size())),
                Placeholder.unparsed("max", Integer.toString(MAX_BACK_HISTORY))),
                () -> pushUndoMove(playerId, backMove),
                () -> restoreConsumedBackHistory(playerId, consumedHistory));
    }

    private void undoBack(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingBackTeleports.containsKey(playerId)) {
            player.sendMessage(message(player, Message.BACK_PENDING));
            return;
        }

        Deque<BackMove> history = undoHistory.get(playerId);
        if (history == null || history.isEmpty()) {
            player.sendMessage(message(player, Message.BACK_UNDO_EMPTY_MM));
            return;
        }

        BackMove backMove = history.removeFirst();
        Location target = backMove.origin();
        if (target.getWorld() == null) {
            history.addFirst(backMove);
            player.sendMessage(message(player, Message.BACK_UNDO_WORLD_MISSING));
            return;
        }

        if (!startPendingBackTeleport(playerId, target)) {
            history.addFirst(backMove);
            player.sendMessage(message(player, Message.BACK_PENDING));
            return;
        }

        teleportBack(player, target, message(player, Message.BACK_UNDO_SUCCESS_MM),
                () -> restoreConsumedBackHistory(playerId, backMove.consumedHistory()),
                () -> history.addFirst(backMove));
    }

    private void teleportBack(Player player, Location target, Component successMessage, Runnable onSuccess, Runnable onFailure) {
        UUID playerId = player.getUniqueId();
        player.teleportAsync(target).whenComplete((success, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                finishPendingBackTeleport(playerId);
                if (!player.isOnline()) {
                    return;
                }
                if (error == null && Boolean.TRUE.equals(success)) {
                    onSuccess.run();
                    player.sendMessage(successMessage);
                    return;
                }
                onFailure.run();
                player.sendMessage(message(player, Message.BACK_TELEPORT_FAILED));
            });
        });
    }

    private Component message(Player player, Message message) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message));
    }

    private void pushBackLocation(UUID playerId, Location location) {
        pushLocation(backHistory.computeIfAbsent(playerId, _ -> new ArrayDeque<>()), location);
    }

    private void pushUndoMove(UUID playerId, BackMove backMove) {
        Deque<BackMove> history = undoHistory.computeIfAbsent(playerId, _ -> new ArrayDeque<>());
        history.addFirst(backMove);
        while (history.size() > MAX_BACK_HISTORY) {
            history.removeLast();
        }
    }

    private List<Location> cloneLocations(List<Location> locations) {
        return locations.stream().map(Location::clone).toList();
    }

    private void restoreConsumedBackHistory(UUID playerId, List<Location> consumedHistory) {
        Deque<Location> history = backHistory.computeIfAbsent(playerId, _ -> new ArrayDeque<>());
        for (int i = consumedHistory.size() - 1; i >= 0; i--) {
            history.addFirst(consumedHistory.get(i).clone());
        }
        while (history.size() > MAX_BACK_HISTORY) {
            history.removeLast();
        }
    }

    private void pushLocation(Deque<Location> history, Location location) {
        if (!history.isEmpty() && isSameBlockPosition(history.peekFirst(), location)) {
            return;
        }
        history.addFirst(location.clone());
        while (history.size() > MAX_BACK_HISTORY) {
            history.removeLast();
        }
    }

    private boolean isSameBlockPosition(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getWorld(), b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean isSamePrecisePosition(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getWorld(), b.getWorld())) return false;
        return Math.abs(a.getX() - b.getX()) < 1.0E-6
                && Math.abs(a.getY() - b.getY()) < 1.0E-6
                && Math.abs(a.getZ() - b.getZ()) < 1.0E-6;
    }

    private boolean startPendingBackTeleport(UUID playerId, Location target) {
        if (pendingBackTeleports.containsKey(playerId)) {
            return false;
        }
        pendingBackTeleports.put(playerId, new PendingBackTeleport(target.clone()));
        return true;
    }

    private void finishPendingBackTeleport(UUID playerId) {
        pendingBackTeleports.remove(playerId);
    }

    private record BackMove(Location origin, List<Location> consumedHistory) {
    }

    private record PendingBackTeleport(Location target) {
    }
}
