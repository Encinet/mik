package org.encinet.mik.feature.server.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperBanSynchronizerTest {

    private static final UUID PLAYER_ID = UUID.fromString("c19f68a4-2b12-4b30-a256-c455d3468765");
    private static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void nameOnlyBanSurvivesReconciliationAndCanBePardonedWithoutPaperProfile() throws Exception {
        try (Harness harness = new Harness()) {
            BanRecord record = harness.service.ban(null, "Mumu1978", "reason", "Admin",
                    null, BanRecord.Origin.MIK);

            harness.synchronizer.reconcileNow();
            harness.synchronizer.reconcileNow();

            assertEquals(record, harness.service.active(PLAYER_ID, "mumu1978").orElseThrow());
            assertTrue(harness.service.loadPaperState().isEmpty());
            // No false restore-success logs on every reconciliation, either.
            verify(harness.logger, never()).info(contains("RESTORE_PAPER_MIRROR"));
            harness.service.pardon(PLAYER_ID, "Mumu1978", "Admin", "appeal");
            harness.synchronizer.reconcileNow();
            assertTrue(harness.service.active(PLAYER_ID, "Mumu1978").isEmpty());
            harness.bukkit.verify(() -> Bukkit.createProfileExact(any(), any()), never());
            verify(harness.bans, never()).addBan(any(PlayerProfile.class), anyString(),
                    nullable(Instant.class), anyString());
            verify(harness.bans, never()).pardon(any(PlayerProfile.class));
            verify(harness.logger, never()).log(any(), anyString(), any(Throwable.class));
        }
    }

    @Test
    void knownUuidStillCreatesPaperMirror() throws Exception {
        try (Harness harness = new Harness()) {
            PlayerProfile profile = mock(PlayerProfile.class);
            harness.bukkit.when(() -> Bukkit.createProfileExact(PLAYER_ID, "Mumu1978")).thenReturn(profile);

            harness.service.ban(PLAYER_ID, "Mumu1978", "reason", "Admin",
                    null, BanRecord.Origin.MIK);

            verify(harness.bans).addBan(profile, "reason", (Instant) null, "Admin");
            // The mock list remains empty, so reconciliation must retry this UUID-backed ban.
            harness.synchronizer.reconcileNow();
            verify(harness.bans, times(2)).addBan(profile, "reason", (Instant) null, "Admin");
        }
    }

    @Test
    void nameOnlyRecordCanUpdateAndRemoveExistingPaperEntryWithUuid() throws Exception {
        try (Harness harness = new Harness()) {
            harness.service.ban(null, "Mumu1978", "reason", "Admin",
                    null, BanRecord.Origin.MIK);
            BanEntry<PlayerProfile> entry = harness.addEntry(PLAYER_ID);

            harness.service.editReason(null, "Mumu1978", "updated", "Admin");
            verify(entry).setReason("updated");
            verify(entry).save();

            harness.service.pardon(null, "Mumu1978", "Admin", "appeal");
            verify(entry).remove();
            harness.bukkit.verify(() -> Bukkit.createProfileExact(any(), any()), never());
        }
    }

    @Test
    void revokedLegacyPaperEntryWithoutUuidIsRemovedDirectly() throws Exception {
        try (Harness harness = new Harness()) {
            BanEntry<PlayerProfile> entry = harness.addEntry(null);
            harness.synchronizer.reconcileNow();
            BanRecord imported = harness.service.active(null, "Mumu1978").orElseThrow();
            // Simulate a committed pardon whose mirror cleanup has not yet completed.
            harness.service.applyPaperPardon(imported);

            harness.synchronizer.reconcileNow();

            verify(entry).remove();
            assertTrue(harness.entries.isEmpty());
            harness.bukkit.verify(() -> Bukkit.createProfileExact(any(), any()), never());
        }
    }

    private final class Harness implements AutoCloseable {
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        @SuppressWarnings("unchecked")
        private final BanList<PlayerProfile> bans = mock(BanList.class);
        private final Set<BanEntry<PlayerProfile>> entries = new LinkedHashSet<>();
        private final Logger logger = mock(Logger.class);
        private final PaperBanSynchronizer synchronizer;
        private final BanService service;

        private Harness() throws BanServiceException {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(() -> Bukkit.getBanList(BanListType.PROFILE)).thenReturn(bans);
            when(bans.<BanEntry<PlayerProfile>>getEntries()).thenReturn(entries);
            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(logger);
            synchronizer = new PaperBanSynchronizer(plugin);
            service = new BanService(new BanRepository(
                    temporaryDirectory.resolve("bans.db").toFile()), synchronizer,
                    logger, Clock.fixed(NOW, ZoneOffset.UTC));
            synchronizer.bind(service);
            service.open();
        }

        private BanEntry<PlayerProfile> addEntry(UUID uuid) {
            PlayerProfile profile = mock(PlayerProfile.class);
            when(profile.getId()).thenReturn(uuid);
            when(profile.getName()).thenReturn("Mumu1978");
            @SuppressWarnings("unchecked")
            BanEntry<PlayerProfile> entry = mock(BanEntry.class);
            when(entry.getBanTarget()).thenReturn(profile);
            when(entry.getReason()).thenReturn("reason");
            when(entry.getSource()).thenReturn("Admin");
            when(entry.getCreated()).thenReturn(Date.from(NOW));
            doAnswer(_ -> entries.remove(entry)).when(entry).remove();
            entries.add(entry);
            return entry;
        }

        @Override
        public void close() throws BanServiceException {
            try {
                service.close();
            } finally {
                bukkit.close();
            }
        }
    }
}
