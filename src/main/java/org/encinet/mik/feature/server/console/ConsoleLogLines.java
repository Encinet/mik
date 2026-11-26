package org.encinet.mik.feature.server.console;

import org.encinet.mik.integration.mikd.ConsolePort.Work;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Shared line decoding and page limits for active and archived Paper logs. */
final class ConsoleLogLines {
    static final int CHUNK_BYTES = 16 * 1024;
    static final int LINE_BYTES = 8 * 1024;
    private static final int PAGE_LINES = 200;
    private static final int PAGE_BYTES = 64 * 1024;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private int bytes;

    record Entry(long start, long end, String text, int bytes) {
        static Entry decode(long start, long end, byte[] prefix, int length, boolean truncated) {
            if (!truncated && length > 0 && prefix[length - 1] == '\r') length--;
            String text = new String(prefix, 0, length, StandardCharsets.UTF_8).replace('\r', ' ');
            if (truncated) text += " …";
            return new Entry(start, end, text, text.getBytes(StandardCharsets.UTF_8).length);
        }
    }

    boolean empty() {
        return entries.isEmpty();
    }

    long start() {
        return entries.getFirst().start();
    }

    long end() {
        return entries.getLast().end();
    }

    List<String> text() {
        return entries.stream().map(Entry::text).toList();
    }

    boolean add(Entry entry, boolean prepend) {
        if (!fits(entry)) return false;
        if (prepend) entries.addFirst(entry);
        else entries.addLast(entry);
        bytes += entry.bytes();
        return true;
    }

    void keepLatest(Entry entry) {
        while (!fits(entry)) bytes -= entries.removeFirst().bytes();
        add(entry, false);
    }

    private boolean fits(Entry entry) {
        return entries.size() < PAGE_LINES && bytes + entry.bytes() <= PAGE_BYTES;
    }

    static void checkDeadline(Work work) throws IOException {
        if (Thread.currentThread().isInterrupted() || !work.active()) {
            throw new IOException("The console log query expired");
        }
    }

    static IOException invalidCursor() {
        return new IOException("The log cursor expired. Return to the latest log page.");
    }

    /** Scans forward, retaining at most the prefix of the current line. */
    static final class Reader {
        private final InputStream input;
        private final long limit;
        private final boolean sealed;
        private final Work work;
        private final byte[] chunk = new byte[CHUNK_BYTES];
        private final byte[] prefix = new byte[LINE_BYTES];
        private long offset;
        private int next;
        private int length;

        Reader(InputStream input, long offset, long limit, boolean sealed, Work work) {
            this.input = input;
            this.offset = offset;
            this.limit = limit;
            this.sealed = sealed;
            this.work = work;
        }

        /** Advances a fresh archived reader to a line boundary. */
        void seek(long position) throws IOException {
            while (offset < position - 1) {
                checkDeadline(work);
                long skipped = input.skip(Math.min(CHUNK_BYTES, position - 1 - offset));
                if (skipped == 0) throw invalidCursor();
                offset += skipped;
            }
            if (position > 0 && readByte() != '\n' && (offset < position || readByte() != -1)) {
                throw invalidCursor();
            }
        }

        long offset() {
            return offset;
        }

        Entry next() throws IOException {
            long start = offset;
            int count = 0;
            boolean truncated = false;
            for (int value; (value = readByte()) >= 0;) {
                if (value == '\n') return Entry.decode(start, offset, prefix, count, truncated);
                if (count < prefix.length) prefix[count++] = (byte) value;
                else truncated = true;
            }
            return sealed && offset > start ? Entry.decode(start, offset, prefix, count, truncated) : null;
        }

        private int readByte() throws IOException {
            if (offset == limit) return -1;
            if (next == length) {
                checkDeadline(work);
                length = input.read(chunk, 0, (int) Math.min(chunk.length, limit - offset));
                next = 0;
                if (length < 0) {
                    length = 0;
                    return -1;
                }
            }
            offset++;
            return chunk[next++] & 0xff;
        }
    }
}
