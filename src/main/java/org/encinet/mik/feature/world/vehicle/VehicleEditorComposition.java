package org.encinet.mik.feature.world.vehicle;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;

/** Object/tool selection is above the inspector; value adjustments and task actions remain separate. */
final class VehicleEditorComposition {
    enum Primary { ITEMS, ACTIONS, SUMMARY }
    private VehicleEditorComposition() { }

    static UiNode create(VehicleEditorRoute route) {
        return create(VehicleEditorView.open(route), null, primary(route), _ -> { });
    }

    static Primary primary(VehicleEditorRoute route) {
        return route instanceof Model || route == Workspace.INSTANCE || route == Utilities.INSTANCE || route == Facing.INSTANCE
                ? Primary.ACTIONS : Primary.ITEMS;
    }

    static UiNode create(VehicleEditorView view, UiNode reading, Primary primary, Consumer<VehicleEditorView> read) {
        VehicleEditorRoute route = view.route();
        UiNode items = route instanceof Position || route instanceof Transform
                ? UiTemplates.actions("items", 2) : new UiPagedPort(UiTemplates.list("items").withSummaryLines(1),
                        3, view.itemPage(), next -> read.accept(view.items(next)))
                        .withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 240)));
        if (reading != null) items = reading;
        UiNode actions = new UiPagedPort(UiTemplates.actions("actions", 3).withSummaryLines(1), 1,
                view.actionPage(), next -> read.accept(view.actions(next)))
                .withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 180)));
        var content = UiTemplates.menu(UiTemplates.title("heading").withSummaryLines(1),
                UiTemplates.information("summary").withSummaryLines(primary == Primary.SUMMARY ? 2 : 1),
                UiTemplates.actions("object-tools", 4).withSummaryLines(1), items);
        UiNode tools = UiTemplates.menu(UiTemplates.pagination("pages"), UiTemplates.navigation("navigation"));
        if (primary == Primary.ACTIONS) content = UiTemplates.menu(content, actions);
        else tools = UiTemplates.menu(actions, tools);
        UiKey focus = switch (primary) {
            case ITEMS -> items.key();
            case ACTIONS -> actions.key();
            case SUMMARY -> UiKey.named("summary");
        };
        // The reading target is a direct child, including on action-only and unavailable-object pages.
        UiNode focused = UiTemplates.focus(content, focus);
        UiNode page = UiTemplates.sheet(UiTemplates.window("editor", focused), UiTemplates.toolbar("tools", tools));
        if (!(route instanceof Library || route instanceof Model)) page = UiTemplates.split(
                UiTemplates.sidebar("sections", UiTemplates.list("sections").withSummaryLines(1)), page);
        return Ui.column().key("route-scope").child(UiKey.item(identity(route)), page).build();
    }

    private static String identity(VehicleEditorRoute route) {
        return switch (route) {
            case Library _ -> "library";
            case Model model -> "model:" + model.id();
            case Workspace _ -> "workspace";
            case Utilities _ -> "utilities";
            case Facing _ -> "facing";
            case Center _ -> "center";
            case Kinds _ -> "kinds";
            case Parameters _ -> "parameters";
            case Settings settings -> "settings:" + settings.group();
            case Components list -> "components:" + list.section();
            case Inspector object -> "inspector:" + object.target();
            case Position object -> "position:" + object.target() + ":" + object.exit();
            case Transform object -> "transform:" + object.target() + ":" + object.mode();
            case Properties object -> "properties:" + object.target();
            case Choices object -> "choices:" + object.target() + ":" + object.property();
        };
    }

    static UiNode axes(List<UiElement> controls, VehicleEditorView view, Consumer<VehicleEditorView> read) {
        if (controls.size() != 6) throw new IllegalArgumentException("Three axes require a negative and positive control each");
        var rows = new ArrayList<UiNode>();
        for (int axis = 0; axis < 3; axis++) rows.add(Ui.grid(2).key("axis:" + axis)
                .child(controls.get(axis * 2)).child(controls.get(axis * 2 + 1)).build());
        return Ui.pagedList().key("items").items(rows).page(view.itemPage(), next -> read.accept(view.items(next)))
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(560, 240))).build();
    }

    static void tools(MenuScene.Builder menu, ObjectView current, Function<Message, Component> label,
                      BiConsumer<Player, ObjectView> select) {
        for (ObjectView tool : current.tools()) {
            String key = switch (tool) {
                case Inspector _ -> "inspector";
                case Position position -> position.exit() ? "exit" : "position";
                case Transform transform -> transform.mode().name();
                case Properties _, Choices _ -> "properties";
            };
            Message message = switch (tool) {
                case Inspector _ -> Message.VEHICLE_MODEL_COMPONENT_DETAILS;
                case Position position -> position.exit() ? Message.VEHICLE_MODEL_EXIT : Message.VEHICLE_MODEL_POSITION;
                case Transform transform -> switch (transform.mode()) {
                    case ROTATE -> Message.VEHICLE_MODEL_ROTATE;
                    case SCALE -> Message.VEHICLE_MODEL_SCALE;
                    case SIZE -> Message.VEHICLE_MODEL_SIZE;
                };
                case Properties _, Choices _ -> Message.VEHICLE_MODEL_OPTIONS;
            };
            menu.control("object-tool:" + key, label.apply(message)).region("object-tools")
                    .selected(tool.equals(current)).primary((player, _) -> select.accept(player, tool));
        }
    }
}
