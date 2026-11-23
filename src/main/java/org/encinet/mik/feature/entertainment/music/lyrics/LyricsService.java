package org.encinet.mik.feature.entertainment.music.lyrics;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.Objects;
import org.encinet.mik.protocol.music.LyricsCommand;
import org.encinet.mik.protocol.music.LyricsLookup;
import org.encinet.mik.protocol.music.LyricsRead;
import org.encinet.mik.protocol.music.LyricsRelease;
import org.encinet.mik.protocol.music.LyricsResult;

/** Fetches bounded, typed pages with one daemon ticket and one revision. */
public final class LyricsService implements AutoCloseable {
    public sealed interface Lookup permits Available, Status { }
    public record Available(Lyrics document) implements Lookup { }
    public enum Status implements Lookup { PENDING, EMPTY, UNAVAILABLE }

    private final Function<LyricsCommand, CompletableFuture<LyricsResult>> remote;
    private final Map<String, CompletableFuture<Lookup>> inFlight = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofVirtual().name("mik-lyrics-", 0).factory());

    public LyricsService(Function<LyricsCommand, CompletableFuture<LyricsResult>> remote) { this.remote = remote; }

    public Playback forPlayback(String version) { return new Playback(Objects.requireNonNull(version)); }

    CompletableFuture<Lookup> load(String version) {
        var first = new CompletableFuture<Lookup>();
        var pending = inFlight.putIfAbsent(version, first);
        if (pending != null) return pending.copy();
        try {
            io.execute(() -> {
                try {
                    first.complete(fetch(version));
                } catch (IOException | ExecutionException | RuntimeException failed) {
                    first.completeExceptionally(failed);
                } catch (InterruptedException cancelled) {
                    Thread.currentThread().interrupt();
                    first.cancel(false);
                } finally {
                    inFlight.remove(version, first);
                }
            });
        } catch (RejectedExecutionException closed) {
            inFlight.remove(version, first);
            first.completeExceptionally(closed);
        }
        return first.copy();
    }

    /** One run accepts one document, shared by every output and the detail panel. */
    public final class Playback {
        private final String version;
        private Lookup state = Status.PENDING;
        private boolean pending;
        private long nextLookup = System.nanoTime();

        private Playback(String version) { this.version = version; }
        public String mediaVersion() { return version; }

        public synchronized Lookup poll() {
            if (!pending && (state == Status.PENDING || state == Status.UNAVAILABLE) && System.nanoTime() - nextLookup >= 0) {
                pending = true;
                load(version).whenComplete((result, error) -> accept(error == null ? result : Status.UNAVAILABLE));
            }
            return state;
        }

        private synchronized void accept(Lookup result) {
            state = result;
            pending = false;
            nextLookup = System.nanoTime() + TimeUnit.SECONDS.toNanos(result == Status.PENDING ? 2 : 12);
        }
    }

    private Lookup fetch(String version) throws IOException, InterruptedException, ExecutionException {
        var response = call(LyricsCommand.newBuilder().setLookup(LyricsLookup.newBuilder().setMediaVersion(version)).build());
        if (response.getState() == LyricsResult.State.EMPTY) return Status.EMPTY;
        if (response.getState() == LyricsResult.State.PENDING) return Status.PENDING;
        var ticket = response.getTicket();
        if (ticket.size() != 16) throw new IOException("Invalid lyrics ticket");
        try {
            var header = response.getDocument();
            if (response.getState() != LyricsResult.State.READY || !response.hasDocument()
                    || !version.equals(header.getMediaVersion()) || !header.getRevision().matches("[0-9a-f]{64}")
                    || header.getTracksCount() < 1 || header.getTracksCount() > 16) throw new IOException("Invalid lyrics document");
            var tracks = new ArrayList<Lyrics.Track>();
            var keys = new HashSet<Integer>();
            int totalBytes = 0;
            for (var schema : header.getTracksList()) {
                var role = switch (schema.getRole()) {
                    case ORIGINAL -> Lyrics.Role.ORIGINAL;
                    case TRANSLATION -> Lyrics.Role.TRANSLATION;
                    case PRONUNCIATION -> Lyrics.Role.PRONUNCIATION;
                    default -> throw new IOException("Unknown lyric role");
                };
                if (schema.getTrackKey() <= 0 || !keys.add(schema.getTrackKey())) throw new IOException("Duplicate lyric track");
                var cues = new ArrayList<Lyrics.Cue>();
                var lines = new ArrayList<String>();
                int offset = 0;
                while (true) {
                    var next = call(LyricsCommand.newBuilder().setRead(LyricsRead.newBuilder().setTicket(ticket)
                            .setTrackKey(schema.getTrackKey()).setOffset(offset)).build());
                    var page = next.getPage();
                    if (!next.hasPage() || !next.getTicket().equals(ticket) || next.getState() != LyricsResult.State.READY
                            || page.getOffset() != offset || page.getTrackKey() != schema.getTrackKey()
                            || (schema.getTimed() ? page.getLinesCount() > 0 : page.getCuesCount() > 0)) throw new IOException("Invalid lyric page");
                    int count = Math.addExact(page.getCuesCount(), page.getLinesCount());
                    totalBytes += page.getSerializedSize();
                    if (count < 1 || count > 64 || offset + count > 10_000 || totalBytes > 3 * 1024 * 1024) throw new IOException("Lyrics over budget");
                    page.getCuesList().forEach(cue -> cues.add(new Lyrics.Cue(cue.getStartMs(),
                            cue.hasEndMs() ? cue.getEndMs() : null, cue.getText())));
                    lines.addAll(page.getLinesList());
                    if (!page.hasNextOffset()) break;
                    if (page.getNextOffset() != offset + count) throw new IOException("Invalid lyric offset");
                    offset = page.getNextOffset();
                }
                tracks.add(new Lyrics.Track(schema.getTrackKey(), role,
                        schema.hasLanguageTag() ? schema.getLanguageTag() : null, cues, lines));
            }
            return new Available(new Lyrics(version, header.getRevision(),
                    header.hasDefaultOriginalTrack() ? header.getDefaultOriginalTrack() : null, tracks));
        } finally {
            release(ticket);
        }
    }

    private LyricsResult call(LyricsCommand command) throws InterruptedException, ExecutionException {
        var request = remote.apply(command);
        try { return request.get(); }
        catch (InterruptedException stopped) { request.cancel(true); throw stopped; }
    }

    private void release(ByteString ticket) {
        remote.apply(LyricsCommand.newBuilder().setRelease(LyricsRelease.newBuilder().setTicket(ticket)).build());
    }

    @Override public void close() {
        io.shutdownNow();
        inFlight.values().forEach(pending -> pending.cancel(false));
        inFlight.clear();
    }
}
