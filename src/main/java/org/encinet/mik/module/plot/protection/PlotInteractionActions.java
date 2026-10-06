package org.encinet.mik.module.plot.protection;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Display;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Steerable;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.encinet.mik.module.plot.PlotPermission;

import java.util.Set;

final class PlotInteractionActions {
    private static final NamespacedKey VEHICLE = new NamespacedKey("mik", "vehicle_instance");
    private static final Set<Material> FACILITIES = Set.of(Material.CRAFTING_TABLE, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.ENCHANTING_TABLE, Material.GRINDSTONE,
            Material.LOOM, Material.STONECUTTER, Material.SMITHING_TABLE, Material.CARTOGRAPHY_TABLE,
            Material.BELL, Material.NOTE_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private PlotInteractionActions() { }

    static PlotPermission block(Material material, boolean inventory, boolean physical) {
        return block(material, inventory, physical, Material.AIR);
    }

    static PlotPermission block(Material material, boolean inventory, boolean physical, Material item) {
        String name = material.name();
        if (physical && material == Material.FARMLAND) return PlotPermission.MODIFY;
        if (name.endsWith("_PRESSURE_PLATE") || material == Material.TRIPWIRE
                || material == Material.TRIPWIRE_HOOK || name.endsWith("_BUTTON") || material == Material.LEVER)
            return PlotPermission.SWITCH;
        if (physical) return null;
        if (name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE"))
            return PlotPermission.DOOR;
        if (name.endsWith("_SIGN") || name.endsWith("_WALL_SIGN")) return PlotPermission.SIGN;
        if (inventory || material == Material.ENDER_CHEST) return PlotPermission.CONTAINER;
        if (material == Material.SWEET_BERRY_BUSH || material == Material.CAVE_VINES
                || material == Material.CAVE_VINES_PLANT || material == Material.BEEHIVE
                || material == Material.BEE_NEST) return PlotPermission.HARVEST;
        if (material == Material.RESPAWN_ANCHOR)
            return item == Material.GLOWSTONE ? PlotPermission.MODIFY : PlotPermission.WORKSTATION;
        if (material == Material.COMPOSTER
                || material == Material.CAKE || material == Material.CANDLE_CAKE || name.endsWith("_CANDLE_CAKE")
                || material == Material.FLOWER_POT || name.startsWith("POTTED_")) return PlotPermission.MODIFY;
        if (FACILITIES.contains(material) || name.endsWith("_BED")) return PlotPermission.WORKSTATION;
        return material.isInteractable() ? PlotPermission.WORKSTATION : null;
    }

    static PlotPermission item(Material material) {
        String name = material.name();
        if (name.endsWith("_SPAWN_EGG") || name.endsWith("_BOAT") || name.endsWith("_RAFT")
                || name.endsWith("_MINECART") || material == Material.MINECART || material == Material.END_CRYSTAL)
            return PlotPermission.PLACE;
        if (material == Material.FLINT_AND_STEEL || material == Material.FIRE_CHARGE)
            return PlotPermission.IGNITE;
        if (name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")
                || name.endsWith("_DYE") || material == Material.BONE_MEAL || material == Material.HONEYCOMB
                || material == Material.SHEARS || material == Material.INK_SAC || material == Material.GLOW_INK_SAC
                || material == Material.ENDER_EYE)
            return PlotPermission.MODIFY;
        return null;
    }

    static PlotPermission item(Material material, PlotPermission target) {
        if (target == PlotPermission.HARVEST && (material == Material.SHEARS || material == Material.GLASS_BOTTLE))
            return PlotPermission.HARVEST;
        PlotPermission action = item(material);
        return target == PlotPermission.SIGN && action == PlotPermission.MODIFY ? PlotPermission.SIGN : action;
    }

    static boolean entityBucket(Material material) {
        return material.name().endsWith("_BUCKET") && material != Material.WATER_BUCKET
                && material != Material.LAVA_BUCKET && material != Material.POWDER_SNOW_BUCKET
                && material != Material.MILK_BUCKET;
    }

    static PlotPermission entity(Entity entity, Material item, boolean sneaking) {
        if ((entity instanceof Interaction || entity instanceof Display || entity instanceof ArmorStand)
                && entity.getPersistentDataContainer().has(VEHICLE, PersistentDataType.STRING))
            return PlotPermission.RIDE;
        if (entity instanceof LeashHitch || item == Material.LEAD) return PlotPermission.ANIMAL;
        if (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
            return PlotPermission.DECORATION;
        if (item == Material.NAME_TAG) return entity instanceof Animals ? PlotPermission.ANIMAL : PlotPermission.ENTITY_INTERACT;
        if (entity instanceof ChestedHorse && item == Material.CHEST) return PlotPermission.ENTITY_STORAGE;
        if (entity instanceof AbstractVillager) return PlotPermission.TRADE;
        if (entity instanceof AbstractHorse) {
            if (sneaking) return PlotPermission.ENTITY_STORAGE;
            if (item == Material.WHEAT || item == Material.SUGAR || item == Material.HAY_BLOCK
                    || item == Material.APPLE || item == Material.GOLDEN_APPLE
                    || item == Material.ENCHANTED_GOLDEN_APPLE || item == Material.GOLDEN_CARROT
                    || item == Material.SADDLE || item.name().endsWith("_HORSE_ARMOR"))
                return PlotPermission.ANIMAL;
            return PlotPermission.RIDE;
        }
        if (entity instanceof Boat && !sneaking) return PlotPermission.RIDE;
        if (entity instanceof InventoryHolder) return PlotPermission.ENTITY_STORAGE;
        if (entity instanceof Vehicle) return PlotPermission.RIDE;
        if (entity instanceof Steerable) {
            if (item == Material.CARROT || item == Material.POTATO || item == Material.BEETROOT
                    || item == Material.WARPED_FUNGUS || item == Material.SADDLE) return PlotPermission.ANIMAL;
            return PlotPermission.RIDE;
        }
        if (entity instanceof Animals) return PlotPermission.ANIMAL;
        return PlotPermission.ENTITY_INTERACT;
    }
}
