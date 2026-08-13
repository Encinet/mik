package org.encinet.mik.module.chat.model;

import java.util.Arrays;
import java.util.Objects;

/** Immutable semantic item data with an optional opaque Minecraft rendering snapshot. */
public record ChatItemSnapshot(
        String itemKey,
        String displayName,
        int amount,
        byte[] minecraftData
) {
    public ChatItemSnapshot {
        itemKey = requireLine(itemKey, "itemKey", 256);
        displayName = requireLine(displayName, "displayName", 512);
        if (amount < 1 || amount > 9999) {
            throw new IllegalArgumentException("item amount is outside supported range");
        }
        minecraftData = minecraftData == null
                ? new byte[0] : Arrays.copyOf(minecraftData, minecraftData.length);
        if (minecraftData.length > 1_048_576) {
            throw new IllegalArgumentException("Minecraft item snapshot is too large");
        }
    }

    @Override
    public byte[] minecraftData() {
        return Arrays.copyOf(minecraftData, minecraftData.length);
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof ChatItemSnapshot snapshot
                && amount == snapshot.amount
                && itemKey.equals(snapshot.itemKey)
                && displayName.equals(snapshot.displayName)
                && Arrays.equals(minecraftData, snapshot.minecraftData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(itemKey, displayName, amount);
        return 31 * result + Arrays.hashCode(minecraftData);
    }

    public String fallbackText() {
        return "[" + displayName + " x" + amount + "]";
    }

    private static String requireLine(String value, String field, int maximumLength) {
        String checked = Objects.requireNonNull(value, field);
        if (checked.isBlank() || checked.length() > maximumLength
                || checked.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is not a safe single line");
        }
        return checked;
    }
}
