package org.encinet.mik.module.social.game;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.identity.IdentityBinding;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The only social component that reads Bukkit player/server state. */
public final class BukkitSocialGameService implements SocialGameService {
    private static final long PROFILE_UPDATE_TIMEOUT_SECONDS = 2;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final SocialMainThreadGateway mainThread;
    private final LuckPerms luckPerms;
    private final MinecraftSkinAvatarService avatars = new MinecraftSkinAvatarService();
    private final long startedAtNanos = System.nanoTime();

    public BukkitSocialGameService(
            JavaPlugin plugin,
            AfkService afkService,
            SocialMainThreadGateway mainThread
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.afkService = Objects.requireNonNull(afkService, "afkService");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        RegisteredServiceProvider<LuckPerms> provider = plugin.getServer()
                .getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = provider == null ? null : provider.getProvider();
    }

    @Override
    public ServerSnapshot serverSnapshot(boolean includePlayerNames) {
        return mainThread.call(() -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            List<ServerSnapshot.PlayerSummary> playerSummaries = includePlayerNames
                    ? players.stream()
                    .map(player -> new ServerSnapshot.PlayerSummary(player.getName(),
                            afkService.isAfk(player.getUniqueId())))
                    .sorted(Comparator.comparing(ServerSnapshot.PlayerSummary::name,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList()
                    : List.of();
            double[] tps = plugin.getServer().getTPS();
            int afk = includePlayerNames
                    ? (int) playerSummaries.stream()
                    .filter(ServerSnapshot.PlayerSummary::afk).count()
                    : (int) players.stream()
                    .filter(player -> afkService.isAfk(player.getUniqueId())).count();
            return new ServerSnapshot(players.size(), plugin.getServer().getMaxPlayers(),
                    playerSummaries, afk, at(tps, 0), at(tps, 1), at(tps, 2),
                    Bukkit.getAverageTickTime(),
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedAtNanos)),
                    Bukkit.getMinecraftVersion());
        });
    }

    @Override
    public SocialPlayerProfile playerProfile(IdentityBinding binding) {
        Objects.requireNonNull(binding, "binding");
        return playerProfile(binding.playerId(), binding.playerName());
    }

    @Override
    public Optional<SocialPlayerProfile> findPlayerProfile(String exactPlayerName) {
        String requested = Objects.requireNonNull(exactPlayerName, "exactPlayerName").strip();
        if (requested.isEmpty() || requested.length() > 64
                || requested.chars().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        Optional<ResolvedPlayer> resolved = mainThread.call(() -> {
            Player online = Bukkit.getPlayerExact(requested);
            OfflinePlayer player = online != null
                    ? online : Bukkit.getOfflinePlayerIfCached(requested);
            if (player == null || (!player.isOnline() && !player.hasPlayedBefore())) {
                return Optional.empty();
            }
            String storedName = player.getName();
            return Optional.of(new ResolvedPlayer(player.getUniqueId(),
                    storedName == null || storedName.isBlank() ? requested : storedName));
        });
        return resolved.map(player -> playerProfile(player.playerId(), player.playerName()));
    }

    @Override
    public boolean isFullMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<Boolean> online = mainThread.call(() -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline()
                    ? Optional.empty() : Optional.of(hasFullMemberPermission(player));
        });
        if (online.isPresent()) {
            return online.orElseThrow();
        }
        if (luckPerms == null) {
            return false;
        }
        try {
            User cached = luckPerms.getUserManager().getUser(playerId);
            User user = cached != null ? cached : luckPerms.getUserManager()
                    .loadUser(playerId)
                    .get(PROFILE_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            var permissions = user.getCachedData().getPermissionData();
            return permissions.checkPermission("group.member").asBoolean()
                    || permissions.checkPermission("group.helper").asBoolean()
                    || permissions.checkPermission("group.manager").asBoolean();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            return false;
        }
    }

    private SocialPlayerProfile playerProfile(UUID playerId, String fallbackName) {
        ProfileSnapshot snapshot = mainThread.call(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            Player player = offlinePlayer.getPlayer();
            boolean online = player != null && player.isOnline();
            SocialPlayerProfile.Presence presence = !online
                    ? SocialPlayerProfile.Presence.OFFLINE
                    : afkService.isAfk(playerId)
                    ? SocialPlayerProfile.Presence.AFK
                    : SocialPlayerProfile.Presence.ONLINE;
            String storedName = offlinePlayer.getName();
            String playerName = online ? player.getName()
                    : storedName == null || storedName.isBlank()
                    ? fallbackName : storedName;
            long playTicks = Math.max(0, offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE));
            return new ProfileSnapshot(playerId, playerName, presence,
                    Duration.ofMillis(playTicks * 50L),
                    instant(offlinePlayer.getFirstPlayed()),
                    instant(offlinePlayer.getLastSeen()), online,
                    offlinePlayer.getPlayerProfile());
        });
        Optional<URI> skinTextureUrl = skinTextureUrl(snapshot.playerProfile());
        if (skinTextureUrl.isEmpty() && !snapshot.online()) {
            skinTextureUrl = updateSkinTextureUrl(snapshot.playerProfile());
        }
        Optional<SocialPlayerAvatar> avatar = skinTextureUrl.flatMap(avatars::avatar);
        return new SocialPlayerProfile(snapshot.playerId(), snapshot.playerName(),
                snapshot.presence(), snapshot.playTime(), snapshot.firstJoined(),
                snapshot.lastSeen(), avatar);
    }

    private static boolean hasFullMemberPermission(Player player) {
        return player.hasPermission("group.member")
                || player.hasPermission("group.helper")
                || player.hasPermission("group.manager");
    }

    private static Optional<Instant> instant(long epochMillis) {
        return epochMillis > 0
                ? Optional.of(Instant.ofEpochMilli(epochMillis)) : Optional.empty();
    }

    private static Optional<URI> updateSkinTextureUrl(PlayerProfile profile) {
        try {
            PlayerProfile updated = profile.update()
                    .get(PROFILE_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return skinTextureUrl(updated);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<URI> skinTextureUrl(PlayerProfile profile) {
        URL skin = profile.getTextures().getSkin();
        if (skin == null) {
            return Optional.empty();
        }
        try {
            URI uri = skin.toURI().normalize();
            String path = uri.getPath();
            if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path == null || !path.matches("/texture/[0-9a-fA-F]{32,128}")) {
                return Optional.empty();
            }
            return Optional.of(URI.create("https://textures.minecraft.net" + path));
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static double at(double[] values, int index) {
        return index < values.length ? values[index] : 0.0;
    }

    private record ProfileSnapshot(
            UUID playerId,
            String playerName,
            SocialPlayerProfile.Presence presence,
            Duration playTime,
            Optional<Instant> firstJoined,
            Optional<Instant> lastSeen,
            boolean online,
            PlayerProfile playerProfile
    ) {
    }

    private record ResolvedPlayer(UUID playerId, String playerName) {
    }

}
