package org.encinet.mik.feature.world.vehicle;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Source positions, component references and transform tools have separate, validated types. */
sealed interface VehicleEditorRoute {
    enum Section {
        PARTS, SEATS, COLLIDERS, SUPPORTS;
        String key() { return name().toLowerCase(Locale.ROOT); }
        static Section named(String key) { return valueOf(key.toUpperCase(Locale.ROOT)); }
    }
    record ComponentRef(Section section, int index) {
        public ComponentRef {
            Objects.requireNonNull(section);
            if (index < 0) throw new IllegalArgumentException("Negative component index");
        }
    }
    record Library(int anchor) implements VehicleEditorRoute {
        public Library { if (anchor < 0) throw new IllegalArgumentException("Negative library position"); }
    }
    record Model(String id, int libraryAnchor) implements VehicleEditorRoute {
        public Model {
            if (Objects.requireNonNull(id).isBlank()) throw new IllegalArgumentException("Blank model identity");
            if (libraryAnchor < 0) throw new IllegalArgumentException("Negative library position");
        }
    }
    enum Workspace implements VehicleEditorRoute { INSTANCE }
    enum Utilities implements VehicleEditorRoute { INSTANCE }
    enum Facing implements VehicleEditorRoute { INSTANCE }
    enum Center implements VehicleEditorRoute { INSTANCE }
    enum Kinds implements VehicleEditorRoute { INSTANCE }
    record Parameters(int anchor) implements VehicleEditorRoute {
        public Parameters { if (anchor < 0) throw new IllegalArgumentException("Negative parameter position"); }
    }
    record Settings(VehicleEditorParameters.Group group, int anchor) implements VehicleEditorRoute {
        public Settings {
            Objects.requireNonNull(group);
            if (anchor < 0) throw new IllegalArgumentException("Negative parameter position");
        }
    }
    record Components(Section section, int anchor) implements VehicleEditorRoute {
        public Components {
            Objects.requireNonNull(section);
            if (anchor < 0) throw new IllegalArgumentException("Negative component position");
        }
    }
    sealed interface ObjectView extends VehicleEditorRoute {
        ComponentRef target();
        default String section() { return target().section().key(); }
        default int index() { return target().index(); }
        default boolean exit() { return false; }
        default List<ObjectView> tools() {
            var tools = new ArrayList<ObjectView>();
            tools.add(new Inspector(target()));
            tools.add(new Position(target(), false));
            switch (target().section()) {
                case PARTS -> {
                    tools.add(new Transform(target(), VehicleEditorTransform.Mode.ROTATE));
                    tools.add(new Transform(target(), VehicleEditorTransform.Mode.SCALE));
                }
                case SEATS -> tools.add(new Position(target(), true));
                case COLLIDERS -> tools.add(new Transform(target(), VehicleEditorTransform.Mode.SIZE));
                case SUPPORTS -> { }
            }
            return List.copyOf(tools);
        }
    }
    record Inspector(ComponentRef target) implements ObjectView {
        public Inspector { Objects.requireNonNull(target); }
    }
    record Position(ComponentRef target, boolean exit) implements ObjectView {
        public Position {
            Objects.requireNonNull(target);
            if (exit && target.section() != Section.SEATS) throw new IllegalArgumentException("Only seats have an exit position");
        }
    }
    record Transform(ComponentRef target, VehicleEditorTransform.Mode mode) implements ObjectView {
        public Transform {
            Objects.requireNonNull(target);
            Objects.requireNonNull(mode);
            Section supported = mode == VehicleEditorTransform.Mode.SIZE ? Section.COLLIDERS : Section.PARTS;
            if (target.section() != supported) throw new IllegalArgumentException("Transform does not apply to this component");
        }
    }
    record Properties(ComponentRef target) implements ObjectView {
        public Properties {
            Objects.requireNonNull(target);
            if (target.section() != Section.PARTS) throw new IllegalArgumentException("Only displays have appearance properties");
        }
    }
    record Choices(ComponentRef target, VehicleEditorProperty property) implements ObjectView {
        public Choices {
            Objects.requireNonNull(target); Objects.requireNonNull(property);
            if (target.section() != Section.PARTS || property.choices().isEmpty()) throw new IllegalArgumentException("Unsupported choice editor");
        }
    }

    default VehicleEditorRoute back() {
        return switch (this) {
            case Choices choices -> choices.property() == VehicleEditorProperty.ANIMATION ? new Inspector(choices.target()) : new Properties(choices.target());
            case ObjectView view -> view instanceof Inspector
                    ? new Components(view.target().section(), view.index()) : new Inspector(view.target());
            case Model model -> new Library(model.libraryAnchor());
            case Settings _ -> new Parameters(0);
            case Kinds _, Center _ -> new Settings(VehicleEditorParameters.Group.BODY, 0);
            case Library _, Workspace _ -> new Library(0);
            case Components _, Parameters _, Utilities _, Facing _ -> Workspace.INSTANCE;
        };
    }
}
