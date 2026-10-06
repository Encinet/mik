package org.encinet.mik.module.pvp;

import org.encinet.mik.module.role.RolePermissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.util.PlayerDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PvpMenuController implements Listener {

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private static final int ADMIN_PAGE_SIZE = 9;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final PvpSettingsStore settingsStore;
    private final PvpCombatController combatController;
    private final PvpStateResolver stateResolver;

    PvpMenuController(JavaPlugin plugin, LanguageService languageService,
                      PvpSettingsStore settingsStore, PvpCombatController combatController,
                      PvpStateResolver stateResolver) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.combatController = combatController;
        this.stateResolver = stateResolver;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void disable() {
        HandlerList.unregisterAll(this);
    }

    void openMenu(Player player) {
        openMenu(player, player);
    }

    void openMenu(Player viewer, Player target) {
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (!self && !canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        PvpSettings settings = settingsStore.get(target.getUniqueId());
        Component title = Component.text(self
                ? languageService.t(viewer, Message.PVP_MENU_TITLE)
                : languageService.t(viewer, Message.PVP_TARGET_MENU_TITLE, target.getName()),
                NamedTextColor.DARK_PURPLE);

        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "pvp-settings:" + target.getUniqueId(), title)
                .layout(FloatingMenuLayouts.actions(3));
        PvpSettingKey[] keys = PvpSettingKey.values();
        boolean[] values = {settings.enabled(), settings.protectMobs(),
                settings.allowMountedMobDamage(), settings.enableOnDeath()};
        for (int i = 0; i < keys.length; i++) {
            PvpSettingKey key = keys[i];
            menu.toggle("setting:" + key.name().toLowerCase(Locale.ROOT),
                            values[i], key.enabledMaterial(), key.disabledMaterial(),
                            toggleLabel(viewer, key, values[i]))
                    .primary((p, handle) -> {
                        if (!self && !canManageOthers(p)) {
                            handle.close();
                            denyManageOthers(p);
                            return;
                        }
                        toggleSetting(p, target, key);
                        openMenu(p, target);
                    });
        }
        menu.back(Component.text(languageService.t(viewer,
                                self ? Message.BACK_TO_MAIN : Message.PVP_BACK_ADMIN),
                        NamedTextColor.GREEN));
        FloatingMenus.present(viewer, menu.build());
    }

    void openAdminMenu(Player viewer, int requestedPage) {
        if (!canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        FloatingMenuPage pagination = new FloatingMenuPage(
                requestedPage, players.size(), ADMIN_PAGE_SIZE);
        int totalPages = pagination.count();
        int page = pagination.index();

        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen(
                        "pvp-admin",
                        Component.text(languageService.t(viewer, Message.PVP_ADMIN_MENU_TITLE,
                                page + 1, totalPages), NamedTextColor.DARK_PURPLE))
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.cards("players", 3, 3),
                        FloatingMenuLayouts.navigation("controls")));
        int from = pagination.fromIndex();
        int to = pagination.toIndex();
        for (int index = from; index < to; index++) {
            Player target = players.get(index);
            builder.item("player:" + target.getUniqueId(), playerHead(target),
                            adminPlayerLabel(viewer, target))
                    .region("players")
                    .primary((p, handle) -> {
                        setTargetPvp(p, target, !settingsStore.get(target.getUniqueId()).enabled());
                        openAdminMenu(p, page);
                    })
                    .secondary((p, handle) -> openMenu(p, target));
        }
        if (pagination.hasPrevious()) {
            builder.navigation("previous",
                            Component.text("‹ " + languageService.t(viewer, Message.PVP_PREV_PAGE),
                                    NamedTextColor.GREEN))
                    .region("controls")
                    .primary((p, handle) -> openAdminMenu(p, pagination.previous().index()));
            builder.on(FloatingMenuInteraction.SCROLL_UP,
                    (p, handle, input) -> openAdminMenu(p, pagination.previous().index()));
        }
        builder.back(
                        Component.text(languageService.t(viewer, Message.BACK_TO_MAIN),
                                NamedTextColor.GREEN))
                .region("controls");
        if (pagination.hasNext()) {
            builder.navigation("next",
                            Component.text(languageService.t(viewer, Message.PVP_NEXT_PAGE) + " ›",
                                    NamedTextColor.GREEN))
                    .region("controls")
                    .primary((p, handle) -> openAdminMenu(p, pagination.next().index()));
            builder.on(FloatingMenuInteraction.SCROLL_DOWN,
                    (p, handle, input) -> openAdminMenu(p, pagination.next().index()));
        }
        FloatingMenus.present(viewer, builder.build());
    }

    private void setTargetPvp(CommandSender sender, Player target, boolean enabled) {
        PvpSettings current = settingsStore.get(target.getUniqueId());
        settingsStore.save(target.getUniqueId(), current.withEnabled(enabled));
        combatController.onPvpStateChanged(target.getUniqueId());

        if (sender instanceof Player viewer) {
            viewer.sendMessage(languageService.rich(viewer, Message.PVP_SET_OTHER_RICH, NamedTextColor.GREEN,
                    org.encinet.mik.module.i18n.RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW), target.getName()),
                    org.encinet.mik.module.i18n.RichArg.component("state", Component.text(languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY), languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF))));
        }

        target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                languageService.t(target, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
    }

    private Component toggleLabel(Player viewer, PvpSettingKey settingKey, boolean enabled) {
        return Component.text(languageService.t(viewer, settingKey.label()),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(languageService.t(viewer,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
    }

    private Component adminPlayerLabel(Player viewer, Player target) {
        boolean effectiveEnabled = stateResolver.effectiveEnabled(target.getUniqueId());
        Component label = PlayerDisplay.name(target,
                        effectiveEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.newline())
                .append(stateLine(viewer, Message.PVP_STATE_LABEL, effectiveEnabled));
        if (combatController.isCombatTagged(target.getUniqueId())) {
            label = label.append(Component.newline())
                    .append(combatLine(viewer, target.getUniqueId()));
        }
        return label;
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void toggleSetting(Player viewer, Player target, PvpSettingKey key) {
        PvpSettings current = settingsStore.get(target.getUniqueId());
        PvpSettings next = current.toggle(key);
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (key == PvpSettingKey.ENABLED && !next.enabled() && self
                && combatController.isCombatTagged(target.getUniqueId())
                && stateResolver.effectiveEnabled(target.getUniqueId())
                && !stateResolver.effectiveEnabled(target.getUniqueId(), false)) {
            viewer.sendMessage(mm(viewer, Message.PVP_COMBAT_LOCKED_MM,
                    combatController.combatTagRemainingSeconds(target.getUniqueId())));
            return;
        }
        settingsStore.save(target.getUniqueId(), next);
        if (key == PvpSettingKey.ENABLED) {
            combatController.onPvpStateChanged(target.getUniqueId());
            if (!viewer.getUniqueId().equals(target.getUniqueId())) {
                target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                        languageService.t(target, next.enabled() ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
            }
        }
    }

    private boolean isPvpMenuTitle(String title) {
        return languageService.titleMatches(Message.PVP_MENU_TITLE, title)
                || titleMatchesTargetMenu(title)
                || titleMatchesAdminMenu(title);
    }

    private int currentAdminPage(String title) {
        for (Language language : Language.values()) {
            if (!title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_ADMIN_MENU_TITLE, 1, 1), "1"))) {
                continue;
            }
            Matcher matcher = FIRST_NUMBER.matcher(title);
            return matcher.find() ? Math.max(0, parseInt(matcher.group(), 1) - 1) : 0;
        }
        return 0;
    }

    private boolean titleMatchesTargetMenu(String title) {
        for (Language language : Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_TARGET_MENU_TITLE, ""), ""))) {
                return true;
            }
        }
        return false;
    }

    private boolean titleMatchesAdminMenu(String title) {
        for (Language language : Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_ADMIN_MENU_TITLE, 1, 1), "1"))) {
                return true;
            }
        }
        return false;
    }

    private String staticTitlePrefix(String sample, String marker) {
        int index = marker.isEmpty() ? sample.length() : sample.indexOf(marker);
        return index < 0 ? sample : sample.substring(0, index);
    }

    private Component stateLine(CommandSender sender, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(t(sender, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();
    }

    private Component combatLine(CommandSender sender, UUID playerId) {
        return Component.text()
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_LABEL) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_VALUE,
                        combatController.combatTagRemainingSeconds(playerId)), NamedTextColor.RED))
                .build();
    }

    private Language senderLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageService.language(player);
        }
        return Language.DEFAULT;
    }

    private String t(CommandSender sender, Message message, Object... args) {
        return languageService.t(senderLanguage(sender), message, args);
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean canManageOthers(Player player) {
        return RolePermissions.canModerate(player);
    }

    private void denyManageOthers(Player player) {
        player.sendMessage(mm(player, Message.PVP_NO_PERMISSION_MM));
    }
}
