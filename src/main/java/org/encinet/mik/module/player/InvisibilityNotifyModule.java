package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

public class InvisibilityNotifyModule {

    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final double NOTIFY_RADIUS = 24.0D;
    private static final double NOTIFY_RADIUS_SQUARED = NOTIFY_RADIUS * NOTIFY_RADIUS;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private BukkitTask notifyTask;

    public InvisibilityNotifyModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
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
                viewer.sendActionBar(actionBarMessage(viewer, detection));
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

    private Component actionBarMessage(Player viewer, Detection detection) {
        String template = languageService.t(viewer, detection.count() == 1
                ? Message.INVISIBILITY_SINGLE_MM
                : Message.INVISIBILITY_MULTI_MM);
        return MINI_MESSAGE.deserialize(template,
                Placeholder.unparsed("count", Integer.toString(detection.count())),
                Placeholder.unparsed("distance", Integer.toString(detection.nearestDistance())));
    }

    private record Detection(int count, int nearestDistance) {
    }
}
