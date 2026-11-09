package org.encinet.mik.feature.server.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.ProfileResult;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Only successful upstream session checks may write this cache; fallback reads never extend its lifetime. */
final class AuthenticationFallbackStore implements AutoCloseable {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create();
    private final Path path;
    private final Clock clock;
    private final long maxAgeMillis;
    private final int maxPlayers;
    private Connection connection;

    AuthenticationFallbackStore(Path path, Clock clock, Duration maxAge, int maxPlayers) {
        if (maxAge.isNegative() || maxAge.isZero() || maxPlayers < 1) throw new IllegalArgumentException("Invalid cache limits");
        this.path = path;
        this.clock = clock;
        this.maxAgeMillis = maxAge.toMillis();
        this.maxPlayers = maxPlayers;
    }

    synchronized void open() throws Exception {
        if (connection != null) throw new IllegalStateException("Authentication cache already open");
        Files.createDirectories(path.toAbsolutePath().getParent());
        boolean fresh = !Files.exists(path);
        if (Files.isSymbolicLink(path)) throw new SQLException("Authentication cache must not be a symbolic link");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try {
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) { /* Non-POSIX host. */ }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=FULL");
                if (fresh) statement.execute("""
                        CREATE TABLE sessions (
                            uuid TEXT PRIMARY KEY,
                            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                            address BLOB NOT NULL,
                            verified_at INTEGER NOT NULL CHECK(verified_at>=0),
                            profile TEXT NOT NULL CHECK(length(profile)<=65536)
                        ) STRICT
                        """);
                try (var rows = statement.executeQuery("PRAGMA integrity_check")) {
                    if (!rows.next() || !"ok".equals(rows.getString(1)) || rows.next()) {
                        throw new SQLException("Authentication cache integrity check failed");
                    }
                }
                try (var rows = statement.executeQuery("SELECT uuid,name,address,verified_at,profile FROM sessions")) {
                    while (rows.next()) {
                        decode(rows.getString(1), rows.getString(2), rows.getString(5));
                        byte[] address = rows.getBytes(3);
                        if (address == null || address.length != 4 && address.length != 16 || rows.getLong(4) < 0) {
                            throw new SQLException("Invalid cached authentication address or timestamp");
                        }
                    }
                }
            }
            prune(clock.millis());
        } catch (Exception error) {
            try { close(); } catch (SQLException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    synchronized void remember(String requestedName, InetAddress address, ProfileResult result) throws Exception {
        if (address == null || !validName(requestedName) || !requestedName.equalsIgnoreCase(result.profile().name())) return;
        String profile = JSON.toJson(result);
        decode(result.profile().id().toString(), result.profile().name(), profile);
        long now = clock.millis();
        if (now < 0) throw new SQLException("Invalid authentication clock");
        connection.setAutoCommit(false);
        try {
            // A renamed UUID or a reassigned name must not retain the old identity/address binding.
            try (var statement = connection.prepareStatement("DELETE FROM sessions WHERE uuid=? OR name=?")) {
                statement.setString(1, result.profile().id().toString());
                statement.setString(2, result.profile().name());
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO sessions VALUES(?,?,?,?,?)")) {
                statement.setString(1, result.profile().id().toString());
                statement.setString(2, result.profile().name());
                statement.setBytes(3, address.getAddress());
                statement.setLong(4, now);
                statement.setString(5, profile);
                statement.executeUpdate();
            }
            prune(now);
            connection.commit();
        } catch (Exception error) {
            try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    synchronized ProfileResult find(String name, InetAddress address) throws Exception {
        if (address == null || !validName(name)) return null;
        long now = clock.millis();
        try (var statement = connection.prepareStatement("""
                SELECT uuid,name,profile FROM sessions
                WHERE name=? AND address=? AND verified_at>? AND verified_at<=?
                """)) {
            statement.setString(1, name);
            statement.setBytes(2, address.getAddress());
            statement.setLong(3, now - maxAgeMillis);
            statement.setLong(4, now);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? decode(rows.getString(1), rows.getString(2), rows.getString(3)) : null;
            }
        }
    }

    private void prune(long now) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM sessions WHERE verified_at<=?")) {
            statement.setLong(1, now - maxAgeMillis);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                DELETE FROM sessions WHERE uuid IN (
                    SELECT uuid FROM sessions ORDER BY verified_at DESC,uuid LIMIT -1 OFFSET ?)
                """)) {
            statement.setInt(1, maxPlayers);
            statement.executeUpdate();
        }
    }

    private static ProfileResult decode(String uuid, String name, String json) throws SQLException {
        try {
            if (!validName(name) || json == null || json.length() > 65_536) throw new IllegalArgumentException();
            ProfileResult result = JSON.fromJson(json, ProfileResult.class);
            UUID id = UUID.fromString(uuid);
            if (!id.toString().equals(uuid) || result == null || !id.equals(result.profile().id())
                    || !name.equals(result.profile().name()) || result.actions() == null) {
                throw new IllegalArgumentException();
            }
            return new ProfileResult(result.profile(), Set.copyOf(result.actions()));
        } catch (RuntimeException error) {
            throw new SQLException("Invalid cached authentication profile", error);
        }
    }

    private static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

    @Override public synchronized void close() throws SQLException {
        if (connection == null) return;
        try { connection.close(); } finally { connection = null; }
    }
}
