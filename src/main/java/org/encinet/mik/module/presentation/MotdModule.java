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
import org.encinet.mik.module.presentation.motd.HolidayMotdService;
import org.encinet.mik.module.presentation.motd.MotdCatalog;
import org.encinet.mik.module.presentation.motd.MotdProfileSpec;
import org.encinet.mik.util.MotdCenterUtil;

import java.net.InetAddress;
import java.time.Duration;
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

    // MOTD text lives in MotdCatalog; this module only owns runtime selection.

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
    private final HolidayMotdService holidayMotdService;
    private final EnumMap<Language, MotdProfile> motdProfiles = new EnumMap<>(Language.class);

    // Replaced atomically after refresh; absent values mean no state override.
    private volatile Map<Language, Component> cachedStateMotds = Map.of();
    private volatile boolean cachedLateNight;

    // Per-session random salts so hashing is not predictable across restarts.
    private long saltNight;
    private long saltAmbient;
    private long saltKnownPlayer;

    private BukkitTask stateRefreshTask;
    private BukkitTask pendingAfkRefreshTask;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public MotdModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService,
                      PlayerAddressModule playerAddressModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.playerAddressModule = playerAddressModule;
        this.holidayMotdService = new HolidayMotdService(plugin);
        this.holidayMotdService.setRefreshListener(this::refreshStateMotds);
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
        holidayMotdService.enable();
        refreshStateMotds();
    }

    public void disable() {
        pingTracker.clear();
        afkService.removeListener(this);
        cancelPendingAfkRefresh();
        cancelRefreshTask();
        holidayMotdService.disable();
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
        scheduleAfkStateRefresh();
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

    private void scheduleAfkStateRefresh() {
        if (pendingAfkRefreshTask != null) {
            return;
        }
        pendingAfkRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingAfkRefreshTask = null;
            refreshStateMotds();
        });
    }

    /**
     * Returns a state-override MOTD, or {@code null} if normal MOTD selection should apply.
     */
    private Component resolveStateMotd(Language language, MotdProfile profile, ThreadLocalRandom rng) {
        Optional<String> holiday = holidayMotdService.resolveLine(language, rng);
        if (holiday.isPresent()) {
            return buildMotd(profile.line1(), holiday.get());
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

    private void cancelPendingAfkRefresh() {
        if (pendingAfkRefreshTask != null) {
            pendingAfkRefreshTask.cancel();
            pendingAfkRefreshTask = null;
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
        for (Language language : Language.values()) {
            registerProfile(language, version, MotdCatalog.profile(language));
        }
    }

    private void registerProfile(Language language, String version, MotdProfileSpec spec) {
        Component line1 = parse(MotdCenterUtil.center(
                spec.brand() + " <gray>| <green>" + version + "<gray> | " + spec.category()));
        motdProfiles.put(language, new MotdProfile(
                line1,
                buildMotds(line1, spec.normal()),
                buildEggMotds(line1, spec.eggs()),
                buildMotds(line1, spec.afk()),
                buildMotds(line1, spec.night()),
                spec.knownPlayer()
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
