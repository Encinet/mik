package org.encinet.mik.module.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatSettingsStore {

    private static final boolean DEFAULT_MENTION_ALERTS = true;
    private static final boolean DEFAULT_MENTION_SOUND = true;
    private static final boolean DEFAULT_MENTION_ACTION_BAR = true;
    private static final boolean DEFAULT_MENTION_MUTE_WHILE_AFK = false;
    private static final ChatDelayOption DEFAULT_DELAY = ChatDelayOption.OFF;

    private final JavaPlugin plugin;
    private final Map<UUID, ChatSettings> cache = new ConcurrentHashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public ChatSettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        settingsFile = new File(plugin.getDataFolder(), "chat-settings.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("chat-settings.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create chat-settings.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
    }

    public ChatSettings get(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::load);
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    ChatSettings toggleMention(UUID playerId, ChatMentionSetting setting) {
        ChatSettings next = get(playerId).toggle(setting);
        save(playerId, next);
        return next;
    }

    ChatSettings setDelay(UUID playerId, ChatDelayOption delay) {
        ChatSettings next = get(playerId).withDelay(delay);
        save(playerId, next);
        return next;
    }

    private ChatSettings load(UUID playerId) {
        if (settingsData == null) {
            return ChatSettings.defaults();
        }
        String path = playerId.toString();
        return new ChatSettings(
                settingsData.getBoolean(path + ".mention.alerts", DEFAULT_MENTION_ALERTS),
                settingsData.getBoolean(path + ".mention.sound", DEFAULT_MENTION_SOUND),
                settingsData.getBoolean(path + ".mention.action-bar", DEFAULT_MENTION_ACTION_BAR),
                settingsData.getBoolean(path + ".mention.mute-while-afk", DEFAULT_MENTION_MUTE_WHILE_AFK),
                delay(settingsData.getString(path + ".delay", DEFAULT_DELAY.id()))
        );
    }

    private ChatDelayOption delay(String value) {
        ChatDelayOption delay = ChatDelayOption.fromInput(value);
        return delay == null ? DEFAULT_DELAY : delay;
    }

    private void save(UUID playerId, ChatSettings settings) {
        cache.put(playerId, settings);
        if (settingsData == null) {
            return;
        }
        String path = playerId.toString();
        settingsData.set(path + ".mention.alerts", settings.mentionAlerts());
        settingsData.set(path + ".mention.sound", settings.mentionSound());
        settingsData.set(path + ".mention.action-bar", settings.mentionActionBar());
        settingsData.set(path + ".mention.mute-while-afk", settings.mentionMuteWhileAfk());
        settingsData.set(path + ".delay", settings.delay().id());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save chat settings for " + playerId + ": " + e.getMessage());
        }
    }

    public record ChatSettings(
            boolean mentionAlerts,
            boolean mentionSound,
            boolean mentionActionBar,
            boolean mentionMuteWhileAfk,
            ChatDelayOption delay
    ) {
        static ChatSettings defaults() {
            return new ChatSettings(DEFAULT_MENTION_ALERTS, DEFAULT_MENTION_SOUND,
                    DEFAULT_MENTION_ACTION_BAR, DEFAULT_MENTION_MUTE_WHILE_AFK, DEFAULT_DELAY);
        }

        ChatSettings toggle(ChatMentionSetting setting) {
            return switch (setting) {
                case ALERTS -> new ChatSettings(!mentionAlerts, mentionSound, mentionActionBar, mentionMuteWhileAfk, delay);
                case SOUND -> new ChatSettings(mentionAlerts, !mentionSound, mentionActionBar, mentionMuteWhileAfk, delay);
                case ACTION_BAR -> new ChatSettings(mentionAlerts, mentionSound, !mentionActionBar, mentionMuteWhileAfk, delay);
                case MUTE_WHILE_AFK -> new ChatSettings(mentionAlerts, mentionSound, mentionActionBar, !mentionMuteWhileAfk, delay);
            };
        }

        ChatSettings withDelay(ChatDelayOption delay) {
            return new ChatSettings(mentionAlerts, mentionSound, mentionActionBar, mentionMuteWhileAfk, delay);
        }
    }
}
