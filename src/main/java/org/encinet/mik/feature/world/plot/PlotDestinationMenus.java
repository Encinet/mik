package org.encinet.mik.feature.world.plot;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.RolePermissions;

/** Both plot management and the launcher's public popover use the same destination actions. */
final class PlotDestinationMenus {
    private static final String DETAIL_SCREEN = "plot-destination";
    private static final String TITLE_SCREEN = "plot-destination-titles";
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRegistry registry;
    private final PlotDestinationController controller;
    private record Reading(Plot plot,PlotDestination point,UiReadingPosition position,boolean manage) { }
    private record Titles(Plot plot,PlotDestination expected,PlotDestination draft,String viewed) { }
    private final FloatingMenuScreen<Reading> details;
    private final FloatingMenuScreen<Titles> titles;


    PlotDestinationMenus(JavaPlugin plugin, LanguageService language, PlotRegistry registry, PlotDestinationController controller) {
        this.plugin = plugin; this.language = language; this.registry = registry; this.controller = controller;
        details = new FloatingMenuScreen<>(DETAIL_SCREEN,this::renderDetail);
        titles = new FloatingMenuScreen<>(TITLE_SCREEN,this::renderTitles);
    }

    private MenuScene renderDetail(FloatingMenuContext<Reading> context) {
        var state = context.state();
        return detail(context.player(),state.plot(),state.point(),state.manage(),state.position(),position -> {
            if (current(details,context.player(),state)) context.setState(new Reading(state.plot(),state.point(),position,state.manage()));
        });
    }

    private MenuScene renderTitles(FloatingMenuContext<Titles> context) {
        var state = context.state();
        return titleEditor(context.player(),state.plot(),state.expected(),state.draft(),state.viewed(),viewed -> {
            if (current(titles,context.player(),state)) context.setState(new Titles(state.plot(),state.expected(),state.draft(),viewed));
        },draft -> {
            if (current(titles,context.player(),state)) titles.open(context.player(),new Titles(state.plot(),state.expected(),draft,state.viewed()));
        });
    }

    private static <S> boolean current(FloatingMenuScreen<S> screen,Player player,S state) {
        return screen.flow(player).filter(flow -> flow.state() == state && flow.handle().state() != FloatingMenuState.CLOSED
                && flow.handle().state() != FloatingMenuState.CLOSING).isPresent();
    }

    void open(Player player, UUID plotId, int page) {
        Plot plot = registry.byId(plotId);
        if (plot == null) { problem(player, new PlotProblem(Message.PLOT_ERROR_ID)); return; }
        FloatingMenus.present(player, listing(player, plot, page));
    }

    MenuScene listing(Player player, Plot plot, int requestedPage) {
        boolean managed = managed(player, plot);
        var points = registry.destinations(plot.id()).stream().filter(point -> managed || controller.canUse(player, point)).toList();
        long revision = registry.revision();
        var labels = points.stream().map(point -> {
            Component label = Component.text(point.name(),NamedTextColor.GOLD).append(Component.newline())
                    .append(text(player,point.publicAccess() ? Message.PLOT_PUBLIC : Message.PLOT_PRIVATE,NamedTextColor.YELLOW));
            return point.description().isBlank() ? label : label.append(Component.newline()).append(Component.text(point.description(),NamedTextColor.WHITE));
        }).toList();
        Component manageLabel = text(player,Message.PLOT_DESTINATION_MANAGE,NamedTextColor.YELLOW);
        var template = destinationRow(new UUID(0,0),Component.text("M"),manageLabel,managed);
        var rows = Ui.windowedList(points.size(),template,index -> destinationRow(points.get(index).id(),labels.get(index),manageLabel,managed))
                .key("destination-rows").page(requestedPage,index -> {
                    if (registry.revision() == revision) open(player,plot.id(),index);
                });
        if (points.isEmpty()) rows.header(Ui.text(text(player,Message.PLOT_DESTINATION_EMPTY,NamedTextColor.WHITE)).key("empty").singleLine().build());
        var menu = PlotMenuLayouts.screen("plot-destinations:" + plot.id(), FloatingMenuAppearance.DEFAULT,
                        PlotMenuLayouts.destinationList(rows.build()))
                .refreshEvery(10, (viewer, handle) -> {
                    Plot fresh = registry.byId(plot.id());
                    if (fresh == null) handle.back();
                    else if (revision != registry.revision() || managed != managed(viewer, fresh))
                        handle.update(listing(viewer, fresh, requestedPage));
                });
        menu.information("heading", text(player, Message.PLOT_DESTINATIONS, NamedTextColor.GOLD)).region("heading");
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        if (managed) {
            var add = menu.control("add", text(player, Message.PLOT_DESTINATION_ADD, NamedTextColor.GREEN))
                    .region("actions").primary((viewer, _) -> input(viewer, plot.id(), null));
            if (!controller.canSetHere(player, plot)) add.disabled(text(player, Message.PLOT_DESTINATION_ERROR_LOCATION, NamedTextColor.WHITE));
        }
        menu.back(text(player, Message.BACK, NamedTextColor.GREEN)).region("navigation");
        return menu.build();
    }

    private void openDetail(Player player, UUID pointId) {
        var point = registry.destination(pointId);
        if (point == null) { problem(player, new PlotProblem(Message.PLOT_DESTINATION_ERROR_MISSING)); return; }
        Plot plot = registry.byId(point.plotId());
        if (!managed(player, plot)) { problem(player, new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY)); return; }
        details.open(player,new Reading(plot,point,UiReadingPosition.START,true));
    }

    private void openInformation(Player player,UUID pointId) {
        var point = registry.destination(pointId);
        if (point == null) { problem(player,new PlotProblem(Message.PLOT_DESTINATION_ERROR_MISSING)); return; }
        if (!controller.canUse(player,point)) { problem(player,new PlotProblem(Message.PLOT_DESTINATION_ERROR_PRIVATE)); return; }
        details.open(player,new Reading(registry.byId(point.plotId()),point,UiReadingPosition.START,false));
    }

    MenuScene information(Player player,Plot plot,PlotDestination point,UiReadingPosition position,java.util.function.Consumer<UiReadingPosition> read) {
        return detail(player,plot,point,false,position,read);
    }

    MenuScene detail(Player player, Plot plot, PlotDestination point) {
        return detail(player,plot,point,true,UiReadingPosition.START,_ -> {});
    }

    private MenuScene detail(Player player,Plot plot,PlotDestination point,boolean manage,UiReadingPosition position,
                             java.util.function.Consumer<UiReadingPosition> read) {
        var blocks = new java.util.ArrayList<UiNode>();
        if (!point.description().isBlank()) blocks.add(Ui.text(Component.text(point.description(),NamedTextColor.WHITE))
                .key("description").tooltip(Component.text(point.description(),NamedTextColor.WHITE))
                .textWidth(FloatingMenuTextWidth.CONTENT).build());
        blocks.add(Ui.text(Component.text(point.blockX() + ", " + point.blockY() + ", " + point.blockZ(),NamedTextColor.AQUA)).key("location").build());
        UiNode reading = Ui.document().key("identity").blocks(blocks).readingBody().position(position,read).build();
        var menu = PlotMenuLayouts.screen("plot-destination:" + point.id(), FloatingMenuAppearance.DEFAULT,
                        PlotMenuLayouts.member(reading))
                .screen(DETAIL_SCREEN)
                .refreshEvery(10, (viewer, handle) -> {
                    var fresh = registry.destination(point.id());
                    Plot current = registry.byId(plot.id());
                    if (fresh == null || (manage ? !managed(viewer,current) : !controller.canUse(viewer,fresh))) handle.back();
                    else if (!point.equals(fresh) || !plot.equals(current))
                        details.open(viewer,new Reading(current,fresh,position,manage));
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(point.name(), NamedTextColor.GOLD)).region("heading");
        menu.control("teleport", text(player, Message.PLOT_DESTINATION_TELEPORT, NamedTextColor.GREEN))
                .region("actions").primary((viewer, handle) -> teleport(viewer, handle, point.id()));
        if (!manage) {
            menu.back(text(player,Message.BACK,NamedTextColor.GREEN)).region("navigation");
            return menu.build();
        }
        menu.control("edit", text(player, Message.PLOT_DESTINATION_EDIT, NamedTextColor.YELLOW))
                .region("actions").primary((viewer, _) -> input(viewer, plot.id(), point));
        menu.control("titles", text(player, Message.PLOT_DESTINATION_TITLES, NamedTextColor.YELLOW))
                .region("actions").primary((viewer, _) -> showTitleEditor(viewer, plot, point, point));
        menu.control("public", text(player, point.publicAccess() ? Message.PLOT_DESTINATION_PUBLIC_ON : Message.PLOT_DESTINATION_PUBLIC_OFF, NamedTextColor.AQUA))
                .region("actions").selected(point.publicAccess()).primary((viewer, _) -> {
                    if (perform(viewer, () -> controller.publish(viewer, point))) openDetail(viewer, point.id());
                });
        var move = menu.control("move", text(player, Message.PLOT_DESTINATION_MOVE, NamedTextColor.YELLOW))
                .region("actions").primary((viewer, _) -> {
                    if (perform(viewer, () -> controller.relocate(viewer, point))) openDetail(viewer, point.id());
                });
        if (!controller.canSetHere(player, plot)) move.disabled(text(player, Message.PLOT_DESTINATION_ERROR_LOCATION, NamedTextColor.WHITE));
        menu.control("delete", text(player, Message.PLOT_DESTINATION_DELETE, NamedTextColor.RED))
                .region("actions").primary((viewer, _) -> MenuDialogs.openConfirm(plugin, viewer,
                        text(viewer, Message.PLOT_DESTINATION_DELETE, NamedTextColor.GOLD),
                        text(viewer, Message.PLOT_DESTINATION_CONFIRM_DELETE, NamedTextColor.WHITE, point.name()),
                        text(viewer, Message.PLOT_DESTINATION_DELETE, NamedTextColor.RED), text(viewer, Message.BACK, NamedTextColor.WHITE),
                        responder -> { if (perform(responder, () -> controller.delete(responder, point))) open(responder, plot.id(), 0); }));
        menu.back(text(player, Message.BACK, NamedTextColor.GREEN)).region("navigation");
        return menu.build();
    }

    private void showTitleEditor(Player player, Plot plot, PlotDestination expected, PlotDestination draft) {
        titles.open(player,new Titles(plot,expected,draft,"title"));
    }

    MenuScene titleEditor(Player player, Plot plot, PlotDestination expected, PlotDestination draft) {
        return titleEditor(player,plot,expected,draft,"title",_ -> {},_ -> {});
    }

    private MenuScene titleEditor(Player player,Plot plot,PlotDestination expected,PlotDestination draft,String viewed,
                                  java.util.function.Consumer<String> browse,java.util.function.Consumer<PlotDestination> changed) {
        Message unavailable = !managed(player,registry.byId(plot.id())) ? Message.PLOT_ERROR_OWNER_ONLY
                : !expected.equals(registry.destination(expected.id())) ? Message.PLOT_DESTINATION_ERROR_CHANGED : null;
        var fields = Ui.form().key("title-fields").viewedField(viewed,browse)
                .field("title",text(player,Message.PLOT_DESTINATION_TITLE,NamedTextColor.YELLOW),
                        titleField(player,expected,draft,true,unavailable,changed))
                .field("subtitle",text(player,Message.PLOT_DESTINATION_SUBTITLE,NamedTextColor.YELLOW),
                        titleField(player,expected,draft,false,unavailable,changed)).build();
        var menu = PlotMenuLayouts.screen("plot-destination-titles:" + expected.id(), FloatingMenuAppearance.DEFAULT,
                        PlotMenuLayouts.destinationTitle(fields))
                .screen(TITLE_SCREEN)
                .refreshWhenChanged(10, viewer -> List.of(registry.revision(), managed(viewer, registry.byId(plot.id()))),
                        (viewer, handle) -> handle.update(titleEditor(viewer, plot, expected, draft,viewed,browse,changed)));
        menu.information("context", Component.text(plot.name() + " · " + expected.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", text(player, Message.PLOT_DESTINATION_TITLES, NamedTextColor.GOLD)).region("heading");
        menu.information("hint", text(player, Message.PLOT_DESTINATION_TITLE_HINT, NamedTextColor.WHITE)).region("hint")
                .textWidth(FloatingMenuTextWidth.WIDE);
        var preview = menu.control("preview", text(player, Message.PLOT_DESTINATION_TITLE_PREVIEW, NamedTextColor.AQUA))
                .region("actions").primary((viewer, handle) -> {
                    if (perform(viewer, () -> controller.preview(viewer, expected, draft))) {
                        // Keep the centre clear while the client displays the actual title. Back restores this draft.
                        var screen = PlotMenuLayouts.screen("plot-destination-title-preview:" + expected.id(), FloatingMenuAppearance.DEFAULT,
                                UiTemplates.focus(Ui.column().gap(UiLength.ui(12))
                                        .child(Ui.column().key("clear-centre").size(new UiSize(480,240)).build())
                                        .child(UiTemplates.toolbar("tools", UiTemplates.navigation("navigation"))).build(),UiKey.named("tools")));
                        screen.back(text(viewer, Message.BACK, NamedTextColor.GREEN)).region("navigation");
                        handle.openChild(screen.build());
                    }
                });
        if (unavailable != null) preview.disabled(text(player, unavailable, NamedTextColor.WHITE));
        else if (draft.title().isEmpty() && draft.subtitle().isEmpty())
            preview.disabled(text(player, Message.PLOT_DESTINATION_TITLE_UNSET, NamedTextColor.WHITE));
        var save = menu.control("save", text(player, Message.PLOT_DESTINATION_TITLE_SAVE, NamedTextColor.GREEN))
                .region("actions").primary((viewer, handle) -> perform(viewer,
                        () -> controller.titles(viewer, expected, draft.title(), draft.subtitle(), () -> {
                            if (FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false))
                                openDetail(viewer, expected.id());
                        })));
        if (unavailable != null) save.disabled(text(player, unavailable, NamedTextColor.WHITE));
        else if (expected.title().equals(draft.title()) && expected.subtitle().equals(draft.subtitle()))
            save.disabled(text(player, Message.PLOT_DESTINATION_TITLE_UNCHANGED, NamedTextColor.WHITE));
        menu.back(text(player, Message.CANCEL, NamedTextColor.WHITE)).region("navigation");
        return menu.build();
    }

    private UiElement titleField(Player player,PlotDestination expected,PlotDestination draft,boolean title,Message unavailable,
                                  java.util.function.Consumer<PlotDestination> changed) {
        String value = title ? draft.title() : draft.subtitle();
        Message label = title ? Message.PLOT_DESTINATION_TITLE : Message.PLOT_DESTINATION_SUBTITLE;
        Component effect = value.isEmpty() ? text(player, Message.PLOT_DESTINATION_TITLE_UNSET, NamedTextColor.WHITE)
                : PlotDestinationTitles.render(value);
        var field = Ui.button(effect).key(title ? "title" : "subtitle").singleLine()
                .tooltip(text(player,label,NamedTextColor.YELLOW).append(Component.newline()).append(effect))
                .primary((viewer, _) -> {
                    FloatingMenus.input(viewer, MenuInputForm.builder(text(viewer, label, NamedTextColor.GOLD))
                            .field(MenuInputForm.Field.text("value", text(viewer, label, NamedTextColor.WHITE), value,
                                    PlotDestinationTitles.SOURCE_LIMIT * 2).preview(PlotDestinationTitles::render))
                            .validate((responder, values) -> {
                                try {
                                    draft.titles(title ? values.text("value") : draft.title(), title ? draft.subtitle() : values.text("value"));
                                    return null;
                                } catch (PlotProblem failure) { return text(responder, failure.message(), NamedTextColor.RED, failure.args()); }
                            })
                            .submit(text(viewer, Message.PLOT_DESTINATION_TITLE_APPLY, NamedTextColor.GREEN), (_, values) ->
                                    changed.accept(draft.titles(
                                            title ? values.text("value") : draft.title(), title ? draft.subtitle() : values.text("value"))))
                            .cancel(text(viewer, Message.CANCEL, NamedTextColor.WHITE)).build());
                });
        if (unavailable != null) field.disabled(text(player, unavailable, NamedTextColor.WHITE));
        return field.build();
    }

    private void input(Player player, UUID plotId, PlotDestination expected) {
        try { controller.managed(plotId, player.getUniqueId(), RolePermissions.canModerate(player)); }
        catch (PlotProblem failure) { problem(player, failure); return; }
        FloatingMenus.input(player, MenuInputForm.builder(text(player,
                        expected == null ? Message.PLOT_DESTINATION_ADD : Message.PLOT_DESTINATION_EDIT, NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("name", text(player, Message.PLOT_DESTINATION_NAME, NamedTextColor.WHITE),
                        expected == null ? "" : expected.name(), PlotDestination.NAME_LIMIT * 2))
                .field(MenuInputForm.Field.text("description", text(player, Message.PLOT_DESTINATION_DESCRIPTION, NamedTextColor.WHITE),
                        expected == null ? "" : expected.description(), PlotDestination.DESCRIPTION_LIMIT * 2).multiline(true))
                .validate((viewer, values) -> {
                    try {
                        Plot current = controller.managed(plotId, viewer.getUniqueId(), RolePermissions.canModerate(viewer));
                        PlotDestination.validateText(values.text("name").strip(),
                                values.text("description").replace("\r\n", "\n").replace('\r', '\n').strip());
                        if (expected != null && !expected.equals(registry.destination(expected.id())))
                            throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_CHANGED);
                        if (expected == null && !controller.canSetHere(viewer, current))
                            throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_LOCATION);
                        return null;
                    } catch (PlotProblem failure) { return text(viewer, failure.message(), NamedTextColor.RED, failure.args()); }
                })
                .cancel(text(player, Message.BACK, NamedTextColor.WHITE))
                .submit(text(player, Message.PLOT_MENU_SUBMIT, NamedTextColor.GREEN), (viewer, values) -> {
                    if (perform(viewer, () -> {
                        if (expected == null) controller.create(viewer, plotId, values.text("name"), values.text("description"));
                        else controller.edit(viewer, expected, values.text("name"), values.text("description"));
                    })) {
                        if (expected == null) open(viewer, plotId, 0); else openDetail(viewer, expected.id());
                    }
                }).build());
    }

    MenuPanel publicPanel(Player player, Runnable redraw) {
        return publicPanel(player,redraw,this::openInformation);
    }

    MenuPanel publicPanel(Player player,Runnable redraw,java.util.function.BiConsumer<Player,UUID> information) {
        return new MenuPanel() {
            private int page;
            private boolean closed;
            private long renderVersion;
            @Override public MenuScene render() {
                long version = ++renderVersion;
                long revision = registry.revision();
                var points = closed ? List.<PlotDestination>of() : registry.publicDestinations();
                Component readLabel = text(player,Message.PLOT_DESTINATION_DESCRIPTION,NamedTextColor.AQUA);
                var cards = points.stream().map(point -> {
                    Plot plot = registry.byId(point.plotId());
                    return new PlotCardList.Card(UiKey.named("point:" + point.id()),card(point,plot == null ? "" : plot.name()),
                            (viewer,handle) -> { if (!closed && renderVersion == version) teleport(viewer,handle,point.id()); },
                            java.util.Optional.of(Ui.button(UiIcons.INFO,readLabel).key("read:" + point.id())
                                    .tooltip(Component.text(point.name(),NamedTextColor.GOLD).append(Component.newline()).append(readLabel))
                                    .primary((viewer,_) -> { if (!closed && renderVersion == version) information.accept(viewer,point.id()); }).build()));
                }).toList();
                UiNode content = PlotCardList.content(cards,text(player,Message.PLOT_DESTINATION_PUBLIC_EMPTY,NamedTextColor.WHITE),page,
                        index -> { if (!closed && renderVersion == version && registry.revision() == revision) { page = index; redraw.run(); } });
                return MenuScene.screen("teleport-points", text(player, Message.PLOT_DESTINATIONS, NamedTextColor.GOLD))
                        .content(content).build();
            }
            @Override public void close() { closed = true; }
        };
    }

    private UiNode destinationRow(UUID point,Component label,Component manageLabel,boolean managed) {
        var travel = Ui.button(label).key("point:" + point).singleLine().tooltip(label)
                .primary((viewer,handle) -> teleport(viewer,handle,point));
        var manage = managed ? Ui.button(UiIcons.SETTINGS,manageLabel).key("manage:" + point)
                .primary((viewer,_) -> openDetail(viewer,point)) : null;
        var row = UiTemplates.actionRow(travel.build(),manage == null ? null : manage.build());
        return new UiContainer(UiKey.item(point),row.layout(),row.children(),row.style(),row.sizing());
    }

    void forget(UUID player) {
        var cleanup = new org.encinet.mik.shared.util.ShutdownSequence();
        cleanup.attempt("destination reader",() -> details.forget(player));
        cleanup.attempt("destination title draft",() -> titles.forget(player));
        cleanup.finish("plot destination menus");
    }

    void close() {
        var cleanup = new org.encinet.mik.shared.util.ShutdownSequence();
        cleanup.attempt("destination readers",details::forgetAll);
        cleanup.attempt("destination title drafts",titles::forgetAll);
        cleanup.finish("plot destination menus");
    }

    static Component card(PlotDestination point, String plotName) {
        Component label = Component.text(point.name(), NamedTextColor.GOLD);
        if (!plotName.isBlank()) label = label.append(Component.newline()).append(Component.text(plotName, NamedTextColor.AQUA));
        if (!point.description().isBlank()) label = label.append(Component.newline())
                .append(Component.text(point.description(), NamedTextColor.WHITE));
        return label;
    }

    private void teleport(Player player, FloatingMenuHandle handle, UUID id) {
        if (perform(player, () -> controller.teleport(player, id))) handle.close();
    }

    private boolean managed(Player player, Plot plot) {
        return plot != null && registry.permitted(plot, player.getUniqueId(), RolePermissions.canModerate(player), PlotPermission.MANAGE_SETTINGS);
    }

    private boolean perform(Player player, Operation operation) {
        try { operation.run(); return true; }
        catch (PlotProblem failure) { problem(player, failure); }
        catch (SQLException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not change plot destination", failure);
            player.sendMessage(text(player, Message.PLOT_ERROR_STORAGE, NamedTextColor.RED));
        }
        return false;
    }

    private void problem(Player player, PlotProblem failure) {
        player.sendMessage(text(player, failure.message(), NamedTextColor.RED, failure.args()));
    }
    private Component text(Player player, Message key, NamedTextColor color, Object... args) {
        return Component.text(language.t(player, key, args), color);
    }
    private interface Operation { void run() throws SQLException; }
}
