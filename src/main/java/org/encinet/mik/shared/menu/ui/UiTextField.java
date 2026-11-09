package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;

import java.util.Objects;

/** A selectable value preview with local editing tools. A form owns its label and the host owns editing. */
public final class UiTextField extends Ui.Builder<UiTextField> {
    private final Component value;
    private final Ui.ElementBuilder preview;
    private Component placeholder = Component.empty();
    private UiNode actions;
    private int maximumLines = 1, textWidth = 4096;

    UiTextField(Component value) {
        this.value = Objects.requireNonNull(value);
        style = UiTheme.INPUT.withControlSurface(false);
        sizing = UiSizing.atLeast(new UiSize(160, 0));
        preview = Ui.button(value).key("preview").style(UiTheme.INPUT.withBackgroundAlpha(.18))
                .alignment(FloatingMenuDecoration.Alignment.LEFT)
                .sizing(UiSizing.atLeast(new UiSize(120, 0)));
    }

    @Override protected UiTextField self() { return this; }
    public UiTextField placeholder(Component text) { placeholder = Objects.requireNonNull(text); return this; }
    public UiTextField actions(UiNode actions) { this.actions = Objects.requireNonNull(actions); return this; }
    public UiTextField editing(boolean editing) { preview.selected(editing); return this; }
    public UiTextField disabled(Component reason) { preview.disabled(reason); return this; }
    public UiTextField tooltip(Component text) { preview.tooltip(text); return this; }
    public UiTextField textWidth(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Invalid text width");
        textWidth = pixels; return this;
    }
    /** Editing controls keep a stable line envelope; fitting never discards their declared value. */
    public UiTextField maxLines(int lines) {
        if (lines < 1) throw new IllegalArgumentException("Invalid preview bounds");
        maximumLines = lines; return this;
    }
    public UiTextField on(FloatingMenuInteraction input, FloatingMenuAction action) { preview.on(input, action); return this; }
    public UiTextField primary(FloatingMenuCommand edit) { preview.primary(edit); return this; }

    @Override public UiContainer build() {
        Component displayed = PlainTextComponentSerializer.plainText().serialize(value).isEmpty()
                ? placeholder.colorIfAbsent(NamedTextColor.AQUA) : value;
        UiElement control = preview.build();
        UiInput input = control.input();
        var value = new UiElement(control.key(), new UiContent.Text(displayed, maximumLines), control.role(), input,
                control.style(), control.sizing(), textWidth, control.alignment(), control.keepAccessible());
        var contents = Ui.layout(new UiTextFieldLayout())
                .key(key).style(style).sizing(sizing)
                .child(UiKey.named("preview"), value, new LinearSlot(1, LinearSlot.Alignment.START));
        if (actions != null) contents.child(UiKey.named("actions"), actions, new LinearSlot(0, LinearSlot.Alignment.END));
        return contents.build();
    }
}
