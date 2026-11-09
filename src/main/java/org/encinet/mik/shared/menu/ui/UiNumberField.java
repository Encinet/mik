package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

/** A bounded adjustment and its precise value share one model; editing and persistence belong to the host. */
public final class UiNumberField extends Ui.Builder<UiNumberField> {
    private final Component label;
    private final UiRange range;
    private final UiRange.Change change;
    private FloatingMenuCommand edit;
    private Component disabledReason;

    UiNumberField(Component label, UiRange range, UiRange.Change change) {
        this.label = Objects.requireNonNull(label);
        this.range = Objects.requireNonNull(range);
        this.change = Objects.requireNonNull(change);
    }

    @Override protected UiNumberField self() { return this; }
    public UiNumberField edit(FloatingMenuCommand edit) { this.edit = Objects.requireNonNull(edit); return this; }
    public UiNumberField disabled(Component reason) { disabledReason = Objects.requireNonNull(reason); return this; }

    @Override public UiContainer build() {
        // The value is already visible beside the track. Endpoint labels would repeat it.
        var trackRange = new UiRange(range.minimum(), range.maximum(), range.value(), range.step(), range.formatter(), false);
        var track = Ui.slider(label, trackRange, change).key("track");
        var value = Ui.rangeValue(range).key(key).style(UiTheme.INPUT.withBackgroundAlpha(.18))
                .textWidth(FloatingMenuTextWidth.CONTENT);
        if (edit != null) value.primary(edit);
        var trackInput = track.build().input();
        for (var direction : java.util.List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN))
            value.on(direction, trackInput.actions().get(direction));
        if (disabledReason != null) { track.disabled(disabledReason); value.disabled(disabledReason); }
        return Ui.layout(new UiActionRowLayout(UiLength.ui(288)))
                .key(key).style(style).sizing(sizing)
                .child(UiKey.named("track"), track.build(), LinearSlot.CONTENT)
                .child(UiKey.named("value"), value.build(), LinearSlot.CONTENT).build();
    }
}
