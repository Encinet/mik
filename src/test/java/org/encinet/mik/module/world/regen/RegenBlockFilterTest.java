package org.encinet.mik.module.world.regen;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void separatorOnlyInputCannotAccidentallySelectEveryBlock() {
        assertThrows(RegenBlockFilter.UnknownBlocksException.class,
                () -> RegenBlockFilter.parse(",, ,"));
    }
}
