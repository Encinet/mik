package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.NameMetaRenderer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves and renders the LuckPerms prefix/name/suffix portion of an identity. */
public final class PlayerNameTagRenderer {

    private static final int MAX_REPORTED_INVALID_DECORATIONS = 512;

    private final JavaPlugin plugin;
    private final Set<String> reportedInvalidDecorations = ConcurrentHashMap.newKeySet();
    private LuckPerms luckPerms;

    public PlayerNameTagRenderer(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning(
                    "LuckPerms not found; player name tags will use undecorated names.");
            return;
        }
        luckPerms = provider.getProvider();
    }

    public PlayerNameTag current(Player player) {
        Objects.requireNonNull(player, "player");
        if (luckPerms == null) {
            return PlayerNameTag.empty();
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return PlayerNameTag.empty();
        }
        CachedMetaData metadata = user.getCachedData().getMetaData();
        return new PlayerNameTag(metadata.getPrefix(), metadata.getSuffix());
    }

    public Component render(Player player, Component baseName) {
        return render(player, baseName, current(player));
    }

    public Component render(Player player, Component baseName, PlayerNameTag nameTag) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(nameTag, "nameTag");
        return Component.text()
                .append(decoration(player, nameTag.prefix(), "prefix"))
                .append(baseName)
                .append(decoration(player, nameTag.suffix(), "suffix"))
                .build();
    }

    private Component decoration(Player player, String raw, String position) {
        if (raw.isEmpty()) {
            return Component.empty();
        }
        try {
            return NameMetaRenderer.deserialize(player, raw);
        } catch (RuntimeException error) {
            String failureKey = player.getUniqueId() + "\u0000" + position + "\u0000" + raw;
            if (reportedInvalidDecorations.size() >= MAX_REPORTED_INVALID_DECORATIONS) {
                reportedInvalidDecorations.clear();
            }
            if (reportedInvalidDecorations.add(failureKey)) {
                plugin.getLogger().warning("Failed to parse LuckPerms " + position
                        + " for " + player.getName() + ": " + error.getMessage());
            }
            return NameMetaRenderer.fallback(player, raw);
        }
    }
}
