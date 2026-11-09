package org.encinet.mik.feature.player.pvp;

import org.encinet.mik.shared.role.RolePermissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.ui.*;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Collection;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuState;
import org.encinet.mik.shared.util.PlayerDisplay;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PvpMenuController implements AutoCloseable {

    private static final String SETTINGS_SCREEN = "pvp-settings";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final LanguageService languageService;
    private final PvpSettingsStore settingsStore;
    private final PvpCombatController combatController;
    private final PvpStateResolver stateResolver;
    private final Supplier<? extends Collection<? extends Player>> onlinePlayers;
    private final Function<UUID, Player> players;
    private final FloatingMenuScreen<PvpAdminDirectory.Position> directory;
    private final FloatingMenuScreen<SettingsView> settings;
    private boolean closed;

    private record SettingsView(UUID target, int page) { }
    private record PlayerRevision(UUID id, String name, Component displayName, PvpSettings settings,
                                  boolean enabled, long combatSeconds) { }
    private record DirectoryRevision(Language language, boolean permitted, List<PlayerRevision> players) { }
    private record SettingsRevision(Language language, boolean permitted, PlayerRevision target) { }

    PvpMenuController(LanguageService languageService,
                      PvpSettingsStore settingsStore, PvpCombatController combatController,
                      PvpStateResolver stateResolver, Supplier<? extends Collection<? extends Player>> onlinePlayers,
                      Function<UUID, Player> players) {
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.combatController = combatController;
        this.stateResolver = stateResolver;
        this.onlinePlayers = onlinePlayers;
        this.players = players;
        directory = new FloatingMenuScreen<>(PvpAdminDirectory.SCREEN_ID, this::directoryDefinition);
        settings = new FloatingMenuScreen<>(SETTINGS_SCREEN, this::settingsDefinition);
    }

    void openMenu(Player player) {
        openMenu(player, player);
    }

    void openMenu(Player viewer, Player target) {
        if (closed || resolveTarget(viewer, target.getUniqueId(), false) == null) return;
        settings.open(viewer, new SettingsView(target.getUniqueId(), 0));
    }

    MenuPanel settingsPanel(Player player, Runnable redraw) {
        return new MenuPanel() {
            private int page;
            private boolean retired;
            private long revision;
            @Override public MenuScene render() {
                if (closed || retired) return MenuScene.screen("pvp-settings-closed").content(Ui.column().build()).build();
                long current = ++revision;
                BooleanSupplier active = () -> !closed && !retired && revision == current;
                return settingsDefinition(player, player, page, index -> {
                    if (active.getAsBoolean()) { page = index; redraw.run(); }
                }, () -> { if (active.getAsBoolean()) redraw.run(); }, active);
            }
            @Override public void close() { retired = true; revision++; }
        };
    }

    private MenuScene settingsDefinition(FloatingMenuContext<SettingsView> context) {
        Player viewer = context.player();
        SettingsView source = context.state();
        Player target = players.apply(source.target());
        boolean permitted = viewer.getUniqueId().equals(source.target()) || canManageOthers(viewer);
        MenuScene content;
        if (!permitted || target == null || !target.isOnline()) {
            content = unavailable(viewer, SETTINGS_SCREEN, permitted ? Message.PVP_PLAYER_NOT_FOUND_MM : Message.PVP_NO_PERMISSION_MM);
        } else {
            content = settingsDefinition(viewer, target, source.page(), page -> {
                if (current(settings, viewer, source)) context.setState(new SettingsView(source.target(), page));
            }, () -> {
                if (current(settings, viewer, source)) context.setState(new SettingsView(source.target(), source.page()));
            }, () -> current(settings, viewer, source));
        }
        return content.toBuilder().content(UiTemplates.withBack(content.content(),
                Component.text(languageService.t(viewer, Message.BACK), NamedTextColor.GREEN)))
                .refreshWhenChanged(20, player -> settingsRevision(player, source.target()), (player, _) -> {
                    if (retained(settings, player, source)) context.setState(new SettingsView(source.target(), source.page()));
                }).build();
    }

    private MenuScene settingsDefinition(Player viewer, Player target, int page, IntConsumer browse,
                                        Runnable redraw, BooleanSupplier active) {
        UUID targetId = target.getUniqueId();
        boolean self = viewer.getUniqueId().equals(targetId);
        var settings = settingsStore.get(targetId);
        Component title = Component.text(self ? languageService.t(viewer, Message.PVP_MENU_TITLE)
                : languageService.t(viewer, Message.PVP_TARGET_MENU_TITLE, target.getName()), NamedTextColor.LIGHT_PURPLE);
        var items = new ArrayList<UiNode>();
        var keys = PvpSettingKey.values();
        boolean[] values = {settings.enabled(), settings.protectMobs(), settings.allowMountedMobDamage(), settings.enableOnDeath()};
        for (int index = 0; index < keys.length; index++) {
            var key = keys[index];
            var label = toggleLabel(viewer, key, values[index]);
            items.add(Ui.button(label).key("setting:" + key.name().toLowerCase(Locale.ROOT))
                    .summaryLines(2).tooltip(label).selected(values[index]).primary((player, _) -> {
                        if (!active.getAsBoolean() || !viewer.getUniqueId().equals(player.getUniqueId())) return;
                        Player current = resolveTarget(player, targetId, false);
                        if (current != null) toggleSetting(player, current, key);
                        redraw.run();
                    }).build());
        }
        var options = Ui.pagedList().key("settings").items(items,LinearSlot.stretch()).page(page, browse).build();
        var body = UiTemplates.reading(options.key(),Ui.heading(title).key("heading").summaryLines(1).build(),options);
        return MenuScene.screen(SETTINGS_SCREEN, title).stableAnchor().withoutTitle()
                .content(Ui.column().child(UiKey.named("target:" + targetId), UiTemplates.window("preferences", body)).build()).build();
    }

    void openAdminMenu(Player viewer) {
        if (closed || !viewer.isOnline()) return;
        if (!canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        directory.open(viewer, directory.state(viewer).orElse(PvpAdminDirectory.Position.START).copy());
    }

    private MenuScene directoryDefinition(FloatingMenuContext<PvpAdminDirectory.Position> context) {
        Player viewer = context.player();
        var source = context.state();
        MenuScene content;
        if (!canManageOthers(viewer)) {
            content = unavailable(viewer, PvpAdminDirectory.SCREEN_ID, Message.PVP_NO_PERMISSION_MM);
            content = content.toBuilder().content(UiTemplates.withBack(content.content(),
                    Component.text(languageService.t(viewer, Message.BACK), NamedTextColor.GREEN))).build();
        } else {
            var rows = roster().stream().map(target -> new PvpAdminDirectory.Row(target.getUniqueId(), playerHead(target),
                    adminPlayerLabel(viewer, target), Component.text(languageService.t(viewer, Message.PVP_TARGET_MENU_TITLE, target.getName())))).toList();
            var actions = new PvpAdminDirectory.Actions(index -> {
                if (current(directory, viewer, source)) context.setState(PvpAdminDirectory.Position.at(rows, index));
            }, (player, id) -> {
                if (!current(directory, player, source)) return;
                toggleAdmin(player, id);
                if (current(directory, player, source)) context.setState(source.copy());
            }, (player, id) -> {
                if (!current(directory, player, source)) return;
                Player target = resolveTarget(player, id, true);
                if (target != null) settings.open(player, new SettingsView(id, 0));
                else context.setState(source.copy());
            });
            content = PvpAdminDirectory.compose(Component.text(languageService.t(viewer, Message.PVP_ADMIN_MENU_TITLE), NamedTextColor.LIGHT_PURPLE),
                    mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM), Component.text(languageService.t(viewer, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                    rows, source, actions);
        }
        return content.toBuilder().refreshWhenChanged(20, this::directoryRevision, (player, _) -> {
            if (retained(directory, player, source)) context.setState(source.copy());
        }).build();
    }

    private List<? extends Player> roster() {
        return onlinePlayers.get().stream().filter(Player::isOnline)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Player::getUniqueId)).toList();
    }

    private PlayerRevision playerRevision(Player player) {
        UUID id = player.getUniqueId();
        return new PlayerRevision(id, player.getName(), player.displayName(), settingsStore.get(id),
                stateResolver.effectiveEnabled(id), combatController.combatTagRemainingSeconds(id));
    }

    private DirectoryRevision directoryRevision(Player viewer) {
        boolean permitted = canManageOthers(viewer);
        return new DirectoryRevision(languageService.language(viewer), permitted,
                permitted ? roster().stream().map(this::playerRevision).toList() : List.of());
    }

    private SettingsRevision settingsRevision(Player viewer, UUID id) {
        boolean permitted = viewer.getUniqueId().equals(id) || canManageOthers(viewer);
        Player target = permitted ? players.apply(id) : null;
        return new SettingsRevision(languageService.language(viewer), permitted,
                target == null || !target.isOnline() ? null : playerRevision(target));
    }

    private <S> boolean retained(FloatingMenuScreen<S> screen, Player viewer, S source) {
        return !closed && viewer.isOnline() && screen.flow(viewer).filter(flow -> flow.state() == source
                && flow.handle().state() != FloatingMenuState.CLOSING && flow.handle().state() != FloatingMenuState.CLOSED).isPresent();
    }

    private <S> boolean current(FloatingMenuScreen<S> screen, Player viewer, S source) {
        return retained(screen, viewer, source) && screen.flow(viewer)
                .filter(flow -> flow.handle().state() == FloatingMenuState.ACTIVE).isPresent();
    }

    private MenuScene unavailable(Player viewer, String screen, Message reason) {
        var status = Ui.text(mm(viewer, reason)).key("status").textWidth(240).build();
        return MenuScene.screen(screen).stableAnchor().withoutTitle()
                .content(UiTemplates.window("status", UiTemplates.focus(Ui.column().child(status).build(), status.key()))).build();
    }

    private Player resolveTarget(Player viewer, UUID targetId, boolean staffOnly) {
        if (closed || !viewer.isOnline()) return null;
        if ((staffOnly || !viewer.getUniqueId().equals(targetId)) && !canManageOthers(viewer)) {
            denyManageOthers(viewer); return null;
        }
        Player target = players.apply(targetId);
        if (target == null || !target.isOnline() || !targetId.equals(target.getUniqueId())) {
            viewer.sendMessage(mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM)); return null;
        }
        return target;
    }

    boolean toggleAdmin(Player viewer, UUID targetId) {
        Player target = resolveTarget(viewer, targetId, true);
        if (target == null) return false;
        setTargetPvp(viewer, target, !settingsStore.get(targetId).enabled());
        return true;
    }

    void forget(UUID viewer) {
        var shutdown = new ShutdownSequence();
        shutdown.attempt("PVP directory", () -> directory.forget(viewer));
        shutdown.attempt("PVP settings", () -> settings.forget(viewer));
        shutdown.finish("PVP viewer " + viewer);
    }

    @Override public void close() {
        closed = true;
        var shutdown = new ShutdownSequence();
        shutdown.attempt("PVP directory", directory::forgetAll);
        shutdown.attempt("PVP settings", settings::forgetAll);
        shutdown.finish("PVP menus");
    }

    private void setTargetPvp(Player viewer, Player target, boolean enabled) {
        PvpSettings current = settingsStore.get(target.getUniqueId());
        settingsStore.save(target.getUniqueId(), current.withEnabled(enabled));
        combatController.onPvpStateChanged(target.getUniqueId());

        viewer.sendMessage(languageService.rich(viewer, Message.PVP_SET_OTHER_RICH, NamedTextColor.GREEN,
                org.encinet.mik.shared.i18n.RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW)),
                org.encinet.mik.shared.i18n.RichArg.component("state", Component.text(languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.WHITE))));

        target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                languageService.t(target, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
    }

    private Component toggleLabel(Player viewer, PvpSettingKey settingKey, boolean enabled) {
        return Component.text(languageService.t(viewer, settingKey.label()),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(languageService.t(viewer,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private Component adminPlayerLabel(Player viewer, Player target) {
        boolean effectiveEnabled = stateResolver.effectiveEnabled(target.getUniqueId());
        Component label = PlayerDisplay.name(target,
                        effectiveEnabled ? NamedTextColor.GREEN : NamedTextColor.WHITE)
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

    private Component stateLine(Player viewer, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(languageService.t(viewer, label) + ": ", NamedTextColor.WHITE))
                .append(Component.text(languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                .build();
    }

    private Component combatLine(Player viewer, UUID playerId) {
        return Component.text()
                .append(Component.text(languageService.t(viewer, Message.PVP_COMBAT_TAG_LABEL) + ": ", NamedTextColor.WHITE))
                .append(Component.text(languageService.t(viewer, Message.PVP_COMBAT_TAG_VALUE,
                        combatController.combatTagRemainingSeconds(playerId)), NamedTextColor.RED))
                .build();
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private boolean canManageOthers(Player player) {
        return RolePermissions.canModerate(player);
    }

    private void denyManageOthers(Player player) {
        player.sendMessage(mm(player, Message.PVP_NO_PERMISSION_MM));
    }
}
