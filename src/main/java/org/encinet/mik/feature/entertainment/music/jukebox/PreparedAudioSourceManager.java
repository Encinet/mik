package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.source.ProbingAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

final class PreparedAudioSourceManager extends ProbingAudioSourceManager {

    private static final String IDENTIFIER_PREFIX = "mik-stream:";

    private final Map<String, Input> inputs = new ConcurrentHashMap<>();

    PreparedAudioSourceManager() {
        super(MediaContainerRegistry.DEFAULT_REGISTRY);
    }

    String register(MediaContainerHints hints, OpenStream open) {
        String identifier = IDENTIFIER_PREFIX + UUID.randomUUID();
        inputs.put(identifier, new Input(hints, open));
        return identifier;
    }

    void unregister(String identifier) {
        inputs.remove(identifier);
    }

    SeekableInputStream open(String identifier) throws IOException {
        var input = inputs.get(identifier);
        if (input == null) throw new IOException("Audio input was released");
        return input.open().stream();
    }

    @Override
    public String getSourceName() {
        return "mik-audio";
    }

    @Override
    public AudioItem loadItem(com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager manager,
                              AudioReference reference) {
        var registered = inputs.get(reference.identifier);
        if (registered == null) return null;
        try (var input = registered.open().stream()) {
            MediaContainerDetectionResult result = new MediaContainerDetection(
                    containerRegistry, reference, input, registered.hints()).detectContainer();
            return handleLoadResult(result);
        } catch (IOException exception) {
            throw new FriendlyException("Failed to read prepared audio", COMMON, exception);
        }
    }

    @Override
    protected AudioTrack createTrack(AudioTrackInfo trackInfo,
                                     MediaContainerDescriptor containerTrackFactory) {
        return new PreparedAudioTrack(trackInfo, containerTrackFactory, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return false;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        throw new IOException("Prepared audio tracks cannot be encoded");
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return null;
    }

    @Override
    public void shutdown() {
        inputs.clear();
    }

    static String extensionFor(String contentType) {
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
            case "audio/webm" -> "webm";
            case "audio/x-matroska" -> "mka";
            default -> null;
        };
    }

    @FunctionalInterface interface OpenStream { SeekableInputStream stream() throws IOException; }
    private record Input(MediaContainerHints hints, OpenStream open) { }
}
