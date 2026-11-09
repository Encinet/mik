package org.encinet.mik.feature.server.ban;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Pattern;

final class BanMenu {

    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final BanService banService;
    private final BanMessageRenderer renderer;
    private final BanAnnouncementBroadcaster announcementBroadcaster;

    BanMenu(
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
        if (!RolePermissions.canModerate(moderator)) return;
        Language language = languageService.language(moderator);
        FloatingMenus.search(moderator,MenuSearch.<Candidate>builder(label(language,Message.BAN_MENU_SELECT_TITLE),
                        label(language,Message.BAN_MENU_PLAYER_INPUT)).maximumLength(36)
                .find(query -> CompletableFuture.completedFuture(RolePermissions.canModerate(moderator)
                        ? candidates(query,MenuPlayerSearch.cached(moderator,query),moderator.getUniqueId()) : List.of()))
                .key(player -> player.uuid() == null ? UiKey.item("name:"+player.name().toLowerCase(Locale.ROOT)) : UiKey.item(player.uuid()))
                .label(player -> player.uuid() == null ? Component.text(player.name(),NamedTextColor.YELLOW)
                        .append(Component.text(" · ",NamedTextColor.WHITE)).append(label(language,Message.BAN_MENU_NEVER_JOINED_TITLE))
                        : MenuPlayerSearch.label(new MenuPlayerSearch.Candidate(player.uuid(),player.name())))
                .selected(this::selectPlayer).build());
    }

    record Candidate(UUID uuid, String name) { }

    static List<Candidate> candidates(String query, List<MenuPlayerSearch.Candidate> known, UUID moderator) {
        var result = new ArrayList<Candidate>();
        for (var player : MenuPlayerSearch.matching(query,known)) if (!player.id().equals(moderator))
            result.add(new Candidate(player.id(),player.name()));
        String name = query.strip();
        // An unseen literal name is an explicit candidate, never an automatic choice.
        if (PLAYER_NAME_PATTERN.matcher(name).matches() && known.stream().noneMatch(player -> player.name().equalsIgnoreCase(name)))
            result.add(new Candidate(null,name));
        return List.copyOf(result);
    }

    private static MenuScene.Builder screen(String id, Component title) {
        var menu = MenuScene.screen(id).content(UiTemplates.window("ban", UiTemplates.menu(
                UiTemplates.title("title"), UiTemplates.information("details"), UiTemplates.actions("actions", 2),
                UiTemplates.navigation("pages"), UiTemplates.navigation("navigation"))));
        menu.information("title", title).region("title");
        return menu;
    }

    private Component label(Language language, Message message) {
        return Component.text(t(language, message), NamedTextColor.WHITE);
    }

    private void selectPlayer(Player moderator, Candidate selected) {
        if (!RolePermissions.canModerate(moderator)) return;
        Target target = selected.uuid() == null ? manualTarget(selected.name()) : knownTarget(selected);
        if (target == null) {
            moderator.sendMessage(error(moderator, Message.BAN_INVALID_PLAYER));
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

    private Target knownTarget(Candidate selected) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(selected.uuid());
        return new Target(selected.uuid(),player.getName() == null ? selected.name() : player.getName(),
                player.isOnline() || player.hasPlayedBefore());
    }

    private void openSeverity(Player moderator, Target target) {
        if (!RolePermissions.canModerate(moderator)) return;
        Language language = languageService.language(moderator);
        var menu = screen("ban-severity", label(language, Message.BAN_MENU_SEVERITY_TITLE));
        menu.information("target", Component.text(target.name(), NamedTextColor.AQUA)).region("details");
        for (BanSeverity severity : BanSeverity.values())
            menu.control(severity.name(), label(language, severity.label())).region("actions")
                    .primary((viewer, _) -> openConfirmation(viewer, target, severity, ""));
        menu.back(label(language, Message.BAN_MENU_BACK)).region("navigation");
        FloatingMenus.present(moderator, menu.build());
    }

    private void openConfirmation(Player moderator, Target target, BanSeverity severity, String reason) {
        if (!RolePermissions.canModerate(moderator)) return;
        Language language = languageService.language(moderator);
        Instant expiration = severity.expiresAt(Instant.now());
        Component details = Component.text()
                .append(renderer.labelLine(language, Message.BANLIST_PLAYER, target.name())).appendNewline()
                .append(renderer.labelLine(language, Message.BAN_MENU_CONFIRM_SEVERITY, t(language, severity.label()))).appendNewline()
                .append(renderer.labelLine(language, Message.BANLIST_EXPIRES, renderer.expirationText(language, expiration)))
                .build();
        FloatingMenus.input(moderator, MenuInputForm.builder(label(language, Message.BAN_MENU_CONFIRM_TITLE))
                .description(details)
                .field(MenuInputForm.Field.text("reason", label(language, Message.BANLIST_REASON), reason, 256))
                .validate((viewer, values) -> BanReason.normalize(values.text("reason")).isPresent()
                        ? null : error(viewer, Message.BAN_REASON_REQUIRED))
                .cancel(label(language, Message.BAN_MENU_BACK))
                .submit(label(language, Message.BAN_MENU_CONFIRM_ACTION), (viewer, values) ->
                        confirmOrWarn(viewer, target, severity, values.text("reason"))).build());
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

    private void openNeverJoinedConfirmation(Player moderator, Target target, BanSeverity severity, String reason) {
        if (!RolePermissions.canModerate(moderator)) return;
        Language language = languageService.language(moderator);
        var menu = screen("ban-never-joined", label(language, Message.BAN_MENU_NEVER_JOINED_TITLE));
        menu.information("warning", Component.text(t(language, Message.BAN_MENU_NEVER_JOINED_WARNING, target.name()),
                NamedTextColor.RED)).region("details");
        menu.information("reason", renderer.labelLine(language, Message.BANLIST_REASON, reason)).region("details");
        menu.control("confirm", label(language, Message.BAN_MENU_NEVER_JOINED_CONFIRM_ACTION)).region("actions")
                .primary((viewer, handle) -> { confirm(viewer, target, severity, reason); handle.back(); });
        menu.control("edit", label(language, Message.BAN_MENU_BACK)).region("navigation")
                .primary((viewer, handle) -> { handle.back(); openConfirmation(viewer, target, severity, reason); });
        FloatingMenus.present(moderator, menu.build());
    }

    static boolean needsNeverJoinedConfirmation(boolean hasPlayedBefore) {
        return !hasPlayedBefore;
    }

    private void confirm(Player moderator, Target target, BanSeverity severity, String reason) {
        if (!RolePermissions.canModerate(moderator)) return;
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
            plugin.getLogger().log(Level.SEVERE, "Ban menu failed", e);
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

    private Component error(Player player, Message message, Object... args) {
        return Component.text(t(languageService.language(player), message, args), NamedTextColor.RED);
    }

    private String t(Language language, Message message, Object... args) {
        return languageService.t(language, message, args);
    }

    private record Target(UUID uuid, String name, boolean hasPlayedBefore) {
    }
}
