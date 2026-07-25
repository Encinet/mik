package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import org.encinet.mik.module.music.online.OnlineAudioCache;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Adapts the cache's blocking reader to Lavaplayer's seekable stream contract. */
final class GrowingCacheSeekableInputStream extends SeekableInputStream {
    private final OnlineAudioCache.StreamingAudio.Reader reader;

    GrowingCacheSeekableInputStream(OnlineAudioCache.StreamingAudio stream,
                                    OnlineAudioCache.StreamingAudio.Reader reader) {
        super(stream.contentLength() < 0 ? Units.CONTENT_LENGTH_UNKNOWN
                        : stream.contentLength(), 0);
        this.reader = reader;
    }

    @Override
    public int read() throws IOException {
        return reader.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return reader.read(bytes, offset, length);
    }

    @Override
    public long skip(long length) throws IOException {
        if (length <= 0) {
            return 0;
        }
        long skipped = 0;
        byte[] buffer = new byte[(int) Math.min(8192, length)];
        while (skipped < length) {
            int read = read(buffer, 0, (int) Math.min(buffer.length, length - skipped));
            if (read < 0) {
                break;
            }
            skipped += read;
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return reader.available();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public long getPosition() {
        return reader.position();
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    protected void seekHard(long position) throws IOException {
        reader.seek(position);
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return Collections.emptyList();
    }
}
