package org.encinet.mik.module.plot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class PlotRepository implements AutoCloseable {
    static final int BATCH_SIZE = 512;
    private static final Comparator<PlotGeometry.Cell> CELL_ORDER = Comparator.comparingInt(PlotGeometry.Cell::x)
            .thenComparingInt(PlotGeometry.Cell::y).thenComparingInt(PlotGeometry.Cell::z);
    private final Path path;
    private final PlotRepository target;
    private final List<SqlOperation> writes = new ArrayList<>();
    private Connection connection;
    private ExecutorService writer;
    private ExecutorService preparer;
    private volatile boolean closing;
    private volatile SQLException writeFailure;

    PlotRepository(Path path) { this.path = path; target = null; }

    private PlotRepository(PlotRepository target) { this.path = target.path; this.target = target; }

    PlotRepository plan() { return new PlotRepository(this); }

    private boolean defer(SqlOperation operation) {
        if (target == null) return false;
        writes.add(operation);
        return true;
    }

    <Result> CompletableFuture<Result> executeAsync(SqlSupplier<Result> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try { return operation.get(); }
            catch (SQLException error) { throw new CompletionException(error); }
        }, writer);
    }

    <Result> CompletableFuture<Result> prepareAsync(SqlSupplier<Result> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try { return operation.get(); }
            catch (SQLException error) { throw new CompletionException(error); }
        }, preparer);
    }

    CompletableFuture<Void> persistAsync(PlotRepository plan) {
        return executeAsync(() -> {
            if (writeFailure != null) throw new SQLException("An earlier plot write could not be drained", writeFailure);
            int attempts = 0;
            while (true) {
                try {
                    plan.commitPlan();
                    return null;
                } catch (SQLException | RuntimeException error) {
                    SQLException failure = error instanceof SQLException sql ? sql
                            : new SQLException("Plot persistence failed", error);
                    if (closing) {
                        writeFailure = failure;
                        throw failure;
                    }
                    attempts++;
                    if (attempts == 1 || attempts % 16 == 0)
                        System.getLogger(PlotRepository.class.getName()).log(System.Logger.Level.WARNING,
                                "Plot persistence failed; retaining accepted changes and retrying in order", failure);
                    try {
                        Thread.sleep(Math.min(5000L, 250L * Math.min(attempts, 20)));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        writeFailure = new SQLException("Plot persistence retry interrupted", interrupted);
                        throw writeFailure;
                    }
                }
            }
        });
    }

    void commitPlan() throws SQLException {
        if (target == null) throw new SQLException("Not a staged plot write");
        target.transaction(() -> {
            for (SqlOperation operation : writes) operation.run();
        });
    }

    void open() throws Exception {
        Files.createDirectories(path.getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE IF NOT EXISTS plots (id TEXT PRIMARY KEY, world TEXT NOT NULL, owner TEXT NOT NULL, name TEXT NOT NULL, public INTEGER NOT NULL, created_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_cells (plot_id TEXT NOT NULL REFERENCES plots(id) ON DELETE CASCADE, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, PRIMARY KEY(plot_id,x,y,z))");
            s.execute("CREATE TABLE IF NOT EXISTS plot_members (plot_id TEXT NOT NULL REFERENCES plots(id) ON DELETE CASCADE, player TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(plot_id,player))");
            s.execute("CREATE TABLE IF NOT EXISTS plot_flags (plot_id TEXT NOT NULL REFERENCES plots(id) ON DELETE CASCADE, flag TEXT NOT NULL, allowed INTEGER NOT NULL, PRIMARY KEY(plot_id,flag))");
            s.execute("CREATE TABLE IF NOT EXISTS plot_arrivals (plot_id TEXT PRIMARY KEY REFERENCES plots(id) ON DELETE CASCADE, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_notice_boards (plot_id TEXT PRIMARY KEY REFERENCES plots(id) ON DELETE CASCADE, body TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS plot_atmospheres (plot_id TEXT PRIMARY KEY REFERENCES plots(id) ON DELETE CASCADE, time_ticks INTEGER CHECK(time_ticks BETWEEN 0 AND 23999), weather TEXT CHECK(weather IN ('CLEAR','RAIN')))");
            s.execute("CREATE TABLE IF NOT EXISTS plot_children (plot_id TEXT PRIMARY KEY REFERENCES plots(id) ON DELETE CASCADE, parent_id TEXT NOT NULL REFERENCES plots(id) ON DELETE RESTRICT, CHECK(plot_id <> parent_id))");
            s.execute("CREATE INDEX IF NOT EXISTS plot_children_parent ON plot_children(parent_id)");
        }
        writer = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mik-plot-storage").daemon(true).factory());
        preparer = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mik-plot-prepare").daemon(true).factory());
    }

    List<Plot> load() throws SQLException {
        List<Plot> result = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rows = s.executeQuery(
                "SELECT plots.*, plot_children.parent_id FROM plots "
                        + "LEFT JOIN plot_children ON plot_children.plot_id=plots.id")) {
            while (rows.next()) {
                String id = rows.getString("id");
                Set<PlotGeometry.Cell> cells = new HashSet<>();
                Map<UUID, Plot.Role> members = new HashMap<>();
                Map<String, Boolean> flags = new HashMap<>();
                try (PreparedStatement q = connection.prepareStatement("SELECT x,y,z FROM plot_cells WHERE plot_id=?")) {
                    q.setString(1, id);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) cells.add(new PlotGeometry.Cell(r.getInt(1), r.getInt(2), r.getInt(3)));
                    }
                }
                try (PreparedStatement q = connection.prepareStatement("SELECT player,role FROM plot_members WHERE plot_id=?")) {
                    q.setString(1, id);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) members.put(UUID.fromString(r.getString(1)), Plot.Role.valueOf(r.getString(2)));
                    }
                }
                try (PreparedStatement q = connection.prepareStatement("SELECT flag,allowed FROM plot_flags WHERE plot_id=?")) {
                    q.setString(1, id);
                    try (ResultSet r = q.executeQuery()) {
                        while (r.next()) flags.put(r.getString(1), r.getInt(2) != 0);
                    }
                }
                String parent = rows.getString("parent_id");
                result.add(new Plot(UUID.fromString(id), UUID.fromString(rows.getString("world")),
                        UUID.fromString(rows.getString("owner")), rows.getString("name"),
                        rows.getInt("public") != 0, rows.getLong("created_at"), cells, members,
                        flags,
                        parent == null ? null : UUID.fromString(parent)));
            }
        }
        return result;
    }

    Map<UUID, PlotArrival> loadArrivals() throws SQLException {
        Map<UUID, PlotArrival> arrivals = new HashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rows = s.executeQuery("SELECT * FROM plot_arrivals")) {
            while (rows.next()) {
                arrivals.put(UUID.fromString(rows.getString("plot_id")),
                        new PlotArrival(rows.getDouble("x"), rows.getDouble("y"),
                                rows.getDouble("z"), rows.getFloat("yaw"),
                                rows.getFloat("pitch"), rows.getString("title"),
                                rows.getString("subtitle")));
            }
        }
        return arrivals;
    }

    Map<UUID, String> loadNoticeBoards() throws SQLException {
        Map<UUID, String> notices = new HashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rows = s.executeQuery("SELECT plot_id,body FROM plot_notice_boards")) {
            while (rows.next()) notices.put(UUID.fromString(rows.getString(1)), rows.getString(2));
        }
        return notices;
    }

    Map<UUID, PlotAtmosphere> loadAtmospheres() throws SQLException {
        Map<UUID, PlotAtmosphere> atmospheres = new HashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rows = s.executeQuery("SELECT plot_id,time_ticks,weather FROM plot_atmospheres")) {
            while (rows.next()) {
                int ticks = rows.getInt("time_ticks");
                Integer time = rows.wasNull() ? null : ticks;
                String weather = rows.getString("weather");
                atmospheres.put(UUID.fromString(rows.getString("plot_id")),
                        new PlotAtmosphere(time, weather == null ? null
                                : PlotAtmosphere.Weather.valueOf(weather)));
            }
        }
        return atmospheres;
    }

    void saveAtmosphere(UUID plotId, PlotAtmosphere atmosphere) throws SQLException {
        if (defer(() -> target.saveAtmosphere(plotId, atmosphere))) return;
        if (atmosphere.followsWorld()) {
            try (PreparedStatement q = connection.prepareStatement(
                    "DELETE FROM plot_atmospheres WHERE plot_id=?")) {
                q.setString(1, plotId.toString());
                q.executeUpdate();
            }
            return;
        }
        try (PreparedStatement q = connection.prepareStatement(
                "INSERT INTO plot_atmospheres(plot_id,time_ticks,weather) VALUES(?,?,?) "
                        + "ON CONFLICT(plot_id) DO UPDATE SET time_ticks=excluded.time_ticks,"
                        + "weather=excluded.weather")) {
            q.setString(1, plotId.toString());
            if (atmosphere.timeTicks() == null) q.setNull(2, java.sql.Types.INTEGER);
            else q.setInt(2, atmosphere.timeTicks());
            if (atmosphere.weather() == null) q.setNull(3, java.sql.Types.VARCHAR);
            else q.setString(3, atmosphere.weather().name());
            q.executeUpdate();
        }
    }

    void saveNoticeBoard(UUID plotId, String body) throws SQLException {
        if (defer(() -> target.saveNoticeBoard(plotId, body))) return;
        if (body.isEmpty()) {
            try (PreparedStatement q = connection.prepareStatement(
                    "DELETE FROM plot_notice_boards WHERE plot_id=?")) {
                q.setString(1, plotId.toString());
                q.executeUpdate();
            }
            return;
        }
        try (PreparedStatement q = connection.prepareStatement(
                "INSERT INTO plot_notice_boards(plot_id,body) VALUES(?,?) "
                        + "ON CONFLICT(plot_id) DO UPDATE SET body=excluded.body")) {
            q.setString(1, plotId.toString());
            q.setString(2, body);
            q.executeUpdate();
        }
    }

    void saveArrival(UUID plotId, PlotArrival arrival) throws SQLException {
        if (defer(() -> target.saveArrival(plotId, arrival))) return;
        try (PreparedStatement q = connection.prepareStatement(
                "INSERT INTO plot_arrivals(plot_id,x,y,z,yaw,pitch,title,subtitle) "
                        + "VALUES(?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(plot_id) DO UPDATE SET x=excluded.x,y=excluded.y,"
                        + "z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch,"
                        + "title=excluded.title,subtitle=excluded.subtitle")) {
            q.setString(1, plotId.toString());
            q.setDouble(2, arrival.x());
            q.setDouble(3, arrival.y());
            q.setDouble(4, arrival.z());
            q.setFloat(5, arrival.yaw());
            q.setFloat(6, arrival.pitch());
            q.setString(7, arrival.title());
            q.setString(8, arrival.subtitle());
            q.executeUpdate();
        }
    }

    void deleteArrival(UUID plotId) throws SQLException {
        if (defer(() -> target.deleteArrival(plotId))) return;
        try (PreparedStatement q = connection.prepareStatement(
                "DELETE FROM plot_arrivals WHERE plot_id=?")) {
            q.setString(1, plotId.toString());
            q.executeUpdate();
        }
    }

    void save(Plot plot, Plot previous) throws SQLException {
        if (defer(() -> target.writePlot(plot, previous))) return;
        transaction(() -> writePlot(plot, previous));
    }

    private void writePlot(Plot plot, Plot previous) throws SQLException {
        if (previous != null && (!previous.id().equals(plot.id())
                || !previous.world().equals(plot.world())
                || previous.createdAt() != plot.createdAt()
                || !java.util.Objects.equals(previous.parentId(), plot.parentId())))
            throw new SQLException("Cannot change a plot's identity, world or creation time");
        if (previous == null || !previous.owner().equals(plot.owner())
                || !previous.name().equals(plot.name())
                || previous.publicProject() != plot.publicProject()) {
            try (PreparedStatement q = connection.prepareStatement("INSERT INTO plots(id,world,owner,name,public,created_at) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET owner=excluded.owner, name=excluded.name, public=excluded.public")) {
                q.setString(1, plot.id().toString());
                q.setString(2, plot.world().toString());
                q.setString(3, plot.owner().toString());
                q.setString(4, plot.name());
                q.setInt(5, plot.publicProject() ? 1 : 0);
                q.setLong(6, plot.createdAt());
                q.executeUpdate();
            }
        }
        if (previous == null || !previous.cells().equals(plot.cells())) {
            if (previous != null) {
                try (PreparedStatement q = connection.prepareStatement(
                        "DELETE FROM plot_cells WHERE plot_id=? AND x=? AND y=? AND z=?")) {
                    String plotId = plot.id().toString();
                    int batch = 0;
                    for (PlotGeometry.Cell cell : previous.cells().stream()
                            .filter(cell -> !plot.cells().contains(cell)).sorted(CELL_ORDER).toList()) {
                        q.setString(1, plotId); q.setInt(2, cell.x()); q.setInt(3, cell.y()); q.setInt(4, cell.z()); q.addBatch();
                        if (++batch == BATCH_SIZE) { q.executeBatch(); q.clearBatch(); batch = 0; }
                    }
                    if (batch > 0) q.executeBatch();
                }
            }
            try (PreparedStatement q = connection.prepareStatement("INSERT INTO plot_cells VALUES(?,?,?,?)")) {
                String plotId = plot.id().toString();
                int batch = 0;
                for (PlotGeometry.Cell cell : plot.cells().stream()
                        .filter(cell -> previous == null || !previous.cells().contains(cell)).sorted(CELL_ORDER).toList()) {
                    q.setString(1, plotId); q.setInt(2, cell.x()); q.setInt(3, cell.y()); q.setInt(4, cell.z()); q.addBatch();
                    if (++batch == BATCH_SIZE) { q.executeBatch(); q.clearBatch(); batch = 0; }
                }
                if (batch > 0) q.executeBatch();
            }
        }
        if (previous == null || !previous.members().equals(plot.members())) {
            clearRows("plot_members", plot.id());
            try (PreparedStatement q = connection.prepareStatement("INSERT INTO plot_members VALUES(?,?,?)")) {
                for (Map.Entry<UUID, Plot.Role> entry : plot.members().entrySet()) {
                    q.setString(1, plot.id().toString()); q.setString(2, entry.getKey().toString()); q.setString(3, entry.getValue().name()); q.addBatch();
                }
                q.executeBatch();
            }
        }
        if (previous == null || !previous.flags().equals(plot.flags())) {
            clearRows("plot_flags", plot.id());
            try (PreparedStatement q = connection.prepareStatement("INSERT INTO plot_flags VALUES(?,?,?)")) {
                for (Map.Entry<String, Boolean> entry : plot.flags().entrySet()) {
                    q.setString(1, plot.id().toString()); q.setString(2, entry.getKey()); q.setInt(3, entry.getValue() ? 1 : 0); q.addBatch();
                }
                q.executeBatch();
            }
        }
        if (previous == null && plot.parentId() != null) {
            try (PreparedStatement q = connection.prepareStatement(
                    "INSERT INTO plot_children(plot_id,parent_id) VALUES(?,?)")) {
                q.setString(1, plot.id().toString());
                q.setString(2, plot.parentId().toString());
                q.executeUpdate();
            }
        }
    }

    private void clearRows(String table, UUID plotId) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement("DELETE FROM " + table + " WHERE plot_id=?")) {
            q.setString(1, plotId.toString());
            q.executeUpdate();
        }
    }

    void rename(UUID id, String name) throws SQLException {
        if (defer(() -> target.rename(id, name))) return;
        try (PreparedStatement q = connection.prepareStatement(
                "UPDATE plots SET name=? WHERE id=?")) {
            q.setString(1, name);
            q.setString(2, id.toString());
            if (q.executeUpdate() != 1) throw new SQLException("Plot no longer exists: " + id);
        }
    }

    void delete(UUID id) throws SQLException {
        if (defer(() -> target.delete(id))) return;
        try (PreparedStatement q = connection.prepareStatement("DELETE FROM plots WHERE id=?")) {
            q.setString(1, id.toString()); q.executeUpdate();
        }
    }

    private void transaction(SqlOperation operation) throws SQLException {
        connection.setAutoCommit(false);
        try { operation.run(); connection.commit(); }
        catch (SQLException | RuntimeException | Error error) {
            try { connection.rollback(); }
            catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
            throw error;
        }
        finally { connection.setAutoCommit(true); }
    }

    @Override public void close() throws SQLException {
        closing = true;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        if (preparer != null) {
            preparer.shutdown();
            awaitTermination(preparer, deadline);
        }
        if (writer != null) {
            writer.shutdown();
            awaitTermination(writer, deadline);
        }
        if (connection != null) connection.close();
        if (writeFailure != null) throw new SQLException("Accepted plot changes could not be persisted before shutdown", writeFailure);
    }

    private static void awaitTermination(ExecutorService executor, long deadline) throws SQLException {
        try {
            if (!executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw new SQLException("Timed out draining plot writes; the active connection was not closed");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while draining plot writes", error);
        }
    }

    interface SqlOperation { void run() throws SQLException; }
    interface SqlSupplier<Result> { Result get() throws SQLException; }
}
