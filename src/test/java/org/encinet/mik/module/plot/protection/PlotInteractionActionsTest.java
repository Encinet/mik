package org.encinet.mik.module.plot.protection;

import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Display;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.encinet.mik.module.plot.PlotPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotInteractionActionsTest {
    @Test
    void doorsSwitchesContainersSignsAndHarvestingHaveSeparateActions() {
        assertEquals(PlotPermission.DOOR, block(Material.OAK_DOOR));
        assertEquals(PlotPermission.DOOR, block(Material.COPPER_TRAPDOOR));
        assertEquals(PlotPermission.DOOR, block(Material.OAK_FENCE_GATE));
        assertEquals(PlotPermission.SWITCH, block(Material.LEVER));
        assertEquals(PlotPermission.SWITCH, block(Material.STONE_BUTTON));
        assertEquals(PlotPermission.CONTAINER, PlotInteractionActions.block(Material.CHEST, true, false));
        assertEquals(PlotPermission.CONTAINER, block(Material.ENDER_CHEST));
        assertEquals(PlotPermission.SIGN, block(Material.OAK_WALL_HANGING_SIGN));
        assertEquals(PlotPermission.HARVEST, block(Material.SWEET_BERRY_BUSH));
        assertEquals(PlotPermission.HARVEST, block(Material.CAVE_VINES_PLANT));
        assertEquals(PlotPermission.HARVEST, block(Material.BEEHIVE));
        assertEquals(PlotPermission.HARVEST, block(Material.BEE_NEST));
    }

    @Test
    void physicalPressurePlatesAndFarmlandDoNotSharePermissions() {
        assertEquals(PlotPermission.SWITCH, PlotInteractionActions.block(Material.OAK_PRESSURE_PLATE, false, true));
        assertEquals(PlotPermission.SWITCH, PlotInteractionActions.block(Material.TRIPWIRE, false, true));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.block(Material.FARMLAND, false, true));
        assertNull(PlotInteractionActions.block(Material.STONE, false, true));
    }

    @Test
    void itemActionsSeparateIgnitionFromBlockModification() {
        assertEquals(PlotPermission.IGNITE, PlotInteractionActions.item(Material.FLINT_AND_STEEL));
        assertEquals(PlotPermission.IGNITE, PlotInteractionActions.item(Material.FIRE_CHARGE));
        assertEquals(PlotPermission.PLACE, PlotInteractionActions.item(Material.COW_SPAWN_EGG));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.IRON_AXE));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.DIAMOND_HOE));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.BONE_MEAL));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.HONEYCOMB));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.ENDER_EYE));
        for (Material material : List.of(Material.END_CRYSTAL, Material.OAK_BOAT, Material.OAK_CHEST_BOAT,
                Material.BAMBOO_RAFT, Material.MINECART, Material.CHEST_MINECART, Material.HOPPER_MINECART)) {
            assertEquals(PlotPermission.PLACE, PlotInteractionActions.item(material), material.name());
        }
        assertNull(PlotInteractionActions.item(Material.STONE));
    }

    @Test
    void signStylingUsesSignPermissionRatherThanGeneralBlockModification() {
        assertEquals(PlotPermission.SIGN, PlotInteractionActions.item(Material.BLUE_DYE, PlotPermission.SIGN));
        assertEquals(PlotPermission.SIGN, PlotInteractionActions.item(Material.HONEYCOMB, PlotPermission.SIGN));
        assertEquals(PlotPermission.SIGN, PlotInteractionActions.item(Material.GLOW_INK_SAC, PlotPermission.SIGN));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.HONEYCOMB, null));
    }

    @Test
    void worldChangingFacilitiesDoNotUseWorkstationAccess() {
        for (Material material : List.of(Material.COMPOSTER, Material.CAKE,
                Material.CANDLE_CAKE, Material.FLOWER_POT, Material.POTTED_DANDELION)) {
            assertEquals(PlotPermission.MODIFY, block(material), material.name());
        }
        assertEquals(PlotPermission.WORKSTATION, block(Material.CRAFTING_TABLE));
        assertEquals(PlotPermission.WORKSTATION, block(Material.RED_BED));
        assertEquals(PlotPermission.WORKSTATION, block(Material.BELL));
        assertEquals(PlotPermission.WORKSTATION, block(Material.RESPAWN_ANCHOR));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.block(Material.RESPAWN_ANCHOR, false, false, Material.GLOWSTONE));
        assertEquals(PlotPermission.HARVEST, PlotInteractionActions.item(Material.SHEARS, PlotPermission.HARVEST));
        assertEquals(PlotPermission.HARVEST, PlotInteractionActions.item(Material.GLASS_BOTTLE, PlotPermission.HARVEST));
        assertEquals(PlotPermission.MODIFY, PlotInteractionActions.item(Material.BONE_MEAL, PlotPermission.HARVEST));
    }

    @Test
    void liquidBucketsAreDistinctFromBucketsThatSpawnEntities() {
        for (Material material : List.of(Material.WATER_BUCKET, Material.LAVA_BUCKET,
                Material.POWDER_SNOW_BUCKET, Material.MILK_BUCKET, Material.BUCKET, Material.STONE)) {
            assertFalse(PlotInteractionActions.entityBucket(material), material.name());
        }
        for (Material material : List.of(Material.COD_BUCKET, Material.SALMON_BUCKET,
                Material.PUFFERFISH_BUCKET, Material.TROPICAL_FISH_BUCKET, Material.AXOLOTL_BUCKET,
                Material.TADPOLE_BUCKET)) {
            assertTrue(PlotInteractionActions.entityBucket(material), material.name());
        }
    }

    @Test
    void renamingLeashingAndAddingHorseStorageDoNotUseRideOrTradeAccess() {
        assertEquals(PlotPermission.ENTITY_INTERACT, action(entity(Villager.class, false), Material.NAME_TAG, false));
        assertEquals(PlotPermission.ANIMAL, action(entity(Animals.class, false), Material.NAME_TAG, false));
        assertEquals(PlotPermission.ANIMAL, action(entity(Villager.class, false), Material.LEAD, false));
        assertEquals(PlotPermission.ANIMAL, action(entity(LeashHitch.class, false), Material.AIR, false));
        assertEquals(PlotPermission.ENTITY_STORAGE, action(entity(ChestedHorse.class, false), Material.CHEST, false));
        assertEquals(PlotPermission.DECORATION, action(entity(Display.class, false), Material.AIR, false));
        for (PlotEntityEditActions.Operation operation : PlotEntityEditActions.Operation.values()) {
            assertEquals(List.of(PlotPermission.ANIMAL), PlotEntityEditActions.required(entity(LeashHitch.class, false), operation));
        }
    }

    @Test
    void horseRidingFeedingAndInventoryAccessRemainIndependent() {
        Entity horse = entity(AbstractHorse.class, false);
        assertEquals(PlotPermission.RIDE, action(horse, Material.AIR, false));
        assertEquals(PlotPermission.RIDE, action(horse, Material.IRON_PICKAXE, false));
        assertEquals(PlotPermission.ANIMAL, action(horse, Material.WHEAT, false));
        assertEquals(PlotPermission.ANIMAL, action(horse, Material.SADDLE, false));
        assertEquals(PlotPermission.ENTITY_STORAGE, action(horse, Material.AIR, true));
    }

    @Test
    void pigAndStriderRidingDoesNotRequirePermissionToFeedAnimals() {
        Entity mount = entity(Steerable.class, false);
        assertEquals(PlotPermission.RIDE, action(mount, Material.AIR, false));
        assertEquals(PlotPermission.ANIMAL, action(mount, Material.CARROT, false));
        assertEquals(PlotPermission.ANIMAL, action(mount, Material.WARPED_FUNGUS, false));
    }

    @Test
    void entityClassificationSeparatesTradingStorageCareAndDecorations() {
        assertEquals(PlotPermission.TRADE, action(entity(Villager.class, false), Material.AIR, false));
        assertEquals(PlotPermission.ENTITY_STORAGE, action(entity(StorageMinecart.class, false), Material.AIR, false));
        assertEquals(PlotPermission.RIDE, action(entity(Boat.class, false), Material.AIR, false));
        assertEquals(PlotPermission.ANIMAL, action(entity(Animals.class, false), Material.AIR, false));
        assertEquals(PlotPermission.DECORATION, action(entity(ArmorStand.class, false), Material.AIR, false));
        assertEquals(PlotPermission.ENTITY_INTERACT, action(entity(Entity.class, false), Material.AIR, false));
    }

    @Test
    void managedVehicleSeatsAndInteractionEntitiesUseRidingNotDecorationPermission() {
        assertEquals(PlotPermission.RIDE, action(entity(ArmorStand.class, true), Material.AIR, false));
        assertEquals(PlotPermission.RIDE, action(entity(Interaction.class, true), Material.AIR, false));
        assertEquals(PlotPermission.ENTITY_INTERACT, action(entity(Interaction.class, false), Material.AIR, false));
    }

    @Test
    void editorOperationsCannotUseGenericInteractionToModifyOrRemoveDecorations() {
        Entity decoration = entity(ArmorStand.class, false);
        for (PlotEntityEditActions.Operation operation : PlotEntityEditActions.Operation.values()) {
            assertEquals(List.of(PlotPermission.DECORATION), PlotEntityEditActions.required(decoration, operation));
        }
        assertEquals(List.of(PlotPermission.ENTITY_INTERACT, PlotPermission.ENTITY_DAMAGE),
                PlotEntityEditActions.required(entity(Entity.class, false), PlotEntityEditActions.Operation.MODIFY));
        assertEquals(List.of(PlotPermission.ENTITY_DAMAGE),
                PlotEntityEditActions.required(entity(Entity.class, false), PlotEntityEditActions.Operation.REMOVE));
        assertEquals(List.of(PlotPermission.PLACE),
                PlotEntityEditActions.required(entity(Entity.class, false), PlotEntityEditActions.Operation.PLACE));
    }

    @Test
    void editingOrRemovingAnEntityInventoryRequiresStoragePermission() {
        Entity chest = entity(StorageMinecart.class, false);
        assertEquals(List.of(PlotPermission.ENTITY_INTERACT, PlotPermission.ENTITY_DAMAGE, PlotPermission.ENTITY_STORAGE),
                PlotEntityEditActions.required(chest, PlotEntityEditActions.Operation.MODIFY));
        assertEquals(List.of(PlotPermission.ENTITY_DAMAGE, PlotPermission.ENTITY_STORAGE),
                PlotEntityEditActions.required(chest, PlotEntityEditActions.Operation.REMOVE));
        assertEquals(List.of(PlotPermission.PLACE), PlotEntityEditActions.required(chest, PlotEntityEditActions.Operation.PLACE));
    }

    @Test
    void worldEditEntityTypesIncludeDisplayDecorations() {
        for (String type : List.of("armor_stand", "painting", "item_frame", "glow_item_frame",
                "block_display", "item_display", "text_display")) {
            assertTrue(PlotEntityEditActions.decoration("minecraft:" + type));
        }
        assertFalse(PlotEntityEditActions.decoration("minecraft:pig"));
        assertEquals(PlotPermission.PLACE, PlotEntityEditActions.placement("minecraft:pig"));
        assertEquals(PlotPermission.DECORATION, PlotEntityEditActions.placement("minecraft:block_display"));
        assertEquals(PlotPermission.ANIMAL, PlotEntityEditActions.placement("minecraft:leash_knot"));
    }

    private static PlotPermission block(Material material) {
        return PlotInteractionActions.block(material, false, false);
    }

    private static PlotPermission action(Entity entity, Material item, boolean sneaking) {
        return PlotInteractionActions.entity(entity, item, sneaking);
    }

    private static Entity entity(Class<? extends Entity> type, boolean vehicle) {
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("has")) return vehicle;
                    throw new AssertionError(method.getName());
                });
        return (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return data;
                    throw new AssertionError(method.getName());
                });
    }
}
