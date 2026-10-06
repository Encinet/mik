package org.encinet.mik.module.api;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.ban.BanRecord;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.PlayerRole;
import org.encinet.mik.util.ShutdownSequence;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Connects Bukkit player events and web-login commands to the local HTTP server. */
public final class ApiModule implements Listener {
    private static final String COMMAND_PERMISSION = "mik.command.api";

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final ApiPlayerSnapshot players;
    private final WebLoginChallengeStore webLoginChallenges;
    private final LocalApiServer http;

    public ApiModule(JavaPlugin plugin, LanguageService languageService,
                     Supplier<List<BanRecord>> activeBans,
                     Supplier<byte[]> announcementsJson, CommunityBoardView communityBoard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.players = new ApiPlayerSnapshot(plugin.getDataFolder().toPath(), plugin.getLogger());
        this.webLoginChallenges = new WebLoginChallengeStore();
        this.http = new LocalApiServer(plugin, players, webLoginChallenges,
                activeBans, announcementsJson, communityBoard);
    }

    public void start(int port) {
        try {
            players.load();
        } catch (IllegalStateException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Local API disabled: could not load player state", error);
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        players.bootstrap(Bukkit.getOnlinePlayers().stream()
                .map(player -> new ApiPlayerSnapshot.Identity(
                        player.getUniqueId(), player.getName()))
                .toList(), Instant.now());
        try {
            http.start(port);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Local API disabled: could not bind "
                            + LocalApiServer.BIND_ADDRESS + ":" + port, error);
        }
    }

    public void stop() {
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("local HTTP server", http::stop);
        shutdown.attempt("API player listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("API player snapshot", players::clear);
        shutdown.attempt("web login challenges", webLoginChallenges::clear);
        shutdown.finish("API module");
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("mikapi")
                        .requires(source -> source.getSender().hasPermission(COMMAND_PERMISSION))
                        .executes(ctx -> {
                            sendStatus(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    sendStatus(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Show Mik API status");

            event.registrar().register(Commands.literal("weblogin")
                    .then(Commands.argument("code", StringArgumentType.word())
                            .executes(ctx -> {
                                confirmWebLogin(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "code"));
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT,
                            Message.WEBLOGIN_COMMAND_DESCRIPTION));
        });
    }

    private void sendStatus(CommandSender sender) {
        boolean running = http.running();
        sender.sendMessage(Component.text("Mik API status", NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text("Server: " + (running ? "running" : "stopped")
                        + " bind=" + LocalApiServer.BIND_ADDRESS + " port=" + http.port(),
                        running ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Online players: " + players.onlineCount()
                        + " peak=" + players.peakOnline(), NamedTextColor.GRAY)));
    }

    private void confirmWebLogin(CommandSender sender, String rawCode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(languageService.t(Language.DEFAULT,
                    Message.PLAYER_ONLY), NamedTextColor.RED));
            return;
        }

        String code = rawCode == null ? "" : rawCode.trim();
        if (!WebLoginChallengeStore.validCode(code)) {
            player.sendMessage(Component.text(languageService.t(player,
                    Message.WEBLOGIN_INVALID_CODE), NamedTextColor.RED));
            return;
        }

        webLoginChallenges.confirm(code, player.getUniqueId(), player.getName(),
                PlayerRole.resolve(player).id());
        player.sendMessage(Component.text(languageService.t(player,
                Message.WEBLOGIN_CONFIRMED), NamedTextColor.GREEN));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.joined(player.getUniqueId(), player.getName(), Instant.now());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        players.left(event.getPlayer().getUniqueId());
    }
}
