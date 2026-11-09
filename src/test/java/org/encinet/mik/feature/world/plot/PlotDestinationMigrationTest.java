package org.encinet.mik.feature.world.plot;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotDestinationMigrationTest {
    @TempDir Path directory;

    @Test void convertsOldPointsOnceAndRemovesOldSchemaWhileKeepingUnrelatedData() throws Exception {
        Path database = directory.resolve("legacy.db");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), withoutPoint = UUID.randomUUID();
        legacy(database, first, second, withoutPoint);
        UUID pointId;
        try (var store = new PlotRepository(database)) {
            store.open(); var points = store.loadDestinations();
            assertEquals(2, points.size());
            var point = points.values().stream().filter(value -> value.plotId().equals(first)).findFirst().orElseThrow();
            pointId = point.id();
            assertEquals("Public park", point.name()); assertTrue(point.publicAccess());
            assertEquals("Welcome\nHave fun", point.description());
            assertEquals("<gold>Welcome</gold>", point.title()); assertEquals("Have fun", point.subtitle());
            assertEquals(-.5, point.x()); assertEquals(65, point.y()); assertEquals(90, point.yaw());
            assertFalse(points.values().stream().filter(value -> value.plotId().equals(second)).findFirst().orElseThrow().publicAccess());
            assertEquals(3, store.load().size());
            assertEquals("Notice", store.loadNoticeBoards().get(first));
            assertFalse(points.values().stream().anyMatch(value -> value.plotId().equals(withoutPoint)));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            assertFalse(columns(connection, "plots").contains("public"));
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='plot_arrivals'")) {
                assertFalse(rows.next());
            }
        }
        try (var store = new PlotRepository(database)) {
            store.open(); assertEquals(2, store.loadDestinations().size()); assertTrue(store.loadDestinations().containsKey(pointId));
        }
    }

    @Test void failedConversionRollsBackRowsAndSchemaAndCanBeRetried() throws Exception {
        Path database = directory.resolve("retry.db"); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        legacy(database, first, second, UUID.randomUUID());
        // A failing insert after another row has been copied must leave all old data available.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database); var query = connection.createStatement()) {
            query.execute("CREATE TABLE plot_destinations(id TEXT PRIMARY KEY,plot_id TEXT,name TEXT,description TEXT,public INTEGER,x REAL,y REAL,z REAL,yaw REAL,pitch REAL)");
            query.execute("CREATE TRIGGER fail_migration BEFORE INSERT ON plot_destinations WHEN NEW.name='Private room' BEGIN SELECT RAISE(ABORT,'migration failure'); END");
        }
        try (var store = new PlotRepository(database)) { assertThrows(SQLException.class, store::open); }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database); var query = connection.createStatement()) {
            assertTrue(columns(connection, "plots").contains("public"));
            assertFalse(columns(connection, "plot_destinations").contains("title"));
            try (var rows = query.executeQuery("SELECT count(*) FROM plot_arrivals")) { assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); }
            try (var rows = query.executeQuery("SELECT count(*) FROM plot_destinations")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
            query.execute("DROP TRIGGER fail_migration");
        }
        try (var store = new PlotRepository(database)) { store.open(); assertEquals(2, store.loadDestinations().size()); }
    }

    @Test void upgradesExistingDestinationsWithoutChangingTheirDetailsAndKeepsNewTitlesAcrossRestart() throws Exception {
        Path database = directory.resolve("destinations.db");
        var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Park",0,
                Set.of(new PlotGeometry.Cell(0,16,0)),java.util.Map.of(),java.util.Map.of(),null);
        var point = new PlotDestination(UUID.randomUUID(),plot.id(),"Gate","Description",true,1,65,1,90,10,"","");
        try (var store = new PlotRepository(database)) {
            store.open(); store.save(plot, null); store.saveDestination(point);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database); var query = connection.createStatement()) {
            query.execute("ALTER TABLE plot_destinations DROP COLUMN title");
            query.execute("ALTER TABLE plot_destinations DROP COLUMN subtitle");
        }
        var titled = point.titles("<gold>Welcome</gold>","<italic>Enjoy</italic>");
        try (var store = new PlotRepository(database)) {
            store.open(); assertEquals(point,store.loadDestinations().get(point.id()));
            store.saveDestination(titled);
        }
        try (var store = new PlotRepository(database)) {
            store.open(); assertEquals(java.util.Map.of(point.id(),titled),store.loadDestinations());
        }
    }

    private void legacy(Path path, UUID first, UUID second, UUID withoutPoint) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path); var query = connection.createStatement()) {
            query.execute("CREATE TABLE plots(id TEXT PRIMARY KEY,world TEXT NOT NULL,owner TEXT NOT NULL,name TEXT NOT NULL,public INTEGER NOT NULL,created_at INTEGER NOT NULL)");
            query.execute("CREATE TABLE plot_arrivals(plot_id TEXT PRIMARY KEY REFERENCES plots(id) ON DELETE CASCADE,x REAL,y REAL,z REAL,yaw REAL,pitch REAL,title TEXT,subtitle TEXT)");
            query.execute("CREATE TABLE plot_notice_boards(plot_id TEXT PRIMARY KEY REFERENCES plots(id),body TEXT)");
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            try (var insert = connection.prepareStatement("INSERT INTO plots VALUES(?,?,?,?,?,?)")) {
                for (var id : new UUID[]{first, second, withoutPoint}) {
                    insert.setString(1,id.toString()); insert.setString(2,world.toString()); insert.setString(3,owner.toString());
                    insert.setString(4,id.equals(first) ? "Public park" : "Private room"); insert.setInt(5,id.equals(second) ? 0 : 1); insert.setLong(6,123); insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO plot_arrivals VALUES(?,?,?,?,?,?,?,?)")) {
                for (var id : new UUID[]{first,second}) {
                    insert.setString(1,id.toString()); insert.setDouble(2,-.5); insert.setDouble(3,65); insert.setDouble(4,1.5);
                    insert.setFloat(5,90); insert.setFloat(6,10); insert.setString(7,"<gold>Welcome</gold>"); insert.setString(8,"Have fun"); insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO plot_notice_boards VALUES(?,?)")) {
                insert.setString(1,first.toString()); insert.setString(2,"Notice"); insert.executeUpdate();
            }
        }
    }
    private Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) names.add(rows.getString("name"));
        }
        return names;
    }
}
