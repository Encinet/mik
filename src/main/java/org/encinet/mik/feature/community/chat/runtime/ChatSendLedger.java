package org.encinet.mik.feature.community.chat.runtime;

import java.util.*;
import java.util.function.LongSupplier;

/** Per-account limits survive connection churn and are shared with game chat. No message bodies. */
public final class ChatSendLedger implements org.encinet.mik.feature.community.chat.bridge.ChatSendAllowance {
    private static final class Usage {
        double tokens = 3;
        long updatedAt;
        Usage(long now) { updatedAt = now; }
    }
    private final Map<UUID, Usage> users = new HashMap<>();
    private final LongSupplier clock;
    public ChatSendLedger() { this(System::currentTimeMillis); }
    public ChatSendLedger(LongSupplier clock) { this.clock = clock; }

    @Override public synchronized boolean allowMessage(UUID player) {
        long now = clock.getAsLong();
        users.entrySet().removeIf(entry -> now - entry.getValue().updatedAt >= 120_000);
        Usage usage = users.get(player);
        if (usage == null) {
            if (users.size() >= 512) return false;
            usage = new Usage(now); users.put(player, usage);
        }
        usage.tokens = Math.min(3, usage.tokens + Math.max(0, now - usage.updatedAt) / 2000.0);
        usage.updatedAt = now;
        if (usage.tokens < 1) return false;
        usage.tokens--;
        return true;
    }

    public synchronized void clear() { users.clear(); }
}
