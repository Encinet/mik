package org.encinet.mik.module.player;

import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public class PlayerBoundaryModule implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final double EPSILON = 0.01;
    private static final double BOUNDARY_MARGIN = 0.5 + EPSILON;
    private static final long MOUNT_CHECK_INTERVAL_TICKS = 5L;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private BukkitTask mountCheckTask;

    public PlayerBoundaryModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        mountCheckTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::checkMountedPlayers, MOUNT_CHECK_INTERVAL_TICKS, MOUNT_CHECK_INTERVAL_TICKS);
        plugin.getLogger().info("PlayerBoundaryModule enabled");
    }

    public void disable() {
        if (mountCheckTask != null) {
            mountCheckTask.cancel();
            mountCheckTask = null;
        }
    }

    private void correctIfOutside(Location loc, Player player,
                                  Consumer<Location> setter) {
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(loc)) {
            setter.accept(clampInside(loc, border));
            player.sendActionBar(buildMessage(player, Message.BOUNDARY_REACHED_MM));
        }
    }

    private Location clampInside(Location loc, WorldBorder border) {
        Location center = border.getCenter();
        double half = Math.max(0, border.getSize() / 2.0 - BOUNDARY_MARGIN);

        double x = clampCoordinate(loc.getX(), center.getX(), half);
        double z = clampCoordinate(loc.getZ(), center.getZ(), half);

        return new Location(loc.getWorld(), x, loc.getY(), z, loc.getYaw(), loc.getPitch());
    }

    static double clampCoordinate(double coordinate, double center, double halfSize) {
        return Math.clamp(coordinate, center - halfSize, center + halfSize);
    }

    private Component buildMessage(Player player, Message msg) {
        return MINI_MESSAGE.deserialize(languageService.t(player, msg));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        World world = to.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        boolean fromOutside = !border.isInside(from);
        boolean toOutside = !border.isInside(to);

        if (!fromOutside && !toOutside) return;

        if (fromOutside) {
            // 已在外面 直接拉回
            event.setTo(clampInside(to, border));
        } else {
            // 试图出去 取消移动
            event.setCancelled(true);
        }
        player.sendActionBar(buildMessage(player, Message.BOUNDARY_NEAR_MM));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        Set<Player> passengers = playerPassengers(event.getVehicle());
        if (passengers.isEmpty()) return;

        correctMountedGroup(event.getVehicle(), event.getTo(), passengers);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMountedEntityMove(EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Vehicle mount)) return;

        Set<Player> passengers = playerPassengers(mount);
        if (passengers.isEmpty()) return;

        Location to = event.getTo();
        World world = to.getWorld();
        if (world == null || world.getWorldBorder().isInside(to)) return;

        event.setTo(clampInside(to, world.getWorldBorder()));
        mount.setVelocity(new Vector());
        warn(passengers, Message.BOUNDARY_NEAR_MM);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMountedEntityTeleport(EntityTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Set<Player> passengers = playerPassengers(event.getEntity());
        if (passengers.isEmpty()) return;

        World world = to.getWorld();
        if (world == null || world.getWorldBorder().isInside(to)) return;

        event.setTo(clampInside(to, world.getWorldBorder()));
        event.getEntity().setVelocity(new Vector());
        warn(passengers, Message.BOUNDARY_REACHED_MM);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();

        correctIfOutside(to, event.getPlayer(), event::setTo);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location respawn = event.getRespawnLocation();

        correctIfOutside(respawn, event.getPlayer(),
                event::setRespawnLocation);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        correctIfOutside(loc, player,
                player::teleport);
    }

    private void checkMountedPlayers() {
        Set<Entity> checkedRoots = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Entity root = rootVehicle(player);
            if (root == player || !checkedRoots.add(root)) continue;

            correctMountedGroup(root, root.getLocation(), playerPassengers(root));
        }
    }

    private void correctMountedGroup(Entity vehicle, Location location, Set<Player> passengers) {
        if (passengers.isEmpty()) return;

        Entity root = rootVehicle(vehicle);
        Location rootLocation = root == vehicle ? location : root.getLocation();
        World world = rootLocation.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        Player outsidePassenger = passengers.stream()
                .filter(player -> player.getWorld().equals(world))
                .filter(player -> !border.isInside(player.getLocation()))
                .findFirst()
                .orElse(null);
        boolean rootOutside = !border.isInside(rootLocation);
        if (!rootOutside && outsidePassenger == null) return;

        Location destination;
        if (outsidePassenger != null) {
            Location playerLocation = outsidePassenger.getLocation();
            Location correctedPlayer = clampInside(playerLocation, border);
            destination = rootLocation.clone().add(
                    correctedPlayer.getX() - playerLocation.getX(),
                    0.0D,
                    correctedPlayer.getZ() - playerLocation.getZ());
            destination = clampInside(destination, border);
        } else {
            destination = clampInside(rootLocation, border);
        }

        root.setVelocity(new Vector());
        if (!root.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            recoverPassengers(passengers);
        }
        warn(passengers, Message.BOUNDARY_NEAR_MM);
    }

    private void recoverPassengers(Set<Player> passengers) {
        for (Player player : passengers) {
            player.leaveVehicle();
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world != null) {
                player.teleport(clampInside(location, world.getWorldBorder()),
                        PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    private void warn(Set<Player> players, Message message) {
        for (Player player : players) {
            player.sendActionBar(buildMessage(player, message));
        }
    }

    private Set<Player> playerPassengers(Entity entity) {
        Set<Player> players = new LinkedHashSet<>();
        collectPlayerPassengers(entity, players);
        return players;
    }

    private void collectPlayerPassengers(Entity entity, Set<Player> players) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                players.add(player);
            }
            collectPlayerPassengers(passenger, players);
        }
    }

    private Entity rootVehicle(Entity entity) {
        Entity root = entity;
        while (root.getVehicle() != null) {
            root = root.getVehicle();
        }
        return root;
    }
}
