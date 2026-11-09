package org.encinet.mik.feature.world.buildings;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.LanguageService;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Publishes the public, reviewed building catalog when Pl3xMap is installed. */
public final class BuildingMapModule {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private BuildingMapPublisher publisher;
    private BuildingCatalogSource source;
    private ScheduledExecutorService worker;
    private BukkitTask publishTask;
    private volatile boolean active;
    private volatile BuildingCatalog snapshot;
    private boolean cacheLoaded;
    private boolean downloadFailed;
    private boolean iconFailed;
    private boolean publishFailed;
    private long nextRefresh;

    public BuildingMapModule(JavaPlugin plugin, LanguageService languages) {
        this.plugin = plugin;
        this.languages = languages;
    }

    public void enable() {
        if (active) return;
        var pl3xmap = plugin.getServer().getPluginManager().getPlugin("Pl3xMap");
        if (pl3xmap == null || !pl3xmap.isEnabled()) return;
        try {
            publisher = new BuildingMapPublisher(languages);
            source = new BuildingCatalogSource(plugin.getDataFolder().toPath().resolve("building-map/catalog.json"));
            worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                    .name("mik-building-map").daemon(true).factory());
            active = true;
            publishTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::publish, 1, 200);
            worker.scheduleWithFixedDelay(this::refresh, 0, 10, TimeUnit.SECONDS);
            plugin.getLogger().info("Pl3xMap building catalog layer enabled; refresh interval is 5 minutes.");
        } catch (RuntimeException | LinkageError error) {
            disable();
            plugin.getLogger().warning("Building map integration is unavailable: " + error);
        }
    }

    private void refresh() {
        if (!active) return;
        try {
            publisher.prepareIcon();
            iconFailed = false;
        } catch (RuntimeException | LinkageError error) {
            if (!iconFailed && active) plugin.getLogger().warning("Cannot prepare building map icon: " + error);
            iconFailed = true;
        }
        if (!cacheLoaded) {
            cacheLoaded = true;
            try {
                BuildingCatalog cached = source.cached();
                if (active && cached != null) snapshot = cached;
            } catch (IOException | RuntimeException error) {
                if (active) plugin.getLogger().warning("Ignoring invalid building map cache: " + error);
            }
        }
        long now = System.nanoTime();
        if (nextRefresh != 0 && now - nextRefresh < 0) return;
        nextRefresh = now + TimeUnit.MINUTES.toNanos(5);
        try {
            var download = source.fetch();
            if (!active) return;
            boolean changed = !download.catalog().equals(snapshot);
            snapshot = download.catalog();
            if (changed || downloadFailed) {
                plugin.getLogger().info("Building map catalog synchronized: " + snapshot.entries().size() + " buildings.");
            }
            downloadFailed = false;
            try {
                // Saving does not gate publication: a read-only disk must not hide valid data.
                source.save(download);
            } catch (IOException error) {
                if (active) plugin.getLogger().warning("Cannot save building map catalog cache: " + error);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException error) {
            if (!downloadFailed && active) {
                plugin.getLogger().warning("Building map sync failed; keeping the last valid catalog: " + error);
            }
            downloadFailed = true;
        }
    }

    private void publish() {
        BuildingCatalog current = snapshot;
        if (!active || current == null) return;
        try {
            publisher.publish(current);
            publishFailed = false;
        } catch (RuntimeException | LinkageError error) {
            if (!publishFailed) plugin.getLogger().warning("Cannot publish building map layer; will retry: " + error);
            publishFailed = true;
        }
    }

    public void disable() {
        active = false;
        if (publishTask != null) {
            publishTask.cancel();
            publishTask = null;
        }
        if (worker != null) worker.shutdownNow();
        if (source != null) source.close();
        if (publisher != null) {
            try {
                publisher.close();
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Cannot remove the building map layer: " + error);
            }
        }
        snapshot = null;
    }
}
