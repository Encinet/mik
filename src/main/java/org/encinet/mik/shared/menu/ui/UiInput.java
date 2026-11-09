package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuFocusAction;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record UiInput(boolean target, Map<FloatingMenuInteraction, FloatingMenuAction> actions,
                      Optional<FloatingMenuFocusAction> focus, boolean selected,
                      Optional<Component> disabledReason, Optional<UiTooltip> tooltip) {
    public static final UiInput NONE = new UiInput(false, Map.of(), Optional.empty(), false, Optional.empty(), Optional.empty());

    public UiInput {
        actions = Map.copyOf(actions);
        focus = Objects.requireNonNull(focus, "focus");
        disabledReason = Objects.requireNonNull(disabledReason, "disabledReason");
        tooltip = Objects.requireNonNull(tooltip, "tooltip");
    }

    public boolean enabled() { return disabledReason.isEmpty(); }
    /** Semantic control occupancy used by layout; pointer feedback follows the current bindings below. */
    public boolean interactive() { return target || !actions.isEmpty() || focus.isPresent(); }

    public UiPointerBehavior pointerBehavior() {
        return UiPointerBehavior.resolve(enabled(), !actions.isEmpty(),
                focus.isPresent() || disabledReason.isPresent() || tooltip.isPresent());
    }
}
