package org.encinet.mik.module.plot.protection;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LeashHitch;
import org.bukkit.inventory.InventoryHolder;
import org.encinet.mik.module.plot.PlotPermission;

import java.util.List;
import java.util.Set;

public final class PlotEntityEditActions {
    private static final Set<String> DECORATIONS = Set.of("minecraft:armor_stand", "minecraft:painting",
            "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:block_display",
            "minecraft:item_display", "minecraft:text_display");

    public enum Operation { PLACE, MODIFY, REMOVE }

    private PlotEntityEditActions() { }

    public static boolean decoration(String type) { return DECORATIONS.contains(type); }

    public static PlotPermission placement(String type) {
        if ("minecraft:leash_knot".equals(type)) return PlotPermission.ANIMAL;
        return decoration(type) ? PlotPermission.DECORATION : PlotPermission.PLACE;
    }

    public static List<PlotPermission> required(Entity entity, Operation operation) {
        if (entity instanceof LeashHitch) return List.of(PlotPermission.ANIMAL);
        if (entity instanceof ArmorStand || entity instanceof Hanging || entity instanceof Display)
            return List.of(PlotPermission.DECORATION);
        if (operation == Operation.PLACE) return List.of(PlotPermission.PLACE);
        if (operation == Operation.REMOVE) return entity instanceof InventoryHolder
                ? List.of(PlotPermission.ENTITY_DAMAGE, PlotPermission.ENTITY_STORAGE)
                : List.of(PlotPermission.ENTITY_DAMAGE);
        return entity instanceof InventoryHolder
                ? List.of(PlotPermission.ENTITY_INTERACT, PlotPermission.ENTITY_DAMAGE, PlotPermission.ENTITY_STORAGE)
                : List.of(PlotPermission.ENTITY_INTERACT, PlotPermission.ENTITY_DAMAGE);
    }
}
