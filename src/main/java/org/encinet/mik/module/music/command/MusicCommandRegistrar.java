package org.encinet.mik.module.music.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.music.MusicModule;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.music.online.LxSourceService;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.encinet.mik.module.music.ui.MusicBrowserGui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Registers the player and manager command surface for the music application. */
public final class MusicCommandRegistrar {

    private final LanguageService languageService;
    private final MusicBrowserGui browser;
    private final RandomMusicActions randomActions;
    private final LxSourceService sourceService;
    private final OnlineAudioCache audioCache;
    private final Supplier<CompletableFuture<MusicModule.ReloadReport>> reload;
    private final Runnable restorePlayback;
    private final Consumer<Runnable> runOnMainThread;

    public MusicCommandRegistrar(LanguageService languageService, MusicBrowserGui browser,
                                 RandomMusicActions randomActions,
                                 LxSourceService sourceService, OnlineAudioCache audioCache,
                                 Supplier<CompletableFuture<MusicModule.ReloadReport>> reload,
                                 Runnable restorePlayback,
                                 Consumer<Runnable> runOnMainThread) {
        this.languageService = languageService;
        this.browser = browser;
        this.randomActions = randomActions;
        this.sourceService = sourceService;
        this.audioCache = audioCache;
        this.reload = reload;
        this.restorePlayback = restorePlayback;
        this.runOnMainThread = runOnMainThread;
    }

    public void register(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("music")
                    .executes(context -> openBrowser(context.getSource().getSender(),
                            context.getSource().getExecutor()))
                    .then(Commands.literal("reload")
                            .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_MANAGER))
                            .executes(context -> reload(context.getSource().getSender())))
                    .then(Commands.literal("sources")
                            .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_MANAGER))
                            .executes(context -> {
                                sendSourceStatuses(context.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("import")
                                    .then(Commands.argument("url", StringArgumentType.greedyString())
                                            .executes(context -> importRemoteSource(
                                                    context.getSource().getSender(),
                                                    context.getArgument("url", String.class)))))
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                sourceService.subscriptionStatuses().stream()
                                                        .map(LxSourceService.SubscriptionStatus::id)
                                                        .filter(id -> id.startsWith(builder.getRemaining()))
                                                        .forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> removeRemoteSource(
                                                    context.getSource().getSender(),
                                                    context.getArgument("id", String.class)))))
                            .then(Commands.literal("update")
                                    .executes(context -> updateRemoteSources(
                                            context.getSource().getSender()))))
                    .then(Commands.literal("cache")
                            .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_MANAGER))
                            .executes(context -> {
                                sendCacheStatus(context.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("clear").executes(context -> {
                                clearCache(context.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("search")
                            .executes(context -> {
                                sendUsage(context.getSource().getSender(), "/music search <keyword>",
                                        Message.MUSIC_SEARCH_USAGE_DESC);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("keyword", StringArgumentType.greedyString())
                                    .executes(context -> search(context.getSource().getSender(),
                                            context.getSource().getExecutor(),
                                            context.getArgument("keyword", String.class)))))
                    .then(Commands.literal("playlist")
                            .executes(context -> {
                                sendUsage(context.getSource().getSender(),
                                        "/music playlist import <platform> <playlist-id-or-url>",
                                        Message.MUSIC_PLAYLIST_IMPORT_USAGE_DESC);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("import")
                                    .executes(context -> {
                                        sendUsage(context.getSource().getSender(),
                                                "/music playlist import <platform> <playlist-id-or-url>",
                                                Message.MUSIC_PLAYLIST_IMPORT_USAGE_DESC);
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("platform", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                for (String platform : List.of(
                                                        "wy", "tx", "kg", "kw", "mg")) {
                                                    if (platform.startsWith(builder.getRemaining())) {
                                                        builder.suggest(platform);
                                                    }
                                                }
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("playlist", StringArgumentType.greedyString())
                                                    .executes(context -> importPlaylist(
                                                            context.getSource().getSender(),
                                                            context.getSource().getExecutor(),
                                                            context.getArgument("platform", String.class),
                                                            context.getArgument("playlist", String.class)))))))
                    .then(Commands.literal("page")
                            .executes(context -> {
                                sendUsage(context.getSource().getSender(), "/music page <page>",
                                        Message.MUSIC_PAGE_USAGE_DESC);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .executes(context -> openPage(context.getSource().getSender(),
                                            context.getSource().getExecutor(),
                                            context.getArgument("number", Integer.class)))))
                    .then(Commands.literal("random")
                            .executes(context -> executeForPlayer(context.getSource().getSender(),
                                    context.getSource().getExecutor(), randomActions::giveRandomDisc)))
                    .then(Commands.literal("randomplay")
                            .executes(context -> executeForPlayer(context.getSource().getSender(),
                                    context.getSource().getExecutor(), randomActions::playRandomDisc)))
                    .build(), languageService.t(Language.DEFAULT, Message.MUSIC_COMMAND_DESCRIPTION));
        });
    }

    private int openBrowser(CommandSender sender, Entity executor) {
        Player player = requirePlayer(sender, executor);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        browser.setJukeboxContext(player.getUniqueId(), null);
        browser.openMenu(player);
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender) {
        sender.sendMessage(Component.text(t(sender, Message.MUSIC_RELOAD_STARTED), NamedTextColor.YELLOW));
        reload.get().whenComplete((report, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_RELOAD_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
                return;
            }
            restorePlayback.run();
            LxSourceService.SubscriptionReload subscriptions = report.online().subscriptions();
            LxSourceService.RuntimeReload runtimes = report.online().runtimes();
            if (!report.anySuccessful()) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_RELOAD_FAILED,
                        String.join("; ", report.errors())), NamedTextColor.RED));
                return;
            }
            Message resultMessage = report.successful()
                    ? Message.MUSIC_RELOAD_DONE : Message.MUSIC_RELOAD_PARTIAL;
            NamedTextColor color = report.successful()
                    ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            sender.sendMessage(Component.text(t(sender, resultMessage,
                    report.library().trackCount(), runtimes.available(), runtimes.total(),
                    subscriptions.total(), subscriptions.changed()), color));
            if (!report.errors().isEmpty()) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_RELOAD_ISSUES,
                        String.join("; ", report.errors())), NamedTextColor.RED));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int search(CommandSender sender, Entity executor, String keyword) {
        Player player = requirePlayer(sender, executor);
        if (player != null) {
            browser.resumeJukeboxSearch(player.getUniqueId());
            browser.searchMusic(player, keyword);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int importPlaylist(CommandSender sender, Entity executor,
                               String platform, String playlist) {
        Player player = requirePlayer(sender, executor);
        if (player != null) {
            browser.resumeJukeboxSearch(player.getUniqueId());
            browser.importPlaylist(player, platform, playlist);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int openPage(CommandSender sender, Entity executor, int pageNumber) {
        Player player = requirePlayer(sender, executor);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        int totalPages = browser.getTotalPages(player.getUniqueId());
        if (pageNumber > totalPages) {
            player.sendMessage(languageService.text(player, Message.MUSIC_PAGE_OUT_OF_RANGE,
                    NamedTextColor.RED, totalPages));
            return Command.SINGLE_SUCCESS;
        }
        browser.openCurrentPage(player, pageNumber - 1);
        return Command.SINGLE_SUCCESS;
    }

    private Player requirePlayer(CommandSender sender, Entity executor) {
        if (executor instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY),
                NamedTextColor.RED));
        return null;
    }

    private int executeForPlayer(CommandSender sender, Entity executor, Consumer<Player> action) {
        Player player = requirePlayer(sender, executor);
        if (player != null) {
            action.accept(player);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendSourceStatuses(CommandSender sender) {
        List<LxSourceService.SourceStatus> statuses = sourceService.statuses();
        List<LxSourceService.SubscriptionStatus> remoteStatuses = sourceService.subscriptionStatuses();
        if (statuses.isEmpty()) {
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_SOURCES_EMPTY), NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_SOURCES_TITLE, statuses.size()),
                    NamedTextColor.AQUA));
            Instant now = Instant.now();
            for (LxSourceService.SourceStatus status : statuses) {
                Component line = Component.text(status.name() + " [" + status.id() + "] ", NamedTextColor.WHITE)
                        .append(Component.text(t(sender, status.available()
                                        ? Message.MUSIC_SOURCE_AVAILABLE : Message.MUSIC_SOURCE_UNAVAILABLE),
                                status.available() ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(" | " + t(sender, Message.MUSIC_SOURCE_CAPABILITIES,
                                formatCapabilities(status.capabilities())), NamedTextColor.GRAY))
                        .append(Component.text(" | " + t(sender, Message.MUSIC_SOURCE_ACTIONS,
                                formatCapabilities(status.actions())), NamedTextColor.DARK_GRAY));
                if (status.consecutiveFailures() > 0) {
                    line = line.append(Component.text(" | " + t(sender, Message.MUSIC_SOURCE_FAILURES,
                            status.consecutiveFailures()), NamedTextColor.YELLOW));
                }
                if (status.retryAt() != null && now.isBefore(status.retryAt())) {
                    long seconds = Math.max(1, Duration.between(now, status.retryAt()).toSeconds());
                    line = line.append(Component.text(" | " + t(sender, Message.MUSIC_SOURCE_RETRY, seconds),
                            NamedTextColor.YELLOW));
                }
                if (status.lastError() != null && !status.lastError().isBlank()) {
                    line = line.append(Component.text(" | " + t(sender, Message.MUSIC_SOURCE_ERROR,
                            truncate(status.lastError(), 160)), NamedTextColor.RED));
                }
                sender.sendMessage(line);
            }
        }
        if (!remoteStatuses.isEmpty()) {
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCES_TITLE,
                    remoteStatuses.size()), NamedTextColor.AQUA));
            for (LxSourceService.SubscriptionStatus status : remoteStatuses) {
                Component line = Component.text(status.id() + " ", NamedTextColor.WHITE)
                        .append(Component.text(status.url(), NamedTextColor.GRAY));
                if (status.updatedAt() != null) {
                    line = line.append(Component.text(" | " + t(sender,
                            Message.MUSIC_REMOTE_SOURCE_UPDATED_AT, status.updatedAt()),
                            NamedTextColor.DARK_GRAY));
                }
                if (status.lastError() != null && !status.lastError().isBlank()) {
                    line = line.append(Component.text(" | " + t(sender,
                            Message.MUSIC_REMOTE_SOURCE_ERROR,
                            truncate(status.lastError(), 160)), NamedTextColor.RED));
                }
                sender.sendMessage(line);
            }
        }
    }

    private int importRemoteSource(CommandSender sender, String url) {
        sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_IMPORT_STARTED),
                NamedTextColor.YELLOW));
        sourceService.importSourceAsync(url).whenComplete((result, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_IMPORT_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_IMPORT_DONE,
                    result.id()), NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int removeRemoteSource(CommandSender sender, String id) {
        sourceService.removeSourceAsync(id).whenComplete((removed, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_REMOVE_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
            } else if (removed) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_REMOVE_DONE, id),
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_REMOVE_NOT_FOUND, id),
                        NamedTextColor.YELLOW));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int updateRemoteSources(CommandSender sender) {
        sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_UPDATE_STARTED),
                NamedTextColor.YELLOW));
        sourceService.refreshSubscriptionsAsync().whenComplete((result, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_UPDATE_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_REMOTE_SOURCE_UPDATE_DONE,
                    result.total(), result.changed(), result.failed()), NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private void sendCacheStatus(CommandSender sender) {
        audioCache.statsAsync().whenComplete((stats, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_CACHE_OPERATION_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_CACHE_STATUS,
                    stats.files(), formatBytes(stats.bytes()), formatBytes(audioCache.maxCacheBytes())),
                    NamedTextColor.AQUA));
        }));
    }

    private void clearCache(CommandSender sender) {
        sender.sendMessage(Component.text(t(sender, Message.MUSIC_CACHE_CLEAR_STARTED), NamedTextColor.YELLOW));
        audioCache.clearAsync().whenComplete((ignored, error) -> runOnMainThread.accept(() -> {
            if (error != null) {
                sender.sendMessage(Component.text(t(sender, Message.MUSIC_CACHE_OPERATION_FAILED,
                        rootMessage(error)), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(t(sender, Message.MUSIC_CACHE_CLEAR_DONE), NamedTextColor.GREEN));
        }));
    }

    private void sendUsage(CommandSender sender, String command, Message description) {
        sender.sendMessage(Component.text()
                .append(Component.text(t(sender, Message.USAGE), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text(command, NamedTextColor.AQUA))
                .append(Component.text("  " + t(sender, description), NamedTextColor.GRAY))
                .build());
    }

    private String t(CommandSender sender, Message message, Object... args) {
        return sender instanceof Player player
                ? languageService.t(player, message, args)
                : languageService.t(Language.DEFAULT, message, args);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static String formatCapabilities(Map<String, List<String>> capabilities) {
        return capabilities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + String.join("/", entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String truncate(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, maximum - 3) + "...";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
