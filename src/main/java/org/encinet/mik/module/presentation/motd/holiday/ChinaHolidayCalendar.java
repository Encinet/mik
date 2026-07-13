package org.encinet.mik.module.presentation.motd.holiday;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ChinaHolidayCalendar {

    private static final String HOLIDAY_YEAR_URL =
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/";
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 60 * 24;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final JavaPlugin plugin;
    private final File cacheFile;
    private final HttpClient httpClient;
    private volatile List<ChinaHolidayEvent> events = List.of();
    private volatile Instant lastFetchedAt = Instant.EPOCH;
    private volatile int cachedYear = -1;
    private BukkitTask refreshTask;
    private Runnable refreshListener = () -> {};

    public ChinaHolidayCalendar(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "holiday-motd-cache.yml");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void enable() {
        loadCache();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshIfStale);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::refreshIfStale, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    public void disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void setRefreshListener(Runnable refreshListener) {
        this.refreshListener = refreshListener != null ? refreshListener : () -> {};
    }

    public Optional<HolidayOccurrence> resolve(LocalDate date) {
        return events.stream()
                .filter(event -> event.includes(date))
                .findFirst()
                .map(event -> new HolidayOccurrence(event.summary()));
    }

    private void refreshIfStale() {
        int currentYear = LocalDate.now(SHANGHAI).getYear();
        if (cachedYear != currentYear || events.isEmpty()) {
            refresh(currentYear);
        }
    }

    private void refresh(int year) {
        try {
            events = List.copyOf(fetchYear(year));
            cachedYear = year;
            lastFetchedAt = Instant.now();
            saveCache();
            Bukkit.getScheduler().runTask(plugin, refreshListener);
            plugin.getLogger().info("China holiday MOTD calendar refreshed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh China holiday MOTD calendar: " + e.getMessage());
        }
    }

    private List<ChinaHolidayEvent> fetchYear(int year) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(HOLIDAY_YEAR_URL + year + ".json"))
                .header("User-Agent", "MIK Minecraft server")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("holiday-cn responded with HTTP " + response.statusCode());
        }
        return HolidayCnParser.parseYearResponse(response.body());
    }

    private void loadCache() {
        if (!cacheFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(cacheFile);
        cachedYear = config.getInt("year", -1);
        String fetchedAt = config.getString("fetched-at");
        if (fetchedAt != null && !fetchedAt.isBlank()) {
            try {
                lastFetchedAt = Instant.parse(fetchedAt);
            } catch (RuntimeException ignored) {
                lastFetchedAt = Instant.EPOCH;
            }
        }
        events = List.copyOf(loadEvents(config, "events.cn"));
    }

    private List<ChinaHolidayEvent> loadEvents(YamlConfiguration config, String path) {
        List<ChinaHolidayEvent> loaded = new ArrayList<>();
        for (Map<?, ?> eventMap : config.getMapList(path)) {
            Object start = eventMap.get("start");
            Object end = eventMap.get("end");
            Object summary = eventMap.get("summary");
            if (start == null || end == null || summary == null) continue;
            try {
                loaded.add(new ChinaHolidayEvent(
                        LocalDate.parse(String.valueOf(start)),
                        LocalDate.parse(String.valueOf(end)),
                        String.valueOf(summary)
                ));
            } catch (RuntimeException ignored) {
            }
        }
        return loaded;
    }

    private void saveCache() throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Failed to create plugin data folder");
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("fetched-at", lastFetchedAt.toString());
        config.set("year", cachedYear);
        config.set("events.cn", dumpEvents(events));
        config.save(cacheFile);
    }

    private List<Map<String, String>> dumpEvents(List<ChinaHolidayEvent> source) {
        List<Map<String, String>> dumped = new ArrayList<>();
        for (ChinaHolidayEvent event : source) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("start", event.start().toString());
            value.put("end", event.endExclusive().toString());
            value.put("summary", event.summary());
            dumped.add(value);
        }
        return dumped;
    }
}
