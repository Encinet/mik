package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

/** A bounded field directory and one complete control. Values and navigation remain owned by the form's host. */
final class UiFormViewport {
    private static final UiSize MAXIMUM_DIRECTORY = new UiSize(200,180);
    private UiFormViewport() { }

    static UiNode content(List<UiForm.Field> fields, UiKey viewed, Consumer<String> choose, UiLayouts.Axis navigationAxis) {
        var selected = fields.stream().filter(field -> field.key().equals(viewed)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown viewed form field: " + viewed.value()));
        var main = Ui.layout(UiLayouts.contentColumn(UiKey.named("field:" + viewed.value() + ":control"),UiLength.ui(6))).key("current-field");
        if (!selected.label().equals(Component.empty())) main.child(UiKey.named("field:" + viewed.value() + ":label"),
                Ui.text(selected.label()).key("label").summaryLines(1).textWidth(FloatingMenuTextWidth.CONTENT)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch());
        UiKey control = UiKey.named("field:" + viewed.value() + ":control");
        main.child(control, selected.control(), LinearSlot.stretch());
        UiNode reading = UiTemplates.focus(main.build(), control);
        if (fields.size() == 1) return reading;

        var directory = new ArrayList<UiNode>();
        var identities = new LinkedHashMap<UiKey,String>();
        UiKey selectedKey = null;
        int selectedIndex = 0;
        for (int index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            UiKey key = UiKey.item("select-field:" + field.key().value());
            identities.put(key, field.key().value());
            if (field.key().equals(viewed)) { selectedKey = key; selectedIndex = index; }
            if (field.label().equals(Component.empty())) throw new IllegalArgumentException("A field directory needs visible labels");
            directory.add(Ui.button(field.label()).key(key).role(UiRole.CHOICE).summaryLines(1)
                    .selected(field.key().equals(viewed)).alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .primary((_, _) -> choose.accept(field.key().value())).build());
        }
        var selector = Ui.document().key("field-directory").blocks(directory).gap(UiLength.ui(6))
                .sizing(new UiSizing(UiSize.ZERO, MAXIMUM_DIRECTORY))
                .position(new UiReadingPosition(java.util.Objects.requireNonNull(selectedKey),0,selectedIndex), position -> {
                    String key = position.block().map(identities::get).orElse(null);
                    if (key != null) choose.accept(key);
                }).build();
        return UiTemplates.split(selector, reading, navigationAxis);
    }
}
