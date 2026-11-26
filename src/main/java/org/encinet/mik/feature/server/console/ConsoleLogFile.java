package org.encinet.mik.feature.server.console;

import org.encinet.mik.integration.mikd.ConsolePort.Work;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

/** Seeks within one snapshot of the active log. */
final class ConsoleLogFile {
    private final FileChannel file;
    private final long size;
    private final Work work;
    private final ByteBuffer chunk = ByteBuffer.allocate(ConsoleLogLines.CHUNK_BYTES);
    private long chunkStart = -1;

    ConsoleLogFile(FileChannel file, Work work) throws IOException {
        this.file = file;
        this.work = work;
        size = file.size();
    }

    ConsoleLogLines before(long offset) throws IOException {
        long end = offset < 0 ? previousNewline(size) + 1 : boundary(offset);
        var page = new ConsoleLogLines();
        byte[] prefix = new byte[ConsoleLogLines.LINE_BYTES];
        while (end > 0) {
            long start = previousNewline(end - 1) + 1;
            long lineBytes = end - start - 1;
            int count = (int) Math.min(prefix.length, lineBytes);
            for (int i = 0; i < count; i++) prefix[i] = readByte(start + i);
            var entry = ConsoleLogLines.Entry.decode(start, end, prefix, count, count < lineBytes);
            if (!page.add(entry, true)) break;
            end = start;
        }
        return page;
    }

    ConsoleLogLines after(long offset) throws IOException {
        var reader = new ConsoleLogLines.Reader(Channels.newInputStream(file.position(boundary(offset))),
                offset, size, false, work);
        var page = new ConsoleLogLines();
        for (var entry = reader.next(); entry != null; entry = reader.next()) {
            if (!page.add(entry, false)) break;
        }
        return page;
    }

    private long boundary(long offset) throws IOException {
        if (offset > size || (offset > 0 && readByte(offset - 1) != '\n')) {
            throw ConsoleLogLines.invalidCursor();
        }
        return offset;
    }

    private long previousNewline(long before) throws IOException {
        for (long index = before - 1; index >= 0; index--) {
            if (readByte(index) == '\n') return index;
        }
        return -1;
    }

    private byte readByte(long offset) throws IOException {
        if (chunkStart < 0 || offset < chunkStart || offset - chunkStart >= chunk.limit()) {
            ConsoleLogLines.checkDeadline(work);
            chunkStart = offset - offset % chunk.capacity();
            chunk.clear().limit((int) Math.min(chunk.capacity(), size - chunkStart));
            while (chunk.hasRemaining()) {
                if (file.read(chunk, chunkStart + chunk.position()) < 0) throw ConsoleLogLines.invalidCursor();
            }
            chunk.flip();
        }
        return chunk.get((int) (offset - chunkStart));
    }
}
