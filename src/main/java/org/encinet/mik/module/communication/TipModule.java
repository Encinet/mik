package org.encinet.mik.module.communication;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TipModule {

    private static final long FIRST_TIP_DELAY_TICKS = 20L * 60 * 5;
    private static final long TIP_INTERVAL_TICKS = 20L * 60 * 15;
    private static final long SAME_TIP_REPEAT_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final long PLAYER_SEND_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(20);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final File tipsFile;
    private final File stateFile;
    private final Map<UUID, PlayerTipState> playerStates = new HashMap<>();

    private List<Tip> tips = List.of();
    private BukkitTask broadcastTask;

    public TipModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tipsFile = new File(plugin.getDataFolder(), "tips.txt");
        this.stateFile = new File(plugin.getDataFolder(), "tips-state.yml");
    }

    public void enable() {
        ensureTipsFile();
        reload();
        loadState();
        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastTips,
                FIRST_TIP_DELAY_TICKS, TIP_INTERVAL_TICKS);
    }

    public void disable() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        saveState();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("reloadtips")
                    .requires(source -> source.getSender().hasPermission("mik.command.reloadtips"))
                    .executes(ctx -> {
                        reload();
                        ctx.getSource().getSender().sendMessage(
                                Component.text()
                                        .append(Component.text("Tips 已重新加载 ", NamedTextColor.GREEN))
                                        .append(Component.text(tips.size() + " 条", NamedTextColor.GRAY))
                                        .build());
                        return Command.SINGLE_SUCCESS;
                    }).build(), "重新加载 tips.txt");

            event.registrar().register(Commands.literal("tip")
                    .executes(ctx -> {
                        if (ctx.getSource().getSender() instanceof Player player) {
                            sendTip(player, true);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), "查看一条服务器 Tip");
        });
    }

    public void reload() {
        tips = loadTipsFromFile();
    }

    private void broadcastTips() {
        if (tips.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed |= sendTip(player, false);
        }

        if (changed) {
            saveState();
        }
    }

    private boolean sendTip(Player player, boolean force) {
        if (tips.isEmpty()) {
            player.sendMessage(Component.text("暂无 Tip", NamedTextColor.GRAY));
            return false;
        }

        long now = System.currentTimeMillis();
        PlayerTipState state = playerStates.computeIfAbsent(player.getUniqueId(), _ -> new PlayerTipState());
        if (!force && now - state.lastSentAt < PLAYER_SEND_COOLDOWN_MILLIS) {
            return false;
        }

        Tip tip = selectTip(state, now, force);
        if (tip == null) {
            return false;
        }

        state.lastSentAt = now;
        state.seenAt.put(tip.id(), now);
        player.sendMessage(formatTip(tip));
        return true;
    }

    private Tip selectTip(PlayerTipState state, long now, boolean force) {
        List<Tip> neverSeen = tips.stream()
                .filter(tip -> !state.seenAt.containsKey(tip.id()))
                .toList();
        if (!neverSeen.isEmpty()) {
            return neverSeen.get(ThreadLocalRandom.current().nextInt(neverSeen.size()));
        }

        return tips.stream()
                .filter(tip -> force || now - state.seenAt.getOrDefault(tip.id(), 0L) >= SAME_TIP_REPEAT_MILLIS)
                .min(Comparator.comparingLong(tip -> state.seenAt.getOrDefault(tip.id(), 0L)))
                .orElse(null);
    }

    private Component formatTip(Tip tip) {
        return Component.text()
                .append(Component.text("TIP", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("服务器小提示", NamedTextColor.GRAY))))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(MINI_MESSAGE.deserialize(tip.content()))
                .build();
    }

    private void ensureTipsFile() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for tips.txt");
            return;
        }

        if (!tipsFile.exists()) {
            plugin.saveResource("tips.txt", false);
        }
    }

    private List<Tip> loadTipsFromFile() {
        if (!tipsFile.exists()) {
            return List.of();
        }

        List<Tip> loaded = new ArrayList<>();
        try {
            String raw = Files.readString(tipsFile.toPath(), StandardCharsets.UTF_8);
            for (String block : raw.split("===")) {
                String content = block.strip();
                if (!content.isEmpty()) {
                    loaded.add(new Tip(hashTip(content), content));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read tips.txt: " + e.getMessage());
        }
        return loaded;
    }

    private void loadState() {
        playerStates.clear();
        if (!stateFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidString : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                PlayerTipState state = new PlayerTipState();
                String base = "players." + uuidString;
                state.lastSentAt = config.getLong(base + ".last-sent-at", 0L);

                ConfigurationSection seenSection = config.getConfigurationSection(base + ".seen");
                if (seenSection != null) {
                    for (String tipId : seenSection.getKeys(false)) {
                        state.seenAt.put(tipId, seenSection.getLong(tipId));
                    }
                }
                playerStates.put(uuid, state);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in tips-state.yml: " + uuidString);
            }
        }
    }

    private void saveState() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for tips-state.yml");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerTipState> entry : playerStates.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerTipState state = entry.getValue();
            config.set(base + ".last-sent-at", state.lastSentAt);
            for (Map.Entry<String, Long> seenEntry : state.seenAt.entrySet()) {
                config.set(base + ".seen." + seenEntry.getKey(), seenEntry.getValue());
            }
        }

        try {
            config.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tips-state.yml: " + e.getMessage());
        }
    }

    private String hashTip(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private record Tip(String id, String content) {
    }

    private static final class PlayerTipState {
        private long lastSentAt;
        private final Map<String, Long> seenAt = new HashMap<>();
    }
}
