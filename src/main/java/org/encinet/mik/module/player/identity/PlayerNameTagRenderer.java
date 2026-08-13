package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.NameMetaRenderer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import java.time.Instant;

/** Resolves and renders the LuckPerms prefix/name/suffix portion of an identity. */
public final class PlayerNameTagRenderer {

    private static final int MAX_REPORTED_INVALID_DECORATIONS = 512;
    private static final int MAX_REPORTED_LOAD_FAILURES = 512;
    private static final long USER_LOAD_TIMEOUT_SECONDS = 2;
    private static final Duration RESOLVED_TAG_TTL = Duration.ofSeconds(30);
    private static final int MAX_RESOLVED_TAGS = 2_048;

    private final JavaPlugin plugin;
    private final Set<String> reportedInvalidDecorations = ConcurrentHashMap.newKeySet();
    private final Set<UUID> reportedLoadFailures = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, CachedNameTag> resolvedTags =
            new ConcurrentHashMap<>();
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
        return snapshot(user);
    }

    /**
     * Resolves a stable tag snapshot without blocking the Bukkit primary thread.
     * Callers on a worker thread may load an offline LuckPerms user when necessary.
     */
    public PlayerNameTag resolve(UUID playerId, String playerName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        if (luckPerms == null) {
            return PlayerNameTag.empty();
        }
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user != null) {
            PlayerNameTag current = snapshot(user);
            cache(playerId, current);
            return current;
        }
        if (Bukkit.isPrimaryThread()) {
            reportLoadFailure(playerId, playerName,
                    "offline metadata was requested on the primary thread");
            return PlayerNameTag.empty();
        }
        CachedNameTag cached = resolvedTags.get(playerId);
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.nameTag();
        }
        resolvedTags.remove(playerId, cached);
        try {
            user = luckPerms.getUserManager().loadUser(playerId)
                    .get(USER_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            PlayerNameTag loaded = snapshot(user);
            cache(playerId, loaded);
            return loaded;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            reportLoadFailure(playerId, playerName, "load was interrupted");
        } catch (ExecutionException | TimeoutException | RuntimeException error) {
            reportLoadFailure(playerId, playerName,
                    error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage());
        }
        return PlayerNameTag.empty();
    }

    private void cache(UUID playerId, PlayerNameTag nameTag) {
        if (resolvedTags.size() >= MAX_RESOLVED_TAGS) {
            Instant now = Instant.now();
            resolvedTags.entrySet().removeIf(entry ->
                    !now.isBefore(entry.getValue().expiresAt()));
            if (resolvedTags.size() >= MAX_RESOLVED_TAGS) {
                resolvedTags.clear();
            }
        }
        resolvedTags.put(playerId, new CachedNameTag(
                nameTag, Instant.now().plus(RESOLVED_TAG_TTL)));
    }

    private static PlayerNameTag snapshot(User user) {
        CachedMetaData metadata = user.getCachedData().getMetaData();
        return new PlayerNameTag(metadata.getPrefix(), metadata.getSuffix());
    }

    public Component render(Player player, Component baseName) {
        return render(player, baseName, current(player));
    }

    public Component render(
            OfflinePlayer player,
            Component baseName,
            PlayerNameTag nameTag
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(nameTag, "nameTag");
        return Component.text()
                .append(decoration(player, nameTag.prefix(), "prefix"))
                .append(baseName)
                .append(decoration(player, nameTag.suffix(), "suffix"))
                .build();
    }

    private Component decoration(OfflinePlayer player, String raw, String position) {
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
                        + " for " + displayName(player) + ": " + error.getMessage());
            }
            return NameMetaRenderer.fallback(player, raw);
        }
    }

    private void reportLoadFailure(UUID playerId, String playerName, String detail) {
        if (reportedLoadFailures.size() >= MAX_REPORTED_LOAD_FAILURES) {
            reportedLoadFailures.clear();
        }
        if (reportedLoadFailures.add(playerId)) {
            plugin.getLogger().warning("Failed to load LuckPerms name tag for "
                    + playerName + " (" + playerId + "): " + detail);
        }
    }

    private static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null || name.isBlank()
                ? player.getUniqueId().toString() : name;
    }

    private record CachedNameTag(PlayerNameTag nameTag, Instant expiresAt) {
    }
}
