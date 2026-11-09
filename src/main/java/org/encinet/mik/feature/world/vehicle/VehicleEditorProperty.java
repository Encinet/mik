package org.encinet.mik.feature.world.vehicle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/** Applicable display properties, their current values and finite choice sets. */
enum VehicleEditorProperty {
    ANIMATION, BILLBOARD, ITEM_TRANSFORM, ALIGNMENT, BLOCK_LIGHT, SKY_LIGHT, BACKGROUND, LINE_WIDTH, OPACITY, SHADOW, SEE_THROUGH;
    String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    String choiceKey(String value) { return this == ALIGNMENT && value.equals("CENTER") ? "TEXT_CENTER" : value; }
    boolean applies(VehicleDefinition.PartKind kind) {
        return switch (this) {
            case ITEM_TRANSFORM -> kind == VehicleDefinition.PartKind.ITEM;
            case ALIGNMENT, BACKGROUND, LINE_WIDTH, OPACITY, SHADOW, SEE_THROUGH -> kind == VehicleDefinition.PartKind.TEXT;
            default -> true;
        };
    }
    List<String> choices() {
        return switch (this) {
            case ANIMATION -> names(VehicleDefinition.Animation.values());
            case BILLBOARD -> names(Display.Billboard.values());
            case ITEM_TRANSFORM -> names(ItemDisplay.ItemDisplayTransform.values());
            case ALIGNMENT -> names(TextDisplay.TextAlignment.values());
            case SHADOW, SEE_THROUGH -> List.of("false", "true");
            default -> List.of();
        };
    }
    String value(VehicleDefinition.Part part) {
        return switch (this) {
            case ANIMATION -> part.animation().name();
            case BILLBOARD -> part.billboard();
            case ITEM_TRANSFORM -> part.itemTransform();
            case ALIGNMENT -> part.alignment();
            case BLOCK_LIGHT -> Integer.toString(part.blockLight());
            case SKY_LIGHT -> Integer.toString(part.skyLight());
            case BACKGROUND -> Integer.toString(part.background());
            case LINE_WIDTH -> Integer.toString(part.lineWidth());
            case OPACITY -> Integer.toString(Byte.toUnsignedInt(part.textOpacity()));
            case SHADOW -> Boolean.toString(part.shadow());
            case SEE_THROUGH -> Boolean.toString(part.seeThrough());
        };
    }
    void apply(VehicleModelDraft draft, int index, String value) {
        if (!applies(draft.parts().get(index).kind())) throw new IllegalArgumentException("Property does not apply to this display");
        draft.changePart(index, this == ANIMATION ? "animation" : "option", this == ANIMATION ? value : key() + " " + value);
    }
    private static List<String> names(Enum<?>[] values) { return Arrays.stream(values).map(Enum::name).toList(); }
}
