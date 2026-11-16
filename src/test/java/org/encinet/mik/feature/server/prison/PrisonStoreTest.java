package org.encinet.mik.feature.server.prison;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrisonStoreTest {
    @TempDir
    Path directory;

    @Test
    void persistsPrisonsCellsAndPrisonersAsOneSnapshot() throws Exception {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID prisonerId = UUID.randomUUID();
        PrisonLocation cell = new PrisonLocation(world, 12.5, 64, -2.5, 90, 0);
        PrisonRecord prison = new PrisonRecord("main", owner, cell, cell, null, null, Map.of("a", cell));
        PrisonerRecord prisoner = new PrisonerRecord(prisonerId, "Prisoner", "main", "a",
                1_800_000_000_000L, true, null, 1_700_000_000_000L);
        PrisonData expected = new PrisonData(Map.of("main", prison), Map.of(prisonerId, prisoner));
        PrisonStore store = new PrisonStore(directory.resolve("prison-data.yml"));

        store.save(expected);

        assertEquals(expected, store.load());
    }
}
