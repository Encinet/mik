package org.encinet.mik.feature.entertainment.music.gateway;

import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import com.google.protobuf.ByteString;
import org.encinet.mik.protocol.music.LyricsCommand;
import org.encinet.mik.protocol.music.LyricsResult;
import org.encinet.mik.protocol.music.MusicCatalogPage;
import org.encinet.mik.protocol.music.MusicCatalogQuery;
import org.encinet.mik.protocol.music.MusicCollectionQuery;
import org.encinet.mik.protocol.music.MusicDecodeFailure;
import org.encinet.mik.protocol.music.MusicFailure;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicMediaOpened;
import org.encinet.mik.protocol.music.MusicOpenMedia;
import org.encinet.mik.protocol.music.MusicReadMedia;
import org.encinet.mik.protocol.music.MusicReply;
import org.encinet.mik.protocol.music.MusicRequest;
import org.encinet.mik.protocol.music.MusicTrackData;
import org.encinet.mik.protocol.music.MusicTrackQuery;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Client of the isolated, source-neutral music protocol. */
public final class RemoteMusic implements MusicMedia, AutoCloseable {
    public record CatalogPage(List<MusicTrack> tracks, ByteString nextCursor, boolean partial) { }

    @FunctionalInterface
    public interface Connector {
        SocketChannel connect() throws IOException;
    }

    private final Connector connector;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Connection> open = ConcurrentHashMap.newKeySet();

    public RemoteMusic(Connector connector) {
        this.connector = connector;
    }

    public CompletableFuture<CatalogPage> catalog(String query, boolean readyOnly, ByteString cursor) {
        return call(MusicRequest.newBuilder().setList(MusicCatalogQuery.newBuilder().setQuery(query)
                .setScope(readyOnly ? MusicCatalogQuery.Scope.READY : MusicCatalogQuery.Scope.ALL)
                .setCursor(cursor)), MusicReply.ResultCase.LIST, reply -> page(reply.getList()));
    }

    public record CollectionPreview(String title, List<MusicTrack> tracks, boolean partial) { }

    public CompletableFuture<CollectionPreview> previewCollection(String reference) {
        return submit(() -> {
            var cursor = ByteString.EMPTY;
            var tracks = new java.util.ArrayList<MusicTrack>();
            String title = "";
            boolean partial = false;
            // The daemon returns bounded pages backed by one ordered snapshot.
            for (int index = 0; index < 16; index++) {
                var query = MusicRequest.newBuilder().setCollection(MusicCollectionQuery.newBuilder()
                        .setReference(reference).setCursor(cursor)).build();
                try (var connection = connect()) {
                    var collection = connection.call(query, MusicReply.ResultCase.COLLECTION).getCollection();
                    var page = page(collection.getPage());
                    title = collection.getTitle();
                    int oldSize = tracks.size();
                    page.tracks().stream().filter(track -> track.availability() != MusicTrack.Availability.UNAVAILABLE)
                            .forEach(tracks::add);
                    partial |= page.partial() || (tracks.size() - oldSize != page.tracks().size());
                    cursor = page.nextCursor();
                    if (cursor.isEmpty()) break;
                }
            }
            return new CollectionPreview(title, List.copyOf(tracks), partial || !cursor.isEmpty());
        }).orTimeout(30, TimeUnit.SECONDS);
    }

    /** The sparse map leaves unknown IDs on their saved display snapshots. Max batch: 24. */
    public CompletableFuture<java.util.Map<String, MusicTrack>> tracks(List<String> ids) {
        return call(MusicRequest.newBuilder().setTracks(MusicTrackQuery.newBuilder().addAllTrackIds(ids)),
                MusicReply.ResultCase.TRACKS, reply -> reply.getTracks().getTracksList().stream()
                        .filter(row -> row.getState() != MusicTrackData.State.UNKNOWN)
                        .map(MusicProto::decodeTrack).collect(java.util.stream.Collectors.toUnmodifiableMap(
                                MusicTrack::id, Function.identity(), (first, _) -> first)));
    }

    private static CatalogPage page(MusicCatalogPage page) {
        return new CatalogPage(page.getTracksList().stream().map(MusicProto::decodeTrack).toList(),
                page.getNextCursor(), page.getPartial());
    }

    public CompletableFuture<LyricsResult> lyrics(LyricsCommand command) {
        return call(MusicRequest.newBuilder().setLyrics(command), MusicReply.ResultCase.LYRICS, MusicReply::getLyrics);
    }

    @Override public CompletableFuture<Boolean> reportCorrupt(TrackTarget.Managed track, String mediaVersion) {
        return call(MusicRequest.newBuilder().setDecodeFailed(MusicDecodeFailure.newBuilder().setTrackId(track.trackId())
                .setMediaVersion(mediaVersion)), MusicReply.ResultCase.DONE, MusicReply::getDone).orTimeout(5, TimeUnit.SECONDS);
    }

    @Override public CompletableFuture<MediaLease> open(TrackTarget.Managed track, Readiness readiness) {
        return openLease(selector(track, readiness), 95);
    }

    @Override public CompletableFuture<MediaLease> openExact(TrackTarget.Managed track, String version, Readiness readiness) {
        return openLease(selector(track, readiness).setExactVersion(version), 95);
    }

    @Override public CompletableFuture<MediaLease> openAvoiding(TrackTarget.Managed track, String version) {
        return openLease(selector(track, Readiness.READABLE).setAvoidVersion(version), 95);
    }

    private static MusicOpenMedia.Builder selector(TrackTarget.Managed track, Readiness readiness) {
        return MusicOpenMedia.newBuilder().setTrackId(track.trackId()).setReadiness(switch (readiness) {
            case READABLE -> MusicOpenMedia.Readiness.READABLE;
            case COMPLETE -> MusicOpenMedia.Readiness.COMPLETE;
        });
    }

    private CompletableFuture<MediaLease> openLease(MusicOpenMedia.Builder selector, long timeoutSeconds) {
        var request = MusicRequest.newBuilder().setOpenMedia(selector).build();
        return this.<MediaLease>submit(() -> {
            var connection = connect();
            try {
                var opened = connection.call(request, MusicReply.ResultCase.MEDIA_OPENED).getMediaOpened();
                var constraint = request.getOpenMedia();
                if (constraint.hasExactVersion() && !constraint.getExactVersion().equals(opened.getMediaVersion())
                        || constraint.hasAvoidVersion() && constraint.getAvoidVersion().equals(opened.getMediaVersion())) {
                    throw new ReadException(MusicFailure.Code.VERSION_GONE);
                }
                return new Lease(connection, opened);
            } catch (IOException | RuntimeException failure) {
                connection.close();
                throw failure;
            }
        }).orTimeout(timeoutSeconds, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> call(MusicRequest.Builder command, MusicReply.ResultCase expected,
                                            Function<MusicReply, T> decode) {
        var request = command.build();
        return this.<T>submit(() -> {
            try (var connection = connect()) {
                return decode.apply(connection.call(request, expected));
            }
        }).orTimeout(23, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> submit(Work<T> work) {
        var result = new CompletableFuture<T>();
        try {
            var task = io.submit(() -> {
                try {
                    T value = work.run();
                    if (!result.complete(value) && value instanceof MediaLease audio) audio.close();
                } catch (IOException | RuntimeException error) {
                    result.completeExceptionally(error);
                }
            });
            result.whenComplete((_, error) -> {
                if (result.isCancelled() || error instanceof TimeoutException) task.cancel(true);
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(closed);
        }
        return result;
    }

    private Connection connect() throws IOException {
        var connection = new Connection(connector.connect());
        open.add(connection);
        if (io.isShutdown()) {
            connection.close();
            throw new IOException("Music connection is closed");
        }
        try {
            connection.handshake();
            return connection;
        } catch (IOException failure) {
            connection.close();
            throw failure;
        }
    }

    @Override public void close() {
        io.shutdownNow();
        open.forEach(Connection::close);
    }

    @FunctionalInterface
    private interface Work<T> { T run() throws IOException; }

    private final class Connection implements AutoCloseable {
        private final SocketChannel socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private int profile;

        @SuppressWarnings("resource")
        private Connection(SocketChannel socket) {
            this.socket = socket;
            in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(socket)));
            out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(socket)));
        }

        private void handshake() throws IOException {
            profile = MusicFrames.negotiate(in, out);
        }

        private MusicReply call(MusicRequest request, MusicReply.ResultCase expected) throws IOException {
            MusicFrames.write(out, request, MusicFrames.MAX_REQUEST);
            var reply = MusicFrames.read(in, MusicReply.parser(), MusicFrames.MAX_REPLY);
            if (reply.hasFailure()) throw remoteFailure(reply.getFailure());
            if (reply.getResultCase() != expected) throw new ReadException("Unexpected music reply", null);
            return reply;
        }

        private int readBlock(byte[] buffer) throws IOException {
            int size = in.readInt();
            if (size == 0) {
                var end = MusicFrames.read(in, MusicReply.parser(), MusicFrames.MAX_REQUEST);
                var outcome = end.getReadEnd();
                return switch (outcome.getOutcomeCase()) {
                    case EOF -> -1;
                    case FAILURE -> throw remoteFailure(outcome.getFailure());
                    case OUTCOME_NOT_SET -> throw new ReadException("Missing media read outcome", null);
                };
            }
            if (size < 0 || size > buffer.length) throw new ReadException("Invalid media block", null);
            in.readFully(buffer, 0, size);
            return size;
        }

        @Override public void close() {
            open.remove(this);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static ReadException remoteFailure(MusicFailure failure) {
        return new ReadException(failure.getCode());
    }

    private final class Lease implements MediaLease {
        private final Connection connection;
        private final MusicMediaOpened opened;
        private final byte[] prefix = new byte[MusicFrames.BLOCK_BYTES];
        private final AtomicLong readSeq = new AtomicLong();
        private final AtomicReference<BufferedReader> reader = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile int prefixSize;

        private Lease(Connection connection, MusicMediaOpened opened) throws IOException {
            if (opened.getLeaseId().size() != 16 || !opened.getMediaVersion().matches("[0-9a-f]{32}")
                    || opened.getMediaVersion().chars().allMatch(ch -> ch == '0')
                    || !opened.hasDescriptor() || !MusicFrames.supports(connection.profile, opened.getDescriptor())
                    || opened.getDescriptor().hasDurationMs() && opened.getDescriptor().getDurationMs() < 0
                    || (opened.getDescriptor().hasByteLength() && opened.getDescriptor().getByteLength() <= 0)) {
                throw new IOException("Invalid media lease descriptor");
            }
            this.connection = connection;
            this.opened = opened;
        }

        @Override public String mediaVersion() { return opened.getMediaVersion(); }
        @Override public MusicMediaDescriptor descriptor() { return opened.getDescriptor(); }
        @Override public Reader openReader() throws IOException {
            var next = new BufferedReader(this);
            var previous = reader.getAndSet(next);
            if (previous != null) previous.close();
            if (closed.get()) {
                next.close();
                throw new IOException("Music lease is closed");
            }
            return next;
        }

        synchronized void remember(long offset, byte[] bytes, int size) {
            if (offset > prefixSize || prefixSize == prefix.length) return;
            int end = (int) Math.min(offset + size, prefix.length);
            if (end <= prefixSize) return;
            System.arraycopy(bytes, prefixSize - (int) offset, prefix, prefixSize, end - prefixSize);
            prefixSize = end;
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            var current = reader.getAndSet(null);
            if (current != null) current.close();
            connection.close();
        }
    }

    private final class BufferedReader implements Reader {
        private final Lease lease;
        private final AtomicReference<Connection> stream = new AtomicReference<>();
        private final byte[] block = new byte[MusicFrames.BLOCK_BYTES];
        private byte[] window = block;
        private volatile boolean closed;
        private long position;
        private long blockStart;
        private int blockSize;
        private long nextOffset;
        private long end = -1;

        private BufferedReader(Lease lease) { this.lease = lease; }

        private boolean fill() throws IOException {
            if (closed) throw new IOException("Music reader is closed");
            if (inWindow(position)) return true;
            int prefixSize = lease.prefixSize;
            if (position < prefixSize) {
                window = lease.prefix;
                blockStart = 0;
                blockSize = prefixSize;
                return true;
            }
            if (end >= 0 && position >= end) return false;
            blockSize = 0;
            int size = readWithReconnect();
            if (size < 0) {
                end = position;
                disconnect();
                return false;
            }
            lease.remember(position, block, size);
            window = block;
            blockStart = position;
            blockSize = size;
            nextOffset = position + size;
            return true;
        }

        private int readWithReconnect() throws IOException {
            for (int attempt = 0; ; attempt++) {
                try {
                    return readNext();
                } catch (IOException failure) {
                    disconnect();
                    if (failure instanceof ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
                        var interrupted = new InterruptedIOException("Music decoder interrupted");
                        interrupted.initCause(failure);
                        throw interrupted;
                    }
                    if (failure instanceof ReadException) throw failure;
                    if (attempt != 0 || closed) throw new ReadException("Music byte stream disconnected", failure);
                }
            }
        }

        private int readNext() throws IOException {
            if (position != nextOffset) disconnect();
            var connection = stream.get();
            if (connection == null) {
                connection = connect();
                stream.set(connection);
                if (closed) {
                    disconnect();
                    throw new IOException("Music reader is closed");
                }
                try {
                    var request = MusicRequest.newBuilder().setReadMedia(MusicReadMedia.newBuilder()
                            .setLeaseId(lease.opened.getLeaseId()).setByteOffset(position)
                            .setReadSeq(lease.readSeq.incrementAndGet())).build();
                    connection.call(request, MusicReply.ResultCase.READ_READY);
                } catch (IOException failure) {
                    disconnect();
                    throw failure;
                }
            }
            return connection.readBlock(block);
        }

        private boolean inWindow(long offset) {
            return offset >= blockStart && offset - blockStart < blockSize;
        }

        private void disconnect() {
            var connection = stream.getAndSet(null);
            if (connection != null) connection.close();
        }

        @Override public int read() throws IOException {
            return fill() ? Byte.toUnsignedInt(window[(int) (position++ - blockStart)]) : -1;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (!fill()) return -1;
            int index = (int) (position - blockStart);
            int size = Math.min(blockSize - index, length);
            System.arraycopy(window, index, bytes, offset, size);
            position += size;
            return size;
        }

        @Override public long position() { return position; }
        @Override public void seek(long offset) throws IOException {
            if (offset < 0) throw new ReadException("Negative media offset", null);
            if (!inWindow(offset) && offset != nextOffset) disconnect();
            position = offset;
        }
        @Override public int available() {
            long bufferedEnd = inWindow(position) ? blockStart + blockSize : position;
            return (int) Math.max(0L, Math.max(bufferedEnd, lease.prefixSize) - position);
        }
        @Override public void close() {
            closed = true;
            lease.reader.compareAndSet(this, null);
            disconnect();
        }
    }

}
