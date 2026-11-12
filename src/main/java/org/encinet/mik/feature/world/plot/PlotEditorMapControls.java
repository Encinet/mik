package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiNode;

/** Map gestures and coordinate forms share draft guards; saving remains owned by the plot module. */
final class PlotEditorMapControls {
    private final PlotSelectionState selections;
    private final PlotEditorMap editorMap;
    private final LanguageService language;
    private final BiConsumer<Player, UUID> bind;
    private final BiFunction<Player, UUID, MenuScene> render;
    private final BiConsumer<Player, PlotProblem> problem;
    private final BiConsumer<Player, UUID> openMap;

    PlotEditorMapControls(PlotSelectionState selections, PlotEditorMap editorMap, LanguageService language,
                          BiConsumer<Player, UUID> bind, BiFunction<Player, UUID, MenuScene> render,
                          BiConsumer<Player, PlotProblem> problem, BiConsumer<Player, UUID> openMap) {
        this.selections = selections;
        this.editorMap = editorMap;
        this.language = language;
        this.bind = bind;
        this.render = render;
        this.problem = problem;
        this.openMap = openMap;
    }

    UiNode map(Player player, UUID plotId, PlotEditorContext context, UUID world,
               PlotSelectionState.Draft draft, PlotEditorMap.Scene scene) {
        return PlotEditorMapPanel.create(scene,
                (message, args) -> Component.text(t(player, message, args), NamedTextColor.WHITE),
                (viewer, handle) -> perform(viewer, () -> {
                    requireEditorSnapshot(viewer, context, world, draft.revision());
                    handle.close();
                    openMap.accept(viewer, plotId);
                }));
    }

    private void requireEditorSnapshot(Player player, PlotEditorContext context, UUID world, long revision) {
        if (!world.equals(player.getWorld().getUID()) || !context.equals(selections.context(player.getUniqueId())))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
        bind.accept(player, context.plotId());
        selections.requireRevision(player.getUniqueId(), world, revision);
    }

    void add(Player player, MenuScene.Builder menu, UUID plotId, PlotEditorContext context,
                             UUID world, PlotSelectionState.Draft draft, PlotEditorMap.Scene scene) {
        for (boolean first : List.of(true, false)) {
            PlotPosition point = first ? scene.points().first() : scene.points().second();
            Component label = Component.text(t(player, first ? Message.PLOT_MAP_CORNER_A : Message.PLOT_MAP_CORNER_B),
                    first ? NamedTextColor.GREEN : NamedTextColor.GOLD).append(Component.newline())
                    .append(Component.text(point == null ? "—" : point.x() + ", " + point.y() + ", " + point.z(), NamedTextColor.WHITE));
            var corner = menu.control(first ? "corner-a" : "corner-b", label).region("corners").selected(scene.first() == first)
                    .tooltip(Component.text(t(player, Message.PLOT_MAP_CORNER_HINT), NamedTextColor.WHITE))
                    .primary((viewer, handle) -> perform(viewer, () -> {
                        requireEditorSnapshot(viewer, context, world, draft.revision());
                        editorMap.corner(viewer, plotId, first);
                        if (point != null) editorMap.focus(viewer, plotId, point);
                        handle.update(render.apply(viewer, plotId));
                    }))
                    .secondary((viewer, _) -> perform(viewer, () -> {
                        requireEditorSnapshot(viewer, context, world, draft.revision());
                        Location at = viewer.getLocation();
                        String initial = point == null ? at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                                : point.x() + "," + point.y() + "," + point.z();
                        FloatingMenus.input(viewer, MenuInputForm.builder(Component.text(t(viewer,
                                        first ? Message.PLOT_MAP_CORNER_A : Message.PLOT_MAP_CORNER_B), NamedTextColor.GOLD))
                                .field(MenuInputForm.Field.text("coordinates", Component.text(t(viewer, Message.PLOT_MAP_COORDINATES)), initial, 64))
                                .validate((responder, values) -> {
                                    try {
                                        requireEditorSnapshot(responder, context, world, draft.revision());
                                        cornerCoordinates(responder, world, values.text("coordinates"));
                                        return null;
                                    } catch (PlotProblem failure) { return problemLabel(responder, failure); }
                                })
                                .cancel(Component.text(t(viewer, Message.BACK)))
                                .submit(Component.text(t(viewer, Message.PLOT_MENU_SUBMIT)), (responder, values) -> perform(responder, () -> {
                                    requireEditorSnapshot(responder, context, world, draft.revision());
                                    PlotPosition parsed = cornerCoordinates(responder, world, values.text("coordinates"));
                                    selections.mark(responder.getUniqueId(), first, parsed);
                                    editorMap.focus(responder, plotId, parsed);
                                    FloatingMenus.present(responder, render.apply(responder, plotId));
                                })).build());
                    }))
                    .scrollUp((viewer, handle) -> shiftCorner(viewer, handle, context, world, draft, first, point, 4))
                    .scrollDown((viewer, handle) -> shiftCorner(viewer, handle, context, world, draft, first, point, -4));
            if (scene.regions().open() && !scene.regions().editing())
                corner.disabled(Component.text(t(player, Message.PLOT_REGION_SELECT_HINT)));
        }
        mapViewAction(player, menu, "zoom-in", Message.PLOT_MAP_ZOOM_IN, "view", (viewer, _) -> editorMap.zoom(viewer, plotId, 1));
        mapViewAction(player, menu, "zoom-out", Message.PLOT_MAP_ZOOM_OUT, "view", (viewer, _) -> editorMap.zoom(viewer, plotId, -1));
        mapViewAction(player, menu, "fit-map", Message.PLOT_MAP_FIT, "view", (viewer, _) -> editorMap.fit(viewer, plotId));
        mapViewAction(player, menu, "near-map", Message.PLOT_MAP_NEAR, "view", (viewer, _) -> editorMap.near(viewer, plotId));
        menu.control("map-layer", Component.text(t(player, Message.PLOT_MAP_LAYER, scene.layer(), scene.layer() + 3), NamedTextColor.AQUA))
                .region("view").tooltip(Component.text(t(player, Message.PLOT_MAP_LAYER_HINT), NamedTextColor.WHITE))
                .primary((viewer, handle) -> {
                    editorMap.layer(viewer, plotId, viewer.getLocation().getBlockY());
                    handle.update(render.apply(viewer, plotId));
                })
                .scrollUp((viewer, handle) -> {
                    editorMap.layer(viewer, plotId, (long) scene.layer() + 4);
                    handle.update(render.apply(viewer, plotId));
                })
                .scrollDown((viewer, handle) -> {
                    editorMap.layer(viewer, plotId, (long) scene.layer() - 4);
                    handle.update(render.apply(viewer, plotId));
                });
        for (var direction : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
            Message label = direction[1] < 0 ? Message.PLOT_MAP_NORTH : direction[0] < 0 ? Message.PLOT_MAP_WEST
                    : direction[0] > 0 ? Message.PLOT_MAP_EAST : Message.PLOT_MAP_SOUTH;
            mapViewAction(player, menu, "pan:" + label.key(), label, "pan",
                    (viewer, _) -> editorMap.pan(viewer, plotId, direction[0], direction[1]));
        }
    }

    private static PlotPosition cornerCoordinates(Player player, UUID world, String value) {
        PlotPosition point = PlotSelectionCommands.parseCoordinates(value.strip(), world);
        if (point.y() < player.getWorld().getMinHeight() || point.y() >= player.getWorld().getMaxHeight())
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        return point;
    }

    private void shiftCorner(Player player, org.encinet.mik.shared.menu.FloatingMenuHandle handle,
                             PlotEditorContext context, UUID world, PlotSelectionState.Draft draft,
                             boolean first, PlotPosition point, int delta) {
        perform(player, () -> {
            requireEditorSnapshot(player, context, world, draft.revision());
            if (point == null) throw new PlotProblem(Message.PLOT_SELECTION_INCOMPLETE);
            int y = Math.clamp((long) point.y() + delta,
                    player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1);
            selections.mark(player.getUniqueId(), first, new PlotPosition(world, point.x(), y, point.z()));
            editorMap.layer(player, context.plotId(), y);
            handle.update(render.apply(player, context.plotId()));
        });
    }

    private void mapViewAction(Player player, MenuScene.Builder menu, String id, Message label,
                               String region, org.encinet.mik.shared.menu.FloatingMenuCommand action) {
        menu.control(id, Component.text(t(player, label), NamedTextColor.WHITE)).region(region)
                .primary((viewer, handle) -> perform(viewer, () -> {
                    action.execute(viewer, handle);
                    UUID plotId = selections.currentPlot(viewer.getUniqueId());
                    handle.update(render.apply(viewer, plotId));
                }));
    }

    private String t(Player player, Message message, Object... args) { return language.t(player, message, args); }
    private Component problemLabel(Player player, PlotProblem failure) {
        return Component.text("✕ " + t(player, failure.message(), failure.args()), NamedTextColor.RED);
    }
    private void perform(Player player, Runnable action) {
        try { action.run(); }
        catch (PlotProblem failure) { problem.accept(player, failure); }
    }
}
