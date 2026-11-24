package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuSearch;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PrisonMenu {
    private final LanguageService language;
    private final PrisonService service;

    PrisonMenu(LanguageService language, PrisonService service) {
        this.language = language;
        this.service = service;
    }

    void open(Player player) {
        if (!RolePermissions.isMember(player)) return;
        MenuScene.Builder menu = MenuScene.screen("prison-home").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("summary"),
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
        menu.information("title", label(player, Message.PRISON_MENU_TITLE)).region("title");
        List<PrisonRecord> prisons = service.prisons();
        Component summary = prisons.isEmpty()
                ? language.text(player, Message.PRISON_MENU_NO_PRISONS, NamedTextColor.WHITE)
                : language.text(player, Message.PRISON_MENU_PRISON_COUNT, NamedTextColor.WHITE, prisons.size());
        menu.information("summary", summary).region("summary");
        menu.control("prisons", label(player, "prisons")).region("actions")
                .primary((actor, _) -> browsePrisons(actor));
        menu.control("inmates", label(player, "inmates")).region("actions")
                .primary((actor, _) -> browseInmates(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    private String plotName(UUID plotId) {
        Plot plot = service.plotFor(plotId);
        return plot == null ? plotId.toString() : plot.name();
    }

    private int cellCount(UUID plotId) {
        if (service.host() == null) return 0;
        return service.host().subplots(plotId).size();
    }

    private void browsePrisons(Player player) {
        if (!RolePermissions.isMember(player)) return;
        List<PrisonRecord> candidates = service.manageablePrisons(player);
        if (candidates.isEmpty()) {
            player.sendMessage(language.text(player, Message.MENU_SEARCH_EMPTY, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(player, MenuSearch.<PrisonRecord>builder(label(player, Message.PRISON_MENU_TITLE),
                        label(player, "prisons"))
                .find(query -> CompletableFuture.completedFuture(candidates.stream()
                        .filter(prison -> plotName(prison.plot()).toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(prison -> UiKey.item("prison:" + prison.plot()))
                .label(prison -> Component.text(plotName(prison.plot())
                        + " · " + cellCount(prison.plot()) + " cells", NamedTextColor.YELLOW))
                .selected((actor, prison) -> openPrison(actor, prison.plot())).build());
    }

    private void openPrison(Player player, UUID plotId) {
        if (!RolePermissions.isMember(player)) return;
        if (service.host() == null || !service.host().isEnabled(plotId)) {
            player.sendMessage(language.text(player, Message.PRISON_ERROR_PLOT_UNAVAILABLE, NamedTextColor.RED));
            return;
        }
        Plot plot = service.host().find(plotId);
        int cells = cellCount(plotId);
        Component details = Component.text(plot.name() + "\n", NamedTextColor.WHITE)
                .append(cells == 0
                        ? language.text(player, Message.PRISON_MENU_WHOLE_PLOT, NamedTextColor.WHITE)
                        : language.text(player, Message.PRISON_MENU_CELL_COUNT, NamedTextColor.WHITE, cells));
        MenuScene.Builder menu = MenuScene.screen("prison-detail").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("details"),
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
        menu.information("title", Component.text(plot.name(), NamedTextColor.LIGHT_PURPLE)).region("title");
        menu.information("details", details).region("details");
        menu.control("exit", label(player, "exit")).region("actions")
                .primary((actor, _) -> attempt(actor, () -> {
                    service.setExit(plotId, PrisonLocation.capture(actor.getLocation()));
                    notify(actor, "saved", "");
                    openPrison(actor, plotId);
                }));
        menu.navigation("back", language.text(player, Message.BACK, NamedTextColor.WHITE)).region("navigation")
                .primary((actor, _) -> open(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    /** Read-only list: a ban is the sentence, so inmates are released through the ban menu. */
    private void browseInmates(Player player) {
        List<PrisonerRecord> candidates = service.prisoners();
        if (candidates.isEmpty()) {
            player.sendMessage(language.text(player, Message.PRISON_MENU_NO_INMATES, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(player, MenuSearch.<PrisonerRecord>builder(label(player, "inmates"),
                        label(player, Message.PRISON_MENU_TITLE))
                .find(query -> CompletableFuture.completedFuture(candidates.stream()
                        .filter(inmate -> inmate.playerName().toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(inmate -> UiKey.item(inmate.playerId()))
                .label(inmate -> Component.text(inmate.playerName() + " · "
                        + plotName(inmate.plot().id()), NamedTextColor.YELLOW))
                .selected((actor, inmate) -> showInmate(actor, inmate)).build());
    }

    private void showInmate(Player player, PrisonerRecord inmate) {
        String remaining = inmate.isPermanent()
                ? language.t(player, Message.PRISON_PANEL_PERMANENT)
                : PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(), System.currentTimeMillis());
        Component details = Component.text(inmate.playerName() + "\n", NamedTextColor.AQUA)
                .append(language.text(player, Message.PRISON_MENU_INMATE_REMAINING, NamedTextColor.WHITE, remaining))
                .append(Component.newline())
                .append(language.text(player, Message.PRISON_MENU_INMATE_REASON, NamedTextColor.WHITE, inmate.reason()));
        MenuScene.Builder menu = MenuScene.screen("prison-inmate").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("details"),
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
        menu.information("title", Component.text(inmate.playerName(), NamedTextColor.LIGHT_PURPLE)).region("title");
        menu.information("details", details).region("details");
        menu.navigation("back", language.text(player, Message.BACK, NamedTextColor.WHITE)).region("navigation")
                .primary((actor, _) -> browseInmates(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    private void attempt(Player player, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(error(player, exception.getMessage()));
        }
    }

    private void notify(Player player, String action, String detail) {
        player.sendMessage(language.text(player, Message.PRISON_MENU_SUCCESS, NamedTextColor.GREEN, action, detail));
    }

    private Component error(Player player, String detail) {
        return language.text(player, Message.PRISON_MENU_ERROR, NamedTextColor.RED,
                detail == null ? "unknown error" : detail);
    }

    private Component label(Player player, Message message) {
        return language.text(player, message, NamedTextColor.LIGHT_PURPLE);
    }

    private Component label(Player player, String action) {
        return language.text(player, Message.PRISON_MENU_LABEL, NamedTextColor.WHITE, action);
    }
}
