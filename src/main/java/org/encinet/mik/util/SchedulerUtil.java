package org.encinet.mik.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Scheduler utility methods
 */
public final class SchedulerUtil {

    private SchedulerUtil() {
        // Utility class
    }

    /**
     * Run a task synchronously on the main thread
     *
     * @param plugin the plugin instance
     * @param task the task to run
     */
    public static void runSync(JavaPlugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
