package org.encinet.mik.module.afk;

import java.util.UUID;

public record AfkState(UUID playerId, String message, AfkSource source, long sinceMillis) {
    public static final int MAX_MESSAGE_CODE_POINTS = 20;

    public boolean hasCustomMessage() {
        return message != null && !message.isBlank();
    }

    public boolean automatic() {
        return source == AfkSource.AUTOMATIC;
    }
}
