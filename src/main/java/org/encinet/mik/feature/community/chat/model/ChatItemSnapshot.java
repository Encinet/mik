package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;

/** Item facts used to interpret a placeholder; native hover data stays in game presentation. */
public record ChatItemSnapshot(String displayName, int amount) {
    public ChatItemSnapshot {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > 512 || displayName.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("item name must be a safe single line");
        if (amount < 1 || amount > 9999) throw new IllegalArgumentException("item amount is outside supported range");
    }

    public String fallbackText() {
        return "[" + displayName + " x" + amount + "]";
    }
}
