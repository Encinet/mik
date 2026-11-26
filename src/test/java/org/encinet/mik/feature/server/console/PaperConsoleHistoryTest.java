package org.encinet.mik.feature.server.console;

import org.encinet.mik.integration.mikd.ConsolePort.LogPage;
import org.encinet.mik.integration.mikd.ConsolePort.LogQuery;
import org.encinet.mik.integration.mikd.ConsolePort.Position;
import org.encinet.mik.integration.mikd.ConsolePort.Work;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperConsoleHistoryTest {
    @TempDir Path directory;

    @Test
    void readsInBothDirectionsAcrossTheActiveFileAndNumberedArchives() throws Exception {
        var active = lines("活动", 240);
        var older = lines("older", 205);
        var recent = List.of("archived before restart", "🧱");
        Files.writeString(directory.resolve("latest.log"), complete(active));
        archive("2026-11-25-2", complete(older));
        archive("2026-11-25-10", String.join("\n", recent));
        var history = new PaperConsoleHistory(directory);

        var latest = read(history, Position.LATEST, "");
        assertEquals(active.subList(40, 240), latest.lines());
        var startup = read(history, Position.BEFORE, latest.before());
        assertEquals(active.subList(0, 40), startup.lines());
        var prior = read(history, Position.BEFORE, startup.before());
        assertEquals(recent, prior.lines());
        var olderTail = read(history, Position.BEFORE, prior.before());
        assertEquals(older.subList(5, 205), olderTail.lines());
        var first = read(history, Position.BEFORE, olderTail.before());
        assertEquals(older.subList(0, 5), first.lines());
        assertTrue(first.before().isEmpty());

        assertEquals(olderTail.lines(), read(history, Position.AFTER, first.after()).lines());
        assertEquals(prior.lines(), read(history, Position.AFTER, olderTail.after()).lines());
        var nextActive = read(history, Position.AFTER, prior.after());
        assertEquals(active.subList(0, 200), nextActive.lines());
        assertEquals(active.subList(200, 240), read(history, Position.AFTER, nextActive.after()).lines());
    }

    @Test
    void followsCompletedLinesAndLoadsTheArchiveAfterRotation() throws Exception {
        Path latest = directory.resolve("latest.log");
        Files.writeString(latest, "first\r\n続");
        var history = new PaperConsoleHistory(directory);
        var start = read(history, Position.LATEST, "");
        assertEquals(List.of("first"), start.lines());
        assertTrue(read(history, Position.AFTER, start.after()).lines().isEmpty());
        Files.writeString(latest, "き\n", StandardOpenOption.APPEND);
        var updated = read(history, Position.AFTER, start.after());
        assertEquals(List.of("続き"), updated.lines());

        Files.move(latest, directory.resolve("old-uncompressed"));
        archive("2026-11-25-1", "first\r\n続き\n");
        Files.writeString(latest, "replacement\n");
        assertThrows(IOException.class, () -> read(history, Position.AFTER, updated.after()));
        var replacement = read(history, Position.LATEST, "");
        assertEquals(List.of("replacement"), replacement.lines());
        assertEquals(List.of("first", "続き"), read(history, Position.BEFORE, replacement.before()).lines());
    }

    @Test
    void reportsUnreadableArchivesAndContinuesToTheAvailableHistory() throws Exception {
        archive("2026-11-22-1", "available\n");
        Files.writeString(directory.resolve("2026-11-23-1.log.gz"), "corrupt");
        Files.writeString(directory.resolve("latest.log"), "");
        var history = new PaperConsoleHistory(directory);

        var fallback = read(history, Position.LATEST, "");
        assertEquals(List.of("available"), fallback.lines());
        assertTrue(fallback.notice().contains("2026-11-23-1.log.gz"));
        assertTrue(fallback.before().isEmpty());
        Files.writeString(directory.resolve("latest.log"), "new\n");
        assertEquals(List.of("new"), read(history, Position.AFTER, fallback.after()).lines());
    }

    @Test
    void boundsOversizedLinesWithoutLosingTheAdjacentEntries() throws Exception {
        String longLine = "水".repeat(30_000);
        Files.writeString(directory.resolve("latest.log"), "before\n" + longLine + "\n" + (longLine + "\n").repeat(9));
        var history = new PaperConsoleHistory(directory);
        var latest = read(history, Position.LATEST, "");
        assertTrue(latest.lines().size() < 10);
        assertTrue(latest.lines().stream().allMatch(line -> line.endsWith(" …")));
        assertTrue(latest.lines().stream().mapToInt(line -> line.getBytes(StandardCharsets.UTF_8).length).sum() <= 64 * 1024);

        var earlier = read(history, Position.BEFORE, latest.before());
        assertEquals("before", earlier.lines().getFirst());
        assertEquals(latest.lines(), read(history, Position.AFTER, earlier.after()).lines());
    }

    private static LogPage read(PaperConsoleHistory history, Position position, String cursor) throws IOException {
        var work = new Work(System.nanoTime() + Duration.ofSeconds(3).toNanos(), () -> true);
        return history.read(new LogQuery(position, cursor), work);
    }

    private void archive(String name, String content) throws IOException {
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(directory.resolve(name + ".log.gz")))) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<String> lines(String name, int length) {
        return IntStream.range(0, length).mapToObj(index -> name + " " + index).toList();
    }

    private static String complete(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }
}
