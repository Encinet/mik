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
import org.encinet.mik.module.player.PlayerAddressModule.PlayerAddressDisplayRecord;
import org.encinet.mik.module.player.PlayerAddressModule.PlayerAddressRecord;
import org.encinet.mik.module.presentation.motd.HolidayMotdCategory;
import org.encinet.mik.util.GeoUtil;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long EASTER_EGG_WINDOW_MS = 30_000L;
    private static final long DEBOUNCE_MS = 500L;
    private static final int EASTER_EGG_THRESHOLD = 2;
    private static final int MAX_TRACKED_PING_IPS = 2048;
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int AFK_EASTER_EGG_MIN_PLAYERS = 3;
    private static final int NIGHT_EASTER_EGG_ONE_IN = 2;
    private static final int KNOWN_PLAYER_EASTER_EGG_ONE_IN = 3;
    private static final long AMBIENT_MOTD_ROTATION_MS = 10_000L;

    private static final Component LINE1_CN = MM.deserialize(
            MotdCenterUtil.center("<gold>米<white>客 <gray>| <green>26.1<gray> | <gold>创意<white>休闲服")
    );
    private static final Component LINE1_EN = MM.deserialize(
            MotdCenterUtil.center("<gold>Mi<white>k  <gray>| <green>26.1<gray> | <gold>Creative<white> Casual")
    );

    private static final String[] NORMAL_LINE2_CN = {
            "<gradient:#5e4fa2:#f79459>建造 · 摸鱼 · 音乐 · 快乐 · AFK",
            "<white><bold>推荐安装</bold> <green><bold>Plasmo Voice</bold> <white><bold>语音模组",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>身临其境 <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>模组已支持 <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>需要帮助？</gradient> <white>访问官网 <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>"
    };

    private static final String[] NORMAL_LINE2_EN = {
            "<gradient:#5e4fa2:#f79459>Build · Chill · Music · Fun · AFK",
            "<white><bold>Recommended to install</bold> <green><bold>Plasmo Voice</bold> <white><bold>Mod",
            "<gradient:#89f7fe:#66a6ff>✦</gradient> <white>Experience <gradient:#ee9ca7:#ffdde1><bold>ViveCraft</bold></gradient> <white>In Reality <gradient:#66a6ff:#89f7fe>✦</gradient>",
            "<gradient:#ee9ca7:#ffdde1>Need Help?</gradient> <white>Visit Website <gradient:#89f7fe:#66a6ff><underlined>mcmik.top</underlined></gradient>"
    };

    private static final String[][] EGG_BRANCHES_CN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>检测到</bold><yellow>高频</yellow><white>刷新...</white>",
                    "<aqua>你在练习手速吗？</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>别再 Ping 了，直接连接吧</gradient>"
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
                    "<gradient:#ff9a9e:#fad0c4><bold>我没有说过这句话。</bold></gradient> <gray>—— 鲁迅</gray>"
            }
    };

    private static final String[][] EGG_BRANCHES_EN = {
            {
                    "<gradient:#66edff:#66ffb2>Ping?</gradient>",
                    "<gradient:#ff9a9e:#fad0c4>Pong!</gradient>",
                    "<white><bold>High speed</bold> <yellow>detected...</yellow>",
                    "<aqua>Practicing your clicking speed?</aqua>",
                    "<gradient:#a18cd1:#fbc2eb>Stop pinging, just join already</gradient>"
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
            }
    };

    private static final String[] AFK_EGG_LINE2_CN = {
            "<gradient:#ffd89b:#19547b>大家都在认真挂机中...</gradient>",
            "<gray>服务器正在进行集体静默测试(AFK)</gray>"
    };

    private static final String[] AFK_EGG_LINE2_EN = {
            "<gradient:#ffd89b:#19547b>Everyone is totally working hard</gradient>",
            "<gray>The server is in collective AFK mode</gray>"
    };

    private static final String[] NIGHT_EGG_LINE2_CN = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>夜已深</gradient><white>，记得</white><gradient:#fbc2eb:#a6c1ee>早点睡觉哦</gradient>",
            "<gradient:#f6d365:#fda085>真晚啊</gradient><white>，记得</white><gradient:#84fab0:#8fd3f4>休息哦</gradient>"
    };

    private static final String[] NIGHT_EGG_LINE2_EN = {
            "<gradient:#7f7fd5:#86a8e7:#91eae4>It's getting late</gradient><white>, remember to </white><gradient:#fbc2eb:#a6c1ee>sleep well</gradient>",
            "<gradient:#f6d365:#fda085>Still awake?</gradient><white> Remember to </white><gradient:#84fab0:#8fd3f4>take a rest</gradient>"
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

    private static final Component[] NORMAL_MOTDS_CN = buildMotds(LINE1_CN, NORMAL_LINE2_CN);
    private static final Component[] NORMAL_MOTDS_EN = buildMotds(LINE1_EN, NORMAL_LINE2_EN);
    private static final Component[][] EGG_MOTDS_CN = buildEggMotds(LINE1_CN, EGG_BRANCHES_CN);
    private static final Component[][] EGG_MOTDS_EN = buildEggMotds(LINE1_EN, EGG_BRANCHES_EN);
    private static final Component[] AFK_EGG_MOTDS_CN = buildMotds(LINE1_CN, AFK_EGG_LINE2_CN);
    private static final Component[] AFK_EGG_MOTDS_EN = buildMotds(LINE1_EN, AFK_EGG_LINE2_EN);
    private static final Component[] NIGHT_EGG_MOTDS_CN = buildMotds(LINE1_CN, NIGHT_EGG_LINE2_CN);
    private static final Component[] NIGHT_EGG_MOTDS_EN = buildMotds(LINE1_EN, NIGHT_EGG_LINE2_EN);

    private static Component buildMotd(Component line1, String line2) {
        String centeredLine2 = MotdCenterUtil.center(line2);
        return line1.append(Component.newline()).append(MM.deserialize(centeredLine2));
    }

    private static Component[] buildMotds(Component line1, String[] line2s) {
        Component[] motds = new Component[line2s.length];
        for (int i = 0; i < line2s.length; i++) {
            motds[i] = buildMotd(line1, line2s[i]);
        }
        return motds;
    }

    private static Component[][] buildEggMotds(Component line1, String[][] branches) {
        Component[][] result = new Component[branches.length][];
        for (int b = 0; b < branches.length; b++) {
            result[b] = buildMotds(line1, branches[b]);
        }
        return result;
    }

    private record PingRecord(int count, long lastPingAt, int eggBranch) {}

    private final ConcurrentHashMap<String, PingRecord> pingTracker = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final PlayerAddressModule playerAddressModule;
    private final HolidayMotdCategory holidayCategory;
    private volatile Component cachedStateMotdCn;
    private volatile Component cachedStateMotdEn;
    private volatile boolean cachedLateNight;
    private volatile long nightEasterEggSalt;
    private volatile long ambientMotdIndexSalt;
    private volatile long knownPlayerMotdSalt;
    private BukkitTask stateRefreshTask;

    public MotdModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService,
                      PlayerAddressModule playerAddressModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.playerAddressModule = playerAddressModule;
        this.holidayCategory = new HolidayMotdCategory(plugin);
        this.holidayCategory.setRefreshListener(this::refreshStateMotds);
    }

    public void enable() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        nightEasterEggSalt = rng.nextLong();
        ambientMotdIndexSalt = rng.nextLong();
        knownPlayerMotdSalt = rng.nextLong();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 1200L, 1200L);
        holidayCategory.enable();
        refreshStateMotds();
    }

    public void disable() {
        pingTracker.clear();
        afkService.removeListener(this);
        if (stateRefreshTask != null) {
            stateRefreshTask.cancel();
            stateRefreshTask = null;
        }
        holidayCategory.disable();
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        event.setMaxPlayers(2026);

        InetAddress address = event.getAddress();
        Optional<PlayerAddressRecord> recentAddressRecord = playerAddressModule.resolveRecentByAddress(address);
        Optional<PlayerAddressDisplayRecord> displayPlayer = playerAddressModule.resolveRecentDisplayByAddress(address);
        if (recentAddressRecord.isPresent()) {
            event.setHidePlayers(false);
        } else {
            event.getListedPlayers().clear();
        }

        boolean isCN = resolveMotdLanguage(address, recentAddressRecord.orElse(null)).isChinese();
        Component[] normals = isCN ? NORMAL_MOTDS_CN : NORMAL_MOTDS_EN;
        Component[][] eggs = isCN ? EGG_MOTDS_CN : EGG_MOTDS_EN;

        String ip = address == null ? "unknown" : address.getHostAddress();
        event.motd(resolveMotd(ip, isCN, normals, eggs, getCachedStateMotd(isCN), displayPlayer.orElse(null)));
    }

    private Language resolveMotdLanguage(InetAddress address, PlayerAddressRecord recentAddressRecord) {
        return languageService.language(recentAddressRecord == null ? null : recentAddressRecord.playerId(), address);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshStateMotds();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refreshStateMotds);
    }

    @Override
    public void onAfkStateChanged(Player player, AfkState state) {
        refreshStateMotds();
    }

    private Component resolveMotd(String ip, boolean isCN, Component[] normals, Component[][] eggs,
                                  Component stateMotd, PlayerAddressDisplayRecord knownPlayer) {
        long now = System.currentTimeMillis();

        if (stateMotd != null) {
            return stateMotd;
        }

        Component repeatPingMotd = resolveRepeatPingMotd(ip, eggs, now);
        if (repeatPingMotd != null) {
            return repeatPingMotd;
        }

        Component knownPlayerMotd = resolveKnownPlayerMotd(ip, isCN, knownPlayer, now);
        if (knownPlayerMotd != null) {
            return knownPlayerMotd;
        }

        return resolveAmbientMotd(ip, isCN, normals, now);
    }

    private Component resolveKnownPlayerMotd(String ip, boolean isCN, PlayerAddressDisplayRecord knownPlayer, long now) {
        if (knownPlayer == null || stableMotdIndex(ip, now, KNOWN_PLAYER_EASTER_EGG_ONE_IN, knownPlayerMotdSalt) != 0) {
            return null;
        }

        String[] templates = isCN ? KNOWN_PLAYER_LINE2_CN : KNOWN_PLAYER_LINE2_EN;
        String template = templates[stableMotdIndex(ip, now, templates.length, knownPlayerMotdSalt ^ 0x5f3759dfL)];
        String line = template.replace("{player}", knownPlayer.playerName());
        return buildMotd(isCN ? LINE1_CN : LINE1_EN, line);
    }

    private Component resolveRepeatPingMotd(String ip, Component[][] eggs, long now) {
        if (!canTrackPing(ip)) {
            return null;
        }

        PingRecord record = updatePingRecord(ip, eggs.length, now);
        int eggIndex = record.count() - EASTER_EGG_THRESHOLD - 1;
        Component[] branch = eggs[record.eggBranch()];

        if (record.count() <= EASTER_EGG_THRESHOLD) {
            return null;
        }

        if (eggIndex >= branch.length) {
            pingTracker.remove(ip);
            return null;
        }

        return branch[eggIndex];
    }

    private boolean canTrackPing(String ip) {
        return pingTracker.containsKey(ip) || pingTracker.mappingCount() < MAX_TRACKED_PING_IPS;
    }

    private PingRecord updatePingRecord(String ip, int branchCount, long now) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return pingTracker.compute(ip, (_, prev) -> {
            if (prev == null || now - prev.lastPingAt() > EASTER_EGG_WINDOW_MS) {
                return new PingRecord(1, now, rng.nextInt(branchCount));
            }
            // Browser refreshes often emit duplicate pings within a few ticks.
            if (now - prev.lastPingAt() < DEBOUNCE_MS) {
                return new PingRecord(prev.count(), now, prev.eggBranch());
            }
            return new PingRecord(prev.count() + 1, now, prev.eggBranch());
        });
    }

    private void refreshStateMotds() {
        cachedLateNight = isLateNight(LocalTime.now(SHANGHAI_ZONE));
        cachedStateMotdCn = resolveStateMotd(true, ThreadLocalRandom.current());
        cachedStateMotdEn = resolveStateMotd(false, ThreadLocalRandom.current());
        scheduleNextStateRefresh();
    }

    private Component getCachedStateMotd(boolean isCN) {
        return isCN ? cachedStateMotdCn : cachedStateMotdEn;
    }

    private void scheduleNextStateRefresh() {
        if (stateRefreshTask != null) {
            stateRefreshTask.cancel();
            stateRefreshTask = null;
        }

        long delayTicks = Math.max(1L, Duration.between(
                ZonedDateTime.now(SHANGHAI_ZONE),
                nextTimeBoundary()
        ).toSeconds() * 20L);
        stateRefreshTask = Bukkit.getScheduler().runTaskLater(plugin, this::refreshStateMotds, delayTicks);
    }

    private ZonedDateTime nextTimeBoundary() {
        ZonedDateTime now = ZonedDateTime.now(SHANGHAI_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(SHANGHAI_ZONE);
        ZonedDateTime nextFive = now.toLocalDate().atTime(5, 0).atZone(SHANGHAI_ZONE);
        if (now.isBefore(nextFive)) {
            return nextFive;
        }
        return nextMidnight;
    }

    private Component resolveStateMotd(boolean isCN, ThreadLocalRandom rng) {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);

        var holidayMotd = holidayCategory.resolveLine(today, isCN, rng);
        if (holidayMotd.isPresent()) {
            return buildMotd(isCN ? LINE1_CN : LINE1_EN, holidayMotd.get());
        }

        if (isMostlyAfk()) {
            return randomMotd(isCN ? AFK_EGG_MOTDS_CN : AFK_EGG_MOTDS_EN, rng);
        }

        return null;
    }

    private Component randomMotd(Component[] motds, ThreadLocalRandom rng) {
        return motds[rng.nextInt(motds.length)];
    }

    private Component resolveAmbientMotd(String ip, boolean isCN, Component[] normals, long now) {
        Component[] motds = normals;
        if (shouldShowNightEgg(ip, now)) {
            motds = isCN ? NIGHT_EGG_MOTDS_CN : NIGHT_EGG_MOTDS_EN;
        }
        return motds[stableMotdIndex(ip, now, motds.length)];
    }

    private boolean shouldShowNightEgg(String ip, long now) {
        return cachedLateNight && stableMotdIndex(ip, now, NIGHT_EASTER_EGG_ONE_IN, nightEasterEggSalt) == 0;
    }

    private int stableMotdIndex(String ip, long now, int length) {
        return stableMotdIndex(ip, now, length, ambientMotdIndexSalt);
    }

    private int stableMotdIndex(String ip, long now, int length, long salt) {
        long bucket = now / AMBIENT_MOTD_ROTATION_MS;
        long hash = mix64(salt ^ ip.hashCode() ^ bucket);
        return (int) Long.remainderUnsigned(hash, length);
    }

    private long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private boolean isLateNight(LocalTime now) {
        return !now.isBefore(LocalTime.of(0, 0)) && now.isBefore(LocalTime.of(5, 0));
    }

    private boolean isMostlyAfk() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (onlinePlayers < AFK_EASTER_EGG_MIN_PLAYERS) {
            return false;
        }

        int nonAfkPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!afkService.isAfk(player.getUniqueId())) {
                nonAfkPlayers++;
            }
        }

        int nonAfkThreshold = Math.max(1, (int) Math.floor(onlinePlayers * 0.1));
        return nonAfkPlayers <= nonAfkThreshold;
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - EASTER_EGG_WINDOW_MS;
        pingTracker.entrySet().removeIf(e -> e.getValue().lastPingAt() < cutoff);
    }

}
