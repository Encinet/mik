package org.encinet.mik.module.player.address;

import org.encinet.mik.module.player.address.PlayerAddressStore.PlayerAddressRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Reads complete address histories and commits snapshots in version order. */
final class PlayerAddressFile {
    private static final String HEADER = "# address\tplayer-id\tcount\tlast-seen-at";

    private final Path path;
    private volatile long savedVersion;

    PlayerAddressFile(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    List<PlayerAddressRecord> load() throws IOException {
        List<PlayerAddressRecord> records = new ArrayList<>();
        Set<RecordKey> seen = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                PlayerAddressRecord record = parse(line, lineNumber);
                if (!seen.add(new RecordKey(record.address(), record.playerId()))) {
                    throw new IOException("Duplicate player address at line " + lineNumber);
                }
                records.add(record);
            }
        } catch (NoSuchFileException missing) {
            return List.of();
        }
        return List.copyOf(records);
    }

    long savedVersion() {
        return savedVersion;
    }

    synchronized void saveIfNewer(long version, List<PlayerAddressRecord> records)
            throws IOException {
        if (version <= savedVersion) return;
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".player-addresses-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();
                for (PlayerAddressRecord record : records) {
                    writer.write(record.address());
                    writer.write('\t');
                    writer.write(record.playerId().toString());
                    writer.write('\t');
                    writer.write(Integer.toString(record.count()));
                    writer.write('\t');
                    writer.write(record.lastSeenAt().toString());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            savedVersion = version;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static PlayerAddressRecord parse(String line, int lineNumber) throws IOException {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 4) throw new IOException("Invalid player address at line " + lineNumber);
        try {
            String address = parts[0];
            UUID playerId = UUID.fromString(parts[1]);
            int count = Integer.parseInt(parts[2]);
            Instant lastSeenAt = Instant.parse(parts[3]);
            if (address.isBlank() || count <= 0) {
                throw new IOException("Invalid player address at line " + lineNumber);
            }
            return new PlayerAddressRecord(address, playerId, count, lastSeenAt);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid player address at line " + lineNumber, invalid);
        }
    }

    private record RecordKey(String address, UUID playerId) { }
}
