package org.encinet.mik.module.social.game;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSkinAvatarServiceTest {

    @Test
    void coalescesConcurrentDownloadsAndBrieflyCachesFailures() throws Exception {
        URI texture = URI.create(
                "https://textures.minecraft.net/texture/0123456789abcdef0123456789abcdef");
        AtomicInteger downloads = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        CountDownLatch downloadStarted = new CountDownLatch(1);
        CountDownLatch releaseDownload = new CountDownLatch(1);
        MinecraftSkinAvatarService service = new MinecraftSkinAvatarService(url -> {
            downloads.incrementAndGet();
            downloadStarted.countDown();
            try {
                releaseDownload.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }, clock::get);

        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> service.avatar(texture));
            assertTrue(downloadStarted.await(2, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            var second = workers.submit(() -> {
                secondStarted.countDown();
                return service.avatar(texture);
            });
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            Thread.sleep(50);
            assertEquals(1, downloads.get());

            releaseDownload.countDown();
            assertFalse(first.get(2, TimeUnit.SECONDS).isPresent());
            assertFalse(second.get(2, TimeUnit.SECONDS).isPresent());
        } finally {
            releaseDownload.countDown();
        }

        assertFalse(service.avatar(texture).isPresent());
        assertEquals(1, downloads.get());

        clock.addAndGet(TimeUnit.SECONDS.toNanos(31));
        assertFalse(service.avatar(texture).isPresent());
        assertEquals(2, downloads.get());
    }
}
