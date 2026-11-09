package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import net.kyori.adventure.text.Component;

public record UiElement(UiKey key, UiContent content, UiRole role, UiInput input,
                        UiStyle style, UiSizing sizing, int textWidthPixels,
                        FloatingMenuDecoration.Alignment alignment, boolean keepAccessible) implements UiNode {
    public UiElement {
        key = Objects.requireNonNull(key, "key"); content = Objects.requireNonNull(content, "content");
        role = Objects.requireNonNull(role, "role"); input = Objects.requireNonNull(input, "input");
        style = Objects.requireNonNull(style, "style"); sizing = Objects.requireNonNull(sizing, "sizing");
        alignment = Objects.requireNonNull(alignment, "alignment");
        if (content instanceof UiContent.Image && (input.interactive() || input.tooltip().isPresent()))
            throw new IllegalArgumentException("Compose image content with a separate semantic control");
        if (textWidthPixels < 1) throw new IllegalArgumentException("Text width must be positive");
        if (!role.supportsActions() && (input.interactive() || input.selected() || input.disabledReason().isPresent())) {
            throw new IllegalArgumentException("Read-only content cannot own business actions");
        }
    }

    public UiElement withTextWidth(int pixels) {
        return new UiElement(key, content, role, input, style, sizing, pixels, alignment, keepAccessible);
    }

    /** Prefer complete text; this line budget is available only when the whole composition cannot fit. */
    public UiElement withSummaryLines(int lines) {
        if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("Line limits require text content");
        return withContent(new UiContent.Text(text.label(),lines,true,lines < Integer.MAX_VALUE));
    }

    /** Resolves the representation without changing its source, identity or actions. */
    UiElement textRepresentation(boolean summaries) {
        if (!(content instanceof UiContent.Text text) || !text.fullFirst()) return this;
        return withContent(new UiContent.Text(text.label(), summaries ? text.maximumLines() : Integer.MAX_VALUE));
    }

    private UiElement withContent(UiContent value) {
        return new UiElement(key,value,role,input,style,sizing,textWidthPixels,alignment,keepAccessible);
    }

    public UiElement withText(Component label) {
        if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("Only text can supply a fitted label");
        UiInput fitted = input;
        if (text.maximumLines() < Integer.MAX_VALUE && !label.equals(text.label())) {
            UiTooltip help = input.tooltip().orElseGet(() -> UiTooltip.of(text.label())).withFullText(text.label());
            fitted = new UiInput(input.target(),input.actions(),input.focus(),input.selected(),input.disabledReason(),
                    java.util.Optional.of(help));
        }
        return new UiElement(key, new UiContent.Text(label, text.maximumLines(), text.wrapping(),text.fullFirst()), role, fitted, style, sizing,
                textWidthPixels, alignment, keepAccessible);
    }

    public UiElement withRole(UiRole value) {
        return new UiElement(key, content, value, input, style, sizing, textWidthPixels, alignment, keepAccessible);
    }

    public UiElement withStyle(UiStyle value) {
        return new UiElement(key, content, role, input, value, sizing, textWidthPixels, alignment, keepAccessible);
    }
}
