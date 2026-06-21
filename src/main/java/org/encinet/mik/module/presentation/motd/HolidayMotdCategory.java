package org.encinet.mik.module.presentation.motd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String[] GENERIC_TEMPLATES_EN = {
            "<gradient:#f7971e:#ffd200>Happy {holiday}! Hope today feels lighter</gradient>",
            "<gold><bold>Happy {holiday}!</bold></gold><white> Wishing you a relaxed day</white>",
            "<gradient:#ff9a9e:#fad0c4>It's {holiday}, have a good one</gradient>",
            "<gradient:#84fab0:#8fd3f4>Another day off~</gradient><white> Happy {holiday}!</white>"
    };

    private static final String[] NATIONAL_MID_AUTUMN_TEMPLATES_CN = {
            "<gradient:#de6262:#ffb88c>国庆中秋双节快乐！愿假期慢一点、月色亮一点</gradient>",
            "<red><bold>国庆快乐！</bold></red><gold> 中秋快乐！祝你假期愉快</gold>",
            "<gradient:#f7971e:#ffd200>双节快乐，愿今天团圆、放松、开心摸鱼</gradient>"
    };

    private static final String[] NATIONAL_MID_AUTUMN_TEMPLATES_EN = {
            "<gradient:#de6262:#ffb88c>Happy National Day and Mid-Autumn Festival!</gradient>",
            "<gold><bold>Double holiday!</bold></gold><white> Wishing you rest, reunion, and a bright moon</white>",
            "<gradient:#f7971e:#ffd200>Happy holidays, hope today feels warm and slow</gradient>"
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

    private static final Map<String, String[]> TEMPLATES_EN = Map.ofEntries(
            Map.entry("元旦", new String[]{
                    "<gradient:#00c6ff:#0072ff>Happy New Year! Wishing you a bright start</gradient>",
                    "<aqua><bold>Happy New Year!</bold></aqua><white> New year, easy days</white>"
            }),
            Map.entry("春节", new String[]{
                    "<gradient:#ff512f:#f09819><bold>Happy Spring Festival!</bold></gradient><white> Wishing you luck and ease</white>",
                    "<red><bold>Happy Lunar New Year!</bold></red><gold> May the year treat you kindly</gold>"
            }),
            Map.entry("除夕", new String[]{
                    "<gradient:#ff512f:#dd2476>Happy Lunar New Year's Eve! Wishing you warmth and reunion</gradient>"
            }),
            Map.entry("清明", new String[]{
                    "<gradient:#bdc3c7:#2c3e50>Qingming, wishing you peace and care</gradient>"
            }),
            Map.entry("劳动", new String[]{
                    "<gradient:#56ab2f:#a8e063>Happy Labor Day! Take a break and chill</gradient>"
            }),
            Map.entry("端午", new String[]{
                    "<gradient:#11998e:#38ef7d>Happy Dragon Boat Festival! Remember the zongzi</gradient>"
            }),
            Map.entry("中秋", new String[]{
                    "<gradient:#f7971e:#ffd200>Happy Mid-Autumn Festival! Wishing you a gentle moonlit day</gradient>",
                    "<gold><bold>Happy Mid-Autumn!</bold></gold><white> May today feel warm and slow</white>"
            }),
            Map.entry("国庆", new String[]{
                    "<gradient:#de6262:#ffb88c>Happy National Day! Wishing you a bright holiday</gradient>"
            }),
            Map.entry("圣诞", new String[]{
                    "<gradient:#00b09b:#96c93d>Merry Christmas! Hope today brings a small surprise</gradient>",
                    "<red><bold>Merry Christmas!</bold></red><white> Good day for snow and rest</white>"
            }),
            Map.entry("情人", new String[]{
                    "<gradient:#ff758c:#ff7eb3>Happy Valentine's Day! Hope you feel loved and free</gradient>"
            }),
            Map.entry("元宵", new String[]{
                    "<gradient:#f7971e:#ffd200>Happy Lantern Festival! Wishing you gentle lights tonight</gradient>"
            })
    );

    private final JavaPlugin plugin;
    private final File cacheFile;
    private final HttpClient httpClient;
    private volatile Map<CalendarLocale, List<HolidayEvent>> holidayEvents = emptyHolidayMap();
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

    public Optional<String> resolveLine(LocalDate today, boolean chinese, ThreadLocalRandom rng) {
        return holidayEvents.getOrDefault(CalendarLocale.CN, List.of()).stream()
                .filter(event -> event.includes(today))
                .findFirst()
                .map(event -> renderHolidayLine(event.summary(), chinese, rng));
    }

    private String renderHolidayLine(String holiday, boolean chinese, ThreadLocalRandom rng) {
        if (holiday.contains("国庆") && holiday.contains("中秋")) {
            String[] templates = chinese ? NATIONAL_MID_AUTUMN_TEMPLATES_CN : NATIONAL_MID_AUTUMN_TEMPLATES_EN;
            return fillHoliday(randomTemplate(templates, rng), holiday, chinese);
        }

        Map<String, String[]> templatesByKeyword = chinese ? TEMPLATES_CN : TEMPLATES_EN;
        for (Map.Entry<String, String[]> entry : templatesByKeyword.entrySet()) {
            if (holiday.contains(entry.getKey())) {
                return fillHoliday(randomTemplate(entry.getValue(), rng), holiday, chinese);
            }
        }

        String[] genericTemplates = chinese ? GENERIC_TEMPLATES_CN : GENERIC_TEMPLATES_EN;
        return fillHoliday(randomTemplate(genericTemplates, rng), holiday, chinese);
    }

    private String randomTemplate(String[] templates, ThreadLocalRandom rng) {
        return templates[rng.nextInt(templates.length)];
    }

    private String fillHoliday(String template, String holiday, boolean chinese) {
        return template.replace("{holiday}", chinese ? holiday : translateHolidayName(holiday));
    }

    private String translateHolidayName(String holiday) {
        if (holiday.contains("元旦")) return "New Year's Day";
        if (holiday.contains("春节") || holiday.contains("初") || holiday.contains("除夕")) return "Spring Festival";
        if (holiday.contains("清明")) return "Qingming Festival";
        if (holiday.contains("劳动")) return "Labor Day";
        if (holiday.contains("端午")) return "Dragon Boat Festival";
        if (holiday.contains("中秋")) return "Mid-Autumn Festival";
        if (holiday.contains("国庆")) return "National Day";
        if (holiday.contains("圣诞")) return "Christmas";
        if (holiday.contains("情人")) return "Valentine's Day";
        if (holiday.contains("元宵")) return "Lantern Festival";
        return holiday;
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
        return holidayEvents.getOrDefault(CalendarLocale.CN, List.of()).isEmpty();
    }

    private void refresh() {
        try {
            int currentYear = LocalDate.now().getYear();
            Map<CalendarLocale, List<HolidayEvent>> fetched = new HashMap<>();
            fetched.put(CalendarLocale.CN, fetchYear(currentYear));
            fetched.put(CalendarLocale.EN, List.of());
            holidayEvents = Map.copyOf(fetched);
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

        Map<CalendarLocale, List<HolidayEvent>> loaded = new HashMap<>();
        loaded.put(CalendarLocale.CN, loadEvents(config, "events.cn"));
        loaded.put(CalendarLocale.EN, loadEvents(config, "events.en"));
        holidayEvents = Map.copyOf(loaded);
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
        config.set("events.cn", dumpEvents(holidayEvents.getOrDefault(CalendarLocale.CN, List.of())));
        config.set("events.en", dumpEvents(holidayEvents.getOrDefault(CalendarLocale.EN, List.of())));
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

    private static Map<CalendarLocale, List<HolidayEvent>> emptyHolidayMap() {
        Map<CalendarLocale, List<HolidayEvent>> map = new HashMap<>();
        map.put(CalendarLocale.CN, List.of());
        map.put(CalendarLocale.EN, List.of());
        return Map.copyOf(map);
    }

    private enum CalendarLocale {
        CN,
        EN
    }

    private record HolidayEvent(LocalDate start, LocalDate endExclusive, String summary) {
        boolean includes(LocalDate date) {
            return !date.isBefore(start) && date.isBefore(endExclusive);
        }
    }
}
