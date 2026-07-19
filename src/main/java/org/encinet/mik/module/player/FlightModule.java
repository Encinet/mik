package org.encinet.mik.module.player;

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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FlightModule implements Listener {

    private static final float[] SPEED_PRESETS = {0.5f, 1.0f, 2.0f, 5.0f, 10.0f};

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Set<UUID> grantedFlight = new HashSet<>();

    public FlightModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("FlightModule enabled");
    }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (grantedFlight.remove(player.getUniqueId())) {
                removeGrantedFlight(player);
            }
        }
        grantedFlight.clear();
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
                            .then(speedCommand("speed"))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.FLY_COMMAND_DESCRIPTION),
                    List.of("flight"));

            commands.register(Commands.literal("flyspeed")
                            .executes(ctx -> showSpeed(ctx.getSource()))
                            .then(speedArgument())
                            .then(Commands.literal("reset")
                                    .executes(ctx -> resetSpeed(ctx.getSource())))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.FLYSPEED_COMMAND_DESCRIPTION),
                    List.of("flightspeed"));
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
        if (grantedFlight.remove(event.getPlayer().getUniqueId())) {
            removeGrantedFlight(event.getPlayer());
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> speedCommand(String literal) {
        return Commands.literal(literal)
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
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private int setSpeed(CommandSourceStack source, float requestedSpeed) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        player.setFlySpeed(FlightSpeed.toBukkit(requestedSpeed));
        String speed = FlightSpeed.format(FlightSpeed.fromBukkit(player.getFlySpeed()));
        player.sendMessage(languageService.text(player, Message.FLY_SPEED_SET, NamedTextColor.GREEN, speed));
        sendSpeedPresets(player);
        return Command.SINGLE_SUCCESS;
    }

    private int resetSpeed(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        player.setFlySpeed(FlightSpeed.toBukkit(FlightSpeed.DEFAULT_USER_SPEED));
        player.sendMessage(languageService.text(player, Message.FLY_SPEED_RESET, NamedTextColor.GREEN));
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
                    .clickEvent(ClickEvent.runCommand("/flyspeed " + label))
                    .hoverEvent(HoverEvent.showText(languageService.text(
                            player, Message.FLY_SPEED_CLICK, NamedTextColor.GRAY, label))))
                    .append(Component.space());
        }
        message = message.append(Component.text("[" + languageService.t(player, Message.FLY_SPEED_RESET_ACTION) + "]",
                        NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/flyspeed reset"))
                .hoverEvent(HoverEvent.showText(languageService.text(
                        player, Message.FLY_SPEED_RESET_CLICK, NamedTextColor.GRAY))));
        player.sendMessage(message);
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
