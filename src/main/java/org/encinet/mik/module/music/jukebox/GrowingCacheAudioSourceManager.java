package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.source.ProbingAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.encinet.mik.module.music.online.OnlineAudioCache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

/** Lavaplayer source manager for cache files which are still being downloaded. */
final class GrowingCacheAudioSourceManager extends ProbingAudioSourceManager {

    private static final String IDENTIFIER_PREFIX = "mik-stream:";

    private final Map<String, OnlineAudioCache.StreamingAudio> streams =
            new ConcurrentHashMap<>();

    GrowingCacheAudioSourceManager() {
        super(MediaContainerRegistry.DEFAULT_REGISTRY);
    }

    String register(OnlineAudioCache.StreamingAudio stream) {
        String identifier = IDENTIFIER_PREFIX + UUID.randomUUID();
        streams.put(identifier, stream);
        return identifier;
    }

    void unregister(String identifier) {
        streams.remove(identifier);
    }

    OnlineAudioCache.StreamingAudio stream(String identifier) {
        return streams.get(identifier);
    }

    @Override
    public String getSourceName() {
        return "mik-growing-cache";
    }

    @Override
    public AudioItem loadItem(com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager manager,
                              AudioReference reference) {
        OnlineAudioCache.StreamingAudio stream = streams.get(reference.identifier);
        if (stream == null) {
            return null;
        }
        try (OnlineAudioCache.StreamingAudio.Reader reader = stream.openReader();
             GrowingCacheSeekableInputStream input =
                     new GrowingCacheSeekableInputStream(stream, reader)) {
            String contentType = stream.contentType();
            MediaContainerHints hints = MediaContainerHints.from(contentType,
                    extensionFor(contentType));
            MediaContainerDetectionResult result = new MediaContainerDetection(
                    containerRegistry, reference, input, hints).detectContainer();
            return handleLoadResult(result);
        } catch (IOException exception) {
            throw new FriendlyException("Failed to read progressively downloaded audio.",
                    COMMON, exception);
        }
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo,
                                     MediaContainerDescriptor containerTrackFactory) {
        return new GrowingCacheAudioTrack(trackInfo, containerTrackFactory, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return false;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        throw new IOException("Progressive cache tracks cannot be encoded");
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return null;
    }

    @Override
    public void shutdown() {
        streams.clear();
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/mp4", "audio/x-m4a" -> "m4a";
            case "audio/aac", "audio/aacp" -> "aac";
            case "audio/flac" -> "flac";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            default -> null;
        };
    }
}
