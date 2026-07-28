package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkStateTest {

    @Test
    void exposesStableSourcesAndCompatibilityFlag() {
        UUID playerId = new UUID(0L, 1L);
        AfkState automatic = new AfkState(playerId, null, AfkSource.AUTOMATIC, 1L);
        AfkState skript = new AfkState(playerId, "event", AfkSource.SKRIPT, 2L);

        assertTrue(automatic.automatic());
        assertFalse(skript.automatic());
        assertTrue(skript.hasCustomMessage());
        assertEquals("skript", skript.source().id());
    }
}
