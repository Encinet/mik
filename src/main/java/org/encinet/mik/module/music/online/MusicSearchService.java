package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.concurrent.CompletableFuture;

/** Song search exposed by aggregated online music sources. */
public interface MusicSearchService {

    CompletableFuture<MusicSearchResult<MusicTrack>> searchMusic(String keyword, int page, int limit);
}
