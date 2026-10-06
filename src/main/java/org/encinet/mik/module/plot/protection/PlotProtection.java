package org.encinet.mik.module.plot.protection;

import org.encinet.mik.module.role.RolePermissions;

import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Display;
import org.bukkit.entity.LeashHitch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.block.DoubleChest;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.plot.Plot;
import org.encinet.mik.module.plot.PlotRegistry;
import org.encinet.mik.module.plot.PlotPermission;
import org.encinet.mik.module.vehicle.event.VehicleUseEvent;

import java.util.ArrayList;
import java.util.List;

/** Every ordinary gameplay entry point uses the same plot lookup as editor integrations. */
public final class PlotProtection implements Listener {
    private final PlotRegistry registry;
    private final LanguageService language;

    public PlotProtection(PlotRegistry registry, LanguageService language) {
        this.registry = registry;
        this.language = language;
    }

    boolean allowed(Player player, Block block, String flag) {
        return registry.allowed(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                player.getUniqueId(), RolePermissions.isMember(player),
                RolePermissions.canModerate(player), flag);
    }

    boolean allowed(Player player, World world, int x, int y, int z, String flag) {
        return registry.allowed(world.getUID(), x, y, z, player.getUniqueId(),
                RolePermissions.isMember(player), RolePermissions.canModerate(player), flag);
    }

    private boolean allowed(Player player, Location location, PlotPermission action) {
        return allowed(player, location.getWorld(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ(), action.key());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getBlockPlaced(), "place")) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                if (!allowed(event.getPlayer(), state.getBlock(), "place")) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock(), "break")
                || event.getBlock().getState(false) instanceof InventoryHolder
                && !allowed(event.getPlayer(), event.getBlock(), "container")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        boolean physical = event.getAction() == Action.PHYSICAL;
        PlotPermission action = PlotInteractionActions.block(block.getType(),
                !physical && block.getState(false) instanceof InventoryHolder, physical,
                !physical && event.getItem() != null ? event.getItem().getType() : Material.AIR);
        if (action != null && !allowed(event.getPlayer(), block, action.key())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (action == PlotPermission.SIGN) event.setUseItemInHand(Event.Result.DENY);
        }
        if (physical || event.getItem() == null) return;
        PlotPermission item = PlotInteractionActions.item(event.getItem().getType(), action);
        if (item != null && (!allowed(event.getPlayer(), block, item.key())
                || (item == PlotPermission.IGNITE || item == PlotPermission.PLACE) && !allowed(event.getPlayer(),
                        block.getRelative(event.getBlockFace()), item.key())))
            event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (!allowedInteraction(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {
        if (!allowedInteraction(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void physicalEntity(EntityInteractEvent event) {
        Player player = actor(event.getEntity());
        if (player == null) return;
        PlotPermission permission = PlotInteractionActions.block(event.getBlock().getType(), false, true);
        if (permission != null && !allowed(player, event.getBlock(), permission.key())) event.setCancelled(true);
    }

    private boolean allowedInteraction(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Location location = event.getRightClicked().getLocation();
        Material item = player.getInventory().getItem(event.getHand()).getType();
        PlotPermission extra = PlotInteractionActions.item(item);
        if ((extra == PlotPermission.PLACE || extra == PlotPermission.IGNITE) && !allowed(player, location, extra))
            return false;
        return allowed(player, location, PlotInteractionActions.entity(event.getRightClicked(), item, player.isSneaking()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent event) {
        Block destination = event.getBlock();
        if (!allowed(event.getPlayer(), destination, "bucket")
                || PlotInteractionActions.entityBucket(event.getBucket())
                && !allowed(event.getPlayer(), destination, "place")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void bucketEntity(PlayerBucketEntityEvent event) {
        Location location = event.getEntity().getLocation();
        if (!allowed(event.getPlayer(), location, PlotPermission.BUCKET)
                || !allowed(event.getPlayer(), location, PlotPermission.ANIMAL)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock(), "bucket")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void hangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && !allowedEntityEdit(event.getPlayer(), event.getEntity(),
                PlotEntityEditActions.Operation.PLACE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void hangingBreak(HangingBreakByEntityEvent event) {
        Player actor = actor(event.getRemover());
        if (actor != null && !allowedEntityEdit(actor, event.getEntity(), PlotEntityEditActions.Operation.REMOVE))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void inventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory() instanceof MerchantInventory merchant
                && merchant.getMerchant() instanceof Entity entity
                && !allowed(player, entity.getLocation(), PlotPermission.TRADE)) {
            event.setCancelled(true);
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof Entity entity) {
            if (entity != player && !allowed(player, entity.getLocation(),
                    entity instanceof org.bukkit.entity.AbstractVillager ? PlotPermission.TRADE : PlotPermission.ENTITY_STORAGE))
                event.setCancelled(true);
        } else if (holder instanceof BlockState state && !allowed(player, state.getBlock(), "container")) {
            event.setCancelled(true);
        } else if (holder instanceof DoubleChest chest) {
            for (InventoryHolder side : new InventoryHolder[]{chest.getLeftSide(false), chest.getRightSide(false)}) {
                if (side instanceof BlockState state && !allowed(player, state.getBlock(), "container"))
                    event.setCancelled(true);
            }
        } else if (!(holder instanceof Player) && event.getInventory().getLocation() != null
                && !allowed(player, event.getInventory().getLocation(), holder == null
                        ? PlotPermission.WORKSTATION : PlotPermission.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player
                && !allowed(player, event.getMount().getLocation(), PlotPermission.RIDE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player player = actor(event.getDamageSource().getCausingEntity());
        if (player == null) player = actor(event.getDamager());
        if (player != null && !allowedEntityEdit(player, event.getEntity(), PlotEntityEditActions.Operation.REMOVE))
            event.setCancelled(true);
    }

    private boolean allowedEntityEdit(Player player, Entity entity, PlotEntityEditActions.Operation operation) {
        for (PlotPermission permission : PlotEntityEditActions.required(entity, operation)) {
            if (!allowed(player, entity.getLocation(), permission)) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vehicleDamage(VehicleDamageEvent event) {
        Player player = actor(event.getDamageSource().getCausingEntity());
        if (player == null) player = actor(event.getAttacker());
        if (player != null && !allowedEntityEdit(player, event.getVehicle(), PlotEntityEditActions.Operation.REMOVE))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vehicleDestroy(VehicleDestroyEvent event) {
        Player player = actor(event.getDamageSource().getCausingEntity());
        if (player == null) player = actor(event.getAttacker());
        if (player != null && !allowedEntityEdit(player, event.getVehicle(), PlotEntityEditActions.Operation.REMOVE))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void customVehicle(VehicleUseEvent event) {
        PlotPermission permission = switch (event.getAction()) {
            case CAPTURE -> PlotPermission.DECORATION;
            case SPAWN -> PlotPermission.PLACE;
            case ENTER -> PlotPermission.RIDE;
            case SERVICE -> PlotPermission.ENTITY_INTERACT;
            case REMOVE -> PlotPermission.ENTITY_DAMAGE;
        };
        if (!allowed(event.getPlayer(), event.getLocation(), permission)) event.setCancelled(true);
    }

    private static Player actor(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed primed && primed.getSource() instanceof Player player) return player;
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !allowedEntityEdit(player, event.getEntity(), PlotEntityEditActions.Operation.PLACE))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !allowed(player, event.getItem().getLocation(), PlotPermission.ITEM_PICKUP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (!allowed(event.getPlayer(), event.getPlayer().getLocation(), PlotPermission.ITEM_DROP)
                || !allowed(event.getPlayer(), event.getItemDrop().getLocation(), PlotPermission.ITEM_DROP))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (caught == null) return;
        PlotPermission action = caught instanceof Item ? PlotPermission.ITEM_PICKUP
                : caught instanceof LeashHitch ? PlotPermission.ANIMAL
                : caught instanceof Hanging || caught instanceof ArmorStand || caught instanceof Display
                ? PlotPermission.DECORATION : PlotPermission.ENTITY_INTERACT;
        if (!allowed(event.getPlayer(), caught.getLocation(), action)
                || caught instanceof InventoryHolder && !allowed(event.getPlayer(), caught.getLocation(), PlotPermission.ENTITY_STORAGE))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void tame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player
                && !allowed(player, event.getEntity().getLocation(), PlotPermission.ANIMAL)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void leash(PlayerLeashEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation(), PlotPermission.ANIMAL)
                || !allowed(event.getPlayer(), event.getLeashHolder().getLocation(), PlotPermission.ANIMAL))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void unleash(PlayerUnleashEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation(), PlotPermission.ANIMAL)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void bed(PlayerBedEnterEvent event) {
        if (!allowed(event.getPlayer(), event.getBed(), "workstation")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void flowerPot(PlayerFlowerPotManipulateEvent event) {
        if (!allowed(event.getPlayer(), event.getFlowerpot(), "modify")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void insertLecternBook(PlayerInsertLecternBookEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock(), "container")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void itemFrame(PlayerItemFrameChangeEvent event) {
        if (!allowed(event.getPlayer(), event.getItemFrame().getLocation(), PlotPermission.DECORATION)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void signOpen(PlayerOpenSignEvent event) {
        if (!allowed(event.getPlayer(), event.getSign().getBlock(), "sign")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void cauldron(CauldronLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlotPermission action = switch (event.getReason()) {
            case BUCKET_FILL, BUCKET_EMPTY, BOTTLE_FILL, BOTTLE_EMPTY -> PlotPermission.BUCKET;
            case BANNER_WASH, ARMOR_WASH, SHULKER_WASH -> PlotPermission.MODIFY;
            default -> null;
        };
        if (action != null && !allowed(player, event.getBlock(), action.key())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void inventoryMove(InventoryMoveItemEvent event) {
        for (Location source : inventoryLocations(event.getSource())) {
            for (Location destination : inventoryLocations(event.getDestination())) {
                if (crosses(source, destination)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void inventoryPickup(InventoryPickupItemEvent event) {
        for (Location destination : inventoryLocations(event.getInventory())) {
            if (crosses(event.getItem().getLocation(), destination)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static List<Location> inventoryLocations(Inventory inventory) {
        if (inventory.getHolder(false) instanceof DoubleChest chest) {
            List<Location> locations = new ArrayList<>(2);
            for (InventoryHolder side : new InventoryHolder[]{chest.getLeftSide(false), chest.getRightSide(false)}) {
                if (side instanceof BlockState state) locations.add(state.getLocation());
            }
            return locations;
        }
        Location location = inventory.getLocation();
        return location == null ? List.of() : List.of(location);
    }

    private boolean crosses(Location from, Location to) {
        Plot source = registry.at(from.getWorld().getUID(), from.getBlockX(), from.getBlockY(), from.getBlockZ());
        Plot destination = registry.at(to.getWorld().getUID(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        return (source == null) != (destination == null)
                || source != null && !source.id().equals(destination.id());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (!allowed(event.getPlayer(), event.getRightClicked().getLocation(), PlotPermission.DECORATION))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void shear(PlayerShearEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation(), PlotPermission.ANIMAL)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void harvest(PlayerHarvestBlockEvent event) {
        if (!allowed(event.getPlayer(), event.getHarvestedBlock(), "harvest")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void sign(SignChangeEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock(), "sign")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void lectern(PlayerTakeLecternBookEvent event) {
        if (!allowed(event.getPlayer(), event.getLectern().getBlock(), "container")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent event) {
        if (event.getPlayer() != null && !allowed(event.getPlayer(), event.getBlock(), "ignite")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent event) {
        if (event.getPlayer() == null) return;
        for (BlockState state : event.getBlocks()) {
            if (!allowed(event.getPlayer(), state.getBlock(), "modify")) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void grow(StructureGrowEvent event) {
        if (event.getPlayer() == null) return;
        for (BlockState state : event.getBlocks()) {
            if (!allowed(event.getPlayer(), state.getBlock(), "modify")) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entityChange(EntityChangeBlockEvent event) {
        if (registry.at(event.getBlock().getWorld().getUID(), event.getBlock().getX(),
                event.getBlock().getY(), event.getBlock().getZ()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (registry.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fluid(BlockFromToEvent event) {
        Block target = event.getToBlock();
        Plot from = registry.at(event.getBlock().getWorld().getUID(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        Plot to = registry.at(target.getWorld().getUID(), target.getX(), target.getY(), target.getZ());
        if (to != null && (from == null || !from.id().equals(to.id()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (crosses(block, block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (crosses(block, block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
        }
    }

    private boolean crosses(Block from, Block to) {
        Plot a = registry.at(from.getWorld().getUID(), from.getX(), from.getY(), from.getZ());
        Plot b = registry.at(to.getWorld().getUID(), to.getX(), to.getY(), to.getZ());
        return (a == null) != (b == null) || (a != null && !a.id().equals(b.id()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    private boolean protectedBlock(Block block) {
        return registry.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()) != null;
    }
}
