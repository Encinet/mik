package org.encinet.mik.shared.menu.runtime.input;

import java.util.concurrent.atomic.AtomicLong;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuInputGateTest {
    @Test void sneakSwapRefillsOneSlotAtATimeAcrossSecondBoundaries() {
        var clock = new AtomicLong();
        var gate = new MenuInputGate(clock::get);
        for (long millis : new long[]{900, 920, 950, 980}) {
            clock.set(millis * 1_000_000L);
            assertTrue(gate.acceptSneakSwap());
        }
        clock.set(1_000_000_000L);
        assertFalse(gate.acceptSneakSwap());
        clock.set(1_899_999_999L);
        assertFalse(gate.acceptSneakSwap());
        clock.incrementAndGet();
        assertTrue(gate.acceptSneakSwap());
        assertFalse(gate.acceptSneakSwap());
        clock.set(1_920_000_000L);
        assertTrue(gate.acceptSneakSwap());
        assertFalse(gate.acceptSneakSwap());

        clock.set(3_000_000_000L);
        for (int i = 0; i < 4; i++) assertTrue(gate.acceptSneakSwap());
        assertFalse(gate.acceptSneakSwap());
    }

    @Test void onlyAnInputDrivenRedrawConsumesInputAndFailedCallbacksReleaseTheirDispatchScope() {
        var clock = new AtomicLong();
        var gate = new MenuInputGate(clock::get);
        gate.contentChanged();
        assertTrue(gate.permits());
        assertThrows(IllegalStateException.class, () -> gate.dispatch(() -> {
            gate.contentChanged();
            assertFalse(gate.accept("new-tool-parameter", FloatingMenuInteraction.PRIMARY));
            throw new IllegalStateException("failed input");
        }));
        clock.addAndGet(55_000_000L);
        gate.contentChanged();
        assertTrue(gate.accept("new-tool-parameter", FloatingMenuInteraction.PRIMARY));
    }
    @Test void duplicatePacketsCoalesceButDifferentExplicitActionsRemainAvailable() {
        var clock = new AtomicLong(-1_000_000_000L);
        var gate = new MenuInputGate(clock::get);
        assertTrue(gate.accept("play", FloatingMenuInteraction.PRIMARY));
        assertFalse(gate.accept("play", FloatingMenuInteraction.PRIMARY));
        assertTrue(gate.accept("select", FloatingMenuInteraction.PRIMARY));
        assertTrue(gate.accept("select", FloatingMenuInteraction.SECONDARY));
        clock.addAndGet(55_000_000L);
        assertTrue(gate.accept("select", FloatingMenuInteraction.SECONDARY));
    }

    @Test void closingAHintConsumesTheSameInputEvenWhenItsNextPacketTargetsAnotherControl() {
        var clock = new AtomicLong();
        var gate = new MenuInputGate(clock::get);
        gate.consume();
        assertFalse(gate.permits());
        assertFalse(gate.accept("button-behind-hint", FloatingMenuInteraction.PRIMARY));
        assertFalse(gate.accept("another-button", FloatingMenuInteraction.SECONDARY));
        clock.addAndGet(54_999_999L);
        assertFalse(gate.permits());
        clock.incrementAndGet();
        assertTrue(gate.accept("button-behind-hint", FloatingMenuInteraction.PRIMARY));
    }
}
