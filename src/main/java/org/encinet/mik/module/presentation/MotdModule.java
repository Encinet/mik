package org.encinet.mik.module.presentation;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.afk.AfkStateListener;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.player.PlayerAddressModule;
import org.encinet.mik.module.player.PlayerAddressModule.AddressPlayer;
import org.encinet.mik.module.presentation.motd.HolidayMotdCategory;
import org.encinet.mik.util.MotdCenterUtil;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MotdModule implements Listener, AfkStateListener {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final long EASTER_EGG_WINDOW_MS = 30_000L;
    private static final long DEBOUNCE_MS = 500L;
    private static final long AMBIENT_ROTATION_MS = 10_000L;
    private static final int EASTER_EGG_THRESHOLD = 2;
    private static final int MAX_TRACKED_PING_IPS = 2048;

    private static final int AFK_MIN_PLAYERS = 3;
    private static final int NIGHT_EGG_ONE_IN = 2;
    private static final int KNOWN_PLAYER_EGG_ONE_IN = 3;

    // -------------------------------------------------------------------------
    // MiniMessage source strings
    // -------------------------------------------------------------------------

    private static final String[] NORMAL_LINE2_CN = {
            "<gradient:#5e4fa2:#f79459>建造 · 摸鱼 · 音乐 · 快乐 · AFK",
            "<white><bold>推荐安装</bold> <green><bold>Plasmo Voice</bold> <white><bold>语音模组",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身临其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模组已支持 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>需要帮助？</gradient> <white>访问官网 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
    };

    private static final String[] NORMAL_LINE2_EN = {
            "<gradient:#5e4fa2:#f79459>Build · Chill · Music · Fun · AFK",
            "<white><bold>Recommended to install</bold> <green><bold>Plasmo Voice</bold> <white><bold>Mod",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>Experience <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>In Reality <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>Need Help?</gradient> <white>Visit Website <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
    };

    // Each inner array is one branch; branches are played back sequentially on repeated pings.
    private static final String[][] EGG_BRANCHES_CN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>检测到</bold><yellow>高频</yellow><white>刷新...</white>",
                    "<aqua>你在练习手速吗？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>别再 Ping 了，直接连接吧</gradient>",
            },
            {
                    "<gradient:#84fab0:#8fd3f4>在考虑要不要加入？</gradient>",
                    "<white>没事的，服务器<green>很友好</green> <white>:)</white>",
                    "<gold>慢慢来</gold>，<white>门为你开着</white>",
                    "<gradient:#ffecd2:#fcb69f>真的，进来看看就好</gradient>",
                    "<gradient:#f6d365:#fda085>等候多时了 ～</gradient>",
            },
            {
                    "<white>Ping！</white>",
                    "<yellow>再 Ping！</yellow>",
                    "<gold>又 Ping！</gold>",
                    "<gradient:#ff0080:#ff8c00>...你是在逗我吗 owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>行吧，进来玩吧 XD</gradient></bold>",
            },
            {
                    "<gray>正在加载精彩内容...</gray>",
                    "<white>其实这段 MOTD <yellow>是人写的</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>写它的人希望你加入</gradient>",
                    "<aqua>（也许有那么一点无聊）</aqua>",
                    "<gradient:#fa709a:#fee140><bold>但服务器是真的好玩！</bold></gradient>",
            },
            {
                    "<gradient:#ff9a9e:#fad0c4><bold>我没有说过这句话。</bold></gradient> <gray>—— 鲁迅</gray>",
            },
    };

    private static final String[][] EGG_BRANCHES_EN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>High speed</bold> <yellow>detected...</yellow>",
                    "<aqua>Practicing your clicking speed?</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>Stop pinging, just join already</gradient>",
            },
            {
                    "<gradient:#84fab0:#8fd3f4>Still deciding whether to join?</gradient>",
                    "<white>It's a <green>friendly</green> place, promise :)</white>",
                    "<gold>Take your time</gold>, <white>door's open</white>",
                    "<gradient:#ffecd2:#fcb69f>Seriously, come take a look</gradient>",
                    "<gradient:#f6d365:#fda085>We'll be here ~</gradient>",
            },
            {
                    "<white>Ping!</white>",
                    "<yellow>Ping again!</yellow>",
                    "<gold>Another ping!</gold>",
                    "<gradient:#ff0080:#ff8c00>...are you testing me? owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>Fine, just come play XD</gradient></bold>",
            },
            {
                    "<gray>Loading interesting content...</gray>",
                    "<white>Fun fact: <yellow>a human wrote this MOTD</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>They really do want you to join</gradient>",
                    "<aqua>(May have been slightly bored at the time)</aqua>",
                    "<gradient:#fa709a:#fee140><bold>The server though? Genuinely fun.</bold></gradient>",
            },
    };

    private static final String[] AFK_LINE2_CN = {
            "<gradient:#ffd89b:#19547b>大家都在认真挂机中...</gradient>",
            "<gray>服务器正在进行集体静默测试(AFK)</gray>",
    };

    private static final String[] AFK_LINE2_EN = {
            "<gradient:#ffd89b:#19547b>Everyone is totally working hard</gradient>",
            "<gray>The server is in collective AFK mode</gray>",
    };

    private static final String[] NIGHT_LINE2_CN = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>夜已深</gradient><white>，记得</white><gradient:#fbc2eb:#a6c1ee>早点睡觉哦</gradient>",
            "<gradient:#f6d365:#fda085>真晚啊</gradient><white>，记得</white><gradient:#84fab0:#8fd3f4>休息哦</gradient>",
    };

    private static final String[] NIGHT_LINE2_EN = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>It's getting late</gradient><white>, remember to </white><gradient:#fbc2eb:#a6c1ee>sleep well</gradient>",
            "<gradient:#f6d365:#fda085>Still awake?</gradient><white> Remember to </white><gradient:#84fab0:#8fd3f4>take a rest</gradient>",
    };

    private static final String[] KNOWN_PLAYER_LINE2_CN = {
            "<white>欢迎回来，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
            "<white>又见面了，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今天也轻松一点</white>",
            "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，欢迎回到这片小世界</white>",
            "<white>看到你回来啦，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，祝你玩得开心</white>",
            "<gradient:#d7e7c6:#b8d8e8>{player}</gradient><white>，今天想从哪里开始？</white>",
    };

    private static final String[] KNOWN_PLAYER_LINE2_EN = {
            "<white>Welcome back, </white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
            "<white>Good to see you again, </white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>. Settle in.</white>",
            "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>, welcome back to this little world.</white>",
            "<white>Nice to have you back, </white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>. Have a good time.</white>",
            "<gradient:#d7e7c6:#b8d8e8>{player}</gradient><white>, where would you like to start?</white>",
    };

    // -------------------------------------------------------------------------
    // Pre-built Component caches  (populated in enable())
    // -------------------------------------------------------------------------

    // LINE1 must be built at runtime because it embeds the server version.
    private static Component LINE1_CN;
    private static Component LINE1_EN;

    private static Component[] NORMAL_MOTDS_CN;
    private static Component[] NORMAL_MOTDS_EN;
    private static Component[][] EGG_MOTDS_CN;
    private static Component[][] EGG_MOTDS_EN;
    private static Component[] AFK_MOTDS_CN;
    private static Component[] AFK_MOTDS_EN;
    private static Component[] NIGHT_MOTDS_CN;
    private static Component[] NIGHT_MOTDS_EN;

    // -------------------------------------------------------------------------
    // Repeat-ping tracker
    // -------------------------------------------------------------------------

    /**
     * @param count      how many pings have been seen in the current window
     * @param lastPingAt wall-clock ms of the most recent ping
     * @param eggBranch  which easter-egg branch was chosen for this window
     * @param isCN       language that opened this window — a language change resets it
     */
    private record PingRecord(int count, long lastPingAt, int eggBranch, boolean isCN) {
    }

    private final ConcurrentHashMap<String, PingRecord> pingTracker = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Services & state
    // -------------------------------------------------------------------------

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final PlayerAddressModule playerAddressModule;
    private final HolidayMotdCategory holidayCategory;

    // Cached per-language state-MOTDs (AFK / holiday).  null = no override.
    private volatile Component cachedStateMotdCn;
    private volatile Component cachedStateMotdEn;
    private volatile boolean cachedLateNight;

    // Per-session random salts so hashing is not predictable across restarts.
    private long saltNight;
    private long saltAmbient;
    private long saltKnownPlayer;

    private BukkitTask stateRefreshTask;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public MotdModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService,
                      PlayerAddressModule playerAddressModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.playerAddressModule = playerAddressModule;
        this.holidayCategory = new HolidayMotdCategory(plugin);
        this.holidayCategory.setRefreshListener(this::refreshStateMotds);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void enable() {
        String version = Bukkit.getMinecraftVersion();
        LINE1_CN = parse(MotdCenterUtil.center("<gold>米<white>客 <gray>| <green>" + version + "<gray> | <gold>创意<white>休闲服"));
        LINE1_EN = parse(MotdCenterUtil.center("<gold>Mi<white>k  <gray>| <green>" + version + "<gray> | <gold>Creative<white> Casual"));
        buildAllMotds();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        saltNight = rng.nextLong();
        saltAmbient = rng.nextLong();
        saltKnownPlayer = rng.nextLong();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 1200L, 1200L);
        holidayCategory.enable();
        refreshStateMotds();
    }

    public void disable() {
        pingTracker.clear();
        afkService.removeListener(this);
        cancelRefreshTask();
        holidayCategory.disable();
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        event.setMaxPlayers(2026);

        InetAddress address = event.getAddress();
        Optional<AddressPlayer> inferredPlayer = playerAddressModule.inferPlayerByAddress(address);

        if (inferredPlayer.isPresent()) {
            event.setHidePlayers(false);
        } else {
            event.getListedPlayers().clear();
        }

        boolean isCN = resolveLanguage(address, inferredPlayer).isChinese();
        event.motd(resolveMotd(address.getHostAddress(), isCN,
                inferredPlayer.flatMap(AddressPlayer::playerNameOptional).orElse(null)));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshStateMotds();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Wait one tick so Bukkit.getOnlinePlayers() reflects the departure.
        Bukkit.getScheduler().runTask(plugin, this::refreshStateMotds);
    }

    @Override
    public void onAfkStateChanged(Player player, AfkState state) {
        refreshStateMotds();
    }

    // -------------------------------------------------------------------------
    // MOTD resolution  (priority: state > repeat-ping easter egg > known-player > ambient)
    // -------------------------------------------------------------------------

    private Component resolveMotd(String ip, boolean isCN, String knownPlayerName) {
        long now = System.currentTimeMillis();

        Component stateMotd = isCN ? cachedStateMotdCn : cachedStateMotdEn;
        if (stateMotd != null) return stateMotd;

        Component eggMotd = resolveRepeatPingMotd(ip, isCN, now);
        if (eggMotd != null) return eggMotd;

        Component knownMotd = resolveKnownPlayerMotd(ip, isCN, knownPlayerName, now);
        if (knownMotd != null) return knownMotd;

        return resolveAmbientMotd(ip, isCN, now);
    }

    // --- Repeat-ping easter egg ---

    private Component resolveRepeatPingMotd(String ip, boolean isCN, long now) {
        if (!canTrackPing(ip)) return null;

        PingRecord record = updatePingRecord(ip, isCN, now);
        if (record.count() <= EASTER_EGG_THRESHOLD) return null;

        Component[][] eggs = isCN ? EGG_MOTDS_CN : EGG_MOTDS_EN;
        Component[] branch = eggs[record.eggBranch()];
        int eggIndex = record.count() - EASTER_EGG_THRESHOLD - 1;

        if (eggIndex >= branch.length) {
            pingTracker.remove(ip);
            return null;
        }

        return branch[eggIndex];
    }

    private boolean canTrackPing(String ip) {
        return pingTracker.containsKey(ip) || pingTracker.mappingCount() < MAX_TRACKED_PING_IPS;
    }

    /**
     * Returns the updated record.  A language change resets the window so that
     * {@code eggBranch} is always allocated from the correct language's branch count.
     */
    private PingRecord updatePingRecord(String ip, boolean isCN, long now) {
        int branchCount = (isCN ? EGG_MOTDS_CN : EGG_MOTDS_EN).length;
        return pingTracker.compute(ip, (_, prev) -> {
            boolean expired = prev == null || now - prev.lastPingAt() > EASTER_EGG_WINDOW_MS;
            boolean languageShift = prev != null && prev.isCN() != isCN;
            if (expired || languageShift) {
                return new PingRecord(1, now, ThreadLocalRandom.current().nextInt(branchCount), isCN);
            }
            if (now - prev.lastPingAt() < DEBOUNCE_MS) {
                // Browser double-ping within debounce window — don't advance counter.
                return new PingRecord(prev.count(), now, prev.eggBranch(), isCN);
            }
            return new PingRecord(prev.count() + 1, now, prev.eggBranch(), isCN);
        });
    }

    // --- Known-player greeting ---

    private Component resolveKnownPlayerMotd(String ip, boolean isCN, String knownPlayerName, long now) {
        if (knownPlayerName == null) return null;
        if (stableIndex(ip, now, KNOWN_PLAYER_EGG_ONE_IN, saltKnownPlayer) != 0) return null;

        String[] templates = isCN ? KNOWN_PLAYER_LINE2_CN : KNOWN_PLAYER_LINE2_EN;
        String template = templates[stableIndex(ip, now, templates.length, saltKnownPlayer ^ 0x5f3759dfL)];
        return buildMotd(isCN ? LINE1_CN : LINE1_EN, template.replace("{player}", knownPlayerName));
    }

    // --- Ambient rotation (normal / night easter egg) ---

    private Component resolveAmbientMotd(String ip, boolean isCN, long now) {
        if (cachedLateNight && stableIndex(ip, now, NIGHT_EGG_ONE_IN, saltNight) == 0) {
            Component[] night = isCN ? NIGHT_MOTDS_CN : NIGHT_MOTDS_EN;
            return night[stableIndex(ip, now, night.length, saltNight ^ 1L)];
        }
        Component[] normals = isCN ? NORMAL_MOTDS_CN : NORMAL_MOTDS_EN;
        return normals[stableIndex(ip, now, normals.length)];
    }

    // -------------------------------------------------------------------------
    // Stable hash  (IP + time-bucket → deterministic index, changes every rotation period)
    // -------------------------------------------------------------------------

    private int stableIndex(String ip, long now, int length) {
        return stableIndex(ip, now, length, saltAmbient);
    }

    private int stableIndex(String ip, long now, int length, long salt) {
        long bucket = now / AMBIENT_ROTATION_MS;
        long hash = mix64(salt ^ ip.hashCode() ^ bucket);
        return (int) Long.remainderUnsigned(hash, length);
    }

    /**
     * finalizer mix from MurmurHash3 / SplitMix64
     */
    private static long mix64(long v) {
        v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
        v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }

    // -------------------------------------------------------------------------
    // State-MOTD cache  (AFK / holiday / late-night flag)
    // -------------------------------------------------------------------------

    private void refreshStateMotds() {
        cachedLateNight = isLateNight(LocalTime.now(SHANGHAI));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        cachedStateMotdCn = resolveStateMotd(true, rng);
        cachedStateMotdEn = resolveStateMotd(false, rng);
        scheduleNextStateRefresh();
    }

    /**
     * Returns a state-override MOTD, or {@code null} if normal MOTD selection should apply.
     */
    private Component resolveStateMotd(boolean isCN, ThreadLocalRandom rng) {
        LocalDate today = LocalDate.now(SHANGHAI);

        Optional<String> holiday = holidayCategory.resolveLine(today, isCN, rng);
        if (holiday.isPresent()) {
            return buildMotd(isCN ? LINE1_CN : LINE1_EN, holiday.get());
        }

        if (isMostlyAfk()) {
            Component[] afk = isCN ? AFK_MOTDS_CN : AFK_MOTDS_EN;
            return afk[rng.nextInt(afk.length)];
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Refresh scheduling  (fires at the next time boundary: midnight or 05:00)
    // -------------------------------------------------------------------------

    private void scheduleNextStateRefresh() {
        cancelRefreshTask();
        long delayTicks = Math.max(1L,
                Duration.between(ZonedDateTime.now(SHANGHAI), nextTimeBoundary()).toSeconds() * 20L);
        stateRefreshTask = Bukkit.getScheduler().runTaskLater(plugin, this::refreshStateMotds, delayTicks);
    }

    private ZonedDateTime nextTimeBoundary() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI);
        ZonedDateTime nextFive = now.toLocalDate().atTime(5, 0).atZone(SHANGHAI);
        return now.isBefore(nextFive) ? nextFive
                : now.toLocalDate().plusDays(1).atStartOfDay(SHANGHAI);
    }

    private void cancelRefreshTask() {
        if (stateRefreshTask != null) {
            stateRefreshTask.cancel();
            stateRefreshTask = null;
        }
    }

    // -------------------------------------------------------------------------
    // AFK detection
    // -------------------------------------------------------------------------

    private boolean isMostlyAfk() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online < AFK_MIN_PLAYERS) return false;

        int nonAfk = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!afkService.isAfk(p.getUniqueId())) nonAfk++;
        }

        int threshold = Math.max(1, (int) Math.floor(online * 0.1));
        return nonAfk <= threshold;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - EASTER_EGG_WINDOW_MS;
        pingTracker.entrySet().removeIf(e -> e.getValue().lastPingAt() < cutoff);
    }

    // -------------------------------------------------------------------------
    // Build helpers
    // -------------------------------------------------------------------------

    private static Component buildMotd(Component line1, String line2) {
        return line1.append(Component.newline()).append(parse(MotdCenterUtil.center(line2)));
    }

    private static Component[] buildMotds(Component line1, String[] lines) {
        Component[] out = new Component[lines.length];
        for (int i = 0; i < lines.length; i++) out[i] = buildMotd(line1, lines[i]);
        return out;
    }

    private static Component[][] buildEggMotds(Component line1, String[][] branches) {
        Component[][] out = new Component[branches.length][];
        for (int b = 0; b < branches.length; b++) out[b] = buildMotds(line1, branches[b]);
        return out;
    }

    private static void buildAllMotds() {
        NORMAL_MOTDS_CN = buildMotds(LINE1_CN, NORMAL_LINE2_CN);
        NORMAL_MOTDS_EN = buildMotds(LINE1_EN, NORMAL_LINE2_EN);
        EGG_MOTDS_CN = buildEggMotds(LINE1_CN, EGG_BRANCHES_CN);
        EGG_MOTDS_EN = buildEggMotds(LINE1_EN, EGG_BRANCHES_EN);
        AFK_MOTDS_CN = buildMotds(LINE1_CN, AFK_LINE2_CN);
        AFK_MOTDS_EN = buildMotds(LINE1_EN, AFK_LINE2_EN);
        NIGHT_MOTDS_CN = buildMotds(LINE1_CN, NIGHT_LINE2_CN);
        NIGHT_MOTDS_EN = buildMotds(LINE1_EN, NIGHT_LINE2_EN);
    }

    private static Component parse(String mm) {
        return MM.deserialize(mm);
    }

    // -------------------------------------------------------------------------
    // Language resolution
    // -------------------------------------------------------------------------

    private Language resolveLanguage(InetAddress address, Optional<AddressPlayer> inferredPlayer) {
        return languageService.language(inferredPlayer.map(AddressPlayer::playerId).orElse(null), address);
    }

    // -------------------------------------------------------------------------
    // Time helpers
    // -------------------------------------------------------------------------

    private static boolean isLateNight(LocalTime t) {
        // 00:00 inclusive → 05:00 exclusive
        return t.isBefore(LocalTime.of(5, 0));
    }
}
