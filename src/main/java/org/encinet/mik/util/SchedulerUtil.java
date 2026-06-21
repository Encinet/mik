package org.encinet.mik.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    public static void runSync(JavaPlugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
