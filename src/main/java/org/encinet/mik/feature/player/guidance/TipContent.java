package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** One evaluation result: a locale-independent situation and content localized for the recipient. */
public record TipContent(String situationKey, Component message) {
    public TipContent {
        if (situationKey == null || situationKey.isBlank() || situationKey.length() > 256) {
            throw new IllegalArgumentException("Tip situation key must have 1..256 characters");
        }
        Objects.requireNonNull(message, "message");
    }
}
