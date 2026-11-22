package org.encinet.mik.shared.menu.runtime.tooltip;

import org.encinet.mik.shared.menu.runtime.layout.FloatingMenuNodeSizing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiText;
import org.encinet.mik.shared.menu.ui.UiTooltip;

/** Bounded, measured popup text; fitting changes wrapping and content, never the player's text scale. */
public record MenuTooltipText(Component content, FloatingMenuNodeSizing.TextLayout layout) {
    public static MenuTooltipText fit(UiTooltip tooltip, UiSize available, double textScale) {
        int width = Math.min(tooltip.widthPixels(), FloatingMenuNodeSizing.textWidthPixels(available.width(), textScale));
        int lines = FloatingMenuNodeSizing.textLines(available.height(), textScale);
        if (width < 1 || lines < 1) return null;
        var complete = FloatingMenuNodeSizing.measureText(tooltip.text(), false, width);
        if (complete.size().width() * textScale <= available.width()
                && complete.size().height() * textScale <= available.height())
            return new MenuTooltipText(tooltip.text(), complete);
        MenuTooltipText best = null;
        String plain = PlainTextComponentSerializer.plainText().serialize(tooltip.text());
        int low = 2, high = plain.codePointCount(0,plain.length());
        while (low <= high) {
            int budget = (low + high) / 2;
            Component content = UiText.abbreviate(tooltip.text(), budget, lines);
            var layout = FloatingMenuNodeSizing.measureText(content, false, width);
            if (layout.lines() <= lines && layout.size().width() * textScale <= available.width()
                    && layout.size().height() * textScale <= available.height()) {
                best = new MenuTooltipText(content, layout);
                low = budget + 1;
            } else high = budget - 1;
        }
        return best;
    }
}
