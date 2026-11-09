package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.image.UiImage;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public sealed interface UiContent permits UiContent.Text, UiContent.Item, UiContent.Image, UiContent.Range, UiContent.RangeValue, UiContent.Icon {
    Component label();

    record Icon(UiIcon value, Component label, UiColor tint) implements UiContent {
        public Icon { Objects.requireNonNull(value, "value"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(tint, "tint"); }
        public Icon(UiIcon value, Component label) { this(value, label, UiColor.themed(UiColorRole.TEXT)); }
    }

    record Range(UiRange value, Component label) implements UiContent {
        public Range { Objects.requireNonNull(value, "value"); Objects.requireNonNull(label, "label"); }
    }

    /** A textual reading reserves the range endpoints' envelope without painting them. */
    record RangeValue(UiRange value, Component label) implements UiContent {
        public RangeValue { Objects.requireNonNull(value, "value"); Objects.requireNonNull(label, "label"); }
    }

    /** Wrapping and preview limits; adaptive summaries prefer complete content. */
    record Text(Component label, int maximumLines, boolean wrapping, boolean fullFirst) implements UiContent {
        public Text {
            label = Objects.requireNonNull(label, "label");
            if (maximumLines < 1) throw new IllegalArgumentException("Text needs at least one line");
            if (!wrapping && maximumLines != Integer.MAX_VALUE)
                throw new IllegalArgumentException("Unwrapped text does not truncate its lines");
            if (fullFirst && !wrapping) throw new IllegalArgumentException("Adaptive text needs wrapping");
        }
        public Text(Component label, int maximumLines, boolean wrapping) { this(label, maximumLines, wrapping, false); }
        public Text(Component label, int maximumLines) { this(label, maximumLines, true); }
        public Text(Component label) { this(label, Integer.MAX_VALUE); }
        public boolean singleLine() { return maximumLines == 1 && !fullFirst; }
    }

    record Image(UiImage source, Component label) implements UiContent {
        public Image { Objects.requireNonNull(source, "source"); Objects.requireNonNull(label, "label"); }
    }

    record Item(ItemStack stack, Component label, boolean block) implements UiContent {
        public Item {
            stack = Objects.requireNonNull(stack, "stack").clone();
            label = Objects.requireNonNull(label, "label");
            if (block && !stack.getType().isBlock()) throw new IllegalArgumentException("Block content requires a block");
        }
        @Override public ItemStack stack() { return stack.clone(); }
    }
}
