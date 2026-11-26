package org.encinet.mik.feature.world.heads;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;
import org.encinet.mik.shared.menu.MenuPlayerSearch;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

/** Uses Paper's detached profile lookup and keeps visibility decisions on the server thread. */
final class HeadPlayers implements AutoCloseable {
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_]{1,16}");
    private static final Pattern COMPACT_UUID = Pattern.compile("[a-fA-F0-9]{32}");
    private static final int MAX_PENDING = 8;
    private static final int MAX_CACHED = 256;
    private static final Duration FOUND_TTL = Duration.ofMinutes(30);
    private static final Duration MISSING_TTL = Duration.ofSeconds(30);
    private final Map<String, CompletableFuture<HeadEntry>> pending = new HashMap<>();
    private final LinkedHashMap<String, Resolved> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Executor main;

    HeadPlayers(Executor main) { this.main = main; }

    List<HeadEntry> online(Player viewer, String query) {
        var candidates = Bukkit.getOnlinePlayers().stream().filter(viewer::canSee)
                .map(player -> new MenuPlayerSearch.Candidate(player.getUniqueId(), player.getName())).toList();
        return MenuPlayerSearch.matching(query, candidates).stream()
                .map(player -> current(viewer, player.id(), null)).filter(java.util.Objects::nonNull).toList();
    }

    HeadEntry current(Player viewer, UUID id, HeadEntry fallback) {
        var player = Bukkit.getPlayer(id);
        if (player != null) return viewer.canSee(player) ? resolved(player.getPlayerProfile()) : null;
        return fallback;
    }

    List<UUID> roster(Player viewer) {
        return Bukkit.getOnlinePlayers().stream().filter(viewer::canSee).map(Player::getUniqueId).sorted().toList();
    }

    boolean visible(Player viewer, UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null || viewer.canSee(player);
    }

    CompletableFuture<HeadEntry> find(Player viewer, String text) {
        String key = text.strip().toLowerCase(Locale.ROOT);
        String value = key.startsWith("player:") ? key.substring(7) : key;
        UUID id = uuid(value);
        if (id == null && !NAME.matcher(value).matches()) return CompletableFuture.completedFuture(null);
        var online = id == null ? Bukkit.getPlayerExact(value) : Bukkit.getPlayer(id);
        if (online != null) return CompletableFuture.completedFuture(viewer.canSee(online) ? resolved(online.getPlayerProfile()) : null);

        var existing = cache.get(value);
        if (existing != null && System.nanoTime() - existing.expires() < 0)
            return CompletableFuture.completedFuture(existing.entry());
        var running = pending.get(value);
        if (running != null) return running;
        if (pending.size() >= MAX_PENDING) return CompletableFuture.failedFuture(new IllegalStateException("Player head lookups are busy"));

        var profile = id == null ? Bukkit.createProfileExact(null, value) : Bukkit.createProfileExact(id, null);
        profile.completeFromCache();
        if (profile.hasTextures() && profile.isComplete()) return CompletableFuture.completedFuture(resolved(profile));

        var result = profile.update().thenApply(HeadPlayers::resolved);
        pending.put(value, result);
        result.whenCompleteAsync((entry, error) -> {
            if (!pending.remove(value, result)) return;
            if (error == null) {
                var ttl = entry == null ? MISSING_TTL : FOUND_TTL;
                cache.put(value, new Resolved(entry, System.nanoTime() + ttl.toNanos()));
                if (cache.size() > MAX_CACHED) cache.pollFirstEntry();
            }
        }, main);
        return result;
    }

    private static UUID uuid(String value) {
        if (COMPACT_UUID.matcher(value).matches()) value = value.substring(0, 8) + '-' + value.substring(8, 12)
                + '-' + value.substring(12, 16) + '-' + value.substring(16, 20) + '-' + value.substring(20);
        if (value.length() != 36) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static HeadEntry resolved(PlayerProfile profile) {
        if (profile.getId() == null || profile.getName() == null || !NAME.matcher(profile.getName()).matches()) return null;
        var skin = profile.getTextures().getSkin();
        String texture = null;
        if (skin != null && "textures.minecraft.net".equalsIgnoreCase(skin.getHost()) && skin.getPath().startsWith("/texture/")) {
            String candidate = skin.getPath().substring(9);
            if (HeadEntry.validTexture(candidate)) texture = candidate;
        }
        return HeadEntry.player(profile.getId(), profile.getName(), texture);
    }

    @Override public void close() {
        var requests = List.copyOf(pending.values());
        pending.clear();
        requests.forEach(request -> request.cancel(false));
        cache.clear();
    }

    private record Resolved(HeadEntry entry, long expires) { }
}
