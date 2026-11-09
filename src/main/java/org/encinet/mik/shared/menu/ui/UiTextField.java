package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

import java.util.Objects;
import java.util.function.Function;

/** A source text snapshot with an optional rich preview. The host owns editing and local tools. */
public final class UiTextField extends Ui.Builder<UiTextField> {
    private final String value;
    private final Ui.ElementBuilder control;
    private Function<String, Component> preview = Component::text;
    private Component placeholder = Component.empty();
    private UiNode actions;
    private int maximumLines = 1;

    UiTextField(String value) {
        this.value = Objects.requireNonNull(value);
        style = UiTheme.INPUT.withControlSurface(false);
        sizing = UiSizing.atLeast(new UiSize(160, 0));
        control = Ui.button(Component.empty()).key("preview").style(UiTheme.INPUT.withBackgroundAlpha(.18))
                .alignment(FloatingMenuDecoration.Alignment.LEFT)
                .sizing(UiSizing.atLeast(new UiSize(120, 0)));
    }

    @Override protected UiTextField self() { return this; }
    /** Renders nonempty source text without changing its value or empty state. */
    public UiTextField preview(Function<String, Component> renderer) { preview = Objects.requireNonNull(renderer); return this; }
    public UiTextField placeholder(Component text) { placeholder = Objects.requireNonNull(text); return this; }
    public UiTextField actions(UiNode actions) { this.actions = Objects.requireNonNull(actions); return this; }
    /** Reflects the host's editing state without acquiring or ending an input session. */
    public UiTextField editing(boolean editing) { control.selected(editing); return this; }
    /** Disables value editing; the host defines the availability of each auxiliary action. */
    public UiTextField disabled(Component reason) { control.disabled(reason); return this; }
    public UiTextField tooltip(Component text) { control.tooltip(text); return this; }
    public UiTextField textWidth(int pixels) { control.textWidth(pixels); return this; }
    public UiTextField textWidth(FloatingMenuTextWidth width) { control.textWidth(width); return this; }
    /** Editing controls keep a stable line envelope; fitting never discards their declared value. */
    public UiTextField maxLines(int lines) {
        if (lines < 1) throw new IllegalArgumentException("Invalid preview bounds");
        maximumLines = lines; return this;
    }
    public UiTextField on(FloatingMenuInteraction input, FloatingMenuAction action) { control.on(input, action); return this; }
    public UiTextField primary(FloatingMenuCommand edit) { control.primary(edit); return this; }

    @Override public UiContainer build() {
        Component displayed = value.isEmpty() ? placeholder.colorIfAbsent(NamedTextColor.AQUA)
                : Objects.requireNonNull(preview.apply(value), "Field preview");
        UiElement element = control.build();
        var content = new UiElement(element.key(), new UiContent.Text(displayed, maximumLines), element.role(), element.input(),
                element.style(), element.sizing(), element.textWidthPixels(), element.alignment(), element.keepAccessible());
        var contents = Ui.layout(new UiTextFieldLayout())
                .key(key).style(style).sizing(sizing)
                .child(UiKey.named("preview"), content, new LinearSlot(1, LinearSlot.Alignment.START));
        if (actions != null) contents.child(UiKey.named("actions"), actions, new LinearSlot(0, LinearSlot.Alignment.END));
        return contents.build();
    }
}
