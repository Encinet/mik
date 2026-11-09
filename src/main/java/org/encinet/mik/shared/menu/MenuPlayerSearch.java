package org.encinet.mik.shared.menu;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.ui.UiKey;

/** Main-thread cached identities plus pure matching for feature-owned offline directories. */
public final class MenuPlayerSearch {
    public record Candidate(UUID id, String name) {
        public Candidate { Objects.requireNonNull(id); if (name == null || name.isBlank()) throw new IllegalArgumentException("Player name is missing"); }
    }
    private MenuPlayerSearch() { }

    public static MenuSearch.Builder<Candidate> builder(Component title, Component placeholder) {
        return MenuSearch.<Candidate>builder(title,placeholder).maximumLength(36)
                .key(player -> UiKey.item(player.id())).label(MenuPlayerSearch::label);
    }

    /** Never resolves an entered name over the network or enumerates offline player files. */
    public static List<Candidate> cached(Player viewer, String query) {
        var candidates = new ArrayList<Candidate>();
        for (Player online : Bukkit.getOnlinePlayers()) if (viewer.canSee(online)) candidates.add(new Candidate(online.getUniqueId(),online.getName()));
        if (!query.isBlank()) {
            OfflinePlayer cached;
            try { cached = Bukkit.getOfflinePlayer(UUID.fromString(query.strip())); }
            catch (IllegalArgumentException notUuid) { cached = Bukkit.getOfflinePlayerIfCached(query.strip()); }
            if (cached != null && cached.getName() != null && (cached.getPlayer() == null || viewer.canSee(cached.getPlayer())))
                candidates.add(new Candidate(cached.getUniqueId(),cached.getName()));
        }
        return List.copyOf(candidates);
    }

    public static List<Candidate> matching(String query, Collection<Candidate> candidates) {
        String value = query.strip().toLowerCase(Locale.ROOT);
        var unique = new LinkedHashMap<UUID,Candidate>();
        for (Candidate player : candidates) unique.putIfAbsent(player.id(),player);
        return unique.values().stream().filter(player -> player.name().toLowerCase(Locale.ROOT).contains(value)
                || player.id().toString().equals(value)).sorted(Comparator.comparingInt((Candidate player) -> rank(player.name(),value))
                .thenComparing(Candidate::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::id)).toList();
    }

    private static int rank(String name, String query) {
        String value = name.toLowerCase(Locale.ROOT);
        return value.equals(query) ? 0 : value.startsWith(query) ? 1 : 2;
    }

    public static Component label(Candidate player) {
        return Component.object(ObjectContents.playerHead(player.id())).append(Component.space()).append(Component.text(player.name(),NamedTextColor.WHITE));
    }
}
