package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.settings.ChatDelayOption;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class ChatDelayScheduler {

    private final Plugin plugin;
    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;
    private final DelayedMessageSender sender;
    private final DelayedPreviewSender previewSender;
    private final Map<UUID, List<BukkitTask>> pendingDelayedMessages = new HashMap<>();
    private boolean active;

    public ChatDelayScheduler(Plugin plugin, LanguageService languageService, ChatSettingsStore settingsStore,
                              DelayedMessageSender sender, DelayedPreviewSender previewSender) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.sender = sender;
        this.previewSender = previewSender;
    }

    void enable() { active = true; }

    void close() {
        active = false;
        pendingDelayedMessages.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        pendingDelayedMessages.clear();
    }

    boolean queue(Player player, Component body, ChatChannelState state, GameChatService.Scope scope, Consumer<Player> onAccepted) {
        UUID playerId = player.getUniqueId();
        ChatDelayOption option = settingsStore.cachedDelay(playerId);
        if (option.delaySeconds() <= 0) return false;
        Runnable enqueue = () -> {
            if (active && current(player, playerId)) schedule(player, playerId, body, state, scope, onAccepted, option);
        };
        if (Bukkit.isPrimaryThread()) enqueue.run();
        else Bukkit.getScheduler().runTask(plugin, enqueue);
        return true;
    }

    private void schedule(Player player, UUID playerId, Component body, ChatChannelState state,
            GameChatService.Scope scope, Consumer<Player> onAccepted, ChatDelayOption option) {
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            remove(playerId, taskHolder[0]);
            if (active && current(player, playerId)) sender.sendDelayedMessage(player, body, state, scope, onAccepted);
        }, option.delaySeconds() * 20L);

        pendingDelayedMessages.computeIfAbsent(playerId, _ -> new ArrayList<>()).add(taskHolder[0]);
        player.sendActionBar(Component.text(languageService.t(player, Message.CHAT_DELAY_QUEUED, option.id()), NamedTextColor.YELLOW));
        previewSender.sendDelayedPreview(player, body, state, scope);
    }

    int cancel(UUID playerId) {
        List<BukkitTask> tasks = pendingDelayedMessages.remove(playerId);
        if (tasks == null) return 0;
        tasks.forEach(BukkitTask::cancel);
        return tasks.size();
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

    private static boolean current(Player player, UUID id) {
        return Bukkit.getPlayer(id) == player && player.isOnline();
    }

    @FunctionalInterface
    public interface DelayedMessageSender {
        void sendDelayedMessage(Player sender, Component body, ChatChannelState state, GameChatService.Scope scope,
                                Consumer<Player> onAccepted);
    }

    @FunctionalInterface
    public interface DelayedPreviewSender {
        void sendDelayedPreview(Player sender, Component body, ChatChannelState state, GameChatService.Scope scope);
    }
}
