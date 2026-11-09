package org.encinet.mik.feature.entertainment.music.storage;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;

/** Serialized durable transactions. A failed commit never publishes partially edited memory. */
public final class MusicStore implements AutoCloseable {
    private final ExecutorService writer =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mik-music-store").factory());
    private final Gson gson = new Gson();
    private volatile MusicState state = MusicState.empty();
    private volatile boolean ready;
    private volatile long revision;
    private volatile boolean closed;
    private Connection connection;
    private static final List<String> TABLES = List.of("tracks", "playlists", "devices", "bindings", "groups",
            "programs", "favorites", "recents", "imports", "checkpoints");
    public CompletableFuture<Void> open(Path database) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(database.toAbsolutePath().getParent());
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA busy_timeout=5000");
                    for (String table : TABLES)
                        statement.execute("CREATE TABLE IF NOT EXISTS music_" + table
                                + " (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
                }
                state = new MusicState(read("tracks", StoredTrack.class, Function.identity()),
                        read("playlists", Playlist.class, UUID::fromString), read("devices", MusicDevice.class, UUID::fromString),
                        read("bindings", MusicState.Binding.class, Function.identity()),
                        read("groups", MusicState.Group.class, UUID::fromString),
                        read("programs", PlaybackProgram.class, UUID::fromString),
                        read("favorites", MusicState.Favorite.class, Function.identity()),
                        read("recents", MusicState.Recent.class, UUID::fromString),
                        read("imports", MusicState.ImportReceipt.class, UUID::fromString),
                        read("checkpoints", MusicState.Checkpoint.class, UUID::fromString));
                state.tracks().values().forEach(StoredTrack::track);
                ready = true;
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        }, writer);
    }
    private <K, V> Map<K, V> read(String table, Class<V> type, Function<String, K> keys) throws SQLException {
        Map<K, V> rows = new HashMap<>();
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery("SELECT id,payload FROM music_" + table)) {
            while (result.next()) {
                K key = keys.apply(result.getString(1));
                rows.put(key,
                        Objects.requireNonNull(
                                gson.fromJson(result.getString(2), type), "Corrupt music row"));
            }
        }
        return rows;
    }
    public long revision() {
        return revision;
    }
    public MusicState snapshot() {
        return state;
    }
    public boolean ready() {
        return ready && !closed;
    }
    public <T> CompletableFuture<T> transact(Function<MusicState.Editor, T> edit) {
        if (!ready())
            return CompletableFuture.failedFuture(new IllegalStateException("Music store unavailable"));
        return CompletableFuture.supplyAsync(() -> {
            MusicState before = state;
            MusicState.Editor editor = before.edit();
            T result = edit.apply(editor);
            MusicState next = editor.snapshot();
            try {
                connection.setAutoCommit(false);
                save("tracks", before.tracks(), next.tracks());
                save("playlists", before.playlists(), next.playlists());
                save("devices", before.devices(), next.devices());
                save("bindings", before.bindings(), next.bindings());
                save("groups", before.groups(), next.groups());
                save("programs", before.programs(), next.programs());
                save("favorites", before.favorites(), next.favorites());
                save("recents", before.recents(), next.recents());
                save("imports", before.imports(), next.imports());
                save("checkpoints", before.checkpoints(), next.checkpoints());
                connection.commit();
                state = next;
                revision++;
                return result;
            } catch (Exception error) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                throw new CompletionException(error);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException error) {
                    ready = false;
                }
            }
        }, writer);
    }
    private void save(String table, Map<?, ?> before, Map<?, ?> next) throws SQLException {
        try (PreparedStatement delete =
                        connection.prepareStatement("DELETE FROM music_" + table + " WHERE id=?");
                PreparedStatement upsert = connection.prepareStatement("INSERT INTO music_" + table
                        + ("(id,payload) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET "
                           + "payload=excluded.payload"))) {
            for (Object key : before.keySet())
                if (!next.containsKey(key)) {
                    delete.setString(1, key.toString());
                    delete.addBatch();
                }
            delete.executeBatch();
            for (var entry : next.entrySet())
                if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                    upsert.setString(1, entry.getKey().toString());
                    upsert.setString(2, gson.toJson(entry.getValue()));
                    upsert.addBatch();
                }
            upsert.executeBatch();
        }
    }
    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }, writer);
        writer.shutdown();
        try {
            closing.get(15, TimeUnit.SECONDS);
        } catch (Exception error) {
            writer.shutdownNow();
            throw new IllegalStateException("Music store close failed", error);
        }
    }
}
