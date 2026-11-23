package org.encinet.mik.feature.world.buildings;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.LanguageService;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Synchronizes the public building catalog independently of the optional Pl3xMap layer. */
public final class BuildingMapModule {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final Supplier<BuildingCatalogSource> sources;
    private BuildingMapPublisher publisher;
    private CatalogSync sync;
    private BukkitTask publishTask;
    private boolean publishFailed;

    public BuildingMapModule(JavaPlugin plugin, LanguageService languages) {
        this(plugin, languages, () -> new BuildingCatalogSource(
                plugin.getDataFolder().toPath().resolve("building-map/catalog.json")));
    }

    BuildingMapModule(JavaPlugin plugin, LanguageService languages, Supplier<BuildingCatalogSource> sources) {
        this.plugin = plugin;
        this.languages = languages;
        this.sources = sources;
    }

    public void enable() {
        if (sync != null) return;
        sync = new CatalogSync(sources.get());
        enablePublisher();
        sync.start(publisher);
    }

    private void enablePublisher() {
        try {
            var pl3xmap = plugin.getServer().getPluginManager().getPlugin("Pl3xMap");
            if (pl3xmap == null || !pl3xmap.isEnabled()) return;
            publisher = new BuildingMapPublisher(languages);
            publishTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::publish, 1, 200);
            plugin.getLogger().info("Pl3xMap building catalog layer enabled; refresh interval is 5 minutes.");
        } catch (RuntimeException | LinkageError error) {
            disablePublisher();
            plugin.getLogger().warning("Building map integration is unavailable: " + error);
        }
    }

    public Supplier<Optional<NearbyBuildings>> prepareNearby(Player player, NearbyBuildings.Search query) {
        BuildingCatalogSnapshot current = sync == null ? null : sync.snapshot;
        if (current == null) return Optional::empty;
        var location = player.getLocation();
        boolean chinese = switch (languages.language(player)) {
            case ZH_CN, ZH_HK, ZH_TW, LZH -> true;
            default -> false;
        };
        var world = location.getWorld().getName();
        double playerX = location.getX(), playerY = location.getY(), playerZ = location.getZ();
        return () -> current.nearby(world, playerX, playerY, playerZ, chinese, query, System.nanoTime());
    }

    private void publish() {
        BuildingCatalogSnapshot current = sync == null ? null : sync.snapshot;
        if (publisher == null || current == null) return;
        try {
            publisher.publish(current.catalog());
            publishFailed = false;
        } catch (RuntimeException | LinkageError error) {
            if (!publishFailed) plugin.getLogger().warning("Cannot publish building map layer; will retry: " + error);
            publishFailed = true;
        }
    }

    public void disable() {
        CatalogSync current = sync;
        sync = null;
        try {
            if (current != null) current.close();
        } finally {
            disablePublisher();
        }
    }

    private void disablePublisher() {
        if (publishTask != null) {
            publishTask.cancel();
            publishTask = null;
        }
        if (publisher != null) {
            try {
                publisher.close();
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Cannot remove the building map layer: " + error);
            }
            publisher = null;
        }
        publishFailed = false;
    }

    private final class CatalogSync implements AutoCloseable {
        private final BuildingCatalogSource source;
        private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("mik-building-map").daemon(true).factory());
        private volatile boolean active = true;
        private volatile BuildingCatalogSnapshot snapshot;
        private boolean iconFailed;
        private boolean downloadFailed;

        CatalogSync(BuildingCatalogSource source) { this.source = source; }

        void start(BuildingMapPublisher map) {
            worker.execute(this::loadCache);
            if (map != null) worker.scheduleWithFixedDelay(() -> prepareIcon(map), 0, 10, TimeUnit.SECONDS);
            worker.scheduleWithFixedDelay(this::refresh, 0, BuildingCatalogSnapshot.REFRESH_MINUTES, TimeUnit.MINUTES);
        }

        private void prepareIcon(BuildingMapPublisher map) {
            if (!active) return;
            try {
                map.prepareIcon();
                iconFailed = false;
            } catch (RuntimeException | LinkageError error) {
                if (!iconFailed && active) plugin.getLogger().warning("Cannot prepare building map icon: " + error);
                iconFailed = true;
            }
        }

        private void loadCache() {
            if (!active) return;
            try {
                BuildingCatalog cached = source.cached();
                if (active && cached != null) snapshot = new BuildingCatalogSnapshot(cached);
            } catch (IOException | RuntimeException error) {
                if (active) plugin.getLogger().warning("Ignoring invalid building map cache: " + error);
            }
        }

        private void refresh() {
            if (!active) return;
            try {
                var download = source.fetch();
                if (!active) return;
                var previous = snapshot;
                boolean changed = previous == null || !download.catalog().equals(previous.catalog());
                var current = changed ? new BuildingCatalogSnapshot(download.catalog()) : previous;
                snapshot = current.verified(System.nanoTime());
                if (changed || downloadFailed) {
                    plugin.getLogger().info("Building map catalog synchronized: " + current.catalog().entries().size() + " buildings.");
                }
                downloadFailed = false;
                try {
                    // Saving does not gate publication: a read-only disk must not hide valid data.
                    source.save(download);
                } catch (IOException | RuntimeException error) {
                    if (active) plugin.getLogger().warning("Cannot save building map catalog cache: " + error);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException error) {
                if (snapshot != null) snapshot = snapshot.unverified();
                if (!downloadFailed && active) {
                    plugin.getLogger().warning("Building map sync failed; keeping the last valid map catalog: " + error);
                }
                downloadFailed = true;
            }
        }

        @Override
        public void close() {
            active = false;
            worker.shutdownNow();
            source.close();
        }
    }
}
