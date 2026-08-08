package org.encinet.mik.module.music.rhythm;

import java.util.Optional;

/** Four logical chart lanes bound left-to-right to hotbar actions 1 through 4. */
public enum RhythmInput {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4);

    private final int hotbarNumber;

    RhythmInput(int hotbarNumber) {
        this.hotbarNumber = hotbarNumber;
    }

    public int hotbarNumber() {
        return hotbarNumber;
    }

    /** Maps Bukkit's zero-based hotbar slot to a playable lane. */
    public static Optional<RhythmInput> fromHotbarSlot(int slot) {
        return switch (slot) {
            case 0 -> Optional.of(ONE);
            case 1 -> Optional.of(TWO);
            case 2 -> Optional.of(THREE);
            case 3 -> Optional.of(FOUR);
            default -> Optional.empty();
        };
    }
}
