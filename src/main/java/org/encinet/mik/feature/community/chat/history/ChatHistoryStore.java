package org.encinet.mik.feature.community.chat.history;

import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** One bounded durable writer; independent bounded readers also own projection and compression. */
public final class ChatHistoryStore implements AutoCloseable {
    public static final int PAGE_SIZE = 256;
    public static final int PAGE_BYTES = 120 * 1024;
    private static final long RETENTION = Duration.ofDays(7).toMillis();
    private final Path path;
    private final Clock clock;
    private final ThreadPoolExecutor worker;
    private final ThreadPoolExecutor readers;
    private final Set<Connection> readConnections = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Connection> readerConnection = new ThreadLocal<>();
    private final Object lifecycle = new Object();
    private CompletableFuture<?> writeTail = CompletableFuture.completedFuture(null);
    private boolean closing, writerStopped;
    private int pendingReads;
    private final AtomicLong failedWrites = new AtomicLong(), rejectedReads = new AtomicLong(), lastCommitAt = new AtomicLong();
    private volatile ChatHistoryCursor cursors;
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private Connection connection;
    private String databaseId;
    private long lastPruned = Long.MIN_VALUE;

    /** Empty viewers means unrestricted within the channel; a present empty set allows nobody. */
    public record Entry(String id, ChatAudience audience, Optional<Set<UUID>> viewers, String payload) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(audience);
            viewers = Objects.requireNonNull(viewers).map(Set::copyOf);
            Objects.requireNonNull(payload);
            if (id.isBlank() || payload.getBytes(StandardCharsets.UTF_8).length > PAGE_BYTES)
                throw new IllegalArgumentException("Invalid history entry");
        }
    }

    public record Query(UUID viewer, boolean staff, ChatChannel channel, UUID target, long before, long after) {
        public Query(UUID viewer, boolean staff, ChatChannel channel, UUID target, long before) {
            this(viewer, staff, channel, target, before, 0);
        }
        public Query {
            Objects.requireNonNull(viewer);
            Objects.requireNonNull(channel);
            if (before <= 0 || after < 0 || (channel != ChatChannel.PRIVATE && target != null) || viewer.equals(target))
                throw new IllegalArgumentException("Invalid history query");
            if (channel == ChatChannel.STAFF && !staff) throw new IllegalArgumentException("Staff access required");
        }
    }

    public record Request(UUID sender, String id, String fingerprint) {
        public Request { Objects.requireNonNull(sender); Objects.requireNonNull(id); Objects.requireNonNull(fingerprint); }
    }
    public record Saved(String id, String order, boolean inserted) { }
    public record Health(int queuedWrites, int pendingReads, long failedWrites, long rejectedReads, long lastCommitAt) { }
    public Health health() {
        synchronized (lifecycle) { return new Health(worker.getQueue().size(), pendingReads, failedWrites.get(), rejectedReads.get(), lastCommitAt.get()); }
    }
    public record SyncQuery(UUID viewer, boolean staff, ChatChannel channel, UUID target, String since, String page, Set<UUID> hidden) {
        public SyncQuery {
            new Query(viewer, staff, channel, target, Long.MAX_VALUE);
            hidden = Set.copyOf(hidden);
        }
        String binding() {
            return "mik-history-cursor\0" + viewer + "\0" + channel + "\0" + target + "\0"
                    + hidden.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
    public record SyncPage(Page page, String checkpoint, Function<Long, String> continuation, boolean reset) { }
    public record Row(long cursor, String payload, long expiresAt, String compressionScope, String order) {
        public Row(long cursor, String payload, long expiresAt, String compressionScope) {
            this(cursor, payload, expiresAt, compressionScope, ChatOrderKey.next(null, cursor));
        }
        public Row(long cursor, String payload, long expiresAt) { this(cursor, payload, expiresAt, "row:" + cursor); }
        public Row(long cursor, String payload) { this(cursor, payload, System.currentTimeMillis() + RETENTION); }
    }
    /** Rows arrive newest first; nextBefore advances even if a presentation layer hides rows. */
    public record Page(List<Row> rows, boolean hasMore, long nextBefore, String databaseId, long head) {
        public Page { rows = List.copyOf(rows); }
    }

    public ChatHistoryStore(Path path) { this(path, Clock.systemUTC()); }

    ChatHistoryStore(Path path, Clock clock) {
        this.path = path.toAbsolutePath();
        this.clock = Objects.requireNonNull(clock);
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), runnable -> {
            Thread thread = new Thread(runnable, "mik-chat-history");
            thread.setDaemon(true);
            return thread;
        }) {
            @Override protected void terminated() {
                try {
                    if (connection != null) connection.close();
                } catch (SQLException error) { stopped.completeExceptionally(error); }
                synchronized (lifecycle) { writerStopped = true; }
                finishReaders();
            }
        };
        readers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
                Thread.ofPlatform().daemon().name("mik-chat-history-read-", 0).factory()) {
            @Override protected void terminated() {
                SQLException failure = null;
                for (Connection reader : readConnections) {
                    try { reader.close(); }
                    catch (SQLException error) {
                        if (failure == null) failure = error; else failure.addSuppressed(error);
                    }
                }
                readConnections.clear();
                if (failure == null) stopped.complete(null); else stopped.completeExceptionally(failure);
            }
        };
    }

    private void finishReaders() {
        boolean done;
        synchronized (lifecycle) { done = closing && writerStopped && pendingReads == 0; }
        if (done) readers.shutdown();
    }

    public CompletableFuture<Void> maintain() {
        return submit(() -> { prune(); return null; });
    }

    public CompletableFuture<Saved> append(Entry entry) {
        Objects.requireNonNull(entry);
        return append(() -> entry);
    }

    /** Prepares an immutable snapshot on the storage worker, in the same order as writes and reads. */
    public CompletableFuture<Saved> append(Supplier<Entry> snapshot) {
        return append(snapshot, null);
    }

    public CompletableFuture<Saved> append(Supplier<Entry> snapshot, Request request) {
        Objects.requireNonNull(snapshot);
        return submit(() -> {
            Entry entry = Objects.requireNonNull(snapshot.get());
            prune();
            List<String> participants = entry.audience().participants().stream().map(UUID::toString).sorted().toList();
            connection.setAutoCommit(false);
            try {
                if (request != null) {
                    try (var existing = connection.prepareStatement("""
                            SELECT r.fingerprint, m.message_id, m.order_key FROM chat_requests r
                            JOIN chat_messages m ON m.cursor = r.message_cursor WHERE r.sender = ? AND r.request_id = ?
                            """)) {
                        existing.setString(1, request.sender().toString()); existing.setString(2, request.id());
                        try (var result = existing.executeQuery()) {
                            if (result.next()) {
                                if (!request.fingerprint().equals(result.getString(1))) throw new IllegalArgumentException("request_conflict");
                                var saved = new Saved(result.getString(2), result.getString(3), false);
                                connection.commit(); return saved;
                            }
                        }
                    }
                }
                try (var existing = connection.prepareStatement("SELECT order_key FROM chat_messages WHERE message_id = ?")) {
                    existing.setString(1, entry.id());
                    try (var result = existing.executeQuery()) {
                        if (result.next()) { var saved = new Saved(entry.id(), result.getString(1), false); connection.commit(); return saved; }
                    }
                }
                String conversation = entry.audience().channel() + ":" + String.join(":", participants);
                String previous = null;
                try (var tail = connection.prepareStatement("SELECT order_key FROM chat_conversations WHERE conversation = ?")) {
                    tail.setString(1, conversation);
                    try (var result = tail.executeQuery()) { if (result.next()) previous = result.getString(1); }
                }
                String order = ChatOrderKey.next(previous, clock.millis());
                try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO chat_messages(message_id, channel, private_first, private_second, restricted, recorded_at, payload, order_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    insert.setString(1, entry.id());
                    insert.setString(2, entry.audience().channel().name());
                    insert.setString(3, participants.isEmpty() ? null : participants.getFirst());
                    insert.setString(4, participants.isEmpty() ? null : participants.getLast());
                    insert.setInt(5, entry.viewers().isPresent() ? 1 : 0);
                    insert.setLong(6, clock.millis());
                    insert.setString(7, entry.payload());
                    insert.setString(8, order);
                    if (insert.executeUpdate() == 1 && entry.viewers().isPresent()) {
                        try (PreparedStatement viewer = connection.prepareStatement("""
                                INSERT INTO chat_viewers(message_cursor, viewer)
                                SELECT cursor, ? FROM chat_messages WHERE message_id = ?
                                """)) {
                            for (UUID id : entry.viewers().orElseThrow()) {
                                viewer.setString(1, id.toString());
                                viewer.setString(2, entry.id());
                                viewer.addBatch();
                            }
                            viewer.executeBatch();
                        }
                    }
                }
                try (var tail = connection.prepareStatement("INSERT INTO chat_conversations VALUES (?, ?) ON CONFLICT(conversation) DO UPDATE SET order_key = excluded.order_key")) {
                    tail.setString(1, conversation); tail.setString(2, order); tail.executeUpdate();
                }
                if (request != null) {
                    try (var receipt = connection.prepareStatement("INSERT INTO chat_requests SELECT ?, ?, ?, cursor FROM chat_messages WHERE message_id = ?")) {
                        receipt.setString(1, request.sender().toString()); receipt.setString(2, request.id());
                        receipt.setString(3, request.fingerprint()); receipt.setString(4, entry.id()); receipt.executeUpdate();
                    }
                }
                connection.commit(); lastCommitAt.set(clock.millis());
                return new Saved(entry.id(), order, true);
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        });
    }

    public CompletableFuture<Page> read(Query query) {
        return read(query, Function.identity());
    }

    /** Readers wait for preceding writes, then use a separate WAL snapshot and bounded executor. */
    public <T> CompletableFuture<T> read(Query query, Function<Page, T> projection) {
        return readTask(connection -> readPage(connection, query, null), projection);
    }

    public <T> CompletableFuture<T> sync(SyncQuery request, Function<SyncPage, T> projection) {
        return readTask(connection -> {
            String binding = request.binding();
            String token = request.page() != null ? request.page() : request.since();
            var position = cursors.open(token, binding);
            long currentHead;
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'chat_messages'), 0)")) {
                if (!result.next()) throw new SQLException("Missing history position");
                currentHead = result.getLong(1);
            }
            if (position != null && position.head() > currentHead) position = null;
            boolean reset = token != null && position == null;
            long after = position == null ? 0 : position.after();
            Long limit = null;
            long before = Long.MAX_VALUE;
            if (position != null && request.page() != null) { limit = position.head(); before = position.before(); }
            else if (position != null) after = position.head();
            var page = readPage(connection, new Query(request.viewer(), request.staff(), request.channel(), request.target(), before, after), limit);
            String checkpoint = cursors.seal(new ChatHistoryCursor.Position(page.head(), page.head(), Long.MAX_VALUE), binding);
            long start = after;
            return new SyncPage(page, checkpoint,
                    next -> cursors.seal(new ChatHistoryCursor.Position(start, page.head(), next), binding), reset);
        }, projection);
    }

    private Page readPage(Connection connection, Query query, Long limit) throws SQLException {
        long head;
        try (var statement = connection.createStatement(); var results = statement.executeQuery(
                "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'chat_messages'), 0)")) {
            if (!results.next()) throw new SQLException("Missing history position");
            head = limit == null ? results.getLong(1) : Math.min(limit, results.getLong(1));
        }
        String privateClause = query.channel() != ChatChannel.PRIVATE ? "" : " AND (private_first = ? OR private_second = ?)"
                + (query.target() == null ? "" : " AND (private_first = ? OR private_second = ?)");
        Page page;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT cursor, payload, recorded_at, restricted, private_first, private_second, order_key FROM chat_messages
                WHERE channel = ? AND cursor < ? AND recorded_at > ? AND cursor > ? AND cursor <= ?
                AND (restricted = 0 OR EXISTS (
                    SELECT 1 FROM chat_viewers WHERE message_cursor = cursor AND viewer = ?))
                """ + privateClause + " ORDER BY cursor DESC LIMIT ?")) {
            select.setString(1, query.channel().name());
            select.setLong(2, query.before());
            select.setLong(3, clock.millis() - RETENTION);
            select.setLong(4, query.after());
            select.setLong(5, head);
            select.setString(6, query.viewer().toString());
            int parameter = 7;
            if (query.channel() == ChatChannel.PRIVATE) {
                select.setString(parameter++, query.viewer().toString());
                select.setString(parameter++, query.viewer().toString());
                if (query.target() != null) {
                    select.setString(parameter++, query.target().toString());
                    select.setString(parameter++, query.target().toString());
                }
            }
            select.setInt(parameter, PAGE_SIZE + 1);
            List<Row> rows = new ArrayList<>();
            int bytes = 0;
            boolean more = false;
            try (var results = select.executeQuery()) {
                while (results.next()) {
                    String payload = results.getString("payload");
                    int size = payload.getBytes(StandardCharsets.UTF_8).length;
                    if (rows.size() == PAGE_SIZE || (!rows.isEmpty() && bytes + size > PAGE_BYTES)) {
                        more = true;
                        break;
                    }
                    long cursor = results.getLong("cursor");
                    String scope = results.getInt("restricted") != 0 ? "row:" + cursor
                            : query.channel() + ":" + results.getString("private_first") + ":" + results.getString("private_second");
                    rows.add(new Row(cursor, payload, results.getLong("recorded_at") + RETENTION, scope, results.getString("order_key")));
                    bytes += size;
                }
            }
            page = new Page(rows, more, rows.isEmpty() ? query.before() : rows.getLast().cursor(), databaseId, head);
        }
        return page;
    }

    @FunctionalInterface private interface ReadOperation<T> { T run(Connection reader) throws SQLException, IOException; }
    private <S, T> CompletableFuture<T> readTask(ReadOperation<S> operation, Function<S, T> projection) {
        var result = new CompletableFuture<T>();
        synchronized (lifecycle) {
            if (closing || pendingReads >= 66) {
                rejectedReads.incrementAndGet(); result.completeExceptionally(new RejectedExecutionException("History readers busy")); return result;
            }
            if (connection == null) maintain();
            pendingReads++;
            writeTail.handle((_, _) -> null).whenComplete((_, _) -> {
                try {
                    readers.execute(() -> {
                        try {
                            Connection reader = readerConnection.get();
                            if (reader == null) {
                                reader = DriverManager.getConnection("jdbc:sqlite:" + path);
                                try {
                                    try (var statement = reader.createStatement()) {
                                        statement.execute("PRAGMA busy_timeout=5000");
                                        statement.execute("PRAGMA query_only=ON");
                                    }
                                } catch (SQLException error) {
                                    try { reader.close(); } catch (SQLException closeError) { error.addSuppressed(closeError); }
                                    throw error;
                                }
                                readConnections.add(reader);
                                readerConnection.set(reader);
                            }
                            reader.setAutoCommit(false);
                            S snapshot;
                            try { snapshot = operation.run(reader); }
                            finally { reader.rollback(); reader.setAutoCommit(true); }
                            result.complete(projection.apply(snapshot));
                        } catch (SQLException | IOException | RuntimeException error) { result.completeExceptionally(error); }
                        finally {
                            synchronized (lifecycle) { pendingReads--; }
                            finishReaders();
                        }
                    });
                } catch (RejectedExecutionException busy) {
                    rejectedReads.incrementAndGet();
                    result.completeExceptionally(busy);
                    synchronized (lifecycle) { pendingReads--; }
                    finishReaders();
                }
            });
        }
        return result;
    }

    private void open() throws SQLException, IOException {
        if (connection != null) return;
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            if (Files.getFileStore(path.getParent()).supportsFileAttributeView(PosixFileAttributeView.class))
                Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            else Files.createFile(path);
        }
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        Connection opened = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (var statement = opened.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                if (!version.next()) throw new SQLException("Missing chat history schema version");
                int schema = version.getInt(1);
                if (schema != 0 && schema != 1) throw new SQLException("Unsupported chat history schema: " + schema);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        cursor INTEGER PRIMARY KEY AUTOINCREMENT,
                        message_id TEXT NOT NULL UNIQUE,
                        channel TEXT NOT NULL,
                        private_first TEXT, private_second TEXT,
                        restricted INTEGER NOT NULL,
                        recorded_at INTEGER NOT NULL,
                        payload TEXT NOT NULL, order_key TEXT NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_viewers (
                        message_cursor INTEGER NOT NULL REFERENCES chat_messages(cursor) ON DELETE CASCADE,
                        viewer TEXT NOT NULL,
                        PRIMARY KEY(message_cursor, viewer))
                    """);
            statement.execute("CREATE TABLE IF NOT EXISTS chat_conversations (conversation TEXT PRIMARY KEY, order_key TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_requests (sender TEXT NOT NULL, request_id TEXT NOT NULL, fingerprint TEXT NOT NULL, message_cursor INTEGER NOT NULL REFERENCES chat_messages(cursor) ON DELETE CASCADE, PRIMARY KEY(sender, request_id))");
            statement.execute("CREATE INDEX IF NOT EXISTS chat_channel_cursor ON chat_messages(channel, cursor)");
            statement.execute("CREATE INDEX IF NOT EXISTS chat_private_first ON chat_messages(channel, private_first, cursor)");
            statement.execute("CREATE INDEX IF NOT EXISTS chat_private_second ON chat_messages(channel, private_second, cursor)");
            statement.execute("CREATE INDEX IF NOT EXISTS chat_recorded_at ON chat_messages(recorded_at)");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
            try (var insert = opened.prepareStatement("INSERT INTO chat_metadata(name, value) VALUES ('database_id', ?) ON CONFLICT(name) DO NOTHING")) {
                insert.setString(1, UUID.randomUUID().toString()); insert.executeUpdate();
            }
            try (var identity = statement.executeQuery("SELECT value FROM chat_metadata WHERE name = 'database_id'")) {
                if (!identity.next()) throw new SQLException("Missing history database identity");
                databaseId = UUID.fromString(identity.getString(1)).toString();
            }
            try (var insert = opened.prepareStatement("INSERT INTO chat_metadata(name, value) VALUES ('cursor_key', ?) ON CONFLICT(name) DO NOTHING")) {
                byte[] secret = new byte[32]; new java.security.SecureRandom().nextBytes(secret);
                insert.setString(1, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)); insert.executeUpdate();
                java.util.Arrays.fill(secret, (byte) 0);
            }
            try (var result = statement.executeQuery("SELECT value FROM chat_metadata WHERE name = 'cursor_key'")) {
                if (!result.next()) throw new SQLException("Missing history cursor key");
                cursors = new ChatHistoryCursor(result.getString(1), databaseId);
            }
            statement.execute("PRAGMA user_version=1");
            connection = opened;
        } catch (SQLException | RuntimeException error) {
            connection = null;
            try { opened.close(); } catch (SQLException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }

    private void prune() throws SQLException {
        long now = clock.millis();
        if (lastPruned != Long.MIN_VALUE && now - lastPruned < Duration.ofHours(1).toMillis()) return;
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chat_messages WHERE recorded_at <= ?")) {
            delete.setLong(1, now - RETENTION);
            delete.executeUpdate();
        }
        lastPruned = now;
    }

    @FunctionalInterface private interface Operation<T> { T run() throws SQLException, IOException; }

    private <T> CompletableFuture<T> submit(Operation<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (lifecycle) {
            try {
                if (closing) throw new RejectedExecutionException("History closed");
                worker.execute(() -> {
                    try { open(); result.complete(operation.run()); }
                    catch (SQLException | IOException | RuntimeException error) { failedWrites.incrementAndGet(); result.completeExceptionally(error); }
                });
                writeTail = result;
            } catch (RejectedExecutionException busy) { failedWrites.incrementAndGet(); result.completeExceptionally(busy); }
        }
        return result;
    }

    /** Drains accepted work and closes the connection on the worker, without blocking the game thread. */
    @Override public void close() {
        synchronized (lifecycle) { closing = true; }
        worker.shutdown();
        finishReaders();
    }
    public CompletableFuture<Void> stopped() { return stopped; }
}
