package org.encinet.mik.feature.world.vehicle;

import java.util.ArrayList;
import java.util.List;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.i18n.Message;

/** A local-space editor scene. Its published handles are also the source of pointer picking. */
record VehicleEditorScene(List<Handle> handles, List<Line> lines) {
    enum Ink { RED, GREEN, BLUE, ORANGE, YELLOW, PURPLE, WHITE }
    sealed interface Action { }
    record Select(VehicleEditorRoute route) implements Action { }
    record Adjust(int axis, int direction) implements Action { }
    record Face(int turns) implements Action { }
    record Navigate(VehicleEditorRoute route) implements Action { }
    enum Tool implements Action { MENU, SAVE, UNDO, REDO, ZOOM_IN, ZOOM_OUT, FRAME }
    record Handle(String key, VehicleVector point, Message label, List<Object> arguments, Ink ink, Action action) {
        Handle { arguments = List.copyOf(arguments); }
    }
    record Line(VehicleVector from, VehicleVector to, Ink ink) { }
    VehicleEditorScene { handles = List.copyOf(handles); lines = List.copyOf(lines); }

    static VehicleEditorScene create(VehicleModelDraft draft, VehicleEditorRoute route, boolean editing, double step) {
        var handles = new ArrayList<Handle>();
        var lines = new ArrayList<Line>();
        // +Z is the driving direction; extend beyond the body so the arrow is visible from outside.
        VehicleVector half = new VehicleVector(1, 1, 1);
        VehicleVector center = VehicleVector.ZERO;
        if (!draft.parts().isEmpty()) {
            try { var bounds = VehicleModel.bounds(draft.parts()); half = bounds.halfSize(); center = bounds.center(); }
            catch (IllegalArgumentException ignored) { /* Incomplete text-only drafts still have a coordinate frame. */ }
        }
        double frontZ = Math.max(1.5, center.coordinateZ() + half.coordinateZ() + 0.6);
        var front = new VehicleVector(center.coordinateX(), center.coordinateY(), frontZ);
        lines.add(new Line(front.add(new VehicleVector(0, 0, -0.8)), front, Ink.BLUE));
        lines.add(new Line(front.add(new VehicleVector(-0.25, 0, -0.3)), front, Ink.BLUE));
        lines.add(new Line(front.add(new VehicleVector(0.25, 0, -0.3)), front, Ink.BLUE));
        handles.add(new Handle("front", front, Message.VEHICLE_MODEL_FRONT, List.of(), Ink.BLUE, new Select(Facing.INSTANCE)));
        for (int axis = 0; axis < 3; axis++) lines.add(new Line(VehicleVector.ZERO, axis(axis, 0.7), ink(axis)));

        if (route == Facing.INSTANCE) {
            for (int turns : new int[]{1, 2, 3}) {
                VehicleVector point = switch (turns) {
                    case 1 -> center.add(new VehicleVector(-half.coordinateX() - 0.7, 0, 0));
                    case 2 -> center.add(new VehicleVector(0, 0, -half.coordinateZ() - 0.7));
                    default -> center.add(new VehicleVector(half.coordinateX() + 0.7, 0, 0));
                };
                handles.add(new Handle("front-choice:" + turns, point, Message.VEHICLE_MODEL_FRONT_CHOICE,
                        List.of(turns == 1 ? "negative-x" : turns == 2 ? "back" : "positive-x"), Ink.YELLOW, new Face(turns)));
                lines.add(new Line(center, point, Ink.YELLOW));
            }
        } else {
            Section section = route instanceof Components list ? list.section() : route instanceof ObjectView object ? object.target().section() : null;
            if (section == null || section == Section.PARTS) for (int index = 0; index < draft.parts().size(); index++) {
                if (draft.parts().get(index).kind() != VehicleDefinition.PartKind.TEXT
                        || route instanceof ObjectView object && object.index() == index) continue;
                handles.add(marker("text:" + index, draft.position("parts", index, false), "parts", index, Ink.WHITE,
                        new Select(new Position(new ComponentRef(Section.PARTS, index), false))));
            }
            for (Section type : Section.values()) {
                if (type == Section.PARTS || section != null && type != section) continue;
                for (int index = 0; index < draft.count(type.key()); index++) {
                    // The overview identifies every seat and physics point; part meshes are picked directly.
                    var target = new ComponentRef(type, index);
                    boolean driver = type == Section.SEATS && Boolean.parseBoolean(draft.field("seats." + index + ".driver"));
                    String kind = type == Section.SEATS ? driver ? "driver" : "passenger" : type.key();
                    Ink color = type == Section.SEATS ? driver ? Ink.GREEN : Ink.BLUE : type == Section.SUPPORTS ? Ink.YELLOW : Ink.WHITE;
                    VehicleVector point = draft.position(type.key(), index, false);
                    handles.add(marker(type + ":" + index, point, kind, index, color, new Select(new Position(target, false))));
                    if (type == Section.SEATS) {
                        VehicleVector exit = draft.position("seats", index, true);
                        handles.add(marker("exit:" + index, exit, "exit", index, Ink.ORANGE, new Select(new Position(target, true))));
                        lines.add(new Line(point, exit, Ink.ORANGE));
                    } else if (type == Section.COLLIDERS) {
                        if (!(route instanceof ObjectView object) || object.index() == index) outline(lines, draft.collider(index), Ink.WHITE);
                    }
                    else lines.add(new Line(point, point.add(new VehicleVector(0, -Double.parseDouble(draft.field("support-length")), 0)), Ink.YELLOW));
                }
            }
            VehicleVector mass = draft.vectorField("center-of-mass");
            handles.add(marker("center", mass, "center", 0, Ink.PURPLE, new Select(Center.INSTANCE)));
            VehicleVector selected = route == Center.INSTANCE ? mass : route instanceof ObjectView object && object.index() < draft.count(object.section())
                    ? draft.position(object.section(), object.index(), object.exit()) : null;
            if (selected != null) {
                if (route instanceof ObjectView object && object.target().section() == Section.PARTS)
                    handles.add(marker("selected", selected, "parts", object.index(), Ink.WHITE, new Select(route)));
                for (int axis = 0; axis < 3; axis++) for (int direction : new int[]{-1, 1}) {
                    VehicleVector arm = axis(axis, 0.9);
                    if (route instanceof Transform transform) {
                        if (transform.mode() == VehicleEditorTransform.Mode.SIZE) {
                            VehicleVector halfSize = draft.collider(transform.index()).halfSize();
                            arm = axis(axis, 0.4 + (axis == 0 ? halfSize.coordinateX() : axis == 1 ? halfSize.coordinateY() : halfSize.coordinateZ()));
                        } else {
                            VehicleVector basis = axis(axis, 1);
                            var part = draft.parts().get(transform.index());
                            var vector = part.transform().transformDirection((float) basis.coordinateX(), (float) basis.coordinateY(), (float) basis.coordinateZ(), new org.joml.Vector3f());
                            var transformed = new VehicleVector(vector.x, vector.y, vector.z);
                            if (transformed.length() < 1.0E-8) continue;
                            arm = transformed.normalized().multiply(Math.max(0.9, transformed.length() * (part.kind() == VehicleDefinition.PartKind.BLOCK ? 1 : 0.5) + 0.4));
                        }
                    }
                    VehicleVector endpoint = selected.add(arm.multiply(direction));
                    lines.addFirst(new Line(selected, endpoint, ink(axis)));
                    if (editing) {
                        String operation = route instanceof Transform transform ? transform.mode().name() : "POSITION";
                        handles.add(new Handle("adjust:" + axis + ":" + direction, endpoint, Message.VEHICLE_MODEL_HANDLE,
                                List.of(operation, "XYZ".substring(axis, axis + 1), operation.equals("SCALE") ? direction < 0 ? "÷" : "×" : direction < 0 ? "−" : "+", step), ink(axis), new Adjust(axis, direction)));
                    }
                }
            }
        }
        return new VehicleEditorScene(handles, lines);
    }

    /** Fixed world-size controls around the miniature; model-space edits retain real units. */
    static VehicleEditorScene stage(VehicleModelDraft draft, VehicleEditorRoute route, double step, double scale) {
        var base = create(draft, route == Workspace.INSTANCE ? new Components(Section.PARTS, 0) : route, true, step);
        var handles = new ArrayList<>(base.handles());
        var lines = new ArrayList<>(base.lines());
        VehicleVector selected = route == Center.INSTANCE ? draft.vectorField("center-of-mass")
                : route instanceof ObjectView object && object.index() < draft.count(object.section())
                ? draft.position(object.section(), object.index(), object.exit()) : null;
        if (selected != null) for (int i = 0; i < handles.size(); i++) {
            var handle = handles.get(i);
            if (!(handle.action() instanceof Adjust)) continue;
            var arm = handle.point().subtract(selected);
            var endpoint = selected.add(arm.normalized().multiply(Math.max(arm.length(), 0.65 / scale)));
            handles.set(i, new Handle(handle.key(), endpoint, handle.label(), handle.arguments(), handle.ink(), handle.action()));
            lines.add(new Line(selected, endpoint, handle.ink()));
        }
        VehicleVector center;
        try { center = VehicleModel.bounds(draft.parts()).center(); }
        catch (IllegalArgumentException ignored) { center = VehicleVector.ZERO; }
        int row = 0;
        for (Section section : Section.values()) handles.add(new Handle("section:" + section,
                center.add(panelOffset(draft, new VehicleVector(-1.9 / scale, (0.7 - row++ * 0.4) / scale, 0))),
                switch (section) { case PARTS -> Message.VEHICLE_MODEL_PARTS; case SEATS -> Message.VEHICLE_MODEL_SEATS;
                    case COLLIDERS -> Message.VEHICLE_MODEL_COLLIDERS; case SUPPORTS -> Message.VEHICLE_MODEL_SUPPORTS; },
                List.of(), Ink.WHITE, new Navigate(new Components(section, 0))));
        if (route instanceof ObjectView object) {
            int column = 0;
            for (var tool : object.tools()) {
                Message label = switch (tool) {
                    case Inspector _ -> Message.VEHICLE_MODEL_COMPONENT_DETAILS;
                    case Position position -> position.exit() ? Message.VEHICLE_MODEL_EXIT : Message.VEHICLE_MODEL_POSITION;
                    case Transform transform -> switch (transform.mode()) { case ROTATE -> Message.VEHICLE_MODEL_ROTATE;
                        case SCALE -> Message.VEHICLE_MODEL_SCALE; case SIZE -> Message.VEHICLE_MODEL_SIZE; };
                    case Properties _, Choices _ -> Message.VEHICLE_MODEL_OPTIONS;
                };
                handles.add(new Handle("tool:" + column, center.add(panelOffset(draft, new VehicleVector((-1.5 + column++ * 0.8) / scale, 1.15 / scale, 0))),
                        label, List.of(), Ink.YELLOW, new Navigate(tool)));
            }
        }
        row = 0;
        for (Tool tool : Tool.values()) {
            if (tool == Tool.UNDO && !draft.canUndo() || tool == Tool.REDO && !draft.canRedo()) continue;
            Message label = switch (tool) { case MENU -> Message.VEHICLE_MODEL_PANEL; case SAVE -> Message.VEHICLE_MODEL_SAVE_CONTINUE;
                case UNDO -> Message.VEHICLE_MODEL_UNDO; case REDO -> Message.VEHICLE_MODEL_REDO;
                default -> Message.VEHICLE_MENU_LABEL; };
            handles.add(new Handle("stage:" + tool, center.add(panelOffset(draft, new VehicleVector(2.0 / scale, (1.1 - row++ * 0.4) / scale, 0))), label,
                    label == Message.VEHICLE_MENU_LABEL ? List.of(switch (tool) { case FRAME -> "frame"; case ZOOM_IN -> "zoom-in"; default -> "zoom-out"; }) : List.of(), Ink.WHITE, tool));
        }
        return new VehicleEditorScene(handles, lines);
    }

    private static VehicleVector panelOffset(VehicleModelDraft draft, VehicleVector offset) {
        return offset.rotate(VehicleModel.orientation(-90 * draft.frameTurns()));
    }

    private static Handle marker(String key, VehicleVector point, String kind, int index, Ink ink, Action action) {
        return new Handle(key, point, Message.VEHICLE_MODEL_MARKER, List.of(kind, index), ink, action);
    }

    private static VehicleVector axis(int axis, double value) {
        return new VehicleVector(axis == 0 ? value : 0, axis == 1 ? value : 0, axis == 2 ? value : 0);
    }
    private static Ink ink(int axis) { return axis == 0 ? Ink.RED : axis == 1 ? Ink.GREEN : Ink.BLUE; }
    private static void outline(List<Line> lines, VehicleDefinition.Collider collider, Ink ink) {
        VehicleVector half = collider.halfSize();
        for (int corner = 0; corner < 8; corner++) {
            VehicleVector point = collider.center().add(new VehicleVector((corner & 1) == 0 ? -half.coordinateX() : half.coordinateX(),
                    (corner & 2) == 0 ? -half.coordinateY() : half.coordinateY(), (corner & 4) == 0 ? -half.coordinateZ() : half.coordinateZ()));
            for (int axis = 0; axis < 3; axis++) if ((corner & (1 << axis)) == 0) {
                double length = axis == 0 ? half.coordinateX() * 2 : axis == 1 ? half.coordinateY() * 2 : half.coordinateZ() * 2;
                lines.add(new Line(point, point.add(axis(axis, length)), ink));
            }
        }
    }

    /** Text labels sit a little above their physical point. Both the label and point can be targeted. */
    static VehicleVector labelPoint(Handle handle) { return labelPoint(handle, 1); }
    static VehicleVector labelPoint(Handle handle, double scale) { return handle.point().add(new VehicleVector(0, 0.18 / scale, 0)); }

    /** Object selection respects terrain; explicit adjustment widgets remain usable inside the body or floor. */
    static Handle pick(List<Handle> published, VehicleVector eye, VehicleVector direction, double maximum) {
        return pick(published, eye, direction, maximum, 1);
    }
    static Handle pick(List<Handle> published, VehicleVector eye, VehicleVector direction, double maximum, double scale) {
        Handle selected = null;
        double nearest = 32 / scale;
        VehicleVector ray = direction.normalized();
        if (ray.length() == 0) return null;
        for (Handle handle : published) for (VehicleVector center : List.of(handle.point(), labelPoint(handle, scale))) {
            VehicleVector delta = eye.subtract(center);
            double projection = delta.dot(ray);
            double discriminant = projection * projection - delta.dot(delta) + Math.pow(0.16 / scale, 2);
            if (discriminant < 0) continue;
            double distance = -projection - Math.sqrt(discriminant);
            if (distance < 0) distance = -projection + Math.sqrt(discriminant);
            double reach = handle.action() instanceof Adjust || handle.action() instanceof Tool || handle.action() instanceof Navigate ? 32 / scale : Math.min(32 / scale, maximum);
            if (distance >= 0 && distance <= reach && distance <= nearest) { selected = handle; nearest = distance; }
        }
        return selected;
    }
}
