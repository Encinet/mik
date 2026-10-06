package org.encinet.mik.module.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/** Owns the announcement file and the public snapshot read by the HTTP API. */
final class AnnouncementCatalog {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;
    private final Logger logger;
    private volatile Snapshot current = Snapshot.of(List.of());

    AnnouncementCatalog(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Snapshot current() {
        return current;
    }

    Snapshot reload() throws IOException {
        List<Announcement> visible = readFile().stream()
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .toList();
        Snapshot loaded = Snapshot.of(visible);
        current = loaded;
        return loaded;
    }

    private List<Announcement> readFile() throws IOException {
        if (Files.notExists(file)) return List.of();

        List<Announcement> entries = new ArrayList<>();
        String raw = normalizeText(Files.readString(file, StandardCharsets.UTF_8));
        for (String block : raw.split("---")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty()) continue;
            int newline = trimmed.indexOf('\n');
            if (newline == -1) continue;
            String dateLine = trimmed.substring(0, newline).strip();
            String content = normalizeText(trimmed.substring(newline + 1)).strip();
            try {
                LocalDateTime date = LocalDateTime.parse(dateLine, DATE_FMT);
                long timestamp = date.atZone(ZoneId.systemDefault()).toEpochSecond();
                entries.add(new Announcement(timestamp, content));
            } catch (Exception invalidDate) {
                logger.warning("Invalid date in announcements.txt: " + dateLine);
            }
        }
        return entries;
    }

    static String normalizeText(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    record Announcement(long timestamp, String content) { }

    record Snapshot(List<Announcement> announcements, byte[] jsonBytes) {
        Snapshot {
            announcements = List.copyOf(announcements);
            jsonBytes = jsonBytes.clone();
        }

        @Override
        public byte[] jsonBytes() {
            return jsonBytes.clone();
        }

        static Snapshot of(List<Announcement> announcements) {
            JsonArray array = new JsonArray();
            for (Announcement announcement : announcements) {
                JsonObject entry = new JsonObject();
                entry.addProperty("timestamp", Instant.ofEpochSecond(announcement.timestamp()).toString());
                entry.addProperty("content", announcement.content());
                array.add(entry);
            }
            String json = array.toString();
            return new Snapshot(announcements, json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
