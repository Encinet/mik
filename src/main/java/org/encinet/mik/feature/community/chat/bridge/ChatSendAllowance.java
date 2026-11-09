package org.encinet.mik.feature.community.chat.bridge;

import java.util.UUID;

/** One account-level send budget shared by game and browser connections. */
@FunctionalInterface
public interface ChatSendAllowance {
    boolean allowMessage(UUID player);
}
