package org.encinet.mik.module.plot.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotCommandGuardTest {
    @Test
    void directAndNestedBulkMutationCommandsAreIdentified() {
        assertTrue(PlotCommandGuard.mutates(new String[] {"place", "structure", "village"}));
        assertTrue(PlotCommandGuard.mutates(new String[] {"execute", "at", "@s", "run", "minecraft:fill"}));
        assertTrue(PlotCommandGuard.mutates(new String[] {"function", "example:build"}));
        assertFalse(PlotCommandGuard.mutates(new String[] {"execute", "run", "say", "hello"}));
    }
}
