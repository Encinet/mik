package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.encinet.mik.module.music.online.OnlineAudioCache;

/** A Lavaplayer track whose input remains readable while the cache file grows. */
final class GrowingCacheAudioTrack extends DelegatedAudioTrack {
    private final MediaContainerDescriptor containerTrackFactory;
    private final GrowingCacheAudioSourceManager sourceManager;

    GrowingCacheAudioTrack(AudioTrackInfo trackInfo,
                           MediaContainerDescriptor containerTrackFactory,
                           GrowingCacheAudioSourceManager sourceManager) {
        super(trackInfo);
        this.containerTrackFactory = containerTrackFactory;
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        OnlineAudioCache.StreamingAudio stream = sourceManager.stream(getIdentifier());
        if (stream == null) {
            throw new IllegalStateException("Progressive cache resource is no longer registered");
        }
        try (OnlineAudioCache.StreamingAudio.Reader reader = stream.openReader()) {
            InternalAudioTrack delegate = (InternalAudioTrack) containerTrackFactory.createTrack(
                    trackInfo, new GrowingCacheSeekableInputStream(stream, reader));
            processDelegate(delegate, localExecutor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GrowingCacheAudioTrack(trackInfo, containerTrackFactory, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
