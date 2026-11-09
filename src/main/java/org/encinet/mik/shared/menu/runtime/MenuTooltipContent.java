package org.encinet.mik.shared.menu.runtime;

import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiTooltip;

/** Supplemental help only; a visible label or inline explanation is already sufficient. */
final class MenuTooltipContent {
    static UiTooltip resolve(MenuScene.Entry entry, Component renderedLabel, Predicate<Component> inlineExplanation) {
        Component reason = entry.disabledReason();
        boolean explained = reason != null && !PlainTextComponentSerializer.plainText().serialize(reason).isBlank();
        var fullText = entry.tooltip() == null ? java.util.Optional.<Component>empty() : entry.tooltip().fullText();
        if (explained && inlineExplanation.test(reason))
            return fullText.filter(text -> !same(text,renderedLabel)).map(UiTooltip::of).orElse(null);
        UiTooltip tooltip = explained ? UiTooltip.of(reason) : entry.tooltip();
        if (explained && fullText.isPresent()) tooltip = tooltip.withFullText(fullText.orElseThrow());
        if (tooltip == null && !same(entry.label(), renderedLabel)) tooltip = UiTooltip.of(entry.label());
        if (tooltip == null || same(tooltip.text(), Component.empty()) || same(tooltip.text(), renderedLabel)) return null;
        return tooltip;
    }

    static boolean same(Component first, Component second) {
        if (first.equals(second)) return true;
        String text = PlainTextComponentSerializer.plainText().serialize(first).strip();
        return !text.isEmpty() && text.equals(PlainTextComponentSerializer.plainText().serialize(second).strip());
    }

    private MenuTooltipContent() { }
}
