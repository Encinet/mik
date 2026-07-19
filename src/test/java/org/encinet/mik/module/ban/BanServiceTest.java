package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("c19f68a4-2b12-4b30-a256-c455d3468765");

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesExpiredRecordWhenPlayerIsBannedAgain() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        RecordingMirror mirror = new RecordingMirror();
        try (BanService service = service(clock, mirror)) {
            BanRecord first = service.ban(PLAYER_ID, "TestPlayer", "first", "Admin",
                    clock.instant().plus(Duration.ofHours(1)), BanRecord.Origin.MIK);

            clock.advance(Duration.ofHours(2));

            assertTrue(service.active(PLAYER_ID, "TestPlayer").isEmpty());
            assertEquals(BanRecord.Status.EXPIRED, first.statusAt(clock.instant()));

            BanRecord second = service.ban(PLAYER_ID, "TestPlayer", "second", "Admin",
                    null, BanRecord.Origin.MIK);

            assertEquals(2, service.history(PLAYER_ID, "TestPlayer").size());
            assertEquals(second.id(), service.active(PLAYER_ID, "TestPlayer").orElseThrow().id());
            assertEquals(2, mirror.upserts.size());
        }
    }

    @Test
    void editsSeverityAtomicallyAndKeepsRecordAfterPardon() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        RecordingMirror mirror = new RecordingMirror();
        try (BanService service = service(clock, mirror)) {
            BanRecord created = service.ban(PLAYER_ID, "TestPlayer", "old reason", "Admin",
                    null, BanRecord.Origin.MIK);
            BanRecord edited = service.editSeverity(
                    PLAYER_ID, "TestPlayer", BanSeverity.MINOR, "Moderator");
            BanRecord pardoned = service.pardon(PLAYER_ID, "TestPlayer", "Moderator", "appeal accepted");

            assertEquals(created.id(), edited.id());
            assertEquals(BanSeverity.MINOR.storedReason(), edited.reason());
            assertEquals(clock.instant().plus(Duration.ofDays(7)), edited.expiresAt());
            assertEquals(BanRecord.Status.REVOKED, pardoned.statusAt(clock.instant()));
            assertTrue(service.active(PLAYER_ID, "TestPlayer").isEmpty());
            assertEquals(1, service.history(PLAYER_ID, "TestPlayer").size());
            assertEquals(1, mirror.pardons.size());
        }
    }

    @Test
    void importsExternalUpdateReplacementAndPardon() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        try (BanService service = service(clock, new RecordingMirror())) {
            PaperBanSnapshot original = paper("reason one", clock.instant(), null);
            BanRecord imported = service.importPaperBan(original, false);

            PaperBanSnapshot modified = paper("reason two", clock.instant(), clock.instant().plusSeconds(60));
            BanRecord updated = service.importPaperBan(modified, true);
            assertEquals(imported.id(), updated.id());
            assertEquals("reason two", updated.reason());

            PaperBanSnapshot replacement = paper("new ban", clock.instant().plusSeconds(5), null);
            BanRecord replaced = service.importPaperBan(replacement, true);
            assertNotEquals(imported.id(), replaced.id());
            assertEquals(2, service.history(PLAYER_ID, "TestPlayer").size());

            service.applyPaperPardon(replaced);
            assertTrue(service.active(PLAYER_ID, "TestPlayer").isEmpty());
            assertEquals(BanRecord.Status.REVOKED,
                    service.history(PLAYER_ID, "TestPlayer").getFirst().statusAt(clock.instant()));
        }
    }

    @Test
    void persistsEmptyPaperStateAsInitialized() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        Path database = temporaryDirectory.resolve("paper-state.db");
        BanRepository repository = new BanRepository(database.toFile());
        try (BanService service = new BanService(repository, new RecordingMirror(),
                Logger.getLogger("BanServiceTest"), clock)) {
            service.open();
            assertFalse(service.hasPaperState());
            service.savePaperState(List.of());
            assertTrue(service.hasPaperState());
            assertTrue(service.loadPaperState().isEmpty());
        }

        BanRepository reopenedRepository = new BanRepository(database.toFile());
        try (BanService reopened = new BanService(reopenedRepository, new RecordingMirror(),
                Logger.getLogger("BanServiceTest"), clock)) {
            reopened.open();
            assertTrue(reopened.hasPaperState());
            assertTrue(reopened.loadPaperState().isEmpty());
        }
    }

    @Test
    void doesNotMatchReusedNameWhenBothUuidsAreKnown() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        try (BanService service = service(clock, new RecordingMirror())) {
            service.ban(PLAYER_ID, "ReusedName", "reason", "Admin",
                    null, BanRecord.Origin.MIK);

            UUID differentPlayer = UUID.fromString("843c14f7-214a-4d13-846c-5d2b7bb4b1cd");

            assertTrue(service.active(differentPlayer, "ReusedName").isEmpty());
            assertTrue(service.active(PLAYER_ID, "Anything").isPresent());
        }
    }

    @Test
    void keepsCommittedRecordWhenPaperMirrorTemporarilyFails() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        BanMirror failingMirror = new BanMirror() {
            @Override
            public void upsert(BanRecord record) {
                throw new IllegalStateException("Paper unavailable");
            }

            @Override
            public void pardon(BanRecord record) {
                throw new IllegalStateException("Paper unavailable");
            }
        };
        try (BanService service = service(clock, failingMirror)) {
            BanRecord record = service.ban(PLAYER_ID, "TestPlayer", "reason", "Admin",
                    null, BanRecord.Origin.MIK);

            assertEquals(record.id(), service.active(PLAYER_ID, "TestPlayer").orElseThrow().id());

            service.pardon(PLAYER_ID, "TestPlayer", "Admin", "appeal");

            assertTrue(service.active(PLAYER_ID, "TestPlayer").isEmpty());
            assertEquals(BanRecord.Status.REVOKED,
                    service.history(PLAYER_ID, "TestPlayer").getFirst().statusAt(clock.instant()));
        }
    }

    @Test
    void nameOnlyBanMatchesFirstRealUuidAndCanLaterBeEnriched() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        try (BanService service = service(clock, new RecordingMirror())) {
            service.ban(null, "NeverJoined", "reason", "Admin", null, BanRecord.Origin.MIK);

            assertTrue(service.active(PLAYER_ID, "NeverJoined").isPresent());

            PaperBanSnapshot enriched = new PaperBanSnapshot(
                    PLAYER_ID, "NeverJoined", "reason", "Admin", clock.instant(), null);
            BanRecord updated = service.importPaperBan(enriched, true);

            assertEquals(PLAYER_ID, updated.playerUuid());
            assertEquals(1, service.history(PLAYER_ID, "NeverJoined").size());
        }
    }

    @Test
    void externalRebanWithNewCreationTimeCreatesHistoryEvenWhenContentMatches() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        try (BanService service = service(clock, new RecordingMirror())) {
            service.ban(PLAYER_ID, "TestPlayer", "same reason", "Admin",
                    null, BanRecord.Origin.MIK);
            PaperBanSnapshot mirror = new PaperBanSnapshot(
                    PLAYER_ID, "TestPlayer", "same reason", "Admin", clock.instant().plusMillis(1), null);

            service.importPaperBan(mirror, true);

            assertEquals(2, service.history(PLAYER_ID, "TestPlayer").size());
        }
    }

    @Test
    void historyDoesNotMixDifferentKnownUuidsWithSameName() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        try (BanService service = service(clock, new RecordingMirror())) {
            UUID otherPlayer = UUID.fromString("843c14f7-214a-4d13-846c-5d2b7bb4b1cd");
            service.ban(PLAYER_ID, "ReusedName", "first account", "Admin",
                    clock.instant().plusSeconds(1), BanRecord.Origin.MIK);
            clock.advance(Duration.ofSeconds(2));
            service.ban(otherPlayer, "ReusedName", "second account", "Admin",
                    null, BanRecord.Origin.MIK);

            assertEquals(1, service.history(PLAYER_ID, "ReusedName").size());
            assertEquals("first account", service.history(PLAYER_ID, "ReusedName").getFirst().reason());
            assertEquals(1, service.history(otherPlayer, "ReusedName").size());
            assertEquals("second account", service.history(otherPlayer, "ReusedName").getFirst().reason());
        }
    }

    private BanService service(MutableClock clock, BanMirror mirror) throws BanServiceException {
        BanService service = new BanService(
                new BanRepository(temporaryDirectory.resolve(UUID.randomUUID() + ".db").toFile()),
                mirror, Logger.getLogger("BanServiceTest"), clock);
        service.open();
        return service;
    }

    private PaperBanSnapshot paper(String reason, Instant createdAt, Instant expiresAt) {
        return new PaperBanSnapshot(PLAYER_ID, "TestPlayer", reason, "OtherPlugin", createdAt, expiresAt);
    }

    private static final class RecordingMirror implements BanMirror {
        private final List<BanRecord> upserts = new ArrayList<>();
        private final List<BanRecord> pardons = new ArrayList<>();

        @Override
        public void upsert(BanRecord record) {
            upserts.add(record);
        }

        @Override
        public void pardon(BanRecord record) {
            pardons.add(record);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
