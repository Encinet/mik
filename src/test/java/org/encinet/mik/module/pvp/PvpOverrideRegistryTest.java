package org.encinet.mik.module.pvp;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpOverrideRegistryTest {

    private static final UUID PLAYER_ID = new UUID(0L, 1L);

    @Test
    void returnsPlayerPreferenceWithoutAnOverride() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);

        assertTrue(registry.effectiveEnabled(PLAYER_ID, true));
        assertFalse(registry.effectiveEnabled(PLAYER_ID, false));
        assertFalse(registry.hasOverride(PLAYER_ID));
    }

    @Test
    void highestPriorityWinsAndClearingItRestoresTheNextLayer() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);
        registry.put(PLAYER_ID, "event.sk", "arena", true, 10, PvpModule.PERMANENT_OVERRIDE);
        registry.put(PLAYER_ID, "safety.sk", "safe-zone", false, 100, PvpModule.PERMANENT_OVERRIDE);

        assertFalse(registry.effectiveEnabled(PLAYER_ID, true));
        assertEquals("safe-zone", registry.activeState(PLAYER_ID).orElseThrow().id());

        assertTrue(registry.remove(PLAYER_ID, "safety.sk", "safe-zone"));
        assertTrue(registry.effectiveEnabled(PLAYER_ID, false));
        assertEquals("arena", registry.activeState(PLAYER_ID).orElseThrow().id());

        assertTrue(registry.remove(PLAYER_ID, "event.sk", "arena"));
        assertFalse(registry.effectiveEnabled(PLAYER_ID, false));
    }

    @Test
    void latestOverrideWinsWhenPrioritiesAreEqual() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);
        registry.put(PLAYER_ID, "event.sk", "first", true, 0, PvpModule.PERMANENT_OVERRIDE);
        registry.put(PLAYER_ID, "event.sk", "second", false, 0, PvpModule.PERMANENT_OVERRIDE);

        assertFalse(registry.effectiveEnabled(PLAYER_ID, true));

        registry.put(PLAYER_ID, "event.sk", "first", true, 0, PvpModule.PERMANENT_OVERRIDE);

        assertTrue(registry.effectiveEnabled(PLAYER_ID, false));
        assertEquals("first", registry.activeState(PLAYER_ID).orElseThrow().id());
    }

    @Test
    void timedOverrideExpiresAndRestoresPreference() {
        AtomicLong now = new AtomicLong(1_000L);
        PvpOverrideRegistry registry = new PvpOverrideRegistry(now::get);
        registry.put(PLAYER_ID, "event.sk", "event", true, 0, 500L);

        assertTrue(registry.effectiveEnabled(PLAYER_ID, false));
        now.set(1_500L);

        assertFalse(registry.effectiveEnabled(PLAYER_ID, false));
        assertFalse(registry.hasOverride(PLAYER_ID));
    }

    @Test
    void explicitZeroDurationExpiresImmediately() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);

        registry.put(PLAYER_ID, "event.sk", "event", true, 0, 0L);

        assertFalse(registry.effectiveEnabled(PLAYER_ID, false));
    }

    @Test
    void validatesAndNormalizesOverrideIds() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);
        registry.put(PLAYER_ID, "event.sk", "  arena:round-1  ", true, 0, PvpModule.PERMANENT_OVERRIDE);

        assertTrue(registry.contains(PLAYER_ID, "event.sk", "arena:round-1"));
        assertEquals(java.util.Set.of("arena:round-1"), registry.ids(PLAYER_ID, "event.sk"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.put(PLAYER_ID, "event.sk", "  ", true, 0, PvpModule.PERMANENT_OVERRIDE));
        assertThrows(IllegalArgumentException.class,
                () -> registry.put(PLAYER_ID, "event.sk", "event", true, 0, -2L));
    }

    @Test
    void sameIdIsIsolatedByScriptAndUnloadClearsOnlyItsOwner() {
        PvpOverrideRegistry registry = new PvpOverrideRegistry(() -> 1_000L);
        registry.put(PLAYER_ID, "arena-a.sk", "round", true, 10, PvpModule.PERMANENT_OVERRIDE);
        registry.put(PLAYER_ID, "arena-b.sk", "round", false, 20, PvpModule.PERMANENT_OVERRIDE);

        assertTrue(registry.contains(PLAYER_ID, "arena-a.sk", "round"));
        assertTrue(registry.contains(PLAYER_ID, "arena-b.sk", "round"));
        assertFalse(registry.effectiveEnabled(PLAYER_ID, true));

        assertEquals(java.util.Set.of(PLAYER_ID), registry.clearOwner("arena-b.sk"));
        assertTrue(registry.effectiveEnabled(PLAYER_ID, false));
        assertTrue(registry.contains(PLAYER_ID, "arena-a.sk", "round"));
    }
}
