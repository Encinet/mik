package org.encinet.mik.module.ban;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

final class BanCommandController {

    private static final String HELPER_PERMISSION = "group." + Mik.GROUP_HELPER;
    private static final String MANAGER_PERMISSION = "group." + Mik.GROUP_MANAGER;
    private static final int BANS_PER_PAGE = 10;
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final PaperBanSynchronizer paperSynchronizer;
    private final BanService banService;
    private final BanMessageRenderer renderer;
    private final BanAnnouncementBroadcaster announcementBroadcaster;
    private final BanDialogController dialogController;

    BanCommandController(
            JavaPlugin plugin,
            LanguageService languageService,
            PaperBanSynchronizer paperSynchronizer,
            BanService banService,
            BanMessageRenderer renderer,
            BanAnnouncementBroadcaster announcementBroadcaster,
            BanDialogController dialogController
    ) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.paperSynchronizer = paperSynchronizer;
        this.banService = banService;
        this.renderer = renderer;
        this.announcementBroadcaster = announcementBroadcaster;
        this.dialogController = dialogController;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("ban")
                            .requires(source -> canManageBans(source.getSender()))
                            .executes(this::openBanDialog)
                            .then(Commands.literal("add")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(bannablePlayerSuggestions())
                                            .then(Commands.argument("severity", StringArgumentType.word())
                                                    .suggests(severitySuggestions())
                                                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                            .executes(context -> executeBan(context, false)))
                                                    .then(Commands.literal("--confirm")
                                                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                                    .executes(context -> executeBan(context, true)))))))
                            .then(Commands.literal("info")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(knownPlayerSuggestions())
                                            .executes(this::showInfo)))
                            .then(Commands.literal("history")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(knownPlayerSuggestions())
                                            .executes(context -> showHistory(context, 1))
                                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                    .executes(context -> showHistory(context,
                                                            IntegerArgumentType.getInteger(context, "page"))))))
                            .then(Commands.literal("edit")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(activePlayerSuggestions())
                                            .then(Commands.literal("severity")
                                                    .then(Commands.argument("severity", StringArgumentType.word())
                                                            .suggests(severitySuggestions())
                                                            .executes(this::editSeverity)))))
                            .then(Commands.literal("sync").executes(this::syncPaperBans))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.BAN_COMMAND_DESCRIPTION), List.of("bb"));

            event.registrar().register(Commands.literal("pardon")
                            .requires(source -> canManageBans(source.getSender()))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(activePlayerSuggestions())
                                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                                            .executes(this::executePardon)))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.UNBAN_COMMAND_DESCRIPTION), List.of("unban"));

            event.registrar().register(Commands.literal("banlist")
                            .requires(source -> canManageBans(source.getSender()))
                            .executes(context -> showBanList(context.getSource().getSender(), 1))
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                    .executes(context -> showBanList(
                                            context.getSource().getSender(),
                                            IntegerArgumentType.getInteger(context, "page"))))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.BANLIST_COMMAND_DESCRIPTION), List.of("bblist"));
        });
    }

    static boolean canManageBans(CommandSender sender) {
        return sender.hasPermission(HELPER_PERMISSION) || sender.hasPermission(MANAGER_PERMISSION);
    }

    private int openBanDialog(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error(sender, Message.PLAYER_ONLY));
            return 0;
        }
        dialogController.open(player);
        return Command.SINGLE_SUCCESS;
    }

    private int executeBan(CommandContext<CommandSourceStack> context, boolean neverJoinedConfirmed) {
        CommandSender sender = context.getSource().getSender();
        String requestedName = StringArgumentType.getString(context, "player");
        if (!validPlayerName(sender, requestedName)) {
            return 0;
        }
        BanSeverity severity = BanSeverity.fromId(StringArgumentType.getString(context, "severity")).orElse(null);
        if (severity == null) {
            sender.sendMessage(error(sender, Message.BAN_INVALID_SEVERITY));
            return 0;
        }
        String reason = BanReason.normalize(StringArgumentType.getString(context, "reason")).orElse(null);
        if (reason == null) {
            sender.sendMessage(error(sender, Message.BAN_REASON_REQUIRED));
            return 0;
        }

        BanTarget target = resolveBanTarget(requestedName);
        if (isSelf(sender, target)) {
            sender.sendMessage(error(sender, Message.BAN_SELF));
            return 0;
        }
        if (banService.active(target.uuid(), target.name()).isPresent()) {
            sender.sendMessage(error(sender, Message.BAN_ALREADY_BANNED, target.name()));
            return 0;
        }
        if (needsNeverJoinedConfirmation(target.hasPlayedBefore(), neverJoinedConfirmed)) {
            sendNeverJoinedConfirmation(sender, target.name(), severity, reason);
            return 0;
        }

        try {
            BanRecord record = banService.ban(
                    target.uuid(), target.name(), severity, reason, sender.getName());
            Language language = senderLanguage(sender);
            sender.sendMessage(Component.text(t(sender, Message.BAN_SUCCESS, record.playerName(),
                    renderer.expirationText(language, record.expiresAt())), NamedTextColor.GREEN));
            announcementBroadcaster.broadcast(sender.getName(), record);
            kickIfOnline(record);
            return Command.SINGLE_SUCCESS;
        } catch (BanServiceException e) {
            if (banService.active(target.uuid(), target.name()).isPresent()) {
                sender.sendMessage(error(sender, Message.BAN_ALREADY_BANNED, target.name()));
                return 0;
            }
            return databaseError(sender, e);
        }
    }

    private void sendNeverJoinedConfirmation(
            CommandSender sender,
            String playerName,
            BanSeverity severity,
            String reason
    ) {
        String command = "/ban add " + playerName + " " + severity.id() + " --confirm " + reason;
        Component confirmButton = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(t(sender, Message.BAN_DIALOG_NEVER_JOINED_CONFIRM_ACTION),
                        NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)))
                .build();
        sender.sendMessage(Component.text(t(sender, Message.BAN_DIALOG_NEVER_JOINED_WARNING, playerName),
                        NamedTextColor.RED)
                .appendNewline()
                .append(confirmButton)
                .append(Component.text(" " + command, NamedTextColor.GRAY)));
    }

    static boolean needsNeverJoinedConfirmation(boolean hasPlayedBefore, boolean explicitlyConfirmed) {
        return !hasPlayedBefore && !explicitlyConfirmed;
    }

    private boolean isSelf(CommandSender sender, BanTarget target) {
        return sender instanceof Player player
                && (player.getUniqueId().equals(target.uuid())
                || player.getName().equalsIgnoreCase(target.name()));
    }

    private int executePardon(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String playerName = StringArgumentType.getString(context, "player");
        if (!validPlayerReference(sender, playerName)) {
            return 0;
        }
        String reason = StringArgumentType.getString(context, "reason").trim();
        if (reason.isEmpty()) {
            sender.sendMessage(error(sender, Message.BAN_PARDON_REASON_REQUIRED));
            return 0;
        }
        PlayerIdentity identity = resolveIdentity(playerName);
        if (banService.active(identity.uuid(), identity.name()).isEmpty()) {
            sender.sendMessage(error(sender, Message.BAN_NOT_BANNED, identity.name()));
            return 0;
        }
        try {
            banService.pardon(identity.uuid(), identity.name(), sender.getName(), reason);
            sender.sendMessage(Component.text(t(sender, Message.BAN_UNBAN_SUCCESS, identity.name()),
                    NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        } catch (BanServiceException e) {
            return databaseError(sender, e);
        }
    }

    private int editSeverity(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerIdentity identity = resolveIdentity(StringArgumentType.getString(context, "player"));
        BanSeverity severity = BanSeverity.fromId(StringArgumentType.getString(context, "severity")).orElse(null);
        if (severity == null) {
            sender.sendMessage(error(sender, Message.BAN_INVALID_SEVERITY));
            return 0;
        }
        try {
            BanRecord updated = banService.editSeverity(
                    identity.uuid(), identity.name(), severity, sender.getName());
            sender.sendMessage(Component.text(t(sender, Message.BAN_UPDATE_SUCCESS,
                    updated.playerName(), t(sender, Message.BAN_DIALOG_CONFIRM_SEVERITY)), NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        } catch (BanServiceException e) {
            return serviceError(sender, identity.name(), e);
        }
    }

    private int syncPaperBans(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        try {
            paperSynchronizer.reconcileNow();
            sender.sendMessage(Component.text(t(sender, Message.BAN_SYNC_SUCCESS,
                    banService.activeRecords().size(), banService.allRecords().size()), NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        } catch (BanServiceException e) {
            return databaseError(sender, e);
        }
    }

    private int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PlayerIdentity identity = resolveIdentity(StringArgumentType.getString(context, "player"));
        List<BanRecord> history = banService.history(identity.uuid(), identity.name());
        if (history.isEmpty()) {
            sender.sendMessage(error(sender, Message.BAN_HISTORY_EMPTY, identity.name()));
            return 0;
        }
        sendRecord(sender, history.getFirst(), true);
        return Command.SINGLE_SUCCESS;
    }

    private int showHistory(CommandContext<CommandSourceStack> context, int requestedPage) {
        CommandSender sender = context.getSource().getSender();
        PlayerIdentity identity = resolveIdentity(StringArgumentType.getString(context, "player"));
        List<BanRecord> records = banService.history(identity.uuid(), identity.name());
        if (records.isEmpty()) {
            sender.sendMessage(error(sender, Message.BAN_HISTORY_EMPTY, identity.name()));
            return 0;
        }
        int totalPages = pages(records.size());
        if (requestedPage > totalPages) {
            sender.sendMessage(error(sender, Message.BANLIST_INVALID_PAGE, totalPages));
            return 0;
        }
        sender.sendMessage(Component.text(t(sender, Message.BAN_HISTORY_TITLE, identity.name()),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(t(sender, Message.BANLIST_PAGE,
                requestedPage, totalPages, records.size()), NamedTextColor.GRAY));
        for (BanRecord record : page(records, requestedPage)) {
            sendRecord(sender, record, false);
        }
        sender.sendMessage(pageNavigation(languageFor(sender), requestedPage, totalPages,
                "/ban history " + identity.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int showBanList(CommandSender sender, int requestedPage) {
        List<BanRecord> records = banService.activeRecords();
        if (records.isEmpty()) {
            sender.sendMessage(Component.text(t(sender, Message.BANLIST_EMPTY), NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        int totalPages = pages(records.size());
        if (requestedPage > totalPages) {
            sender.sendMessage(error(sender, Message.BANLIST_INVALID_PAGE, totalPages));
            return 0;
        }
        Language language = senderLanguage(sender);
        sender.sendMessage(Component.text(t(sender, Message.BANLIST_TITLE), NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(t(sender, Message.BANLIST_PAGE,
                requestedPage, totalPages, records.size()), NamedTextColor.GRAY));
        List<BanRecord> page = page(records, requestedPage);
        for (int index = 0; index < page.size(); index++) {
            sender.sendMessage(banEntry(language, (requestedPage - 1) * BANS_PER_PAGE + index + 1,
                    page.get(index)));
        }
        sender.sendMessage(pageNavigation(language, requestedPage, totalPages, "/banlist"));
        return Command.SINGLE_SUCCESS;
    }

    private Component banEntry(Language language, int index, BanRecord record) {
        Component hover = Component.text()
                .append(renderer.labelLine(language, Message.BANLIST_PLAYER, record.playerName()))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_REASON,
                        renderer.reasonText(language, record.reason())))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_EXPIRES,
                        renderer.expirationText(language, record.expiresAt())))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_SOURCE,
                        renderer.sourceText(language, record.source())))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.BANLIST_COPY), NamedTextColor.YELLOW))
                .build();
        return Component.text(index + ". ", NamedTextColor.DARK_GRAY)
                .append(Component.text(record.playerName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(renderer.reasonText(language, record.reason()), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(renderer.expirationText(language, record.expiresAt()), NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.copyToClipboard(record.playerName()));
    }

    private void sendRecord(CommandSender sender, BanRecord record, boolean detailed) {
        Language language = senderLanguage(sender);
        NamedTextColor statusColor = record.statusAt(Instant.now()) == BanRecord.Status.ACTIVE
                ? NamedTextColor.RED : NamedTextColor.GRAY;
        Component line = Component.text("#" + record.id() + " ", NamedTextColor.DARK_GRAY)
                .append(Component.text(record.playerName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(renderer.statusText(language, record, Instant.now()), statusColor))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(record.createdAt().toString(), NamedTextColor.GRAY));
        sender.sendMessage(line);
        if (detailed) {
            sender.sendMessage(renderer.labelLine(language, Message.BANLIST_REASON,
                    renderer.reasonText(language, record.reason())));
            sender.sendMessage(renderer.labelLine(language, Message.BANLIST_EXPIRES,
                    renderer.expirationText(language, record.expiresAt())));
            sender.sendMessage(renderer.labelLine(language, Message.BANLIST_SOURCE,
                    renderer.sourceText(language, record.source())));
        }
    }

    private SuggestionProvider<CommandSourceStack> activePlayerSuggestions() {
        return (context, builder) -> suggest(builder,
                banService.activeRecords().stream().map(BanRecord::playerName).toList());
    }

    private SuggestionProvider<CommandSourceStack> knownPlayerSuggestions() {
        return (context, builder) -> suggest(builder,
                banService.allRecords().stream().map(BanRecord::playerName).distinct().toList());
    }

    private SuggestionProvider<CommandSourceStack> bannablePlayerSuggestions() {
        return (context, builder) -> {
            List<String> playerNames = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(playerNames::add);
            banService.allRecords().stream().map(BanRecord::playerName)
                    .filter(name -> playerNames.stream().noneMatch(name::equalsIgnoreCase))
                    .forEach(playerNames::add);
            return suggest(builder, playerNames);
        };
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder, List<String> values) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private SuggestionProvider<CommandSourceStack> severitySuggestions() {
        return (context, builder) -> suggest(builder,
                Arrays.stream(BanSeverity.values()).map(BanSeverity::id).toList());
    }

    private PlayerIdentity resolveIdentity(String requestedName) {
        Optional<BanRecord> known = banService.allRecords().stream()
                .filter(record -> record.playerName().equalsIgnoreCase(requestedName))
                .findFirst();
        if (known.isPresent()) {
            BanRecord record = known.get();
            return new PlayerIdentity(record.playerUuid(), record.playerName());
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(requestedName);
        return new PlayerIdentity(player.getUniqueId(), player.getName() == null ? requestedName : player.getName());
    }

    private BanTarget resolveBanTarget(String requestedName) {
        Player online = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(requestedName))
                .findFirst()
                .orElse(null);
        if (online != null) {
            return new BanTarget(online.getUniqueId(), online.getName(), true);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(requestedName);
        if (cached != null) {
            return new BanTarget(cached.getUniqueId(),
                    cached.getName() == null ? requestedName : cached.getName(), cached.hasPlayedBefore());
        }
        Optional<BanRecord> known = banService.allRecords().stream()
                .filter(record -> record.playerName().equalsIgnoreCase(requestedName))
                .findFirst();
        if (known.isPresent()) {
            BanRecord record = known.get();
            return new BanTarget(record.playerUuid(), record.playerName(), false);
        }
        return new BanTarget(null, requestedName, false);
    }

    private boolean validPlayerName(CommandSender sender, String playerName) {
        if (PLAYER_NAME_PATTERN.matcher(playerName).matches()) {
            return true;
        }
        sender.sendMessage(error(sender, Message.BAN_INVALID_PLAYER));
        return false;
    }

    private boolean validPlayerReference(CommandSender sender, String playerName) {
        boolean known = banService.allRecords().stream()
                .anyMatch(record -> record.playerName().equalsIgnoreCase(playerName));
        return known || validPlayerName(sender, playerName);
    }

    private int serviceError(CommandSender sender, String playerName, BanServiceException error) {
        if (banService.active(null, playerName).isEmpty()) {
            sender.sendMessage(error(sender, Message.BAN_NOT_BANNED, playerName));
            return 0;
        }
        return databaseError(sender, error);
    }

    private int databaseError(CommandSender sender, Exception error) {
        plugin.getLogger().log(Level.SEVERE, "Ban command failed", error);
        sender.sendMessage(error(sender, Message.BAN_DATABASE_ERROR));
        return 0;
    }

    private void kickIfOnline(BanRecord record) {
        Player player = record.playerUuid() == null
                ? Bukkit.getPlayerExact(record.playerName())
                : Bukkit.getPlayer(record.playerUuid());
        if (player == null) {
            return;
        }
        InetSocketAddress socketAddress = player.getAddress();
        InetAddress address = socketAddress == null ? null : socketAddress.getAddress();
        player.kick(renderer.banMessage(languageService.languageForAddress(address), record),
                PlayerKickEvent.Cause.BANNED);
    }

    private List<BanRecord> page(List<BanRecord> records, int page) {
        int start = (page - 1) * BANS_PER_PAGE;
        return records.subList(start, Math.min(start + BANS_PER_PAGE, records.size()));
    }

    private int pages(int size) {
        return Math.max(1, (size + BANS_PER_PAGE - 1) / BANS_PER_PAGE);
    }

    private Component pageNavigation(Language language, int page, int totalPages, String command) {
        Component previous = page > 1
                ? pageButton(languageService.t(language, Message.BANLIST_PREVIOUS), command, page - 1)
                : Component.text(languageService.t(language, Message.BANLIST_PREVIOUS), NamedTextColor.DARK_GRAY);
        Component next = page < totalPages
                ? pageButton(languageService.t(language, Message.BANLIST_NEXT), command, page + 1)
                : Component.text(languageService.t(language, Message.BANLIST_NEXT), NamedTextColor.DARK_GRAY);
        return previous.append(Component.text("  |  " + page + "/" + totalPages + "  |  ", NamedTextColor.GRAY))
                .append(next);
    }

    private Component pageButton(String label, String command, int page) {
        return Component.text(label, NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(command + " " + page));
    }

    private Language languageFor(CommandSender sender) {
        return senderLanguage(sender);
    }

    private Component error(CommandSender sender, Message message, Object... args) {
        return Component.text(t(sender, message, args), NamedTextColor.RED);
    }

    private String t(CommandSender sender, Message message, Object... args) {
        return languageService.t(senderLanguage(sender), message, args);
    }

    private Language senderLanguage(CommandSender sender) {
        return sender instanceof Player player ? languageService.language(player) : Language.DEFAULT;
    }

    private record PlayerIdentity(UUID uuid, String name) {
    }

    private record BanTarget(UUID uuid, String name, boolean hasPlayedBefore) {
    }
}
