package org.encinet.mik.module.player.address;

import org.encinet.mik.module.player.address.PlayerAddressStore.PlayerAddressRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAddressFileTest {
    @TempDir Path directory;

    @Test
    void loadsCompleteSnapshotAndRejectsAnInvalidRowWithoutChangingTheFile() throws Exception {
        Path path = directory.resolve("player-addresses.tsv");
        PlayerAddressRecord record = record("203.0.113.10", 1, 3);
        PlayerAddressFile file = new PlayerAddressFile(path);
        assertTrue(file.load().isEmpty());
        file.saveIfNewer(1, List.of(record));
        assertEquals(List.of(record), file.load());

        String original = Files.readString(path, StandardCharsets.UTF_8);
        String malformed = original + "203.0.113.11\tinvalid-uuid\t1\t2026-01-01T00:00:00Z\n";
        Files.writeString(path, malformed, StandardCharsets.UTF_8);
        assertThrows(IOException.class, file::load);
        assertEquals(malformed, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void olderAsyncSnapshotCannotReplaceNewerShutdownSnapshot() throws Exception {
        PlayerAddressFile file = new PlayerAddressFile(directory.resolve("player-addresses.tsv"));
        PlayerAddressRecord oldRecord = record("203.0.113.10", 1, 1);
        PlayerAddressRecord currentRecord = record("203.0.113.10", 1, 2);

        file.saveIfNewer(2, List.of(currentRecord));
        file.saveIfNewer(1, List.of(oldRecord));

        assertEquals(2, file.savedVersion());
        assertEquals(List.of(currentRecord), file.load());
    }

    @Test
    void failedWriteLeavesVersionAvailableForRetry() throws Exception {
        Path blockedParent = directory.resolve("blocked");
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);
        PlayerAddressFile file = new PlayerAddressFile(blockedParent.resolve("player-addresses.tsv"));
        PlayerAddressRecord record = record("2001:db8::1", 2, 1);

        assertThrows(IOException.class, () -> file.saveIfNewer(1, List.of(record)));
        assertEquals(0, file.savedVersion());

        Files.delete(blockedParent);
        file.saveIfNewer(1, List.of(record));
        assertEquals(1, file.savedVersion());
        assertEquals(List.of(record), file.load());
    }

    private static PlayerAddressRecord record(String address, long player, int count) {
        return new PlayerAddressRecord(address, new UUID(0L, player), count,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
