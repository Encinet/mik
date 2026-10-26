package org.encinet.mik.feature.world.regen;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenBlockFilterTest {

    @Test
    void blankInputSelectsEveryCurrentBlockType() throws Exception {
        RegenBlockFilter filter = RegenBlockFilter.parse("  ");

        assertTrue(filter.replacesAll());
        assertTrue(filter.includes(Material.STONE));
        assertTrue(filter.includes(Material.AIR));
    }

    @Test
    void reportsEveryUnknownToken() {
        RegenBlockFilter.UnknownBlocksException error = assertThrows(
                RegenBlockFilter.UnknownBlocksException.class,
                () -> RegenBlockFilter.parse("not_a_material, still_not_a_material"));

        assertEquals(java.util.List.of("not_a_material", "still_not_a_material"), error.blockIds());
    }

    @Test
    void legacyBlockNamesAreReportedWithoutServerConversion() {
        List<Material> legacyMaterials = Arrays.stream(Material.values())
                .filter(Material::isLegacy).toList();
        assertFalse(legacyMaterials.isEmpty());
        for (Material material : legacyMaterials) {
            String name = material.name().toLowerCase(Locale.ROOT);
            RegenBlockFilter.UnknownBlocksException error = assertThrows(
                    RegenBlockFilter.UnknownBlocksException.class,
                    () -> RegenBlockFilter.parse(name), name);
            assertEquals(List.of(name), error.blockIds());
        }
    }

    @Test
    void directConstructionRejectsLegacyMaterialsWithoutServerConversion() {
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                assertThrows(IllegalArgumentException.class,
                        () -> new RegenBlockFilter(Set.of(material)), material.name());
            }
        }
    }

    @Test
    void separatorOnlyInputCannotAccidentallySelectEveryBlock() {
        assertThrows(RegenBlockFilter.UnknownBlocksException.class,
                () -> RegenBlockFilter.parse(",, ,"));
    }
}
