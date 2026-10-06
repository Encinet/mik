package org.encinet.mik.module.world.regen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncRegenModuleTest {

    @Test
    void regenCommandUsesTheManagerGroupPermission() {
        assertEquals("group.custodian", AsyncRegenModule.COMMAND_PERMISSION);
    }

    @Test
    void legacyEntriesAreRejectedBeforeTheirUnavailableKeyIsRead() {
        Candidate legacy = new Candidate(true, true, "legacy:stone");
        Candidate stone = new Candidate(false, true, "minecraft:stone");
        Candidate dirt = new Candidate(false, true, "minecraft:dirt");
        Candidate item = new Candidate(false, false, "minecraft:stick");

        List<String> blockIds = AsyncRegenModule.searchableBlockIds(
                List.of(legacy, stone, dirt, stone, item),
                Candidate::legacy,
                Candidate::block,
                candidate -> {
                    if (candidate.legacy()) {
                        throw new IllegalArgumentException("Cannot get key of Legacy Material");
                    }
                    return candidate.key();
                });

        assertTrue(blockIds.contains("minecraft:stone"));
        assertEquals(List.of("minecraft:dirt", "minecraft:stone"), blockIds);
    }

    private record Candidate(boolean legacy, boolean block, String key) {
    }
}
