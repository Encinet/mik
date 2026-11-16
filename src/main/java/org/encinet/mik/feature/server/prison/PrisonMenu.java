package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.MenuPlayerSearch;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuSearch;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.Locale;
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
        Component summary = service.prisons().isEmpty()
                ? language.text(player, Message.PRISON_MENU_NO_PRISONS, NamedTextColor.WHITE)
                : Component.text(service.prisons().size() + " · ").append(label(player, "prisons"));
        menu.information("summary", summary).region("summary");
        menu.control("create", label(player, "create")).region("actions")
                .primary((actor, _) -> create(actor));
        menu.control("prisons", label(player, "prisons")).region("actions")
                .primary((actor, _) -> browsePrisons(actor));
        if (RolePermissions.canModerate(player)) {
            menu.control("jail", label(player, "jail")).region("actions")
                    .primary((actor, _) -> chooseTarget(actor));
            menu.control("release", label(player, "release")).region("actions")
                    .primary((actor, _) -> choosePrisoner(actor));
        }
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    private void create(Player player) {
        if (!RolePermissions.isMember(player)) return;
        FloatingMenus.input(player, MenuInputForm.builder(label(player, "create"))
                .field(MenuInputForm.Field.text("name", label(player, Message.PRISON_MENU_PROMPT, "name"), "", 32))
                .cancel(language.text(player, Message.CLOSE, NamedTextColor.WHITE))
                .submit(language.text(player, Message.MENU_INPUT_DONE, NamedTextColor.GREEN), (actor, values) ->
                        attempt(actor, () -> {
                            PrisonRecord prison = service.create(actor, values.text("name"));
                            notify(actor, "created", prison.name());
                            openPrison(actor, prison.name());
                        })).build());
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
                        .filter(prison -> prison.name().contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(prison -> UiKey.item("prison:" + prison.name()))
                .label(prison -> Component.text(prison.name() + " · " + prison.cells().size() + " cells",
                        NamedTextColor.YELLOW))
                .selected((actor, prison) -> openPrison(actor, prison.name())).build());
    }

    private void openPrison(Player player, String name) {
        if (!RolePermissions.isMember(player)) return;
        PrisonRecord prison = service.prison(name).filter(record -> service.canManage(player, record)).orElse(null);
        if (prison == null) {
            player.sendMessage(error(player, "Prison does not exist or you cannot manage it."));
            return;
        }
        String cells = prison.cells().isEmpty() ? "—" : String.join(", ", prison.cells().keySet());
        Component details = Component.text(prison.name() + "\nCells: " + cells, NamedTextColor.WHITE);
        MenuScene.Builder menu = MenuScene.screen("prison-detail").content(UiTemplates.window("prison",
                UiTemplates.menu(UiTemplates.title("title"), UiTemplates.information("details"),
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
        menu.information("title", Component.text(prison.name(), NamedTextColor.LIGHT_PURPLE)).region("title");
        menu.information("details", details).region("details");
        for (String action : List.of("spawn", "exit", "corner1", "corner2")) {
            menu.control(action, label(player, action)).region("actions")
                    .primary((actor, _) -> attempt(actor, () -> {
                        String field = action.equals("corner1") ? "corner1" : action.equals("corner2") ? "corner2" : action;
                        service.setLocation(actor, prison.name(), field);
                        notify(actor, "saved", "");
                        openPrison(actor, prison.name());
                    }));
        }
        menu.control("add-cell", label(player, "add-cell")).region("actions")
                .primary((actor, _) -> inputCell(actor, prison.name()));
        menu.control("remove-cell", label(player, "remove-cell")).region("actions")
                .primary((actor, _) -> removeCell(actor, prison));
        menu.control("delete", label(player, "delete")).region("actions")
                .primary((actor, _) -> confirmDelete(actor, prison.name()));
        menu.navigation("back", language.text(player, Message.BACK, NamedTextColor.WHITE)).region("navigation")
                .primary((actor, _) -> open(actor));
        menu.close(language.text(player, Message.CLOSE, NamedTextColor.RED)).region("navigation");
        FloatingMenus.openRoot(player, menu.build());
    }

    private void inputCell(Player player, String prison) {
        FloatingMenus.input(player, MenuInputForm.builder(label(player, "add-cell"))
                .field(MenuInputForm.Field.text("name", label(player, Message.PRISON_MENU_PROMPT, "cell"), "", 32))
                .cancel(language.text(player, Message.CLOSE, NamedTextColor.WHITE))
                .submit(language.text(player, Message.MENU_INPUT_DONE, NamedTextColor.GREEN), (actor, values) ->
                        attempt(actor, () -> {
                            service.addCell(actor, prison, values.text("name"));
                            notify(actor, "cell-added", "");
                            openPrison(actor, prison);
                        })).build());
    }

    private void removeCell(Player player, PrisonRecord prison) {
        if (prison.cells().isEmpty()) {
            player.sendMessage(language.text(player, Message.MENU_SEARCH_EMPTY, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(player, MenuSearch.<String>builder(label(player, Message.PRISON_MENU_TITLE),
                        label(player, "remove-cell"))
                .find(query -> CompletableFuture.completedFuture(prison.cells().keySet().stream()
                        .filter(cell -> cell.contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(cell -> UiKey.item("cell:" + cell))
                .label(cell -> Component.text(cell, NamedTextColor.YELLOW))
                .selected((actor, cell) -> attempt(actor, () -> {
                    service.removeCell(actor, prison.name(), cell);
                    notify(actor, "cell-removed", "");
                    openPrison(actor, prison.name());
                })).build());
    }

    private void confirmDelete(Player player, String prison) {
        FloatingMenus.input(player, MenuInputForm.builder(label(player, "delete"))
                .field(MenuInputForm.Field.text("name", label(player, Message.PRISON_MENU_PROMPT, "name"), "", 32))
                .validate((actor, values) -> values.text("name").equalsIgnoreCase(prison)
                        ? null : error(actor, "Enter the exact prison name to confirm deletion."))
                .cancel(language.text(player, Message.CLOSE, NamedTextColor.WHITE))
                .submit(language.text(player, Message.MENU_INPUT_DONE, NamedTextColor.GREEN), (actor, values) ->
                        attempt(actor, () -> {
                            if (!values.text("name").equalsIgnoreCase(prison))
                                throw new IllegalArgumentException("Enter the exact prison name to confirm deletion.");
                            service.deletePrison(actor, values.text("name"));
                            notify(actor, "deleted", prison);
                            open(actor);
                        })).build());
    }

    private void chooseTarget(Player player) {
        if (!RolePermissions.canModerate(player)) return;
        FloatingMenus.search(player, MenuSearch.<MenuPlayerSearch.Candidate>builder(
                        label(player, "jail"), label(player, Message.PRISON_MENU_PROMPT, "player"))
                .find(query -> CompletableFuture.completedFuture(MenuPlayerSearch.matching(query,
                        MenuPlayerSearch.cached(player, query)).stream()
                        .filter(candidate -> !candidate.id().equals(player.getUniqueId())).toList()))
                .key(candidate -> UiKey.item(candidate.id()))
                .label(MenuPlayerSearch::label)
                .selected((actor, target) -> choosePrisonForJail(actor, target)).build());
    }

    private void choosePrisonForJail(Player moderator, MenuPlayerSearch.Candidate target) {
        List<PrisonRecord> candidates = service.prisons();
        if (candidates.isEmpty()) {
            moderator.sendMessage(language.text(moderator, Message.MENU_SEARCH_EMPTY, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(moderator, MenuSearch.<PrisonRecord>builder(label(moderator, "jail"),
                        Component.text(target.name(), NamedTextColor.AQUA))
                .find(query -> CompletableFuture.completedFuture(candidates.stream()
                        .filter(prison -> prison.name().contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(prison -> UiKey.item("prison:" + prison.name()))
                .label(prison -> Component.text(prison.name(), NamedTextColor.YELLOW))
                .selected((actor, prison) -> jail(actor, target, prison.name())).build());
    }

    private void jail(Player moderator, MenuPlayerSearch.Candidate target, String prison) {
        FloatingMenus.input(moderator, MenuInputForm.builder(label(moderator, "jail"))
                .field(MenuInputForm.Field.text("duration", label(moderator, Message.PRISON_MENU_PROMPT,
                        "duration"), "permanent", 32))
                .validate((actor, values) -> {
                    try {
                        PrisonDuration.parse(values.text("duration"));
                        return null;
                    } catch (IllegalArgumentException exception) {
                        return error(actor, exception.getMessage());
                    }
                })
                .cancel(language.text(moderator, Message.CLOSE, NamedTextColor.WHITE))
                .submit(language.text(moderator, Message.MENU_INPUT_DONE, NamedTextColor.GREEN), (actor, values) ->
                        attempt(actor, () -> {
                            long duration = PrisonDuration.parse(values.text("duration"));
                            service.jail(actor, target.id(), target.name(), prison, duration);
                            notify(actor, "jailed", target.name());
                            open(actor);
                        })).build());
    }

    private void choosePrisoner(Player moderator) {
        if (!RolePermissions.canModerate(moderator)) return;
        List<PrisonerRecord> candidates = service.prisoners();
        if (candidates.isEmpty()) {
            moderator.sendMessage(language.text(moderator, Message.MENU_SEARCH_EMPTY, NamedTextColor.YELLOW));
            return;
        }
        FloatingMenus.search(moderator, MenuSearch.<PrisonerRecord>builder(label(moderator, "inmates"),
                        label(moderator, Message.PRISON_MENU_TITLE))
                .find(query -> CompletableFuture.completedFuture(candidates.stream()
                        .filter(inmate -> inmate.playerName().toLowerCase(Locale.ROOT)
                                .contains(query.strip().toLowerCase(Locale.ROOT))).toList()))
                .key(inmate -> UiKey.item(inmate.playerId()))
                .label(inmate -> Component.text(inmate.playerName() + " · " + inmate.prison() + "/" + inmate.cell(),
                        NamedTextColor.YELLOW))
                .selected((actor, inmate) -> attempt(actor, () -> {
                    service.release(actor, inmate.playerId());
                    notify(actor, "released", inmate.playerName());
                    open(actor);
                })).build());
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

    private Component label(Player player, Message message, String argument) {
        return language.text(player, message, NamedTextColor.WHITE, argument);
    }
}
