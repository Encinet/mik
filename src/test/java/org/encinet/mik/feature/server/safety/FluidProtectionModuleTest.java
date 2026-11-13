package org.encinet.mik.feature.server.safety;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FluidProtectionModuleTest {

    private final FluidProtectionModule module = new FluidProtectionModule(null);

    @ParameterizedTest
    @CsvSource({
            "WATER, REDSTONE_WIRE", "LAVA, REDSTONE_WIRE",
            "WATER, TORCH", "LAVA, TORCH",
            "WATER, WHEAT", "LAVA, WHEAT",
            "WATER, POPPY", "LAVA, POPPY",
            "WATER, SNOW", "LAVA, SNOW"
    })
    void cancelsFlowsThatWouldWashAwayBlocks(Material source, Material target) {
        BlockFromToEvent event = flow(source, block(target));

        module.onBlockFromTo(event);

        assertTrue(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "AIR", "CAVE_AIR", "VOID_AIR", "WATER", "LAVA", "FIRE", "SOUL_FIRE"
    })
    void permitsOpenSpaceFluidInteractionsAndExtinguishingFire(Material target) {
        for (Material source : new Material[]{Material.WATER, Material.LAVA}) {
            BlockFromToEvent event = flow(source, block(target));

            module.onBlockFromTo(event);

            assertFalse(event.isCancelled(), source + " -> " + target);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "OAK_STAIRS", "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS", "BUBBLE_COLUMN"
    })
    void alsoProtectsAgainstWaterFlowingFromWaterloggedBlocksAndPlants(Material source) {
        BlockFromToEvent event = flow(source, block(Material.REDSTONE_WIRE));

        module.onBlockFromTo(event);

        assertTrue(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"OAK_SIGN", "OAK_STAIRS", "RAIL"})
    void permitsWaterloggingButPreventsLavaFromReplacingWaterloggableBlocks(Material material) {
        Block target = block(material);
        when(target.getBlockData()).thenReturn(mock(Waterlogged.class));
        BlockFromToEvent water = flow(Material.WATER, target);
        BlockFromToEvent lava = flow(Material.LAVA, target);

        module.onBlockFromTo(water);
        module.onBlockFromTo(lava);

        assertFalse(water.isCancelled());
        assertTrue(lava.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS", "BUBBLE_COLUMN"
    })
    void preservesAquaticPlantsInWaterButProtectsThemFromLava(Material target) {
        BlockFromToEvent water = flow(Material.WATER, block(target));
        BlockFromToEvent lava = flow(Material.LAVA, block(target));

        module.onBlockFromTo(water);
        module.onBlockFromTo(lava);

        assertFalse(water.isCancelled());
        assertTrue(lava.isCancelled());
    }

    @Test
    void leavesDragonEggTeleportationAlone() {
        BlockFromToEvent event = flow(Material.DRAGON_EGG, block(Material.TORCH));

        module.onBlockFromTo(event);

        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "WATER_BUCKET", "LAVA_BUCKET", "COD_BUCKET", "SALMON_BUCKET", "PUFFERFISH_BUCKET",
            "TROPICAL_FISH_BUCKET", "AXOLOTL_BUCKET", "TADPOLE_BUCKET"
    })
    void blocksDirectPlacementFromPlayerAndDispenserBuckets(Material bucket) {
        Block target = block(Material.WHEAT);
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            PlayerBucketEmptyEvent player = bucketEmpty(bucket, target, block(Material.STONE), hand);

            module.onPlayerBucketEmpty(player);

            assertTrue(player.isCancelled(), bucket + " in " + hand);
        }
        BlockDispenseEvent dispenser = dispense(Material.DISPENSER, bucket, target, BlockFace.NORTH);

        module.onBlockDispense(dispenser);

        assertTrue(dispenser.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
    void protectsTheBlockInFrontOfEachDispenserFacing(BlockFace facing) {
        BlockDispenseEvent event = dispense(Material.DISPENSER, Material.WATER_BUCKET,
                block(Material.REDSTONE_WIRE), facing);

        module.onBlockDispense(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void checksTheActualBucketTargetRatherThanTheClickedBlock() {
        PlayerBucketEmptyEvent event = bucketEmpty(Material.WATER_BUCKET, block(Material.AIR),
                block(Material.OAK_SIGN), EquipmentSlot.HAND);

        module.onPlayerBucketEmpty(event);

        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @CsvSource({"WATER_BUCKET, AIR", "LAVA_BUCKET, AIR", "COD_BUCKET, WATER",
            "WATER_BUCKET, KELP", "WATER_BUCKET, FIRE", "LAVA_BUCKET, LAVA"})
    void allowsSafeBucketPlacement(Material bucket, Material targetType) {
        Block target = block(targetType);
        PlayerBucketEmptyEvent player = bucketEmpty(bucket, target, block(Material.STONE), EquipmentSlot.HAND);
        BlockDispenseEvent dispenser = dispense(Material.DISPENSER, bucket, target, BlockFace.NORTH);

        module.onPlayerBucketEmpty(player);
        module.onBlockDispense(dispenser);

        assertFalse(player.isCancelled());
        assertFalse(dispenser.isCancelled());
    }

    @Test
    void allowsWaterloggingFromBucketsButBlocksDestructiveLavaPlacement() {
        Block target = block(Material.OAK_SIGN);
        when(target.getBlockData()).thenReturn(mock(Waterlogged.class));
        for (Material bucket : new Material[]{Material.WATER_BUCKET, Material.LAVA_BUCKET}) {
            PlayerBucketEmptyEvent player = bucketEmpty(bucket, target, block(Material.STONE), EquipmentSlot.HAND);
            BlockDispenseEvent dispenser = dispense(Material.DISPENSER, bucket, target, BlockFace.NORTH);

            module.onPlayerBucketEmpty(player);
            module.onBlockDispense(dispenser);

            assertEquals(bucket == Material.LAVA_BUCKET, player.isCancelled());
            assertEquals(bucket == Material.LAVA_BUCKET, dispenser.isCancelled());
        }
    }

    @Test
    void droppersCanEjectBucketItemsInFrontOfFragileBlocks() {
        BlockDispenseEvent event = dispense(Material.DROPPER, Material.WATER_BUCKET,
                block(Material.WHEAT), BlockFace.NORTH);

        module.onBlockDispense(event);

        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"BUCKET", "MILK_BUCKET", "POWDER_SNOW_BUCKET",
            "SULFUR_CUBE_BUCKET", "STONE"})
    void leavesItemsWithoutWaterOrLavaAlone(Material item) {
        Block target = block(Material.WHEAT);
        PlayerBucketEmptyEvent player = bucketEmpty(item, target, block(Material.STONE), EquipmentSlot.HAND);
        BlockDispenseEvent dispenser = dispense(Material.DISPENSER, item, target, BlockFace.NORTH);

        module.onPlayerBucketEmpty(player);
        module.onBlockDispense(dispenser);

        assertFalse(player.isCancelled());
        assertFalse(dispenser.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(BlockIgniteEvent.IgniteCause.class)
    void preventsLavaIgnitionAndRetainsOtherIgnitionCauses(BlockIgniteEvent.IgniteCause cause) {
        BlockIgniteEvent event = new BlockIgniteEvent(block(Material.AIR), cause, null, null);

        module.onBlockIgnite(event);

        assertEquals(cause == BlockIgniteEvent.IgniteCause.LAVA, event.isCancelled());
    }

    private static PlayerBucketEmptyEvent bucketEmpty(Material bucket, Block target, Block clicked,
                                                       EquipmentSlot hand) {
        return new PlayerBucketEmptyEvent(mock(Player.class), target, clicked, BlockFace.UP, bucket, null, hand);
    }

    private static BlockDispenseEvent dispense(Material sourceType, Material item, Block target, BlockFace facing) {
        Block source = block(sourceType);
        Dispenser data = mock(Dispenser.class);
        when(data.getFacing()).thenReturn(facing);
        when(source.getBlockData()).thenReturn(data);
        when(source.getRelative(facing)).thenReturn(target);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(item);
        when(stack.clone()).thenReturn(stack);
        return new BlockDispenseEvent(source, stack, new Vector());
    }

    private static BlockFromToEvent flow(Material source, Block target) {
        return new BlockFromToEvent(block(source), target);
    }

    private static Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(mock(BlockData.class));
        return block;
    }
}
