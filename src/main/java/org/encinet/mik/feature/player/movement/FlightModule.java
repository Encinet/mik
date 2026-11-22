package org.encinet.mik.feature.player.movement;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public class FlightModule implements Listener, FlightSpeedControl {

    private static final float[] SPEED_PRESETS = {0.5f, 1.0f, 2.0f, 5.0f, 10.0f};

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final LongSupplier clock;
    private final Set<UUID> grantedFlight = new HashSet<>();
    private final Map<UUID, LimitedSpeed> limitedSpeeds = new HashMap<>();

    public FlightModule(JavaPlugin plugin, LanguageService languageService) {
        this(plugin, languageService, System::nanoTime);
    }

    FlightModule(JavaPlugin plugin, LanguageService languageService, LongSupplier clock) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.clock = clock;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("FlightModule enabled");
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            releaseExplorationSpeed(player);
            if (grantedFlight.remove(player.getUniqueId())) {
                removeGrantedFlight(player);
            }
        }
        grantedFlight.clear();
        limitedSpeeds.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(Commands.literal("fly")
                            .executes(ctx -> toggleFlight(ctx.getSource()))
                            .then(Commands.literal("on")
                                    .executes(ctx -> setFlight(ctx.getSource(), true)))
                            .then(Commands.literal("off")
                                    .executes(ctx -> setFlight(ctx.getSource(), false)))
                            .then(Commands.literal("status")
                                    .executes(ctx -> showStatus(ctx.getSource())))
                            .then(speedCommand())
                            .build(),
                    languageService.t(Language.DEFAULT, Message.FLY_COMMAND_DESCRIPTION));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!grantedFlight.contains(player.getUniqueId()) || hasNativeFlight(event.getNewGameMode())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (grantedFlight.contains(player.getUniqueId()) && !hasNativeFlight(player.getGameMode())) {
                player.setAllowFlight(true);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releaseExplorationSpeed(event.getPlayer());
        if (grantedFlight.remove(event.getPlayer().getUniqueId())) {
            removeGrantedFlight(event.getPlayer());
        }
    }

    // Check before space traversal can turn a movement into a teleport. PlayerTeleportEvent
    // has a separate handler list and does not reach this listener.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LimitedSpeed limit = limitedSpeeds.get(player.getUniqueId());
        if (limit == null || !event.hasExplicitlyChangedPosition()
                || !player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return;
        }

        float current = player.getFlySpeed();
        if (current != limit.applied()) {
            limit = applyLimitedSpeed(player, limit, current, limit.ceiling());
        }

        // Use the previous event position, not the player's position just before this packet:
        // Paper coalesces small displacements before firing PlayerMoveEvent.
        Location from = event.getFrom();
        Location to = event.getTo();
        double horizontal = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double vertical = Math.abs(to.getY() - from.getY());
        if (!limit.movement().tryMove(horizontal, vertical, clock.getAsLong())) {
            // Paper rolls back without firing a teleport event (which would release the ceiling).
            event.setCancelled(true);
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> speedCommand() {
        return Commands.literal("speed")
                .executes(ctx -> showSpeed(ctx.getSource()))
                .then(speedArgument())
                .then(Commands.literal("reset")
                        .executes(ctx -> resetSpeed(ctx.getSource())));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Float> speedArgument() {
        return Commands.argument("speed", FloatArgumentType.floatArg(
                        FlightSpeed.MIN_USER_SPEED,
                        FlightSpeed.MAX_USER_SPEED))
                .executes(ctx -> setSpeed(ctx.getSource(), FloatArgumentType.getFloat(ctx, "speed")));
    }

    private int toggleFlight(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        return setFlight(player, !grantedFlight.contains(player.getUniqueId()));
    }

    private int setFlight(CommandSourceStack source, boolean enabled) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        return setFlight(player, enabled);
    }

    private int setFlight(Player player, boolean enabled) {
        if (enabled) {
            grantedFlight.add(player.getUniqueId());
            player.setAllowFlight(true);
            player.sendMessage(languageService.text(player, Message.FLY_ENABLED, NamedTextColor.GREEN));
            sendSpeedPresets(player);
            return Command.SINGLE_SUCCESS;
        }

        grantedFlight.remove(player.getUniqueId());
        if (hasNativeFlight(player.getGameMode())) {
            player.sendMessage(languageService.text(player, Message.FLY_NATIVE_MODE, NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        removeGrantedFlight(player);
        player.sendMessage(languageService.text(player, Message.FLY_DISABLED, NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int showStatus(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        boolean enabled = hasNativeFlight(player.getGameMode())
                || grantedFlight.contains(player.getUniqueId())
                || player.getAllowFlight();
        String state = languageService.t(player, enabled ? Message.FLY_STATE_ENABLED : Message.FLY_STATE_DISABLED);
        String speed = FlightSpeed.format(FlightSpeed.fromBukkit(player.getFlySpeed()));
        player.sendMessage(languageService.text(player, Message.FLY_STATUS, enabled
                ? NamedTextColor.GREEN
                : NamedTextColor.YELLOW, state, speed));
        sendSpeedLimit(player);
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private int showSpeed(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        String speed = FlightSpeed.format(FlightSpeed.fromBukkit(player.getFlySpeed()));
        player.sendMessage(languageService.text(player, Message.FLY_SPEED_CURRENT, NamedTextColor.GREEN, speed));
        sendSpeedLimit(player);
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private int setSpeed(CommandSourceStack source, float requestedSpeed) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        applyRequestedSpeed(player, FlightSpeed.toBukkit(requestedSpeed));
        String speed = FlightSpeed.format(FlightSpeed.fromBukkit(player.getFlySpeed()));
        player.sendMessage(languageService.text(player, Message.FLY_SPEED_SET, NamedTextColor.GREEN, speed));
        sendSpeedLimit(player);
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private int resetSpeed(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        applyRequestedSpeed(player, FlightSpeed.toBukkit(FlightSpeed.DEFAULT_USER_SPEED));
        player.sendMessage(languageService.text(player, Message.FLY_SPEED_RESET, NamedTextColor.GREEN));
        sendSpeedLimit(player);
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private void sendSpeedPresets(Player player) {
        float currentSpeed = FlightSpeed.fromBukkit(player.getFlySpeed());
        Component message = Component.text(languageService.t(player, Message.FLY_SPEED_PRESETS) + ": ", NamedTextColor.GRAY);
        for (float preset : SPEED_PRESETS) {
            String label = FlightSpeed.format(preset);
            NamedTextColor color = Math.abs(currentSpeed - preset) < 0.001f
                    ? NamedTextColor.GREEN
                    : NamedTextColor.AQUA;
            message = message.append(Component.text("[" + label + "x]", color)
                    .clickEvent(ClickEvent.runCommand("/fly speed " + label))
                    .hoverEvent(HoverEvent.showText(languageService.text(
                            player, Message.FLY_SPEED_CLICK, NamedTextColor.GRAY, label))))
                    .append(Component.space());
        }
        message = message.append(Component.text("[" + languageService.t(player, Message.FLY_SPEED_RESET_ACTION) + "]",
                        NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/fly speed reset"))
                .hoverEvent(HoverEvent.showText(languageService.text(
                        player, Message.FLY_SPEED_RESET_CLICK, NamedTextColor.GRAY))));
        player.sendMessage(message);
    }

    @Override
    public void limitExplorationSpeed(Player player, float maximumSpeed) {
        if (!Float.isFinite(maximumSpeed) || maximumSpeed < 0.01f || maximumSpeed > 1.0f) {
            throw new IllegalArgumentException("Invalid exploration flight speed");
        }
        LimitedSpeed previous = limitedSpeeds.get(player.getUniqueId());
        float current = player.getFlySpeed();
        float requested = previous != null && current == previous.applied() ? previous.requested() : current;
        LimitedSpeed next = applyLimitedSpeed(player, previous, requested, maximumSpeed);
        if (previous == null && next.applied() != requested) {
            sendSpeedLimit(player);
        }
    }

    @Override
    public void releaseExplorationSpeed(Player player) {
        LimitedSpeed previous = limitedSpeeds.remove(player.getUniqueId());
        if (previous != null && player.getFlySpeed() == previous.applied()) {
            player.setFlySpeed(previous.requested());
        }
    }

    void applyRequestedSpeed(Player player, float requested) {
        LimitedSpeed previous = limitedSpeeds.get(player.getUniqueId());
        if (previous == null) {
            player.setFlySpeed(requested);
        } else {
            applyLimitedSpeed(player, previous, requested, previous.ceiling());
        }
    }

    private LimitedSpeed applyLimitedSpeed(Player player, LimitedSpeed previous, float requested, float ceiling) {
        float applied = Math.copySign(Math.min(Math.abs(requested), ceiling), requested);
        FlightMovementBudget movement;
        if (previous == null) {
            movement = new FlightMovementBudget(applied, clock.getAsLong());
        } else {
            movement = previous.movement();
            if (applied != previous.applied()) movement.setSpeed(applied, clock.getAsLong());
        }
        LimitedSpeed next = new LimitedSpeed(requested, applied, ceiling, movement);
        limitedSpeeds.put(player.getUniqueId(), next);
        if (player.getFlySpeed() != applied) player.setFlySpeed(applied);
        return next;
    }

    private void sendSpeedLimit(Player player) {
        LimitedSpeed limit = limitedSpeeds.get(player.getUniqueId());
        if (limit != null && limit.applied() != limit.requested()) {
            player.sendMessage(languageService.text(player, Message.FLY_SPEED_LIMITED, NamedTextColor.YELLOW,
                    FlightSpeed.fromBukkit(limit.applied()), FlightSpeed.fromBukkit(limit.requested())));
        }
    }

    private record LimitedSpeed(float requested, float applied, float ceiling, FlightMovementBudget movement) {
    }

    private Player requirePlayer(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) {
            return player;
        }
        CommandSender sender = source.getSender();
        sender.sendMessage(languageService.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
        return null;
    }

    private void removeGrantedFlight(Player player) {
        if (hasNativeFlight(player.getGameMode())) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private static boolean hasNativeFlight(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }
}
