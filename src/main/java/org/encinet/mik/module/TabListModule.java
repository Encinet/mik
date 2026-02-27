package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module for customizing player tab list with LuckPerms prefix and suffix
 */
public class TabListModule implements Listener {

    private final JavaPlugin plugin;
    private LuckPerms luckPerms;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final Component TABLIST_HEADER = MiniMessage.miniMessage().deserialize(
            "<gold><bold>Mi</bold><white><bold>k</bold> <green><bold>Casual</bold></green></white></gold>"
    );

    public TabListModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable tab list module
     */
    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found! TabListModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Update tab list for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerTabList(player);
        }

        plugin.getLogger().info("TabListModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendPlayerListHeaderAndFooter(TABLIST_HEADER, Component.empty());
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> updatePlayerTabList(player), 200L, 2L);
    }

    /**
     * Disable tab list module and reset all players' tab list names
     */
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
        }
    }

    /**
     * Update player's tab list display name with LuckPerms prefix and suffix
     */
    private void updatePlayerTabList(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return;
        }

        CachedMetaData metaData = user.getCachedData().getMetaData();
        String prefix = metaData.getPrefix();
        String suffix = metaData.getSuffix();
        String usernameColor = metaData.getMetaValue("username-color");

        TextComponent.Builder builder = Component.text();

        if (prefix != null && !prefix.isEmpty()) {
            builder.append(miniMessage.deserialize(prefix));
        }

        // escapeTags 会将玩家名中的 < > 等字符转义，防止被 MiniMessage 解析
        String escapedName = miniMessage.escapeTags(player.getName());
        String colorTag = (usernameColor != null && !usernameColor.isEmpty())
                ? usernameColor
                : "<white>";
        builder.append(miniMessage.deserialize(colorTag + escapedName));

        if (suffix != null && !suffix.isEmpty()) {
            builder.append(miniMessage.deserialize(suffix));
        }

        player.playerListName(builder.build());

        Component footer = miniMessage.deserialize(
                "<gray>在线玩家: <green>" + Bukkit.getOnlinePlayers().size() + "</green> <dark_gray>/ <gray>" + Bukkit.getMaxPlayers() + "</gray>"
        );
        player.sendPlayerListHeaderAndFooter(TABLIST_HEADER, footer);
    }
}
