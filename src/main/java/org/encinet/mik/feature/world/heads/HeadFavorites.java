package org.encinet.mik.feature.world.heads;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Accessed serially by the feature worker. An unsuccessful write leaves the old file intact. */
final class HeadFavorites {
    private static final int LIMIT = 512;
    private static final int MAX_BYTES = 512 * 1024;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Path folder;

    HeadFavorites(Path folder) { this.folder = folder; }

    Map<String, HeadEntry> load(UUID player) throws IOException {
        Path path = path(player);
        if (!Files.exists(path)) return Map.of();
        try {
            var file = JSON.fromJson(new String(HeadFiles.read(path, MAX_BYTES), StandardCharsets.UTF_8), Saved.class);
            if (file == null || file.schema() != 1 || file.entries().size() > LIMIT)
                throw new IOException("Unsupported head favorites file");
            var entries = new HashMap<String, HeadEntry>();
            for (Favorite favorite : file.entries()) {
                if (entries.putIfAbsent(favorite.id(), HeadEntry.saved(favorite.id(), favorite.name())) != null)
                    throw new IOException("Duplicate favorite head: " + favorite.id());
            }
            return Map.copyOf(entries);
        } catch (RuntimeException error) {
            throw new IOException("Invalid head favorites file", error);
        }
    }

    record Change(Map<String, HeadEntry> entries, boolean full) { }

    Change set(UUID player, HeadEntry entry, boolean saved) throws IOException {
        Map<String, HeadEntry> previous = load(player);
        if (previous.containsKey(entry.id()) == saved) return new Change(previous, false);
        if (saved && previous.size() >= LIMIT) return new Change(previous, true);
        var next = new HashMap<>(previous);
        if (saved) next.put(entry.id(), HeadEntry.saved(entry.id(), entry.name()));
        else next.remove(entry.id());
        var file = new Saved(1, next.keySet().stream().sorted().map(id -> new Favorite(id, next.get(id).name())).toList());
        var bytes = JSON.toJson(file).getBytes(StandardCharsets.UTF_8);
        HeadFiles.replace(path(player), output -> output.write(bytes));
        return new Change(Map.copyOf(next), false);
    }

    private Path path(UUID player) { return folder.resolve(player + ".json"); }

    private record Saved(int schema, List<Favorite> entries) { }
    private record Favorite(String id, String name) { }
}
