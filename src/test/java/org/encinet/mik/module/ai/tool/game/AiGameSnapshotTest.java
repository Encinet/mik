package org.encinet.mik.module.ai.tool.game;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGameSnapshotTest {

    @Test
    void registryKeyLookupSkipsEveryLegacyMaterial() {
        assertTrue(Arrays.stream(Material.values()).anyMatch(Material::isLegacy));

        for (Material material : Material.values()) {
            String key = AiGameSnapshot.materialKey(material);
            if (material.isLegacy()) assertNull(key);
            else assertFalse(key.isBlank());
        }

        assertEquals("minecraft:stone", AiGameSnapshot.materialKey(Material.STONE));
    }
}
