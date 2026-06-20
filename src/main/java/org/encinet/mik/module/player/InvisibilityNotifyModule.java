package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class InvisibilityNotifyModule {

    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final double NOTIFY_RADIUS = 24.0D;
    private static final double NOTIFY_RADIUS_SQUARED = NOTIFY_RADIUS * NOTIFY_RADIUS;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private BukkitTask notifyTask;

    public InvisibilityNotifyModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        notifyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::notifyNearbyPlayers,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        plugin.getLogger().info("InvisibilityNotifyModule enabled");
    }

    public void disable() {
        if (notifyTask != null) {
            notifyTask.cancel();
            notifyTask = null;
        }
    }

    private void notifyNearbyPlayers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Detection detection = detectNearbyInvisiblePlayers(viewer);
            if (detection.count() > 0) {
                viewer.sendActionBar(actionBarMessage(detection));
            }
        }
    }

    private Detection detectNearbyInvisiblePlayers(Player viewer) {
        World world = viewer.getWorld();
        int count = 0;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Player target : world.getPlayers()) {
            if (target.equals(viewer) || !target.isInvisible() || !viewer.canSee(target)) {
                continue;
            }

            double distanceSquared = viewer.getLocation().distanceSquared(target.getLocation());
            if (distanceSquared > NOTIFY_RADIUS_SQUARED) {
                continue;
            }

            count++;
            nearestDistanceSquared = Math.min(nearestDistanceSquared, distanceSquared);
        }

        int nearestDistance = count == 0 ? 0 : (int) Math.round(Math.sqrt(nearestDistanceSquared));
        return new Detection(count, nearestDistance);
    }

    private Component actionBarMessage(Detection detection) {
        String template = detection.count() == 1
                ? "<yellow>附近有玩家处于 <gold>隐身</gold> 状态</yellow> <gray>·</gray> <gray>约 <distance> 格内</gray>"
                : "<yellow>附近有 <gold><count></gold> 名玩家处于 <gold>隐身</gold> 状态</yellow> <gray>·</gray> <gray>最近约 <distance> 格</gray>";
        return MINI_MESSAGE.deserialize(template,
                Placeholder.unparsed("count", Integer.toString(detection.count())),
                Placeholder.unparsed("distance", Integer.toString(detection.nearestDistance())));
    }

    private record Detection(int count, int nearestDistance) {
    }
}
