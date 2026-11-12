package org.encinet.mik.feature.community.chat.web;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class WebChatPacketEncoderTest {
    private static final String LARGE = "{\"type\":\"message\",\"text\":\"" + "较长消息 🌍".repeat(500) + "\"}";

    @Test void backgroundCompressionPreservesApplicationOrderWhileHeartbeatsRemainResponsive() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.channel.writeOutbound(new TextWebSocketFrame(LARGE));
            fixture.channel.writeOutbound(new TextWebSocketFrame("small live message"));
            assertNull(fixture.channel.readOutbound(), "Small data must not overtake an earlier compression job");
            fixture.channel.writeOutbound(new PingWebSocketFrame());
            PingWebSocketFrame ping = fixture.channel.readOutbound();
            assertNotNull(ping); ping.release();
            fixture.release.countDown();
            until(fixture.channel, () -> fixture.channel.outboundMessages().size() == 2);
            WebChatPacket first = fixture.channel.readOutbound();
            assertEquals(WebChatPacket.COMPRESSED_APPLICATION, first.kind());
            assertEquals(LARGE, WebChatPacketTest.expand(first));
            TextWebSocketFrame second = fixture.channel.readOutbound();
            try { assertEquals("small live message", second.text()); }
            finally { second.release(); }
        }
    }

    @Test void closingCancelsPendingJobsReleasesQueuedFramesAndDiscardsLateResults() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.channel.writeOneOutbound(new TextWebSocketFrame(LARGE));
            var small = new TextWebSocketFrame("pending");
            var second = fixture.channel.writeOneOutbound(small);
            fixture.channel.close();
            assertTrue(first.isDone()); assertFalse(first.isSuccess());
            assertTrue(second.isDone()); assertFalse(second.isSuccess());
            assertEquals(0, small.refCnt());
            fixture.release.countDown();
            fixture.workers.submit(() -> {}).sync();
            fixture.channel.runPendingTasks();
            assertNull(fixture.channel.readOutbound());
        }
    }

    @Test void queueByteBudgetClosesTheConnectionInsteadOfRetainingUnboundedFrames() throws Exception {
        try (var fixture = new Fixture()) {
            String json = "{\"type\":\"message\",\"text\":\"" + "x".repeat(120 * 1024) + "\"}";
            var first = fixture.channel.writeOneOutbound(new TextWebSocketFrame(json));
            var second = fixture.channel.writeOneOutbound(new TextWebSocketFrame(json));
            var last = new TextWebSocketFrame(json);
            var rejected = fixture.channel.writeOneOutbound(last);
            assertTrue(rejected.isDone()); assertFalse(rejected.isSuccess());
            assertFalse(fixture.channel.isActive());
            assertFalse(first.isSuccess()); assertFalse(second.isSuccess());
            assertEquals(0, last.refCnt());
        }
    }

    @Test void workerRejectionFailsTheWriteAndClosesTheConnection() {
        var workers = new DefaultEventExecutorGroup(1);
        workers.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        var channel = new EmbeddedChannel(new WebChatPacketEncoder(workers));
        channel.attr(WebChatCipher.COMPRESSION).set(true);
        var frame = new TextWebSocketFrame(LARGE);
        var written = channel.writeOneOutbound(frame);
        assertTrue(written.isDone()); assertFalse(written.isSuccess());
        assertFalse(channel.isActive()); assertEquals(0, frame.refCnt());
        channel.finishAndReleaseAll();
    }

    private static void until(EmbeddedChannel channel, BooleanSupplier check) throws Exception {
        for (int attempt = 0; attempt < 200 && !check.getAsBoolean(); attempt++) {
            channel.runPendingTasks(); Thread.sleep(5);
        }
        assertTrue(check.getAsBoolean(), "Background encoding did not settle");
    }
    private static final class Fixture implements AutoCloseable {
        final DefaultEventExecutorGroup workers = new DefaultEventExecutorGroup(1);
        final CountDownLatch release = new CountDownLatch(1);
        final EmbeddedChannel channel = new EmbeddedChannel(new WebChatPacketEncoder(workers));
        Fixture() throws Exception {
            channel.attr(WebChatCipher.COMPRESSION).set(true);
            var started = new CountDownLatch(1);
            workers.submit(() -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; });
            assertTrue(started.await(1, TimeUnit.SECONDS));
        }
        @Override public void close() {
            channel.close(); release.countDown();
            workers.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
            channel.runPendingTasks();
            Object frame;
            while ((frame = channel.readOutbound()) != null) ReferenceCountUtil.release(frame);
            channel.finishAndReleaseAll();
        }
    }
}
