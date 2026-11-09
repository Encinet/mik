package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Labels share a column; controls remain ordinary subtrees. Editing and submission belong to the host. */
public final class UiForm extends Ui.Builder<UiForm> {
    record Field(UiKey key, Component label, UiNode control) { }
    private final List<Field> fields = new ArrayList<>();
    private final HashSet<UiKey> keys = new HashSet<>();
    private UiNode feedback, actions;
    private UiKey viewed;
    private UiLayouts.Axis navigationAxis = UiLayouts.Axis.HORIZONTAL;
    private java.util.function.Consumer<String> choose;

    UiForm() { }
    @Override protected UiForm self() { return this; }

    public UiForm field(String key, Component label, UiNode control) {
        UiKey id = UiKey.named(key);
        if (!keys.add(id)) throw new IllegalArgumentException("Duplicate form field: " + key);
        fields.add(new Field(id, Objects.requireNonNull(label), Objects.requireNonNull(control)));
        return this;
    }

    /** The surrounding title already labels this control. */
    public UiForm field(String key, UiNode control) { return field(key, Component.empty(), control); }
    public UiForm feedback(UiNode feedback) { this.feedback = Objects.requireNonNull(feedback); return this; }
    public UiForm actions(UiNode actions) { this.actions = Objects.requireNonNull(actions); return this; }

    /** Preferred placement of the field directory relative to its current control; editing remains independent. */
    public UiForm navigationAxis(UiLayouts.Axis axis) { navigationAxis = Objects.requireNonNull(axis); return this; }

    /** A feature-owned field viewport. Browsing requests a field key and never invokes its editing action. */
    public UiForm viewedField(String key, java.util.function.Consumer<String> choose) {
        viewed = UiKey.named(key); this.choose = Objects.requireNonNull(choose); return this;
    }

    @Override public UiContainer build() {
        if (fields.isEmpty()) throw new IllegalArgumentException("A form needs fields");
        if (viewed != null) {
            var content = Ui.layout(UiLayouts.contentColumn(UiKey.named("fields"),UiLength.ui(10))).key(key).style(style).sizing(sizing)
                    .child(UiKey.named("fields"), UiFormViewport.content(fields, viewed, choose, navigationAxis), LinearSlot.stretch());
            if (feedback != null || actions != null) content.child(UiTemplates.actionRow(feedback, actions), LinearSlot.stretch());
            return UiTemplates.focus(content.build(), UiKey.named("fields"));
        }
        List<UiChild> cells = new ArrayList<>();
        List<UiFormLayout.Row> rows = new ArrayList<>();
        for (var field : fields) {
            int label = -1;
            if (!field.label().equals(Component.empty())) {
                label = cells.size();
                cells.add(UiChild.linear(UiKey.named("field:" + field.key().value() + ":label"),
                        Ui.text(field.label()).key("label").textWidth(FloatingMenuTextWidth.CONTENT)
                                .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.CONTENT));
            }
            int control = cells.size();
            cells.add(UiChild.linear(UiKey.named("field:" + field.key().value() + ":control"), field.control(), LinearSlot.CONTENT));
            rows.add(new UiFormLayout.Row(label, control));
        }
        var content = Ui.layout(UiLayouts.contentColumn(UiKey.named("fields"),UiLength.ui(10))).key(key).style(style).sizing(sizing)
                .child(new UiContainer(UiKey.named("fields"), new UiFormLayout(rows), cells, UiStyle.PLAIN, UiSizing.CONTENT),
                        LinearSlot.stretch());
        if (feedback != null || actions != null) content.child(UiTemplates.actionRow(feedback, actions),
                LinearSlot.stretch());
        return content.build();
    }
}
