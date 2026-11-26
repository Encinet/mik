package org.encinet.mik.feature.server.console;

import org.encinet.mik.integration.mikd.ConsolePort.LogHistory;
import org.encinet.mik.integration.mikd.ConsolePort.LogPage;
import org.encinet.mik.integration.mikd.ConsolePort.LogQuery;
import org.encinet.mik.integration.mikd.ConsolePort.Position;
import org.encinet.mik.integration.mikd.ConsolePort.Work;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Pages the default Paper log files on the provider's log worker. */
final class PaperConsoleHistory implements LogHistory {
    private static final String LATEST_LOG = "latest.log";
    private static final Pattern ARCHIVE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-(\\d{1,10})\\.log\\.gz");

    private final Path directory;

    PaperConsoleHistory(Path directory) {
        this.directory = directory;
    }

    @Override
    public LogPage read(LogQuery query, Work work) throws IOException {
        var paths = paths(work);
        boolean forward = query.position() == Position.AFTER;
        Cursor cursor = query.position() == Position.LATEST ? null : Cursor.parse(query.cursor());
        int anchor = cursor == null ? paths.size() - 1 : find(paths, cursor);
        String notice = "";
        String after = "";
        for (int i = anchor; i >= 0 && i < paths.size(); i += forward ? 1 : -1) {
            ConsoleLogLines.checkDeadline(work);
            Path path = paths.get(i);
            boolean anchored = i == anchor && cursor != null;
            long offset = anchored ? cursor.offset() : forward ? 0 : -1;
            try {
                var source = Source.stat(path);
                if (anchored && !source.key().equals(cursor.key())) throw ConsoleLogLines.invalidCursor();
                var lines = source.read(offset, forward, work);
                if (!lines.empty()) {
                    String before = i > 0 || lines.start() > 0 ? source.cursor(lines.start()) : "";
                    return new LogPage(lines.text(), before, source.cursor(lines.end()), notice);
                }
                if (after.isEmpty() && (forward || cursor == null)) after = source.cursor(Math.max(0, offset));
            } catch (IOException error) {
                ConsoleLogLines.checkDeadline(work);
                if (anchored) throw new IOException(ConsoleLogLines.invalidCursor().getMessage(), error);
                if (notice.isEmpty()) notice = "Skipped unreadable log " + path.getFileName() + ".";
            }
        }
        if (paths.isEmpty()) throw new IOException("No Paper console logs found");
        return new LogPage(List.of(), "", after, notice);
    }

    private List<Path> paths(Work work) throws IOException {
        List<Archive> archives = new ArrayList<>();
        Path latest = null;
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path path : entries) {
                ConsoleLogLines.checkDeadline(work);
                String name = path.getFileName().toString();
                if (LATEST_LOG.equals(name)) {
                    latest = path;
                    continue;
                }
                var archive = ARCHIVE.matcher(name);
                if (archive.matches()) {
                    archives.add(new Archive(path, archive.group(1), Long.parseLong(archive.group(2))));
                }
            }
        }
        archives.sort(Comparator.comparing(Archive::date).thenComparingLong(Archive::index));
        List<Path> paths = new ArrayList<>(archives.size() + 1);
        for (var archive : archives) paths.add(archive.path());
        if (latest != null) paths.add(latest);
        return paths;
    }

    private static int find(List<Path> paths, Cursor cursor) throws IOException {
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).getFileName().toString().equals(cursor.name())) return i;
        }
        throw ConsoleLogLines.invalidCursor();
    }

    private record Archive(Path path, String date, long index) {}

    private record Cursor(String name, String key, long offset) {
        static Cursor parse(String encoded) throws IOException {
            var parts = encoded.split(":", 3);
            if (parts.length == 3) {
                try {
                    long offset = Long.parseLong(parts[2]);
                    if (offset >= 0) return new Cursor(parts[0], parts[1], offset);
                } catch (NumberFormatException invalid) {
                    throw ConsoleLogLines.invalidCursor();
                }
            }
            throw ConsoleLogLines.invalidCursor();
        }
    }

    private record Source(Path path, boolean archive, String key) {
        static Source stat(Path path) throws IOException {
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw new IOException("Log is not a regular file");
            boolean archive = !LATEST_LOG.equals(path.getFileName().toString());
            String identity = attributes.fileKey() + "|" + attributes.creationTime();
            if (archive) identity += "|" + attributes.lastModifiedTime() + "|" + attributes.size();
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.getBytes(StandardCharsets.UTF_8));
            return new Source(path, archive, key);
        }

        String cursor(long offset) {
            return path.getFileName() + ":" + key + ":" + offset;
        }

        ConsoleLogLines read(long offset, boolean forward, Work work) throws IOException {
            if (archive) return readArchive(offset, forward, work);
            try (var file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                verifyOpen();
                var log = new ConsoleLogFile(file, work);
                return forward ? log.after(offset) : log.before(offset);
            }
        }

        private ConsoleLogLines readArchive(long offset, boolean forward, Work work) throws IOException {
            try (var raw = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                 var gzip = new GZIPInputStream(raw, ConsoleLogLines.CHUNK_BYTES)) {
                verifyOpen();
                var reader = new ConsoleLogLines.Reader(gzip, 0, Long.MAX_VALUE, true, work);
                var page = new ConsoleLogLines();
                if (forward) {
                    reader.seek(offset);
                    for (var entry = reader.next(); entry != null; entry = reader.next()) {
                        if (!page.add(entry, false)) break;
                    }
                } else {
                    while (offset < 0 || reader.offset() < offset) {
                        var entry = reader.next();
                        if (entry == null) break;
                        page.keepLatest(entry);
                    }
                    if (offset >= 0 && reader.offset() != offset) throw ConsoleLogLines.invalidCursor();
                }
                return page;
            }
        }

        private void verifyOpen() throws IOException {
            if (!key.equals(stat(path).key())) throw ConsoleLogLines.invalidCursor();
        }
    }
}
