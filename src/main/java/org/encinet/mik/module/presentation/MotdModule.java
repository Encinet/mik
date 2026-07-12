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
import java.util.EnumMap;
import java.util.Map;
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

    private static final String[] NORMAL_LINE2_HK = {
            "<gradient:#5e4fa2:#f79459>建造 · 摸魚 · 音樂 · 快樂 · AFK",
            "<white><bold>推薦安裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音模組",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身歷其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模組現已支援 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>需要協助？</gradient> <white>瀏覽網站 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
    };

    private static final String[] NORMAL_LINE2_TW = {
            "<gradient:#5e4fa2:#f79459>建造 · 摸魚 · 音樂 · 快樂 · AFK",
            "<white><bold>推薦安裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音模組",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身歷其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模組已支援 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>需要幫忙？</gradient> <white>前往網站 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
    };

    private static final String[] NORMAL_LINE2_LZH = {
            "<gradient:#5e4fa2:#f79459>營造 · 閒遊 · 絲竹 · 歡娛 · 暫離</gradient>",
            "<white><bold>宜裝</bold> <green><bold>Plasmo Voice</bold> <white><bold>語音增益",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>如臨其境，<gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>今亦可用 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>有所疑乎？</gradient> <white>且訪 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>",
    };

    private static final String[][] EGG_BRANCHES_TRADITIONAL = {
            {
                    "<gradient:#66edff:#66ffb2>Ping？</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong！</gradient>",
                    "<white><bold>偵測到</bold><yellow>高頻</yellow><white>重新整理...</white>",
                    "<aqua>你在練習手速嗎？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>別再 Ping 了，直接連線吧</gradient>",
            },
            {
                    "<gradient:#84fab0:#8fd3f4>還在考慮要不要加入？</gradient>",
                    "<white>放心，這裡<green>很友善</green> <white>:)</white>",
                    "<gold>慢慢來</gold>，<white>門一直開著</white>",
                    "<gradient:#ffecd2:#fcb69f>真的，進來看看就好</gradient>",
                    "<gradient:#f6d365:#fda085>等你很久了～</gradient>",
            },
            {
                    "<white>Ping！</white>",
                    "<yellow>再 Ping！</yellow>",
                    "<gold>又 Ping！</gold>",
                    "<gradient:#ff0080:#ff8c00>…你是在逗我嗎 owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>好啦，進來玩吧 XD</gradient></bold>",
            },
            {
                    "<gray>正在載入精彩內容...</gray>",
                    "<white>其實這段 MOTD <yellow>是人寫的</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>寫它的人希望你加入</gradient>",
                    "<aqua>（也許當時有點無聊）</aqua>",
                    "<gradient:#fa709a:#fee140><bold>但伺服器真的很好玩！</bold></gradient>",
            },
    };

    private static final String[][] EGG_BRANCHES_LZH = {
            {
                    "<gradient:#66edff:#66ffb2>叩之？</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>應之！</gradient>",
                    "<white>屢叩不止...</white>",
                    "<aqua>子試指力乎？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>毋復叩矣，入而遊之</gradient>",
            },
            {
                    "<gradient:#84fab0:#8fd3f4>尚疑入否？</gradient>",
                    "<white>此間<green>和善</green>，君可無憂</white>",
                    "<gold>徐徐思之</gold>，<white>戶常爲君啟</white>",
                    "<gradient:#ffecd2:#fcb69f>姑入一觀，亦何妨？</gradient>",
                    "<gradient:#f6d365:#fda085>候君久矣</gradient>",
            },
            {
                    "<white>一叩！</white>",
                    "<yellow>再叩！</yellow>",
                    "<gold>又叩！</gold>",
                    "<gradient:#ff0080:#ff8c00>子其戲我乎 owo</gradient>",
                    "<bold><gradient:#42e695:#3bb2b8>善，入而同遊 XD</gradient></bold>",
            },
            {
                    "<gray>方載妙物⋯</gray>",
                    "<white>其實此 MOTD <yellow>出於人手</yellow></white>",
                    "<gradient:#43e97b:#38f9d7>纂此文者願君入焉</gradient>",
                    "<aqua>（彼時或稍覺無聊）</aqua>",
                    "<gradient:#fa709a:#fee140><bold>然此服誠可樂也！</bold></gradient>",
            },
    };

    private static final String[] AFK_LINE2_TRADITIONAL = {
            "<gradient:#ffd89b:#19547b>眾人都在認真掛機中...</gradient>",
            "<gray>伺服器正在進行集體靜默測試（AFK）</gray>",
    };

    private static final String[] AFK_LINE2_LZH = {
            "<gradient:#ffd89b:#19547b>眾皆暫離，勤矣哉⋯</gradient>",
            "<gray>舉服寂然，若有所待</gray>",
    };

    private static final String[] NIGHT_LINE2_TRADITIONAL = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>夜已深</gradient><white>，記得</white><gradient:#fbc2eb:#a6c1ee>早點休息</gradient>",
            "<gradient:#f6d365:#fda085>這麼晚了</gradient><white>，也該</white><gradient:#84fab0:#8fd3f4>睡覺囉</gradient>",
    };

    private static final String[] NIGHT_LINE2_LZH = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>夜既深矣</gradient><white>，宜</white><gradient:#fbc2eb:#a6c1ee>早寢</gradient>",
            "<gradient:#f6d365:#fda085>更深露重</gradient><white>，君其</white><gradient:#84fab0:#8fd3f4>少憩</gradient>",
    };

    private static final String[] KNOWN_PLAYER_LINE2_TRADITIONAL = {
            "<white>歡迎回來，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
            "<white>又見面了，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今天也放輕鬆吧</white>",
            "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，歡迎回到這個小世界</white>",
            "<white>看到你回來了，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，祝你玩得開心</white>",
    };

    private static final String[] KNOWN_PLAYER_LINE2_LZH = {
            "<white>歸去來兮，</white><gradient:#9bd8d0:#b9d7f0>{player}</gradient>",
            "<white>復見君，</white><gradient:#d6c6f2:#b7d9ea>{player}</gradient><white>，今日且從容</white>",
            "<gradient:#b8dfd8:#d7e8c8>{player}</gradient><white>，此方小天地候君久矣</white>",
            "<white>幸見君還，</white><gradient:#f0c9c2:#d6d4f0>{player}</gradient><white>，願君盡興</white>",
    };

    private record MotdProfile(
            Component line1,
            Component[] normal,
            Component[][] eggs,
            Component[] afk,
            Component[] night,
            String[] knownPlayer
    ) {}

    // -------------------------------------------------------------------------
    // Repeat-ping tracker
    // -------------------------------------------------------------------------

    /**
     * @param count      how many pings have been seen in the current window
     * @param lastPingAt wall-clock ms of the most recent ping
     * @param eggBranch  which easter-egg branch was chosen for this window
     * @param language   language that opened this window; a language change resets it
     */
    private record PingRecord(int count, long lastPingAt, int eggBranch, Language language) {}

    private final ConcurrentHashMap<String, PingRecord> pingTracker = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Services & state
    // -------------------------------------------------------------------------

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final PlayerAddressModule playerAddressModule;
    private final HolidayMotdCategory holidayCategory;
    private final EnumMap<Language, MotdProfile> motdProfiles = new EnumMap<>(Language.class);

    // Replaced atomically after refresh; absent values mean no state override.
    private volatile Map<Language, Component> cachedStateMotds = Map.of();
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
        buildProfiles(version);

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

        Language language = resolveLanguage(address, inferredPlayer);
        event.motd(resolveMotd(address.getHostAddress(), language,
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

    private Component resolveMotd(String ip, Language language, String knownPlayerName) {
        long now = System.currentTimeMillis();
        MotdProfile profile = profile(language);

        Component stateMotd = cachedStateMotds.get(language);
        if (stateMotd != null) return stateMotd;

        Component eggMotd = resolveRepeatPingMotd(ip, language, profile, now);
        if (eggMotd != null) return eggMotd;

        Component knownMotd = resolveKnownPlayerMotd(ip, profile, knownPlayerName, now);
        if (knownMotd != null) return knownMotd;

        return resolveAmbientMotd(ip, profile, now);
    }

    // --- Repeat-ping easter egg ---

    private Component resolveRepeatPingMotd(String ip, Language language, MotdProfile profile, long now) {
        if (!canTrackPing(ip)) return null;

        PingRecord record = updatePingRecord(ip, language, profile.eggs().length, now);
        if (record.count() <= EASTER_EGG_THRESHOLD) return null;

        Component[] branch = profile.eggs()[record.eggBranch()];
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
    private PingRecord updatePingRecord(String ip, Language language, int branchCount, long now) {
        return pingTracker.compute(ip, (_, prev) -> {
            boolean expired = prev == null || now - prev.lastPingAt() > EASTER_EGG_WINDOW_MS;
            boolean languageShift = prev != null && prev.language() != language;
            if (expired || languageShift) {
                return new PingRecord(1, now, ThreadLocalRandom.current().nextInt(branchCount), language);
            }
            if (now - prev.lastPingAt() < DEBOUNCE_MS) {
                // Browser double-ping within debounce window — don't advance counter.
                return new PingRecord(prev.count(), now, prev.eggBranch(), language);
            }
            return new PingRecord(prev.count() + 1, now, prev.eggBranch(), language);
        });
    }

    // --- Known-player greeting ---

    private Component resolveKnownPlayerMotd(String ip, MotdProfile profile, String knownPlayerName, long now) {
        if (knownPlayerName == null) return null;
        if (stableIndex(ip, now, KNOWN_PLAYER_EGG_ONE_IN, saltKnownPlayer) != 0) return null;

        String[] templates = profile.knownPlayer();
        String template = templates[stableIndex(ip, now, templates.length, saltKnownPlayer ^ 0x5f3759dfL)];
        return buildMotd(profile.line1(), template.replace("{player}", knownPlayerName));
    }

    // --- Ambient rotation (normal / night easter egg) ---

    private Component resolveAmbientMotd(String ip, MotdProfile profile, long now) {
        if (cachedLateNight && stableIndex(ip, now, NIGHT_EGG_ONE_IN, saltNight) == 0) {
            Component[] night = profile.night();
            return night[stableIndex(ip, now, night.length, saltNight ^ 1L)];
        }
        Component[] normals = profile.normal();
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
        EnumMap<Language, Component> refreshed = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            Component stateMotd = resolveStateMotd(language, profile(language), rng);
            if (stateMotd != null) {
                refreshed.put(language, stateMotd);
            }
        }
        cachedStateMotds = Map.copyOf(refreshed);
        scheduleNextStateRefresh();
    }

    /**
     * Returns a state-override MOTD, or {@code null} if normal MOTD selection should apply.
     */
    private Component resolveStateMotd(Language language, MotdProfile profile, ThreadLocalRandom rng) {
        if (language == Language.ZH_CN) {
            Optional<String> holiday = holidayCategory.resolveLine(LocalDate.now(SHANGHAI), language, rng);
            if (holiday.isPresent()) {
                return buildMotd(profile.line1(), holiday.get());
            }
        }

        if (isMostlyAfk()) {
            Component[] afk = profile.afk();
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

    private void buildProfiles(String version) {
        motdProfiles.clear();
        registerProfile(Language.ZH_CN, version, "<gold>米<white>客", "<gold>创意<white>休闲服",
                NORMAL_LINE2_CN, EGG_BRANCHES_CN, AFK_LINE2_CN, NIGHT_LINE2_CN, KNOWN_PLAYER_LINE2_CN);
        registerProfile(Language.ZH_HK, version, "<gold>米<white>客", "<gold>創意<white>休閒服",
                NORMAL_LINE2_HK, EGG_BRANCHES_TRADITIONAL, AFK_LINE2_TRADITIONAL,
                NIGHT_LINE2_TRADITIONAL, KNOWN_PLAYER_LINE2_TRADITIONAL);
        registerProfile(Language.ZH_TW, version, "<gold>米<white>客", "<gold>創意<white>休閒服",
                NORMAL_LINE2_TW, EGG_BRANCHES_TRADITIONAL, AFK_LINE2_TRADITIONAL,
                NIGHT_LINE2_TRADITIONAL, KNOWN_PLAYER_LINE2_TRADITIONAL);
        registerProfile(Language.LZH, version, "<gold>米<white>客", "<gold>營造<white>閒遊之服",
                NORMAL_LINE2_LZH, EGG_BRANCHES_LZH, AFK_LINE2_LZH,
                NIGHT_LINE2_LZH, KNOWN_PLAYER_LINE2_LZH);
        registerProfile(Language.EN_US, version, "<gold>Mi<white>k", "<gold>Creative<white> Casual",
                NORMAL_LINE2_EN, EGG_BRANCHES_EN, AFK_LINE2_EN, NIGHT_LINE2_EN, KNOWN_PLAYER_LINE2_EN);
    }

    private void registerProfile(
            Language language,
            String version,
            String brand,
            String category,
            String[] normal,
            String[][] eggs,
            String[] afk,
            String[] night,
            String[] knownPlayer
    ) {
        Component line1 = parse(MotdCenterUtil.center(
                brand + " <gray>| <green>" + version + "<gray> | " + category));
        motdProfiles.put(language, new MotdProfile(
                line1,
                buildMotds(line1, normal),
                buildEggMotds(line1, eggs),
                buildMotds(line1, afk),
                buildMotds(line1, night),
                knownPlayer
        ));
    }

    private MotdProfile profile(Language language) {
        MotdProfile profile = motdProfiles.get(language);
        if (profile == null) {
            throw new IllegalStateException("Missing MOTD profile for " + language.id());
        }
        return profile;
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
