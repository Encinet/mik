package org.encinet.mik.feature.player.presence;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.TextArg;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
        detectNearbyInvisiblePlayers(Bukkit.getOnlinePlayers()).forEach((viewer, detection) ->
                viewer.sendActionBar(actionBarMessage(viewer, detection)));
    }

    static Map<Player, Detection> detectNearbyInvisiblePlayers(Collection<? extends Player> players) {
        Map<Player, Detection> detections = new HashMap<>();
        for (Player target : players) {
            if (!target.isInvisible()) continue;
            Location location = target.getLocation();
            // Ask the world's entity index only around invisible players. With
            // none present, the pass needs no location or neighbourhood queries.
            for (Player viewer : target.getWorld().getNearbyPlayers(location, NOTIFY_RADIUS)) {
                if (target.equals(viewer) || !viewer.canSee(target)) continue;
                double distanceSquared = viewer.getLocation().distanceSquared(location);
                if (distanceSquared > NOTIFY_RADIUS_SQUARED) continue;
                Detection previous = detections.get(viewer);
                detections.put(viewer, new Detection(previous == null ? 1 : previous.count() + 1,
                        previous == null ? distanceSquared
                                : Math.min(previous.nearestDistanceSquared(), distanceSquared)));
            }
        }
        return detections;
    }

    private Component actionBarMessage(Player viewer, Detection detection) {
        String template = languageService.format(viewer, Message.INVISIBILITY_NEARBY_MM,
                TextArg.of("count", detection.count()),
                TextArg.of("distance", detection.nearestDistance()));
        return MINI_MESSAGE.deserialize(template);
    }

    record Detection(int count, double nearestDistanceSquared) {
        int nearestDistance() {
            return (int) Math.round(Math.sqrt(nearestDistanceSquared));
        }
    }
}
