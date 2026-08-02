package org.encinet.mik.module.communication;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.communication.tip.TipCatalog;
import org.encinet.mik.module.communication.tip.TipEntry;
import org.encinet.mik.module.communication.tip.TipIntentMatcher;
import org.encinet.mik.module.communication.tip.TipLegacyMigrator;
import org.encinet.mik.module.communication.tip.TipRenderer;
import org.encinet.mik.module.communication.tip.TipScene;
import org.encinet.mik.module.communication.tip.TipSelector;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Coordinates contextual tip triggers, semantic rendering, selection, and player history. */
public final class TipModule implements Listener {

    private static final long FIRST_PERIODIC_DELAY_TICKS = 20L * 60 * 5;
    private static final long PERIODIC_INTERVAL_TICKS = 20L * 60 * 15;
    private static final long JOIN_DELAY_TICKS = 20L * 90;
    private static final long CHAT_DELAY_TICKS = 12L;
    private static final long RESPAWN_DELAY_TICKS = 20L * 4;
    private static final long WORLD_CHANGE_DELAY_TICKS = 20L * 12;
    private static final long STATE_SAVE_DELAY_TICKS = 20L * 60;
    private static final long CHAT_TOPIC_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final File tipsFile;
    private final File stateFile;
    private final TipCatalog catalog = new TipCatalog();
    private final TipIntentMatcher intentMatcher = new TipIntentMatcher();
    private final TipLegacyMigrator legacyMigrator = new TipLegacyMigrator();
    private final TipSelector selector = new TipSelector();
    private final TipRenderer renderer = new TipRenderer();
    private final Map<UUID, PlayerTipState> playerStates = new HashMap<>();
    private final Map<PendingTrigger, BukkitTask> pendingTriggers = new HashMap<>();

    private List<TipEntry> tips = List.of();
    private BukkitTask broadcastTask;
    private BukkitTask stateSaveTask;
    private boolean stateDirty;

    public TipModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.tipsFile = new File(plugin.getDataFolder(), "tips.txt");
        this.stateFile = new File(plugin.getDataFolder(), "tips-state.yml");
    }

    public void enable() {
        ensureTipsFile();
        reload();
        loadState();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastTips,
                FIRST_PERIODIC_DELAY_TICKS, PERIODIC_INTERVAL_TICKS);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        if (stateSaveTask != null) {
            stateSaveTask.cancel();
            stateSaveTask = null;
        }
        pendingTriggers.values().forEach(BukkitTask::cancel);
        pendingTriggers.clear();
        saveState();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("reloadtips")
                    .requires(source -> source.getSender().hasPermission("mik.command.reloadtips"))
                    .executes(ctx -> {
                        reload();
                        CommandSender sender = ctx.getSource().getSender();
                        sender.sendMessage(Component.text()
                                .append(Component.text(t(sender, Message.TIP_RELOAD_DONE),
                                        NamedTextColor.GREEN))
                                .append(Component.space())
                                .append(Component.text(t(sender, Message.TIP_RELOAD_COUNT, tips.size()),
                                        NamedTextColor.GRAY))
                                .build());
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT,
                            Message.TIP_RELOAD_COMMAND_DESCRIPTION));

            event.registrar().register(Commands.literal("tip")
                    .executes(ctx -> {
                        if (ctx.getSource().getSender() instanceof Player player) {
                            deliver(player, TipScene.MANUAL, null);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT, Message.TIP_COMMAND_DESCRIPTION));
        });
    }

    /** Reloads valid semantic records; a wholly broken update cannot erase a working catalog. */
    public void reload() {
        try {
            TipCatalog.LoadResult result = catalog.load(tipsFile.toPath());
            for (String error : result.errors()) {
                plugin.getLogger().warning("tips.txt " + error);
            }
            if (!result.entries().isEmpty() || result.errors().isEmpty() || tips.isEmpty()) {
                tips = result.entries();
            } else {
                plugin.getLogger().warning("No valid tips were loaded; keeping "
                        + tips.size() + " previous entries");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to read tips.txt: " + exception.getMessage());
        }
    }

    /** Public contextual entry point for other modules; delivery always returns to the main thread. */
    public void trigger(Player player, TipScene scene) {
        trigger(player, scene, null);
    }

    public void trigger(Player player, TipScene scene, String topic) {
        if (player == null || scene == null) throw new NullPointerException();
        String normalizedTopic = normalizeTopic(topic);
        if (topic != null && !topic.isBlank() && normalizedTopic == null) {
            throw new IllegalArgumentException("Invalid tip topic: " + topic);
        }
        Runnable action = () -> {
            if (player.isOnline()) deliver(player, scene, normalizedTopic);
        };
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleTrigger(event.getPlayer().getUniqueId(), TipScene.JOIN, null, JOIN_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleTrigger(event.getPlayer().getUniqueId(), TipScene.RESPAWN,
                null, RESPAWN_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleTrigger(event.getPlayer().getUniqueId(), TipScene.WORLD_CHANGE,
                "teleport", WORLD_CHANGE_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.originalMessage());
        intentMatcher.match(message).ifPresent(match -> {
            UUID playerId = event.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> scheduleTrigger(
                    playerId, TipScene.CHAT, match.topic(), CHAT_DELAY_TICKS));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingTriggers.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId.equals(playerId)) return false;
            entry.getValue().cancel();
            return true;
        });
    }

    private void broadcastTips() {
        if (tips.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            deliver(player, TipScene.PERIODIC, null);
        }
    }

    private boolean deliver(Player player, TipScene scene, String topic) {
        if (tips.isEmpty()) {
            if (scene == TipScene.MANUAL) {
                player.sendMessage(languageService.text(player, Message.TIP_EMPTY,
                        NamedTextColor.GRAY));
            }
            return false;
        }

        long now = System.currentTimeMillis();
        PlayerTipState state = playerStates.computeIfAbsent(
                player.getUniqueId(), ignored -> new PlayerTipState());
        if (!canDeliver(state, scene, topic, now)) return false;
        TipEntry tip = selector.select(tips, scene, topic, state.seenAt,
                        now, ThreadLocalRandom.current())
                .orElse(null);
        if (tip == null) return false;

        state.lastSentAt = now;
        state.seenAt.put(tip.id(), now);
        if (scene != TipScene.MANUAL) state.sceneSentAt.put(scene, now);
        if (topic != null) state.topicSentAt.put(topic, now);
        player.sendMessage(renderer.render(
                languageService.t(player, Message.TIP_LABEL),
                languageService.t(player, Message.TIP_HOVER),
                tip.template(), ThreadLocalRandom.current()));
        markStateDirty();
        return true;
    }

    private static boolean canDeliver(PlayerTipState state, TipScene scene,
                                      String topic, long now) {
        if (scene == TipScene.MANUAL) return true;
        if (now - state.lastSentAt < scene.globalCooldownMillis()) return false;
        if (now - state.sceneSentAt.getOrDefault(scene, 0L)
                < scene.sceneCooldownMillis()) return false;
        return topic == null || now - state.topicSentAt.getOrDefault(topic, 0L)
                >= CHAT_TOPIC_COOLDOWN_MILLIS;
    }

    private void scheduleTrigger(UUID playerId, TipScene scene,
                                 String topic, long delayTicks) {
        PendingTrigger key = new PendingTrigger(playerId, scene);
        BukkitTask previous = pendingTriggers.remove(key);
        if (previous != null) previous.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTriggers.remove(key);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) deliver(player, scene, topic);
        }, delayTicks);
        pendingTriggers.put(key, task);
    }

    private String t(CommandSender sender, Message message, Object... args) {
        if (sender instanceof Player player) return languageService.t(player, message, args);
        return languageService.t(Language.DEFAULT, message, args);
    }

    private void ensureTipsFile() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for tips.txt");
            return;
        }
        if (!tipsFile.exists()) {
            plugin.saveResource("tips.txt", false);
            return;
        }
        upgradeTipsFile();
    }

    private void upgradeTipsFile() {
        Path current = tipsFile.toPath();
        try {
            String source = Files.readString(current);
            boolean legacyDocument = catalog.isLegacyDocument(source);
            boolean previousBundledDefaults = legacyMigrator.isBundledSemanticV2(source);
            if (!legacyDocument && !previousBundledDefaults) return;
            byte[] bundled;
            try (InputStream input = plugin.getResource("tips.txt")) {
                if (input == null) throw new IOException("Bundled tips.txt is unavailable");
                bundled = input.readAllBytes();
            }
            Path backup = nextLegacyBackup(current);
            Files.copy(current, backup);
            String migrated = legacyMigrator.isBundledV1(source) || previousBundledDefaults
                    ? new String(bundled, StandardCharsets.UTF_8)
                    : legacyMigrator.migrate(source);
            Files.writeString(current, migrated, StandardCharsets.UTF_8);
            plugin.getLogger().warning((previousBundledDefaults
                    ? "Updated previous bundled tips.txt defaults"
                    : "Migrated legacy tips.txt to semantic markup")
                    + "; backup: " + backup.getFileName());
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().severe("Failed to migrate legacy tips.txt: "
                    + exception.getMessage());
        }
    }

    private static Path nextLegacyBackup(Path current) throws IOException {
        Path directory = current.toAbsolutePath().getParent();
        if (directory == null) throw new IOException("tips.txt has no parent directory");
        for (int version = 1; version <= 100; version++) {
            Path candidate = directory.resolve("tips.txt.legacy-v" + version);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("Too many legacy tips.txt backups");
    }

    private void loadState() {
        playerStates.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidString : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                PlayerTipState state = new PlayerTipState();
                String base = "players." + uuidString;
                state.lastSentAt = config.getLong(base + ".last-sent-at", 0L);
                loadLongMap(config.getConfigurationSection(base + ".seen"), state.seenAt);
                ConfigurationSection scenes = config.getConfigurationSection(base + ".scenes");
                if (scenes != null) {
                    for (String sceneId : scenes.getKeys(false)) {
                        TipScene.fromId(sceneId)
                                .filter(scene -> scene != TipScene.MANUAL)
                                .ifPresent(scene -> state.sceneSentAt.put(
                                        scene, scenes.getLong(sceneId)));
                    }
                }
                loadLongMap(config.getConfigurationSection(base + ".topics"), state.topicSentAt);
                playerStates.put(uuid, state);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in tips-state.yml: " + uuidString);
            }
        }
        stateDirty = false;
    }

    private static void loadLongMap(ConfigurationSection section, Map<String, Long> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            target.put(key, section.getLong(key));
        }
    }

    private void markStateDirty() {
        stateDirty = true;
        if (stateSaveTask != null) return;
        stateSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stateSaveTask = null;
            saveState();
        }, STATE_SAVE_DELAY_TICKS);
    }

    private void saveState() {
        if (!stateDirty && stateFile.exists()) return;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for tips-state.yml");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerTipState> entry : playerStates.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerTipState state = entry.getValue();
            config.set(base + ".last-sent-at", state.lastSentAt);
            state.seenAt.forEach((id, timestamp) ->
                    config.set(base + ".seen." + id, timestamp));
            state.sceneSentAt.forEach((scene, timestamp) ->
                    config.set(base + ".scenes." + scene.id(), timestamp));
            state.topicSentAt.forEach((topic, timestamp) ->
                    config.set(base + ".topics." + topic, timestamp));
        }
        try {
            config.save(stateFile);
            stateDirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save tips-state.yml: " + exception.getMessage());
        }
    }

    private static String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) return null;
        String normalized = topic.strip().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z][a-z0-9-]{0,31}") ? normalized : null;
    }

    private record PendingTrigger(UUID playerId, TipScene scene) {
    }

    private static final class PlayerTipState {
        private long lastSentAt;
        private final Map<String, Long> seenAt = new HashMap<>();
        private final EnumMap<TipScene, Long> sceneSentAt = new EnumMap<>(TipScene.class);
        private final Map<String, Long> topicSentAt = new HashMap<>();
    }
}
