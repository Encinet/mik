package org.encinet.mik.module.plot;

import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.plot.protection.PlotProtection;
import org.encinet.mik.module.vehicle.event.VehicleUseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotProtectionCoverageTest {
    @TempDir Path directory;
    private final UUID worldId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getUID")) return worldId;
                throw new AssertionError(method.getName());
            });
    private final Player player = player(0);

    @Test
    void pickupAndDropAreIndependentFromContainerAndEntityStorage() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.item_pickup", false, "collaborator.item_drop", true,
                "collaborator.container", false, "collaborator.entity_storage", false))) {
            EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, entity(Item.class, 0), 0);
            fixture.guard.pickup(pickup);
            assertTrue(pickup.isCancelled());
            PlayerDropItemEvent drop = new PlayerDropItemEvent(player, entity(Item.class, 0));
            fixture.guard.drop(drop);
            assertFalse(drop.isCancelled());
            fixture.set("item_pickup", true);
            EntityPickupItemEvent permitted = new EntityPickupItemEvent(player, entity(Item.class, 0), 0);
            fixture.guard.pickup(permitted);
            assertFalse(permitted.isCancelled());
        }
    }

    @Test
    void droppingChecksBothPlayerAndItemLocations() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.item_drop", false))) {
            for (PlayerDropItemEvent drop : new PlayerDropItemEvent[]{
                    new PlayerDropItemEvent(player, entity(Item.class, 4)),
                    new PlayerDropItemEvent(player(4), entity(Item.class, 0))}) {
                fixture.guard.drop(drop);
                assertTrue(drop.isCancelled());
            }
            PlayerDropItemEvent outside = new PlayerDropItemEvent(player(4), entity(Item.class, 4));
            fixture.guard.drop(outside);
            assertFalse(outside.isCancelled());
        }
    }

    @Test
    void creatureCaptureRequiresBucketAndAnimalAccessTogether() throws Exception {
        for (boolean bucket : new boolean[]{false, true}) {
            for (boolean animal : new boolean[]{false, true}) {
                try (Fixture fixture = fixture(Map.of("collaborator.bucket", bucket, "collaborator.animal", animal))) {
                    PlayerBucketEntityEvent event = new PlayerBucketEntityEvent(player, entity(LivingEntity.class, 0),
                            null, null, EquipmentSlot.HAND);
                    fixture.guard.bucketEntity(event);
                    assertEquals(!bucket || !animal, event.isCancelled());
                }
            }
        }
    }

    @Test
    void releasingCreatureBucketsRequiresPlacementButPlainLiquidsDoNot() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.bucket", true, "collaborator.place", false))) {
            for (Material material : new Material[]{Material.WATER_BUCKET, Material.COD_BUCKET, Material.AXOLOTL_BUCKET}) {
                PlayerBucketEmptyEvent event = new PlayerBucketEmptyEvent(player, block(0), block(4),
                        org.bukkit.block.BlockFace.UP, material, null, EquipmentSlot.HAND);
                fixture.guard.bucketEmpty(event);
                assertEquals(material != Material.WATER_BUCKET, event.isCancelled());
            }
            fixture.set("bucket", false);
            PlayerBucketEmptyEvent denied = new PlayerBucketEmptyEvent(player, block(0), block(4),
                    org.bukkit.block.BlockFace.UP, Material.WATER_BUCKET, null, EquipmentSlot.HAND);
            fixture.guard.bucketEmpty(denied);
            assertTrue(denied.isCancelled());
        }
    }

    @Test
    void vehicleDamageAndDestructionCannotBypassEntityStorage() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.entity_damage", true, "collaborator.entity_storage", false))) {
            StorageMinecart cart = entity(StorageMinecart.class, 0);
            DamageSource damage = damageSource(player);
            VehicleDamageEvent hit = new VehicleDamageEvent(cart, damage, player, 1);
            fixture.guard.vehicleDamage(hit);
            assertTrue(hit.isCancelled());
            VehicleDestroyEvent destroy = new VehicleDestroyEvent(cart, damage, player);
            fixture.guard.vehicleDestroy(destroy);
            assertTrue(destroy.isCancelled());
            fixture.set("entity_storage", true);
            VehicleDestroyEvent permitted = new VehicleDestroyEvent(cart, damage, player);
            fixture.guard.vehicleDestroy(permitted);
            assertFalse(permitted.isCancelled());
            fixture.set("entity_damage", false);
            VehicleDamageEvent denied = new VehicleDamageEvent(cart, damage, player, 1);
            fixture.guard.vehicleDamage(denied);
            assertTrue(denied.isCancelled());
        }
    }

    @Test
    void projectileVehicleDamageIsAttributedToItsPlayer() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.entity_damage", false))) {
            VehicleDamageEvent event = new VehicleDamageEvent(entity(StorageMinecart.class, 0),
                    damageSource(null), entity(Projectile.class, 4), 1);
            fixture.guard.vehicleDamage(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    void commandAndMenuVehicleOperationsUseTheirCorrespondingPlotPermissions() throws Exception {
        Map<VehicleUseEvent.Action, String> permissions = Map.of(VehicleUseEvent.Action.CAPTURE, "decoration",
                VehicleUseEvent.Action.SPAWN, "place", VehicleUseEvent.Action.ENTER, "ride",
                VehicleUseEvent.Action.SERVICE, "entity_interact", VehicleUseEvent.Action.REMOVE, "entity_damage");
        try (Fixture fixture = fixture(Map.of())) {
            for (var operation : permissions.entrySet()) {
                fixture.set(operation.getValue(), false);
                VehicleUseEvent denied = new VehicleUseEvent(player, UUID.randomUUID(), location(0), operation.getKey());
                fixture.guard.customVehicle(denied);
                assertTrue(denied.isCancelled(), operation.getKey().name());
                fixture.set(operation.getValue(), true);
                VehicleUseEvent allowed = new VehicleUseEvent(player, UUID.randomUUID(), location(0), operation.getKey());
                fixture.guard.customVehicle(allowed);
                assertFalse(allowed.isCancelled(), operation.getKey().name());
            }
        }
    }

    @Test
    void tamingAndUnleashingUseAnimalCareRatherThanGenericEntityAccess() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.animal", false, "collaborator.entity_interact", true))) {
            EntityTameEvent tame = new EntityTameEvent(entity(LivingEntity.class, 0), player);
            fixture.guard.tame(tame);
            assertTrue(tame.isCancelled());
            PlayerUnleashEntityEvent unleash = new PlayerUnleashEntityEvent(entity(LivingEntity.class, 0), player,
                    EquipmentSlot.HAND, true);
            fixture.guard.unleash(unleash);
            assertTrue(unleash.isCancelled());
            fixture.set("animal", true);
            EntityTameEvent permitted = new EntityTameEvent(entity(LivingEntity.class, 0), player);
            fixture.guard.tame(permitted);
            assertFalse(permitted.isCancelled());
        }
    }

    @Test
    void leashChecksBothAnimalAndAnchorSidesOfTheBoundary() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.animal", false))) {
            for (int animalX : new int[]{0, 4}) {
                PlayerLeashEntityEvent leash = new PlayerLeashEntityEvent(entity(LivingEntity.class, animalX),
                        entity(Entity.class, 4 - animalX), player, EquipmentSlot.HAND);
                fixture.guard.leash(leash);
                assertTrue(leash.isCancelled());
            }
        }
    }

    @Test
    void fishingCannotPullItemsStorageOrDecorationsWithOnlyGenericEntityAccess() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.item_pickup", false, "collaborator.entity_interact", true,
                "collaborator.entity_storage", false, "collaborator.decoration", false))) {
            for (Entity caught : new Entity[]{entity(Item.class, 0), entity(StorageMinecart.class, 0),
                    entity(ArmorStand.class, 0)}) {
                PlayerFishEvent event = new PlayerFishEvent(player, caught, null, EquipmentSlot.HAND,
                        PlayerFishEvent.State.CAUGHT_ENTITY);
                fixture.guard.fish(event);
                assertTrue(event.isCancelled());
            }
            PlayerFishEvent harmless = new PlayerFishEvent(player, null, null, EquipmentSlot.HAND,
                    PlayerFishEvent.State.FISHING);
            fixture.guard.fish(harmless);
            assertFalse(harmless.isCancelled());
            fixture.set("item_pickup", true);
            PlayerFishEvent permitted = new PlayerFishEvent(player, entity(Item.class, 0), null, EquipmentSlot.HAND,
                    PlayerFishEvent.State.CAUGHT_FISH);
            fixture.guard.fish(permitted);
            assertFalse(permitted.isCancelled());
        }
    }

    @Test
    void flowerPotChangesAreModificationNotFacilityUsage() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.modify", false, "collaborator.workstation", true))) {
            for (boolean placing : new boolean[]{false, true}) {
                PlayerFlowerPotManipulateEvent event = new PlayerFlowerPotManipulateEvent(player, block(0), null, placing);
                fixture.guard.flowerPot(event);
                assertTrue(event.isCancelled());
            }
        }
    }

    @Test
    void honeyHarvestingDoesNotRequireGeneralModificationPermission() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.harvest", true, "collaborator.modify", false))) {
            for (Material tool : new Material[]{Material.SHEARS, Material.GLASS_BOTTLE}) {
                PlayerInteractEvent permitted = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        new TestItemStack(tool), block(0, Material.BEEHIVE), org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
                fixture.guard.interact(permitted);
                assertFalse(permitted.useInteractedBlock() == Event.Result.DENY);
                assertFalse(permitted.useItemInHand() == Event.Result.DENY);
            }
            fixture.set("harvest", false);
            PlayerInteractEvent denied = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                    new TestItemStack(Material.GLASS_BOTTLE), block(0, Material.BEE_NEST), org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
            fixture.guard.interact(denied);
            assertEquals(Event.Result.DENY, denied.useInteractedBlock());
            assertEquals(Event.Result.DENY, denied.useItemInHand());
        }
    }

    @Test
    void playerProjectilesCannotBypassPressurePlatePermissions() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.switch", false))) {
            EntityInteractEvent event = new EntityInteractEvent(entity(Projectile.class, 4), block(0, Material.OAK_PRESSURE_PLATE));
            fixture.guard.physicalEntity(event);
            assertTrue(event.isCancelled());
            fixture.set("switch", true);
            EntityInteractEvent permitted = new EntityInteractEvent(entity(Projectile.class, 4), block(0, Material.OAK_PRESSURE_PLATE));
            fixture.guard.physicalEntity(permitted);
            assertFalse(permitted.isCancelled());
        }
    }

    @Test
    void lecternBookInsertionUsesContainerAccess() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.container", false, "collaborator.workstation", true))) {
            PlayerInsertLecternBookEvent event = new PlayerInsertLecternBookEvent(player, block(0), null);
            fixture.guard.insertLecternBook(event);
            assertTrue(event.isCancelled());
            fixture.set("container", true);
            PlayerInsertLecternBookEvent permitted = new PlayerInsertLecternBookEvent(player, block(0), null);
            fixture.guard.insertLecternBook(permitted);
            assertFalse(permitted.isCancelled());
        }
    }

    @Test
    void frameInsertionRotationAndRemovalAllUseDecorationAccess() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.decoration", false, "collaborator.entity_interact", true))) {
            for (PlayerItemFrameChangeEvent.ItemFrameChangeAction action : PlayerItemFrameChangeEvent.ItemFrameChangeAction.values()) {
                PlayerItemFrameChangeEvent event = new PlayerItemFrameChangeEvent(player, entity(ItemFrame.class, 0), null, action);
                fixture.guard.itemFrame(event);
                assertTrue(event.isCancelled());
            }
        }
    }

    @Test
    void cauldronLiquidsAndWashingHaveIndependentPermissions() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.bucket", false, "collaborator.modify", true))) {
            for (CauldronLevelChangeEvent.ChangeReason reason : CauldronLevelChangeEvent.ChangeReason.values()) {
                CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(block(0), player, reason, null);
                fixture.guard.cauldron(event);
                boolean liquid = reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY
                        || reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL
                        || reason == CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY
                        || reason == CauldronLevelChangeEvent.ChangeReason.BOTTLE_FILL;
                assertEquals(liquid, event.isCancelled(), reason.name());
            }
            fixture.set("bucket", true);
            fixture.set("modify", false);
            CauldronLevelChangeEvent wash = new CauldronLevelChangeEvent(block(0), player,
                    CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH, null);
            fixture.guard.cauldron(wash);
            assertTrue(wash.isCancelled());
            CauldronLevelChangeEvent natural = new CauldronLevelChangeEvent(block(0), null,
                    CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, null);
            fixture.guard.cauldron(natural);
            assertFalse(natural.isCancelled());
        }
    }

    @Test
    void bedsAndSignEditingHaveDistinctFacilityAndSignPermissions() throws Exception {
        try (Fixture fixture = fixture(Map.of("collaborator.workstation", false, "collaborator.sign", true))) {
            PlayerBedEnterEvent bed = new PlayerBedEnterEvent(player, block(0), PlayerBedEnterEvent.BedEnterResult.OK, null);
            fixture.guard.bed(bed);
            assertEquals(Event.Result.DENY, bed.useBed());
            Sign sign = (Sign) Proxy.newProxyInstance(Sign.class.getClassLoader(), new Class<?>[]{Sign.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getBlock")) return block(0);
                        throw new AssertionError(method.getName());
                    });
            PlayerOpenSignEvent open = new PlayerOpenSignEvent(player, sign, Side.FRONT, PlayerOpenSignEvent.Cause.INTERACT);
            fixture.guard.signOpen(open);
            assertFalse(open.isCancelled());
            fixture.set("sign", false);
            PlayerOpenSignEvent denied = new PlayerOpenSignEvent(player, sign, Side.BACK, PlayerOpenSignEvent.Cause.INTERACT);
            fixture.guard.signOpen(denied);
            assertTrue(denied.isCancelled());
        }
    }

    @Test
    void automationCannotMoveItemsAcrossPlotBoundariesButSamePlotTransfersWork() throws Exception {
        try (Fixture fixture = fixture(Map.of())) {
            for (int sourceX : new int[]{0, 4}) {
                InventoryMoveItemEvent event = new InventoryMoveItemEvent(inventory(sourceX), new TestItemStack(),
                        inventory(4 - sourceX), true);
                fixture.guard.inventoryMove(event);
                assertTrue(event.isCancelled());
            }
            InventoryMoveItemEvent internal = new InventoryMoveItemEvent(inventory(0), new TestItemStack(), inventory(1), true);
            fixture.guard.inventoryMove(internal);
            assertFalse(internal.isCancelled());
            InventoryMoveItemEvent outside = new InventoryMoveItemEvent(inventory(4), new TestItemStack(), inventory(5), true);
            fixture.guard.inventoryMove(outside);
            assertFalse(outside.isCancelled());
        }
    }

    @Test
    void hoppersCannotCollectItemsAcrossPlotBoundaries() throws Exception {
        try (Fixture fixture = fixture(Map.of())) {
            InventoryPickupItemEvent outside = new InventoryPickupItemEvent(inventory(4), entity(Item.class, 0));
            fixture.guard.inventoryPickup(outside);
            assertTrue(outside.isCancelled());
            InventoryPickupItemEvent internal = new InventoryPickupItemEvent(inventory(1), entity(Item.class, 0));
            fixture.guard.inventoryPickup(internal);
            assertFalse(internal.isCancelled());
        }
    }

    @Test
    void automationChecksBothHalvesOfABoundarySpanningDoubleChest() throws Exception {
        try (Fixture fixture = fixture(Map.of())) {
            Inventory spanning = doubleChest(0, 4);
            InventoryMoveItemEvent withdrawal = new InventoryMoveItemEvent(spanning, new TestItemStack(), inventory(1), true);
            fixture.guard.inventoryMove(withdrawal);
            assertTrue(withdrawal.isCancelled());
            InventoryPickupItemEvent insertion = new InventoryPickupItemEvent(spanning, entity(Item.class, 0));
            fixture.guard.inventoryPickup(insertion);
            assertTrue(insertion.isCancelled());
            InventoryMoveItemEvent internal = new InventoryMoveItemEvent(doubleChest(0, 1), new TestItemStack(), inventory(2), true);
            fixture.guard.inventoryMove(internal);
            assertFalse(internal.isCancelled());
        }
    }

    @Test
    void automationTreatsParentAndChildAsSeparateProtectedAreas() throws Exception {
        try (Fixture fixture = fixture(Map.of())) {
            Plot parent = fixture.plot;
            fixture.registry.put(new Plot(parent.id(), parent.world(), parent.owner(), parent.name(), false, 0,
                    Set.of(PlotGeometry.Cell.at(0, 64, 0), PlotGeometry.Cell.at(4, 64, 0)),
                    parent.members(), parent.flags(), null));
            fixture.registry.createSubPlot(parent.id(), UUID.randomUUID(), "Child",
                    new PlotPosition(worldId, 0, 64, 0), new PlotPosition(worldId, 3, 67, 3));
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inventory(0), new TestItemStack(), inventory(4), true);
            fixture.guard.inventoryMove(event);
            assertTrue(event.isCancelled());
        }
    }

    private Fixture fixture(Map<String, Boolean> flags) throws Exception {
        PlotRepository store = new PlotRepository(directory.resolve(UUID.randomUUID() + ".db"));
        store.open();
        PlotRegistry registry = new PlotRegistry(store);
        Plot plot = new Plot(UUID.randomUUID(), worldId, UUID.randomUUID(), "Coverage", false, 0,
                Set.of(PlotGeometry.Cell.at(0, 64, 0)), Map.of(actor, Plot.Role.COLLABORATOR), flags, null);
        registry.put(plot);
        return new Fixture(store, registry, plot, new PlotProtection(registry, null));
    }

    private Location location(int blockX) { return new Location(world, blockX, 64, 0); }

    private Player player(int blockX) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> actor;
                    case "getLocation" -> location(blockX);
                    case "hasPermission" -> false;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private Block block(int blockX) {
        return block(blockX, Material.STONE);
    }

    private Block block(int blockX, Material material) {
        BlockState state = (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(), new Class<?>[]{BlockState.class},
                (proxy, method, arguments) -> { throw new AssertionError(method.getName()); });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> blockX;
                    case "getY" -> 64;
                    case "getZ" -> 0;
                    case "getType" -> material;
                    case "getState" -> state;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private <EntityType extends Entity> EntityType entity(Class<EntityType> type, int blockX) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> location(blockX);
                    case "getShooter" -> player;
                    default -> throw new AssertionError(method.getName());
                }));
    }

    private DamageSource damageSource(Entity entity) {
        return (DamageSource) Proxy.newProxyInstance(DamageSource.class.getClassLoader(), new Class<?>[]{DamageSource.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getCausingEntity")) return entity;
                    throw new AssertionError(method.getName());
                });
    }

    private Inventory inventory(int blockX) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getHolder" -> null;
                    case "getLocation" -> location(blockX);
                    default -> throw new AssertionError(method.getName());
                });
    }

    private Inventory doubleChest(int leftX, int rightX) {
        DoubleChest holder = new DoubleChest(null) {
            @Override public InventoryHolder getLeftSide(boolean useSnapshot) { return storageBlock(leftX); }
            @Override public InventoryHolder getRightSide(boolean useSnapshot) { return storageBlock(rightX); }
        };
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getHolder")) return holder;
                    throw new AssertionError(method.getName());
                });
    }

    private InventoryHolder storageBlock(int blockX) {
        return (InventoryHolder) Proxy.newProxyInstance(BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class, InventoryHolder.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getLocation")) return location(blockX);
                    throw new AssertionError(method.getName());
                });
    }

    private static final class TestItemStack extends ItemStack {
        private final Material material;
        private TestItemStack() { this(Material.STONE); }
        private TestItemStack(Material material) { super(); this.material = material; }
        @Override public Material getType() { return material; }
    }

    private record Fixture(PlotRepository store, PlotRegistry registry, Plot plot, PlotProtection guard) implements AutoCloseable {
        void set(String permission, boolean allowed) throws Exception {
            new PlotEdits(registry).flag(plot, plot.owner(), false, "collaborator." + permission, allowed);
        }

        @Override public void close() throws Exception { store.close(); }
    }
}
