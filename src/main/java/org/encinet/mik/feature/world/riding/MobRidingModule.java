package org.encinet.mik.feature.world.riding;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Owns only mounts started by the sneak/right-click gesture. All state is main-thread confined. */
public final class MobRidingModule implements Listener {
    private final JavaPlugin plugin;
    private final BiPredicate<Player, Location> mayRide;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private BukkitTask task;

    public MobRidingModule(JavaPlugin plugin, BiPredicate<Player, Location> mayRide) {
        this.plugin = plugin;
        this.mayRide = mayRide;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void disable() {
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        for (Ride ride : List.copyOf(rides.values())) stop(ride, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        onInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || !eligible(player) || player.isInsideVehicle() || rides.containsKey(player.getUniqueId())
                || !(event.getRightClicked() instanceof Mob mob) || !mob.isValid() || mob.isDead()
                || mob.isInsideVehicle() || !mob.getPassengers().isEmpty()) return;
        if (!mayControl(player, mob)) {
            event.setCancelled(true);
            return;
        }
        if (!mayRide.test(player, mob.getLocation())) return;
        Ride ride = new Ride(player, mob);
        if (!mob.addPassenger(player)) return;
        rides.put(player.getUniqueId(), ride);
        mob.setAware(false);
        event.setCancelled(true);
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Ride ride = rides.get(event.getPlayer().getUniqueId());
        if (ride != null && !event.getInput().isSneak()) ride.awaitingSneakRelease = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInitialDismount(EntityDismountEvent event) {
        Ride ride = rides.get(event.getEntity().getUniqueId());
        // The gesture still holds Shift on the first mounted tick. Require a release before dismounting.
        if (ride != null && event.getDismounted().equals(ride.mob) && ride.awaitingSneakRelease
                && ride.player.getCurrentInput().isSneak() && eligible(ride.player)
                && ride.mob.isValid() && !ride.mob.isDead()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Ride ride = rides.get(event.getEntity().getUniqueId());
        if (ride != null && event.getDismounted().equals(ride.mob)) stop(ride, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Ride ride = rides.get(event.getPlayer().getUniqueId());
        if (ride != null) stop(ride, true);
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        for (Ride ride : List.copyOf(rides.values())) {
            if (event.getEntity().equals(ride.mob) || event.getEntity().equals(ride.player)) stop(ride, true);
        }
    }

    void tick() {
        for (Ride ride : List.copyOf(rides.values())) {
            Player player = ride.player;
            Mob mob = ride.mob;
            if (!eligible(player) || !mob.isValid() || mob.isDead() || !mob.equals(player.getVehicle())
                    || !mayControl(player, mob) || !mayRide.test(player, mob.getLocation())) {
                stop(ride, true);
                continue;
            }
            Input input = player.getCurrentInput();
            if (!input.isSneak()) ride.awaitingSneakRelease = false;
            boolean flying = mob instanceof Ghast || mob instanceof HappyGhast || mob instanceof Phantom
                    || mob instanceof Bee || mob instanceof Allay || mob instanceof Vex || mob instanceof Wither;
            Vector movement = movement(input, player.getYaw(), player.getPitch(), flying);
            double vertical = mob.getVelocity().getY();
            if (flying) vertical = movement.getY();
            else if (input.isJump() && mob.isOnGround()) vertical = 0.42;
            else if (mob.isInWater() && input.isJump()) vertical = 0.25;
            mob.setRotation(player.getYaw(), flying ? player.getPitch() : 0);
            mob.setVelocity(movement.setY(vertical));
        }
    }

    static Vector movement(Input input, float yaw, float pitch, boolean flying) {
        double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double sideways = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
        double angle = Math.toRadians(yaw);
        double tilt = flying ? Math.toRadians(pitch) : 0;
        Vector direction = new Vector(-Math.sin(angle) * Math.cos(tilt) * forward + Math.cos(angle) * sideways,
                -Math.sin(tilt) * forward, Math.cos(angle) * Math.cos(tilt) * forward + Math.sin(angle) * sideways);
        if (flying && input.isJump()) direction.setY(direction.getY() + 1);
        if (direction.lengthSquared() > 1) direction.normalize();
        return direction.multiply(input.isSprint() ? 0.32 : 0.22);
    }

    private static boolean eligible(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && player.hasPermission("mik.riding.use");
    }

    private static boolean mayControl(Player player, Mob mob) {
        if (!(mob instanceof Tameable tameable)) return true;
        UUID owner = tameable.getOwnerUniqueId();
        return owner == null || owner.equals(player.getUniqueId());
    }

    private void stop(Ride ride, boolean dismount) {
        if (!rides.remove(ride.player.getUniqueId(), ride)) return;
        ride.mob.setAware(ride.aware);
        if (dismount && ride.mob.equals(ride.player.getVehicle())) ride.mob.removePassenger(ride.player);
    }

    private static final class Ride {
        final Player player;
        final Mob mob;
        final boolean aware;
        boolean awaitingSneakRelease = true;

        Ride(Player player, Mob mob) {
            this.player = player;
            this.mob = mob;
            aware = mob.isAware();
        }
    }
}
