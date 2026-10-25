package org.encinet.mik.feature.entertainment.music.online;

import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;

import java.util.concurrent.CompletableFuture;

/** Song search exposed by aggregated online music sources. */
public interface MusicSearchService {

    CompletableFuture<MusicSearchResult<MusicTrack>> searchMusic(String keyword, int page, int limit);
}
