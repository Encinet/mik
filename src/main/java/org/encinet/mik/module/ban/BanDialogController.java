package org.encinet.mik.module.ban;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

final class BanDialogController {

    private static final String MANUAL_PLAYER_OPTION = "manual";
    private static final String PLAYER_INPUT = DialogInputKeys.requireValid("manual_player");
    private static final String PLAYER_OPTION = DialogInputKeys.requireValid("online_player");
    private static final String REASON_INPUT = DialogInputKeys.requireValid("ban_reason");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int SEVERITY_DIALOG_WIDTH = 320;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final BanService banService;
    private final BanMessageRenderer renderer;
    private final BanAnnouncementBroadcaster announcementBroadcaster;

    BanDialogController(
            JavaPlugin plugin,
            LanguageService languageService,
            BanService banService,
            BanMessageRenderer renderer,
            BanAnnouncementBroadcaster announcementBroadcaster
    ) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.banService = banService;
        this.renderer = renderer;
        this.announcementBroadcaster = announcementBroadcaster;
    }

    void open(Player moderator) {
        Language language = languageService.language(moderator);
        List<SingleOptionDialogInput.OptionEntry> choices = new ArrayList<>();
        choices.add(SingleOptionDialogInput.OptionEntry.create(
                MANUAL_PLAYER_OPTION,
                Component.text(t(language, Message.BAN_DIALOG_PLAYER_MANUAL), NamedTextColor.YELLOW),
                true));
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(moderator.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(player -> choices.add(SingleOptionDialogInput.OptionEntry.create(
                        "online:" + player.getUniqueId(),
                        Component.text(player.getName(), NamedTextColor.WHITE),
                        false)));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(t(language, Message.BAN_DIALOG_SELECT_TITLE), NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of())
                        .inputs(List.of(
                                DialogInput.text(PLAYER_INPUT, 260,
                                        Component.text(t(language, Message.BAN_DIALOG_PLAYER_INPUT), NamedTextColor.GRAY),
                                        true, "", 16, null),
                                DialogInput.singleOption(PLAYER_OPTION, 260, choices,
                                        Component.text(t(language, Message.BAN_DIALOG_PLAYER_ONLINE), NamedTextColor.GRAY),
                                        true)))
                        .build())
                .type(DialogType.confirmation(
                        button(language, Message.BAN_DIALOG_NEXT, NamedTextColor.GREEN,
                                (response, audience) -> runOnMain(audience,
                                        player -> selectPlayer(player,
                                                response.getText(PLAYER_INPUT), response.getText(PLAYER_OPTION)))),
                        ActionButton.create(Component.text(t(language, Message.BAN_DIALOG_CANCEL), NamedTextColor.GRAY),
                                null, 90, null))));
        moderator.showDialog(dialog);
    }

    private void selectPlayer(Player moderator, String manualName, String selectedOption) {
        String requestedName = manualName == null ? "" : manualName.trim();
        Target target;
        if (!requestedName.isEmpty()) {
            target = manualTarget(requestedName);
        } else {
            target = onlineTarget(selectedOption);
        }
        if (target == null) {
            moderator.sendMessage(error(moderator, Message.BAN_INVALID_PLAYER));
            open(moderator);
            return;
        }
        if (moderator.getUniqueId().equals(target.uuid())) {
            moderator.sendMessage(error(moderator, Message.BAN_SELF));
            return;
        }
        if (banService.active(target.uuid(), target.name()).isPresent()) {
            moderator.sendMessage(error(moderator, Message.BAN_ALREADY_BANNED, target.name()));
            return;
        }
        openSeverity(moderator, target);
    }

    private Target manualTarget(String requestedName) {
        if (!PLAYER_NAME_PATTERN.matcher(requestedName).matches()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(requestedName);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName(), true);
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(requestedName);
        if (cached != null) {
            return new Target(cached.getUniqueId(), cached.getName() == null ? requestedName : cached.getName(),
                    cached.hasPlayedBefore());
        }
        return new Target(null, requestedName, false);
    }

    private Target onlineTarget(String selectedOption) {
        if (selectedOption == null || !selectedOption.startsWith("online:")) {
            return null;
        }
        try {
            UUID playerId = UUID.fromString(selectedOption.substring("online:".length()));
            Player player = Bukkit.getPlayer(playerId);
            return player == null ? null : new Target(player.getUniqueId(), player.getName(), true);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void openSeverity(Player moderator, Target target) {
        Language language = languageService.language(moderator);
        List<ActionButton> severityButtons = new ArrayList<>();
        for (BanSeverity severity : BanSeverity.values()) {
            severityButtons.add(ActionButton.create(
                    Component.text(t(language, severity.label()), NamedTextColor.YELLOW),
                    null,
                    SEVERITY_DIALOG_WIDTH,
                    DialogAction.customClick((response, audience) -> runOnMain(audience,
                            player -> openConfirmation(player, target, severity, "")), CALLBACK_OPTIONS)));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(t(language, Message.BAN_DIALOG_SEVERITY_TITLE), NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text(target.name(), NamedTextColor.WHITE), SEVERITY_DIALOG_WIDTH)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.multiAction(severityButtons,
                        ActionButton.create(Component.text(t(language, Message.BAN_DIALOG_BACK), NamedTextColor.GRAY),
                                null, 90, DialogAction.customClick((response, audience) -> runOnMain(audience, this::open),
                                CALLBACK_OPTIONS)),
                        1)));
        moderator.showDialog(dialog);
    }

    private void openConfirmation(Player moderator, Target target, BanSeverity severity, String reason) {
        Language language = languageService.language(moderator);
        Instant expiration = severity.expiresAt(Instant.now());
        Component details = Component.text()
                .append(renderer.labelLine(language, Message.BANLIST_PLAYER, target.name()))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BAN_DIALOG_CONFIRM_SEVERITY,
                        t(language, severity.label())))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_EXPIRES,
                        renderer.expirationText(language, expiration)))
                .build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(t(language, Message.BAN_DIALOG_CONFIRM_TITLE), NamedTextColor.RED))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(details, 260)))
                        .inputs(List.of(DialogInput.text(REASON_INPUT, 260,
                                Component.text(t(language, Message.BANLIST_REASON), NamedTextColor.GRAY),
                                true, reason, 256, null)))
                        .build())
                .type(DialogType.confirmation(
                        button(language, Message.BAN_DIALOG_CONFIRM_ACTION, NamedTextColor.RED,
                                (response, audience) -> runOnMain(audience,
                                        player -> confirmOrWarn(
                                                player, target, severity, response.getText(REASON_INPUT)))),
                        ActionButton.create(Component.text(t(language, Message.BAN_DIALOG_BACK), NamedTextColor.GRAY),
                                null, 90, DialogAction.customClick((response, audience) -> runOnMain(audience,
                                player -> openSeverity(player, target)), CALLBACK_OPTIONS)))));
        moderator.showDialog(dialog);
    }

    private void confirmOrWarn(Player moderator, Target target, BanSeverity severity, String rawReason) {
        String reason = BanReason.normalize(rawReason).orElse(null);
        if (reason == null) {
            moderator.sendMessage(error(moderator, Message.BAN_REASON_REQUIRED));
            openConfirmation(moderator, target, severity, "");
            return;
        }
        if (needsNeverJoinedConfirmation(target.hasPlayedBefore())) {
            openNeverJoinedConfirmation(moderator, target, severity, reason);
            return;
        }
        confirm(moderator, target, severity, reason);
    }

    private void openNeverJoinedConfirmation(
            Player moderator,
            Target target,
            BanSeverity severity,
            String reason
    ) {
        Language language = languageService.language(moderator);
        Component warning = Component.text()
                .append(Component.text(
                        t(language, Message.BAN_DIALOG_NEVER_JOINED_WARNING, target.name()), NamedTextColor.RED))
                .appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_REASON, reason))
                .build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(
                                t(language, Message.BAN_DIALOG_NEVER_JOINED_TITLE), NamedTextColor.RED))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(warning, 280)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.confirmation(
                        button(language, Message.BAN_DIALOG_NEVER_JOINED_CONFIRM_ACTION, NamedTextColor.RED,
                                (response, audience) -> runOnMain(audience,
                                        player -> confirm(player, target, severity, reason))),
                        ActionButton.create(Component.text(t(language, Message.BAN_DIALOG_BACK), NamedTextColor.GRAY),
                                null, 90, DialogAction.customClick((response, audience) -> runOnMain(audience,
                                player -> openConfirmation(player, target, severity, reason)), CALLBACK_OPTIONS)))));
        moderator.showDialog(dialog);
    }

    static boolean needsNeverJoinedConfirmation(boolean hasPlayedBefore) {
        return !hasPlayedBefore;
    }

    private void confirm(Player moderator, Target target, BanSeverity severity, String reason) {
        if (moderator.getUniqueId().equals(target.uuid())) {
            moderator.sendMessage(error(moderator, Message.BAN_SELF));
            return;
        }
        if (banService.active(target.uuid(), target.name()).isPresent()) {
            moderator.sendMessage(error(moderator, Message.BAN_ALREADY_BANNED, target.name()));
            return;
        }
        try {
            BanRecord record = banService.ban(
                    target.uuid(), target.name(), severity, reason, moderator.getName());
            Language language = languageService.language(moderator);
            String expiration = renderer.expirationText(language, record.expiresAt());
            moderator.sendMessage(Component.text(t(language, Message.BAN_SUCCESS, record.playerName(), expiration),
                    NamedTextColor.GREEN));
            announcementBroadcaster.broadcast(moderator.getName(), record);
            kickIfOnline(record);
        } catch (BanServiceException e) {
            plugin.getLogger().log(Level.SEVERE, "Ban dialog failed", e);
            moderator.sendMessage(error(moderator, Message.BAN_DATABASE_ERROR));
        }
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
        player.kick(renderer.banMessage(languageService.languageForAddress(address), record), PlayerKickEvent.Cause.BANNED);
    }

    private ActionButton button(
            Language language,
            Message message,
            NamedTextColor color,
            io.papermc.paper.registry.data.dialog.action.DialogActionCallback callback
    ) {
        return ActionButton.create(Component.text(t(language, message), color), null, 130,
                DialogAction.customClick(callback, CALLBACK_OPTIONS));
    }

    private void runOnMain(Audience audience, Consumer<Player> action) {
        if (!(audience instanceof Player player) || !BanCommandController.canManageBans(player)) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.accept(player);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> action.accept(player));
        }
    }

    private Component error(Player player, Message message, Object... args) {
        return Component.text(t(languageService.language(player), message, args), NamedTextColor.RED);
    }

    private String t(Language language, Message message, Object... args) {
        return languageService.t(language, message, args);
    }

    private record Target(UUID uuid, String name, boolean hasPlayedBefore) {
    }
}
