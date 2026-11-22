package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.GameQueryEmpty;
import org.encinet.mik.protocol.GameQueryExchange;
import org.encinet.mik.protocol.GameQueryJob;
import org.encinet.mik.protocol.GameQueryReply;
import org.encinet.mik.protocol.GameQueryWork;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** One in-flight request on the independent game query lane. */
public final class GameQueryWorker implements AutoCloseable {
    @FunctionalInterface
    public interface Handler {
        GameQueryReply execute(GameQueryJob job) throws Exception;
    }

    private final Function<GameQueryExchange, CompletableFuture<GameQueryWork>> poll;
    private final Handler handler;
    private final Thread worker;
    public GameQueryWorker(String name, Function<GameQueryExchange, CompletableFuture<GameQueryWork>> poll, Handler handler) {
        this.poll = poll;
        this.handler = handler;
        worker = Thread.ofVirtual().name(name).start(this::run);
    }

    private void run() {
        GameQueryReply previous = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var request = GameQueryExchange.newBuilder();
                if (previous != null) request.setReply(previous);
                var pending = poll.apply(request.build());
                previous = null;
                try {
                    var work = pending.get();
                    if (work.hasJob()) previous = execute(work.getJob());
                } catch (ExecutionException failure) {
                    Thread.sleep(1000);
                } finally {
                    if (!pending.isDone()) pending.cancel(false);
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private GameQueryReply execute(GameQueryJob job) throws InterruptedException {
        try {
            var reply = handler.execute(job);
            if (reply.getSerializedSize() < MikdFrames.MAX_BYTES - 256) return reply;
        } catch (InterruptedException stopped) {
            throw stopped;
        } catch (Exception failure) { return failed(job); }
        return failed(job);
    }

    private static GameQueryReply failed(GameQueryJob job) {
        return GameQueryReply.newBuilder().setId(job.getId()).setFailed(GameQueryEmpty.getDefaultInstance()).build();
    }

    @Override public void close() {
        worker.interrupt();
    }
}
