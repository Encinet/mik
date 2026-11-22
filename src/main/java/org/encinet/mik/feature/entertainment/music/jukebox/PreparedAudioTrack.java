package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
final class PreparedAudioTrack extends DelegatedAudioTrack {
    private final MediaContainerDescriptor containerTrackFactory;
    private final PreparedAudioSourceManager sourceManager;

    PreparedAudioTrack(AudioTrackInfo trackInfo,
                           MediaContainerDescriptor containerTrackFactory,
                           PreparedAudioSourceManager sourceManager) {
        super(trackInfo);
        this.containerTrackFactory = containerTrackFactory;
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        try (var input = sourceManager.open(getIdentifier())) {
            InternalAudioTrack delegate = (InternalAudioTrack) containerTrackFactory.createTrack(trackInfo, input);
            processDelegate(delegate, localExecutor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new PreparedAudioTrack(trackInfo, containerTrackFactory, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
