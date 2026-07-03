package org.encinet.mik.module.chat.delay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.chat.ChatChannelState;
import org.encinet.mik.module.chat.ChatDelayOption;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatDelayScheduler {

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;
    private final DelayedMessageSender sender;
    private final DelayedPreviewSender previewSender;
    private final Map<UUID, List<BukkitTask>> pendingDelayedMessages = new ConcurrentHashMap<>();

    public ChatDelayScheduler(JavaPlugin plugin, LanguageService languageService, ChatSettingsStore settingsStore,
                              DelayedMessageSender sender, DelayedPreviewSender previewSender) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.sender = sender;
        this.previewSender = previewSender;
    }

    public boolean queue(Player player, String plainMessage, ChatChannelState state) {
        ChatDelayOption option = settingsStore.get(player.getUniqueId()).delay();
        if (option.delaySeconds() <= 0) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            remove(playerId, taskHolder[0]);
            Player onlineSender = Bukkit.getPlayer(playerId);
            if (onlineSender != null) {
                sender.sendDelayedMessage(onlineSender, plainMessage, state);
            }
        }, option.delaySeconds() * 20L);

        pendingDelayedMessages
                .computeIfAbsent(playerId, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(taskHolder[0]);
        sendActionBar(player, Component.text(languageService.t(player, Message.CHAT_DELAY_QUEUED, option.id()), NamedTextColor.YELLOW));
        previewSender.sendDelayedPreview(player, plainMessage, state);
        return true;
    }

    public int cancel(UUID playerId) {
        List<BukkitTask> tasks = pendingDelayedMessages.remove(playerId);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int count = 0;
        synchronized (tasks) {
            for (BukkitTask task : tasks) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                    count += 1;
                }
            }
        }
        return count;
    }

    private void remove(UUID playerId, BukkitTask task) {
        List<BukkitTask> tasks = pendingDelayedMessages.get(playerId);
        if (tasks == null) {
            return;
        }
        tasks.remove(task);
        if (tasks.isEmpty()) {
            pendingDelayedMessages.remove(playerId, tasks);
        }
    }

    private void sendActionBar(Player player, Component message) {
        if (Bukkit.isPrimaryThread()) {
            player.sendActionBar(message);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendActionBar(message);
            }
        });
    }

    @FunctionalInterface
    public interface DelayedMessageSender {
        void sendDelayedMessage(Player sender, String plainMessage, ChatChannelState state);
    }

    @FunctionalInterface
    public interface DelayedPreviewSender {
        void sendDelayedPreview(Player sender, String plainMessage, ChatChannelState state);
    }
}
