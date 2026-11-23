package org.encinet.mik.feature.entertainment.music.lyrics;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Main-thread caption projection of the actual audible outputs and their media clocks. */
public final class LyricDisplayService implements AutoCloseable {
    private static final int REFRESH_TICKS = 8;
    private static final int MAX_CAPTION = 120;
    private final LyricsService service;
    private final Function<UUID, Player> players;
    private final Map<UUID, Lyrics.Role> auxiliary = new HashMap<>();
    private final Map<UUID, String> displayed = new HashMap<>();
    private final Set<Output> outputs = new java.util.HashSet<>();
    private int ticks;

    public LyricDisplayService(LyricsService service) { this(service, Bukkit::getPlayer); }

    LyricDisplayService(LyricsService service, Function<UUID, Player> players) {
        this.service = service;
        this.players = players;
    }

    public LyricsService.Playback document(String version) { return service.forPlayback(version); }

    public Runnable start(Location location, LyricsService.Playback document, LongSupplier position) {
        var output = new Output(location.clone(), document, position);
        outputs.add(output);
        return () -> outputs.remove(output);
    }

    public Lyrics.Role auxiliary(UUID player) { return auxiliary.get(player); }

    public void auxiliary(UUID player, Lyrics.Role role) {
        if (role == null) auxiliary.remove(player);
        else if (role != Lyrics.Role.ORIGINAL) auxiliary.put(player, role);
    }

    public void quit(UUID player) {
        auxiliary.remove(player);
        displayed.remove(player);
    }

    public void refresh(Map<Location, Set<UUID>> listeners) {
        var chosen = new HashMap<UUID, Caption>();
        for (var output : outputs) {
            var audience = listeners.getOrDefault(output.location(), Set.of());
            if (audience.isEmpty() || !(output.document().poll() instanceof LyricsService.Available available)) continue;
            long position = output.position().getAsLong();
            for (UUID id : audience) {
                var player = players.apply(id);
                if (player == null || !output.location().getWorld().equals(player.getWorld())) continue;
                double distance = player.getLocation().distanceSquared(output.location());
                var previous = chosen.get(id);
                if (previous != null && distance >= previous.distance()) continue;
                var preference = List.of(new Locale.LanguageRange(player.locale().toLanguageTag().toLowerCase(Locale.ROOT)));
                String caption = captionAt(available.document(), preference, auxiliary.get(id), position);
                if (caption != null) chosen.put(id, new Caption(caption, distance));
            }
        }
        boolean refresh = ++ticks % REFRESH_TICKS == 0;
        chosen.forEach((id, caption) -> {
            if (refresh || !caption.text().equals(displayed.get(id))) send(id, Component.text(caption.text()));
        });
        displayed.keySet().removeAll(chosen.keySet());
        displayed.keySet().forEach(id -> send(id, Component.empty()));
        displayed.clear();
        chosen.forEach((id, caption) -> displayed.put(id, caption.text()));
    }

    static String captionAt(Lyrics document, List<Locale.LanguageRange> preference, Lyrics.Role auxiliary, long position) {
        var primary = document.choose(Lyrics.Role.ORIGINAL, preference).flatMap(track -> track.textAt(position));
        if (primary.isEmpty()) return null;
        String line = shorten(primary.get());
        if (auxiliary == null || auxiliary == Lyrics.Role.ORIGINAL) return line;
        return document.choose(auxiliary, preference).flatMap(track -> track.textAt(position))
                .map(text -> line + " · " + shorten(text)).orElse(line);
    }

    private static String shorten(String text) {
        String line = text.replace('\n', ' ');
        if (line.length() <= MAX_CAPTION) return line;
        int end = MAX_CAPTION - (Character.isHighSurrogate(line.charAt(MAX_CAPTION - 1)) ? 1 : 0);
        return line.substring(0, end) + "…";
    }

    private void send(UUID playerId, Component content) {
        var player = players.apply(playerId);
        if (player != null) player.sendActionBar(content);
    }

    @Override public void close() {
        outputs.clear();
        auxiliary.clear();
        displayed.keySet().forEach(id -> send(id, Component.empty()));
        displayed.clear();
    }

    private record Output(Location location, LyricsService.Playback document, LongSupplier position) { }
    private record Caption(String text, double distance) { }
}
