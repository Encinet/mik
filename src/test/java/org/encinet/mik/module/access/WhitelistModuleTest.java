package org.encinet.mik.module.access;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistModuleTest {

    @Test
    void temporaryEntryAllowsOnlyOneOfTwoConcurrentLogins() throws Exception {
        var entries = new ConcurrentHashMap<String, Long>();
        entries.put("player", 2_000L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var consume = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                start.await();
                return WhitelistModule.consumeTemporaryEntry(entries, "player", 1_000L);
            };
            Future<Boolean> first = executor.submit(consume);
            Future<Boolean> second = executor.submit(consume);
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            assertFalse(entries.containsKey("player"));
        }
    }

    @Test
    void expiredEntryCannotBeConsumed() {
        var entries = new ConcurrentHashMap<String, Long>();
        entries.put("player", 999L);
        assertFalse(WhitelistModule.consumeTemporaryEntry(entries, "player", 1_000L));
        assertFalse(entries.containsKey("player"));
    }
}
