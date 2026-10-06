package org.encinet.mik.module.plot.board;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The game's authoritative community board. UUIDs stay in this database only. */
final class PlotBoardStore implements AutoCloseable {
    private static final long HOUR_MS = 60L * 60 * 1000;
    private static final long ALERT_WINDOW_MS = 7L * 24 * HOUR_MS;

    private final Path path;
    private Connection connection;

    PlotBoardStore(Path path) { this.path = path; }

    synchronized void open() throws Exception {
        Files.createDirectories(path.getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("CREATE TABLE IF NOT EXISTS board_entries ("
                    + "id TEXT PRIMARY KEY, author_uuid TEXT NOT NULL, kind TEXT NOT NULL,"
                    + "status TEXT NOT NULL, related_notice_id TEXT, created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL, public_json TEXT NOT NULL, recipients_json TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS board_entries_created ON board_entries(created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS board_entries_updated ON board_entries(updated_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS board_entries_author_created ON board_entries(author_uuid,created_at)");
        }
    }

    synchronized JsonObject submit(String action, UUID actorId, String actorName,
                                   boolean staff, JsonObject payload) throws SQLException {
        return switch (action) {
            case "create" -> create(actorId, actorName, payload, false);
            case "withdraw" -> withdraw(actorId, payload);
            case "rule" -> rule(actorId, staff, payload);
            default -> throw new Failure("invalid_payload");
        };
    }

    synchronized JsonObject submitAutomaticNotice(UUID ownerId, String ownerName,
                                                  JsonObject payload) throws SQLException {
        return create(ownerId, ownerName, payload, true);
    }

    synchronized JsonArray listPublic() throws SQLException {
        JsonArray notices = new JsonArray();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT public_json FROM board_entries ORDER BY created_at DESC,id DESC LIMIT 300");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) notices.add(publicRecord(rows.getString(1)));
        }
        return notices;
    }

    synchronized JsonObject getPublic(String id) throws SQLException {
        if (!isUuid(id)) return null;
        Entry entry = find(id.toLowerCase());
        return entry == null ? null : publicRecord(entry.record().toString());
    }

    synchronized boolean isAuthor(String id, UUID playerId) throws SQLException {
        Entry entry = find(id);
        return entry != null && entry.authorId().equals(playerId);
    }

    synchronized String findIdByPrefix(String prefix) throws SQLException {
        String match = null;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM board_entries WHERE id LIKE ? LIMIT 2")) {
            query.setString(1, prefix + "%");
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (match != null) throw new Failure("invalid_record");
                    match = rows.getString(1);
                }
            }
        }
        return match;
    }

    synchronized JsonArray listAlerts() throws SQLException {
        JsonArray alerts = new JsonArray();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT public_json,recipients_json FROM board_entries WHERE updated_at>=? "
                        + "ORDER BY updated_at DESC,id DESC")) {
            query.setLong(1, System.currentTimeMillis() - ALERT_WINDOW_MS);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    JsonObject alert = publicRecord(rows.getString(1));
                    alert.add("notifiedRecipients", JsonParser.parseString(rows.getString(2)));
                    alerts.add(alert);
                }
            }
        }
        return alerts;
    }

    private JsonObject create(UUID actorId, String actorName, JsonObject input,
                              boolean automatic) throws SQLException {
        String kind = string(input, "kind", 1, 16);
        if (!kind.equals("notice") && !kind.equals("objection")) throw new Failure("invalid_payload");
        if (automatic && !kind.equals("notice")) throw new Failure("invalid_payload");
        if (automatic && (!input.has("details") || !input.get("details").isJsonObject()))
            throw new Failure("invalid_payload");
        String description = string(input, "text", 12, 1000);
        LinkedHashMap<UUID, String> recipients = recipients(input.get("notifiedPlayers"));
        String relatedId = nullableString(input, "relatedNoticeId");
        Entry related = relatedId == null ? null : find(relatedId);
        if (kind.equals("objection") && relatedId == null) throw new Failure("invalid_record");
        if (relatedId != null && (!isUuid(relatedId) || related == null
                || !related.record().get("kind").getAsString().equals("notice") || kind.equals("notice")))
            throw new Failure("invalid_record");
        JsonObject location = related == null ? input : related.record();
        String world = string(location, "world", 1, 64);
        int x = integer(location, "x"), y = integer(location, "y"), z = integer(location, "z");
        if (related != null && !related.authorId().equals(actorId))
            recipients.putIfAbsent(related.authorId(), related.record().get("authorName").getAsString());
        long now = System.currentTimeMillis();
        if (!automatic) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM board_entries WHERE author_uuid=? AND kind=? AND created_at>=?")) {
                query.setString(1, actorId.toString());
                query.setString(2, kind);
                query.setLong(3, now - HOUR_MS);
                try (ResultSet rows = query.executeQuery()) {
                    if (rows.next() && rows.getInt(1) >= 5) throw new Failure("rate_limited");
                }
            }
        }
        String id = UUID.randomUUID().toString();
        long deadline = now + (kind.equals("notice") ? 72 : 144) * HOUR_MS;
        String subject = automatic ? string(input, "subject", 1, 80)
                : description.replaceAll("\\s+", " ");
        if (subject.length() > 80) subject = subject.substring(0, 80);
        JsonObject record = new JsonObject();
        record.addProperty("id", id);
        record.addProperty("source", automatic ? "plot" : "game");
        record.addProperty("subject", subject);
        record.add("projectId", nullableJson(nullableString(input, "projectId")));
        record.add("relatedNoticeId", nullableJson(relatedId));
        record.addProperty("world", world);
        record.addProperty("x", x);
        record.addProperty("y", y);
        record.addProperty("z", z);
        record.addProperty("authorName", actorName);
        record.addProperty("kind", kind);
        record.addProperty("text", description);
        record.add("details", automatic ? input.getAsJsonObject("details").deepCopy()
                : JsonNull.INSTANCE);
        record.add("notifiedPlayers", recipientNames(recipients));
        record.addProperty("status", "open");
        record.addProperty("createdAt", now);
        record.addProperty("updatedAt", now);
        record.addProperty("deadline", deadline);
        record.add("ruling", JsonNull.INSTANCE);
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO board_entries VALUES(?,?,?,?,?,?,?,?,?)")) {
            query.setString(1, id);
            query.setString(2, actorId.toString());
            query.setString(3, kind);
            query.setString(4, "open");
            query.setString(5, relatedId);
            query.setLong(6, now);
            query.setLong(7, now);
            query.setString(8, record.toString());
            query.setString(9, recipientObjects(recipients).toString());
            query.executeUpdate();
        }
        return record;
    }

    private JsonObject withdraw(UUID actorId, JsonObject input) throws SQLException {
        String id = string(input, "id", 36, 36);
        Entry entry = find(id);
        if (entry == null || !entry.record().get("kind").getAsString().equals("objection")
                || !entry.record().get("status").getAsString().equals("open"))
            throw new Failure("invalid_record");
        if (!entry.authorId().equals(actorId)) throw new Failure("invalid_record");
        JsonObject record = entry.record();
        record.addProperty("status", "withdrawn");
        record.addProperty("updatedAt", System.currentTimeMillis());
        update(id, record, entry.recipients());
        return record;
    }

    private JsonObject rule(UUID actorId, boolean staff, JsonObject input) throws SQLException {
        if (!staff) throw new Failure("invalid_record");
        String id = string(input, "id", 36, 36);
        String ruling = string(input, "ruling", 8, 1000);
        Entry entry = find(id);
        if (entry == null || !entry.record().get("kind").getAsString().equals("objection")
                || !entry.record().get("status").getAsString().equals("open"))
            throw new Failure("invalid_record");
        String relatedId = nullableString(entry.record(), "relatedNoticeId");
        Entry related = relatedId == null ? null : find(relatedId);
        if (entry.authorId().equals(actorId) || related != null && related.authorId().equals(actorId))
            throw new Failure("invalid_record");
        LinkedHashMap<UUID, String> recipients = entry.recipients();
        recipients.putIfAbsent(entry.authorId(), entry.record().get("authorName").getAsString());
        if (related != null)
            recipients.putIfAbsent(related.authorId(), related.record().get("authorName").getAsString());
        recipients.remove(actorId);
        JsonObject record = entry.record();
        record.addProperty("status", "ruled");
        record.addProperty("ruling", ruling);
        record.addProperty("updatedAt", System.currentTimeMillis());
        record.add("notifiedPlayers", recipientNames(recipients));
        update(id, record, recipients);
        return record;
    }

    private void update(String id, JsonObject record, LinkedHashMap<UUID, String> recipients)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "UPDATE board_entries SET status=?,updated_at=?,public_json=?,recipients_json=? WHERE id=?")) {
            query.setString(1, record.get("status").getAsString());
            query.setLong(2, record.get("updatedAt").getAsLong());
            query.setString(3, record.toString());
            query.setString(4, recipientObjects(recipients).toString());
            query.setString(5, id);
            query.executeUpdate();
        }
    }

    private Entry find(String id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT author_uuid,public_json,recipients_json FROM board_entries WHERE id=?")) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return null;
                return new Entry(UUID.fromString(rows.getString(1)),
                        JsonParser.parseString(rows.getString(2)).getAsJsonObject(),
                        recipients(JsonParser.parseString(rows.getString(3))));
            }
        }
    }

    private static JsonObject publicRecord(String json) {
        JsonObject record = JsonParser.parseString(json).getAsJsonObject();
        if (record.get("kind").getAsString().equals("notice")
                && record.get("status").getAsString().equals("open")
                && record.get("deadline").getAsLong() <= System.currentTimeMillis())
            record.addProperty("status", "elapsed");
        return record;
    }

    private static LinkedHashMap<UUID, String> recipients(JsonElement value) {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        if (value == null || !value.isJsonArray()) {
            if (value == null) return result;
            throw new Failure("invalid_payload");
        }
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new Failure("invalid_payload");
            JsonObject person = element.getAsJsonObject();
            String id = string(person, "uuid", 36, 36);
            if (!isUuid(id)) throw new Failure("invalid_payload");
            result.putIfAbsent(UUID.fromString(id), string(person, "name", 1, 64));
        }
        return result;
    }

    private static JsonArray recipientNames(Map<UUID, String> recipients) {
        JsonArray names = new JsonArray();
        recipients.values().forEach(names::add);
        return names;
    }

    private static JsonArray recipientObjects(Map<UUID, String> recipients) {
        JsonArray values = new JsonArray();
        recipients.forEach((id, name) -> {
            JsonObject person = new JsonObject();
            person.addProperty("uuid", id.toString());
            person.addProperty("name", name);
            values.add(person);
        });
        return values;
    }

    private static String string(JsonObject input, String key, int min, int max) {
        try {
            String value = input.get(key).getAsString().strip();
            if (value.length() >= min && value.length() <= max) return value;
        } catch (RuntimeException ignored) { }
        throw new Failure("invalid_payload");
    }

    private static String nullableString(JsonObject input, String key) {
        JsonElement value = input.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static int integer(JsonObject input, String key) {
        try { return input.get(key).getAsInt(); }
        catch (RuntimeException invalid) { throw new Failure("invalid_payload"); }
    }

    private static JsonElement nullableJson(String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private static boolean isUuid(String value) {
        return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    @Override public synchronized void close() throws SQLException {
        if (connection != null) connection.close();
    }

    private record Entry(UUID authorId, JsonObject record, LinkedHashMap<UUID, String> recipients) { }

    static final class Failure extends RuntimeException {
        Failure(String code) { super(code); }
    }
}
