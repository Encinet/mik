package org.encinet.mik.feature.server.prison;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrisonStoreTest {
    @TempDir
    Path directory;

    @Test
    void persistsOnlyTheExitPointPerPrisonPlot() throws Exception {
        UUID plot = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        PrisonLocation exit = new PrisonLocation(world, 12.5, 64, -2.5, 90, 0);
        PrisonData expected = new PrisonData(Map.of(plot, new PrisonRecord(plot, exit)));
        PrisonStore store = new PrisonStore(directory.resolve("prison-data.yml"));

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void aPlotWithoutAnExitPointIsStillPersisted() throws Exception {
        UUID plot = UUID.randomUUID();
        PrisonData expected = new PrisonData(Map.of(plot, new PrisonRecord(plot, null)));
        PrisonStore store = new PrisonStore(directory.resolve("prison-data.yml"));

        store.save(expected);

        PrisonData loaded = store.load();
        assertEquals(expected, loaded);
        assertFalse(Files.readString(directory.resolve("prison-data.yml")).contains("prisoners"));
    }

    @Test
    void legacyNamedPrisonEntriesAreNotSilentlyMigrated() throws Exception {
        Path file = directory.resolve("prison-data.yml");
        Files.writeString(file, """
                prisons:
                  main:
                    owner: 00000000-0000-0000-0000-000000000000
                prisoners:
                  %s:
                    name: Prisoner
                    prison: main
                    cell: a
                """.formatted(UUID.randomUUID()));

        assertThrows(IOException.class, new PrisonStore(file)::load);
    }
}
