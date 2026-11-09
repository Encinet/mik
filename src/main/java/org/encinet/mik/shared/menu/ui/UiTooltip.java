package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import java.util.Objects;
import java.util.Optional;

/** Read-only secondary help; actions belong to an explicit view or popover, never a tooltip. */
public record UiTooltip(Component help, int widthPixels, int delayTicks, Optional<Component> fullText) {
    public UiTooltip {
        Objects.requireNonNull(help, "help");
        Objects.requireNonNull(fullText, "fullText");
        if (widthPixels < 80 || widthPixels > 240 || delayTicks < 0 || delayTicks > 200)
            throw new IllegalArgumentException("Invalid tooltip bounds or delay");
    }
    public UiTooltip(Component help, int widthPixels, int delayTicks) { this(help,widthPixels,delayTicks,Optional.empty()); }
    public static UiTooltip of(Component text) { return new UiTooltip(text, 160, 10); }
    public UiTooltip withFullText(Component source) { return new UiTooltip(help,widthPixels,delayTicks,Optional.of(source)); }
    public Component text() {
        return fullText.filter(source -> !source.equals(help)).map(source -> source.append(Component.newline()).append(help)).orElse(help);
    }
}
