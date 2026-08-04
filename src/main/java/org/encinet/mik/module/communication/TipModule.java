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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.communication.tip.TipCatalog;
import org.encinet.mik.module.communication.tip.TipEntry;
import org.encinet.mik.module.communication.tip.TipIntentMatcher;
import org.encinet.mik.module.communication.tip.TipRenderer;
import org.encinet.mik.module.communication.tip.TipSelector;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Answers recognized chat intents with semantic tips and per-player history. */
public final class TipModule implements Listener {

    private static final long CHAT_DELAY_TICKS = 12L;
    private static final long STATE_SAVE_DELAY_TICKS = 20L * 60;
    private static final long CHAT_GLOBAL_COOLDOWN_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long CHAT_TOPIC_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long CHAT_REPEAT_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(6);
    private static final Pattern TIP_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TIP_OPEN = Pattern.compile("(?i)<tip\\b");
    private static final Pattern REMOVED_TRIGGER_ATTRIBUTE = Pattern.compile(
            "(?is)<tip\\b[^>]*\\b(?:triggers|scenes)\\s*=");
    private static final Pattern REMOVED_PRESENTATION_TAG = Pattern.compile(
            "(?is)<(?:aqua|black|blue|dark_[a-z]+|gold|gray|green|light_purple|red|white|yellow)>"
    );

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final File tipsFile;
    private final File stateFile;
    private final TipCatalog catalog = new TipCatalog();
    private final TipIntentMatcher intentMatcher = new TipIntentMatcher();
    private final TipSelector selector = new TipSelector();
    private final TipRenderer renderer = new TipRenderer();
    private final Map<UUID, PlayerTipState> playerStates = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingChatTips = new HashMap<>();

    private List<TipEntry> tips = List.of();
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
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (stateSaveTask != null) {
            stateSaveTask.cancel();
            stateSaveTask = null;
        }
        pendingChatTips.values().forEach(BukkitTask::cancel);
        pendingChatTips.clear();
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.originalMessage());
        intentMatcher.match(message).ifPresent(match -> {
            UUID playerId = event.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> scheduleChatTip(
                    playerId, match.topic()));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask pending = pendingChatTips.remove(playerId);
        if (pending != null) pending.cancel();
    }

    private boolean deliverChatTip(Player player, String topic) {
        if (tips.isEmpty()) return false;

        long now = System.currentTimeMillis();
        PlayerTipState state = playerStates.computeIfAbsent(
                player.getUniqueId(), ignored -> new PlayerTipState());
        if (!canDeliverChatTip(state, topic, now)) return false;
        TipEntry tip = selector.select(tips, topic, state.seenAt, now,
                        CHAT_REPEAT_COOLDOWN_MILLIS)
                .orElse(null);
        if (tip == null) return false;

        state.lastSentAt = now;
        state.seenAt.put(tip.id(), now);
        state.topicSentAt.put(topic, now);
        player.sendMessage(renderer.render(
                languageService.t(player, Message.TIP_LABEL),
                languageService.t(player, Message.TIP_HOVER),
                tip.template(), ThreadLocalRandom.current()));
        markStateDirty();
        return true;
    }

    private static boolean canDeliverChatTip(PlayerTipState state, String topic, long now) {
        if (now - state.lastSentAt < CHAT_GLOBAL_COOLDOWN_MILLIS) return false;
        return now - state.topicSentAt.getOrDefault(topic, 0L)
                >= CHAT_TOPIC_COOLDOWN_MILLIS;
    }

    private void scheduleChatTip(UUID playerId, String topic) {
        BukkitTask previous = pendingChatTips.remove(playerId);
        if (previous != null) previous.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingChatTips.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) deliverChatTip(player, topic);
        }, CHAT_DELAY_TICKS);
        pendingChatTips.put(playerId, task);
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
        try {
            String source = Files.readString(tipsFile.toPath());
            if (!usesRemovedTipFormat(source)) return;
            Files.delete(tipsFile.toPath());
            plugin.saveResource("tips.txt", false);
            plugin.getLogger().warning(
                    "Deleted removed tips.txt format and restored the chat-only defaults");
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to replace removed tips.txt format: "
                    + exception.getMessage());
        }
    }

    static boolean usesRemovedTipFormat(String source) {
        String document = TIP_COMMENT.matcher(source).replaceAll("");
        if (REMOVED_TRIGGER_ATTRIBUTE.matcher(document).find()) return true;
        return !TIP_OPEN.matcher(document).find()
                && (document.contains("===")
                || REMOVED_PRESENTATION_TAG.matcher(document).find());
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

    private static final class PlayerTipState {
        private long lastSentAt;
        private final Map<String, Long> seenAt = new HashMap<>();
        private final Map<String, Long> topicSentAt = new HashMap<>();
    }
}
