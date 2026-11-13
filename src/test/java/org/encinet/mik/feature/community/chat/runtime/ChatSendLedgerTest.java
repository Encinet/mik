package org.encinet.mik.feature.community.chat.runtime;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ChatSendLedgerTest {
    @Test void tabsAndGameShareAnAccountBudgetAndReconnectDoesNotResetIt() {
        AtomicLong clock = new AtomicLong(1_000);
        ChatSendLedger limits = new ChatSendLedger(clock::get);
        UUID account = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(limits.allowMessage(account)); // First tab
        assertTrue(limits.allowMessage(account)); // Second tab
        assertTrue(limits.allowMessage(account)); // Game
        assertFalse(limits.allowMessage(account)); // A newly connected tab has the same budget
        assertTrue(limits.allowMessage(other));
        clock.addAndGet(2000);
        assertTrue(limits.allowMessage(account));
        assertFalse(limits.allowMessage(account));
    }

}
