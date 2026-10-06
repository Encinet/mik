package org.encinet.mik.module.communication;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnouncementCatalogTest {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @TempDir Path directory;

    @Test
    void publishesValidJsonForAnnouncementControlCharacters() throws IOException {
        Path file = directory.resolve("announcements.txt");
        String content = "Text\u0001\b\f\"\\中文\nMore";
        Files.writeString(file, dateLine() + "\n" + content, StandardCharsets.UTF_8);
        AnnouncementCatalog catalog = new AnnouncementCatalog(file,
                Logger.getLogger("AnnouncementCatalogTest"));

        AnnouncementCatalog.Snapshot published = catalog.reload();
        String json = new String(published.jsonBytes(), StandardCharsets.UTF_8);
        assertEquals(content, JsonParser.parseString(json).getAsJsonArray()
                .get(0).getAsJsonObject().get("content").getAsString());
        assertEquals(1, published.announcements().size());

        byte[] changed = published.jsonBytes();
        changed[0] = '!';
        assertEquals(json, new String(catalog.current().jsonBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void failedReadKeepsTheLastPublishedSnapshotAndMissingFileClearsIt() throws IOException {
        Path file = directory.resolve("announcements.txt");
        Files.writeString(file, dateLine() + "\nFirst", StandardCharsets.UTF_8);
        AnnouncementCatalog catalog = new AnnouncementCatalog(file,
                Logger.getLogger("AnnouncementCatalogTest"));
        AnnouncementCatalog.Snapshot published = catalog.reload();

        Files.delete(file);
        Files.createDirectory(file);
        assertThrows(IOException.class, catalog::reload);
        assertSame(published, catalog.current());
        assertEquals("First", JsonParser.parseString(new String(catalog.current().jsonBytes(),
                StandardCharsets.UTF_8))
                .getAsJsonArray().get(0).getAsJsonObject().get("content").getAsString());

        Files.delete(file);
        AnnouncementCatalog.Snapshot cleared = catalog.reload();
        assertEquals("[]", new String(cleared.jsonBytes(), StandardCharsets.UTF_8));
        assertEquals(0, cleared.announcements().size());
    }

    @Test
    void includesOlderAnnouncementsAndOrdersTheWholeCatalogNewestFirst() throws IOException {
        Path file = directory.resolve("announcements.txt");
        String older = NOW.minusSeconds(2L * 365 * 24 * 60 * 60)
                .atZone(ZoneId.systemDefault()).format(FILE_DATE);
        Files.writeString(file, older + "\nOlder notice\n---\n"
                + dateLine() + "\nNewer notice", StandardCharsets.UTF_8);
        AnnouncementCatalog catalog = new AnnouncementCatalog(file,
                Logger.getLogger("AnnouncementCatalogTest"));

        AnnouncementCatalog.Snapshot snapshot = catalog.reload();
        assertEquals(2, snapshot.announcements().size());
        assertEquals("Newer notice", snapshot.announcements().getFirst().content());
        assertEquals("Older notice", snapshot.announcements().getLast().content());
        assertEquals("Older notice", JsonParser.parseString(new String(
                snapshot.jsonBytes(), StandardCharsets.UTF_8)).getAsJsonArray()
                .get(1).getAsJsonObject().get("content").getAsString());
    }

    private static String dateLine() {
        return NOW.atZone(ZoneId.systemDefault()).format(FILE_DATE);
    }
}
