package org.encinet.mik.module.afk;

import java.util.UUID;

public record AfkState(UUID playerId, String message, boolean automatic, long sinceMillis) {

    public boolean hasCustomMessage() {
        return message != null && !message.isBlank();
    }
}
