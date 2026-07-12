package org.encinet.mik.module.presentation.motd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.Language;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class HolidayMotdCategory {

    private static final String HOLIDAY_YEAR_URL =
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/";
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60 * 60 * 24;

    private static final String[] GENERIC_TEMPLATES_CN = {
            "<gradient:#f7971e:#ffd200>{holiday}快乐！愿今天轻松一点</gradient>",
            "<gold><bold>{holiday}快乐！</bold></gold><white> 祝你摸鱼顺利，心情不错</white>",
            "<gradient:#ff9a9e:#fad0c4>今天是 {holiday}，祝你玩得开心</gradient>",
            "<gradient:#84fab0:#8fd3f4>又可以放假了~</gradient><white> {holiday}快乐！</white>"
    };

    private static final String[] NATIONAL_MID_AUTUMN_TEMPLATES_CN = {
            "<gradient:#de6262:#ffb88c>国庆中秋双节快乐！愿假期慢一点、月色亮一点</gradient>",
            "<red><bold>国庆快乐！</bold></red><gold> 中秋快乐！祝你假期愉快</gold>",
            "<gradient:#f7971e:#ffd200>双节快乐，愿今天团圆、放松、开心摸鱼</gradient>"
    };

    private static final Map<String, String[]> TEMPLATES_CN = Map.ofEntries(
            Map.entry("春节", new String[]{
                    "<gradient:#ff512f:#f09819><bold>春节快乐！</bold></gradient><white> 新年也要开心摸鱼</white>",
                    "<red><bold>春节快乐！</bold></red><gold> 祝你欧气满满</gold>",
                    "<gradient:#f7971e:#ffd200>新春快乐，愿你今年顺顺利利</gradient>"
            }),
            Map.entry("除夕", new String[]{
                    "<gradient:#ff512f:#dd2476>除夕快乐，今晚适合守岁和团圆</gradient>",
                    "<gold><bold>除夕快乐！</bold></gold><white> 年夜饭后见</white>"
            }),
            Map.entry("元旦", new String[]{
                    "<gradient:#00c6ff:#0072ff>元旦快乐！新的一年继续闪闪发光</gradient>",
                    "<aqua><bold>新年快乐！</bold></aqua><white> 从一个轻松的早晨开始</white>"
            }),
            Map.entry("清明", new String[]{
                    "<gradient:#bdc3c7:#2c3e50>清明时节，愿你安好</gradient>"
            }),
            Map.entry("劳动", new String[]{
                    "<gradient:#56ab2f:#a8e063>劳动节快乐！今天也可以合法摸鱼</gradient>",
                    "<green><bold>劳动节快乐！</bold></green><white> 辛苦了，来休息一下</white>"
            }),
            Map.entry("端午", new String[]{
                    "<gradient:#11998e:#38ef7d>端午安康！记得吃粽子</gradient>",
                    "<green><bold>端午安康！</bold></green><white> 今天适合划船，也适合摸鱼</white>"
            }),
            Map.entry("中秋", new String[]{
                    "<gradient:#f7971e:#ffd200>中秋快乐，愿月色和心情都很好</gradient>",
                    "<gold><bold>中秋快乐！</bold></gold><white> 今天适合团圆，也适合放松</white>"
            }),
            Map.entry("国庆", new String[]{
                    "<gradient:#de6262:#ffb88c>国庆快乐！愿假期慢一点、开心多一点</gradient>",
                    "<red><bold>国庆快乐！</bold></red><gold> 祝你假期愉快</gold>"
            }),
            Map.entry("圣诞", new String[]{
                    "<gradient:#00b09b:#96c93d>圣诞快乐！愿今天有一点惊喜</gradient>",
                    "<red><bold>圣诞快乐！</bold></red><white> 今天适合下雪和摸鱼</white>"
            }),
            Map.entry("情人", new String[]{
                    "<gradient:#ff758c:#ff7eb3>情人节快乐！也祝一个人过得自在</gradient>"
            }),
            Map.entry("元宵", new String[]{
                    "<gradient:#f7971e:#ffd200>元宵快乐，愿今晚灯火温柔</gradient>"
            }),
            Map.entry("儿童", new String[]{
                    "<gradient:#89f7fe:#66a6ff>儿童节快乐！保持一点幼稚也不错</gradient>"
            }),
            Map.entry("教师", new String[]{
                    "<gradient:#667eea:#764ba2>教师节快乐，感谢每一位认真教人的人</gradient>"
            })
    );

    private final JavaPlugin plugin;
    private final File cacheFile;
    private final HttpClient httpClient;
    private volatile List<HolidayEvent> holidayEvents = List.of();
    private volatile Instant lastFetchedAt = Instant.EPOCH;
    private volatile int cachedYear = -1;
    private BukkitTask refreshTask;
    private Runnable refreshListener = () -> {};

    public HolidayMotdCategory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "holiday-motd-cache.yml");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void enable() {
        loadCache();
        refreshAsync();
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

    public Optional<String> resolveLine(LocalDate today, Language language, ThreadLocalRandom rng) {
        if (language != Language.ZH_CN) {
            return Optional.empty();
        }
        return holidayEvents.stream()
                .filter(event -> event.includes(today))
                .findFirst()
                .map(event -> renderHolidayLine(event.summary(), rng));
    }

    private String renderHolidayLine(String holiday, ThreadLocalRandom rng) {
        if (holiday.contains("国庆") && holiday.contains("中秋")) {
            return fillHoliday(randomTemplate(NATIONAL_MID_AUTUMN_TEMPLATES_CN, rng), holiday);
        }

        for (Map.Entry<String, String[]> entry : TEMPLATES_CN.entrySet()) {
            if (holiday.contains(entry.getKey())) {
                return fillHoliday(randomTemplate(entry.getValue(), rng), holiday);
            }
        }
        return fillHoliday(randomTemplate(GENERIC_TEMPLATES_CN, rng), holiday);
    }

    private String randomTemplate(String[] templates, ThreadLocalRandom rng) {
        return templates[rng.nextInt(templates.length)];
    }

    private String fillHoliday(String template, String holiday) {
        return template.replace("{holiday}", holiday);
    }

    private void refreshAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshIfStale);
    }

    private void refreshIfStale() {
        int currentYear = LocalDate.now().getYear();
        if (cachedYear != currentYear || hasNoCachedHolidays()) {
            refresh();
        }
    }

    private boolean hasNoCachedHolidays() {
        return holidayEvents.isEmpty();
    }

    private void refresh() {
        try {
            int currentYear = LocalDate.now().getYear();
            holidayEvents = List.copyOf(fetchYear(currentYear));
            cachedYear = currentYear;
            lastFetchedAt = Instant.now();
            saveCache();
            Bukkit.getScheduler().runTask(plugin, refreshListener);
            plugin.getLogger().info("Holiday MOTD calendar refreshed");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh holiday MOTD calendar: " + e.getMessage());
        }
    }

    private List<HolidayEvent> fetchYear(int year) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(HOLIDAY_YEAR_URL + year + ".json"))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("holiday-cn responded with HTTP " + response.statusCode());
        }
        return parseYearResponse(response.body());
    }

    private List<HolidayEvent> parseYearResponse(String json) {
        List<HolidayEvent> events = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement daysElement = root.get("days");
        if (daysElement == null || !daysElement.isJsonArray()) {
            return events;
        }

        for (JsonElement dayElement : daysElement.getAsJsonArray()) {
            if (!dayElement.isJsonObject()) {
                continue;
            }

            JsonObject day = dayElement.getAsJsonObject();
            if (!getBoolean(day, "isOffDay")) {
                continue;
            }

            String name = getString(day, "name");
            String date = getString(day, "date");
            if (name == null || date == null) {
                continue;
            }

            LocalDate start = LocalDate.parse(date);
            events.add(new HolidayEvent(start, start.plusDays(1), name));
        }
        return events;
    }

    private boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(cacheFile);
        String fetchedAt = config.getString("fetched-at");
        cachedYear = config.getInt("year", -1);
        if (fetchedAt != null && !fetchedAt.isBlank()) {
            try {
                lastFetchedAt = Instant.parse(fetchedAt);
            } catch (RuntimeException ignored) {
                lastFetchedAt = Instant.EPOCH;
            }
        }

        holidayEvents = List.copyOf(loadEvents(config, "events.cn"));
    }

    private List<HolidayEvent> loadEvents(YamlConfiguration config, String path) {
        List<Map<?, ?>> eventMaps = config.getMapList(path);
        List<HolidayEvent> events = new ArrayList<>();
        for (Map<?, ?> eventMap : eventMaps) {
            Object startValue = eventMap.get("start");
            Object endValue = eventMap.get("end");
            Object summaryValue = eventMap.get("summary");
            if (startValue == null || endValue == null || summaryValue == null) {
                continue;
            }
            try {
                events.add(new HolidayEvent(
                        LocalDate.parse(String.valueOf(startValue)),
                        LocalDate.parse(String.valueOf(endValue)),
                        String.valueOf(summaryValue)
                ));
            } catch (RuntimeException ignored) {
            }
        }
        return events;
    }

    private void saveCache() throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Failed to create plugin data folder");
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("fetched-at", lastFetchedAt.toString());
        config.set("year", cachedYear);
        config.set("events.cn", dumpEvents(holidayEvents));
        config.save(cacheFile);
    }

    private List<Map<String, String>> dumpEvents(List<HolidayEvent> events) {
        List<Map<String, String>> dumped = new ArrayList<>();
        for (HolidayEvent event : events) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("start", event.start().toString());
            map.put("end", event.endExclusive().toString());
            map.put("summary", event.summary());
            dumped.add(map);
        }
        return dumped;
    }

    private record HolidayEvent(LocalDate start, LocalDate endExclusive, String summary) {
        boolean includes(LocalDate date) {
            return !date.isBefore(start) && date.isBefore(endExclusive);
        }
    }
}
