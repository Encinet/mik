package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
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
        if (!RolePermissions.canModerate(player)) return;
        MenuScene.Builder menu = MenuScene.screen("prison-home").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("summary"),
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
        menu.information("title", label(player, Message.PRISON_MENU_TITLE)).region("title");
        List<Region> prisons = service.prisons();
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

    private void browsePrisons(Player player) {
        if (!RolePermissions.canModerate(player)) return;
        FloatingMenus.search(player, MenuSearch.<Region>builder(label(player, Message.PRISON_MENU_TITLE),
                        label(player, "prisons"))
                .find(query -> CompletableFuture.completedFuture(service.prisons().stream()
                        .filter(region -> region.plot().name().toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(region -> UiKey.item(region.plot().id()))
                .label(region -> Component.text(region.plot().name() + " · ", NamedTextColor.YELLOW)
                        .append(cellsLabel(player, region)))
                .selected((actor, region) -> openPrison(actor, region.plot().id())).build());
    }

    private Component cellsLabel(Player player, Region region) {
        return region.cells().isEmpty()
                ? language.text(player, Message.PRISON_MENU_WHOLE_PLOT, NamedTextColor.WHITE)
                : language.text(player, Message.PRISON_MENU_CELL_COUNT, NamedTextColor.WHITE, region.cells().size());
    }

    private void openPrison(Player player, UUID plotId) {
        if (!RolePermissions.canModerate(player)) return;
        Region region = service.prison(plotId);
        if (region == null) {
            player.sendMessage(language.text(player, Message.PRISON_ERROR_PLOT_UNAVAILABLE, NamedTextColor.RED));
            return;
        }
        var plot = region.plot();
        Component details = cellsLabel(player, region);
        MenuScene.Builder menu = MenuScene.screen("prison-detail").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("details"),
                        UiTemplates.navigation("navigation"))));
        menu.information("title", Component.text(plot.name(), NamedTextColor.LIGHT_PURPLE)).region("title");
        menu.information("details", details).region("details");
        menu.navigation("back", language.text(player, Message.BACK, NamedTextColor.WHITE)).region("navigation")
                .primary((actor, _) -> open(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    /** Read-only list: a ban is the sentence, so inmates are released through the ban menu. */
    private void browseInmates(Player player) {
        if (!RolePermissions.canModerate(player)) return;
        List<PrisonAssignment> candidates = service.prisoners();
        if (candidates.isEmpty()) {
            player.sendMessage(language.text(player, Message.PRISON_MENU_NO_INMATES, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(player, MenuSearch.<PrisonAssignment>builder(label(player, "inmates"),
                        label(player, Message.PRISON_MENU_TITLE))
                .find(query -> CompletableFuture.completedFuture(candidates.stream()
                        .filter(inmate -> inmate.playerName().toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(inmate -> UiKey.item(inmate.playerId()))
                .label(inmate -> Component.text(inmate.playerName() + " · "
                        + inmate.plot().name(), NamedTextColor.YELLOW))
                .selected((actor, inmate) -> service.prisoner(inmate.playerId()).ifPresent(live -> showInmate(actor, live))).build());
    }

    private void showInmate(Player player, PrisonAssignment inmate) {
        if (!RolePermissions.canModerate(player)) return;
        String remaining = inmate.isPermanent()
                ? language.t(player, Message.PRISON_PANEL_PERMANENT)
                : PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(), System.currentTimeMillis());
        Component details = Component.text(inmate.playerName() + "\n", NamedTextColor.AQUA)
                .append(language.text(player, Message.PRISON_MENU_INMATE_REMAINING, NamedTextColor.WHITE, remaining))
                .append(Component.newline())
                .append(language.text(player, Message.PRISON_MENU_INMATE_REASON, NamedTextColor.WHITE, reason(player, inmate)));
        MenuScene.Builder menu = MenuScene.screen("prison-inmate").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("details"),
                        UiTemplates.navigation("navigation"))));
        menu.information("title", Component.text(inmate.playerName(), NamedTextColor.LIGHT_PURPLE)).region("title");
        menu.information("details", details).region("details");
        menu.navigation("back", language.text(player, Message.BACK, NamedTextColor.WHITE)).region("navigation")
                .primary((actor, _) -> browseInmates(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    private String reason(Player player, PrisonAssignment inmate) {
        String reason = inmate.reason();
        return reason == null || reason.isBlank() ? language.t(player, Message.BAN_UNKNOWN_REASON) : reason;
    }

    private Component label(Player player, Message message) {
        return language.text(player, message, NamedTextColor.LIGHT_PURPLE);
    }

    private Component label(Player player, String action) {
        return language.text(player, Message.PRISON_MENU_LABEL, NamedTextColor.WHITE, action);
    }
}
