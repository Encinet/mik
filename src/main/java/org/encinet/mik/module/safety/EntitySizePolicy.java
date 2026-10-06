package org.encinet.mik.module.safety;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.component.TypedEntityData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractCubeMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared size limits for commands, spawn eggs, and entities already in a world. */
public final class EntitySizePolicy {

    private static final int MAX_CUBE_SIZE_TAG = 12;
    private static final int MAX_CUBE_SIZE = MAX_CUBE_SIZE_TAG + 1;
    private static final double MAX_SCALE = 4.0;
    private static final double MAX_DIMENSION = 32.0;
    private static final int MAX_SCANNED_ENTITIES = 128;
    private static final Pattern ENTITY_DATA_COMPONENT = Pattern.compile(
            "(?<![A-Za-z0-9_])(?:minecraft:)?entity_data\\s*=\\s*",
            Pattern.CASE_INSENSITIVE);

    private EntitySizePolicy() {
    }

    public static boolean isOversized(Entity entity) {
        if (entity instanceof AbstractCubeMob cube && cube.getSize() > MAX_CUBE_SIZE) {
            return true;
        }
        if (entity instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null && isOversizedScale(scale.getValue())) {
                return true;
            }
        }
        return isOversizedDimension(entity.getWidth()) || isOversizedDimension(entity.getHeight());
    }

    private static boolean isOversizedDimension(double dimension) {
        return !Double.isFinite(dimension) || dimension > MAX_DIMENSION;
    }

    private static boolean isOversizedScale(double scale) {
        return !Double.isFinite(scale) || scale > MAX_SCALE;
    }

    public static boolean hasOversizedEntityData(net.minecraft.world.item.ItemStack item) {
        TypedEntityData<?> entityData = item.get(DataComponents.ENTITY_DATA);
        return entityData != null && hasOversizedEntityData(entityData.copyTagWithoutId());
    }

    public static boolean hasOversizedSummonData(String arguments) {
        int tagStart = arguments.indexOf('{');
        if (tagStart < 0) {
            return false;
        }
        try {
            return hasOversizedEntityData(TagParser.parseCompoundFully(arguments.substring(tagStart)));
        } catch (CommandSyntaxException ignored) {
            // The Minecraft command parser will reject malformed SNBT itself.
            return false;
        }
    }

    public static boolean hasOversizedGiveData(String arguments) {
        Matcher matcher = ENTITY_DATA_COMPONENT.matcher(arguments);
        while (matcher.find()) {
            try {
                StringReader reader = new StringReader(arguments.substring(matcher.end()));
                if (hasOversizedEntityData(TagParser.parseCompoundAsArgument(reader))) {
                    return true;
                }
            } catch (CommandSyntaxException ignored) {
                // Invalid item components will be rejected by Minecraft.
            }
        }
        return false;
    }

    static boolean hasOversizedEntityData(CompoundTag root) {
        Deque<CompoundTag> pending = new ArrayDeque<>();
        pending.add(root);
        int scanned = 0;
        while (!pending.isEmpty()) {
            if (++scanned > MAX_SCANNED_ENTITIES) {
                return true;
            }
            CompoundTag entity = pending.removeLast();
            if (entity.get("Size") instanceof NumericTag size
                    && (!Double.isFinite(size.doubleValue())
                    || size.doubleValue() > MAX_CUBE_SIZE_TAG)) {
                return true;
            }
            for (Tag attribute : entity.getListOrEmpty("attributes")) {
                if (attribute instanceof CompoundTag compound
                        && isOversizedScaleAttribute(compound)) {
                    return true;
                }
            }
            for (Tag attribute : entity.getListOrEmpty("Attributes")) {
                if (attribute instanceof CompoundTag compound
                        && isOversizedScaleAttribute(compound)) {
                    return true;
                }
            }
            for (Tag passenger : entity.getListOrEmpty("Passengers")) {
                if (passenger instanceof CompoundTag compound) {
                    pending.add(compound);
                }
            }
        }
        return false;
    }

    private static boolean isOversizedScaleAttribute(CompoundTag attribute) {
        String id = attribute.getStringOr("id", attribute.getStringOr("Name", ""));
        if (!id.equals("minecraft:scale") && !id.equals("scale")) {
            return false;
        }

        Tag baseTag = attribute.get("base");
        if (baseTag == null) {
            baseTag = attribute.get("Base");
        }
        double base = baseTag instanceof NumericTag number ? number.doubleValue() : 1.0;
        if (isOversizedScale(base)) {
            return true;
        }

        double possibleScale = base;
        ListTag modifiers = attribute.getListOrEmpty("modifiers");
        for (Tag modifierTag : modifiers) {
            if (!(modifierTag instanceof CompoundTag modifier)
                    || !(modifier.get("amount") instanceof NumericTag amountTag)) {
                continue;
            }
            double amount = amountTag.doubleValue();
            if (!Double.isFinite(amount)) {
                return true;
            }
            if (amount <= 0) {
                continue;
            }
            String operation = modifier.getStringOr("operation", "add_value");
            int namespaceSeparator = operation.lastIndexOf(':');
            if (namespaceSeparator >= 0) {
                operation = operation.substring(namespaceSeparator + 1);
            }
            possibleScale = switch (operation) {
                case "add_multiplied_base" -> possibleScale + base * amount;
                case "add_multiplied_total" -> possibleScale * (1.0 + amount);
                default -> possibleScale + amount;
            };
            if (isOversizedScale(possibleScale)) {
                return true;
            }
        }
        return false;
    }
}
