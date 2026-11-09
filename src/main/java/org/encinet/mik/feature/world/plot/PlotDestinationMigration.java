package org.encinet.mik.feature.world.plot;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Startup-only schema upgrades. No runtime model or access rules depend on legacy storage. */
final class PlotDestinationMigration {
    private PlotDestinationMigration() { }

    static void apply(Connection connection) throws SQLException {
        boolean visibility = false;
        try (Statement query = connection.createStatement(); ResultSet columns = query.executeQuery("PRAGMA table_info(plots)")) {
            while (columns.next()) if ("public".equals(columns.getString("name"))) visibility = true;
        }
        boolean arrivals;
        try (Statement query = connection.createStatement(); ResultSet tables = query.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='plot_arrivals'")) {
            arrivals = tables.next();
        }
        var columns = new java.util.HashSet<String>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA table_info(plot_destinations)")) {
            while (rows.next()) columns.add(rows.getString("name"));
        }
        if (!visibility && !arrivals && columns.contains("title") && columns.contains("subtitle")) return;
        connection.setAutoCommit(false);
        try {
            try (Statement query = connection.createStatement()) {
                if (!columns.contains("title")) query.execute("ALTER TABLE plot_destinations ADD COLUMN title TEXT NOT NULL DEFAULT ''");
                if (!columns.contains("subtitle")) query.execute("ALTER TABLE plot_destinations ADD COLUMN subtitle TEXT NOT NULL DEFAULT ''");
            }
            if (arrivals) {
                try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                        "SELECT a.*, p.name AS plot_name, " + (visibility ? "p.public" : "0")
                                + " AS published FROM plot_arrivals a JOIN plots p ON p.id=a.plot_id");
                     PreparedStatement insert = connection.prepareStatement(
                             "INSERT INTO plot_destinations(id,plot_id,name,description,public,x,y,z,yaw,pitch,title,subtitle) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    while (rows.next()) {
                        String plotId = rows.getString("plot_id");
                        String title = plain(rows.getString("title"));
                        String subtitle = plain(rows.getString("subtitle"));
                        insert.setString(1, UUID.nameUUIDFromBytes(("plot-arrival:" + plotId).getBytes(StandardCharsets.UTF_8)).toString());
                        insert.setString(2, plotId); insert.setString(3, rows.getString("plot_name"));
                        insert.setString(4, title + (title.isBlank() || subtitle.isBlank() ? "" : "\n") + subtitle);
                        insert.setInt(5, rows.getInt("published"));
                        insert.setDouble(6, rows.getDouble("x")); insert.setDouble(7, rows.getDouble("y")); insert.setDouble(8, rows.getDouble("z"));
                        insert.setFloat(9, rows.getFloat("yaw")); insert.setFloat(10, rows.getFloat("pitch"));
                        insert.setString(11, rows.getString("title")); insert.setString(12, rows.getString("subtitle"));
                        insert.executeUpdate();
                    }
                }
                try (Statement query = connection.createStatement()) { query.execute("DROP TABLE plot_arrivals"); }
            }
            if (visibility) try (Statement query = connection.createStatement()) { query.execute("ALTER TABLE plots DROP COLUMN public"); }
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        } finally { connection.setAutoCommit(true); }
    }

    private static String plain(String value) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.builder().tags(TagResolver.resolver(
                StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset()))
                .build().deserialize(value));
    }
}
