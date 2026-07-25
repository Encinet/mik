package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSearchRankerTest {

    @Test
    void strongerUncachedTitleMatchOutranksWeakCachedMetadataMatch() {
        MusicTrack cachedArtistMatch = online(
                "lx:kw:cached", "Completely Different", "Target Song", null, "cached");
        MusicTrack uncachedExactTitle = online(
                "lx:wy:exact", "Target Song", "Another Artist", null, "exact");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(cachedArtistMatch, uncachedExactTitle), Set.of(cachedArtistMatch.id()));

        assertEquals(List.of(uncachedExactTitle, cachedArtistMatch), ordered);
    }

    @Test
    void cacheBreaksATieBetweenEquallyRelevantResults() {
        MusicTrack uncached = online(
                "lx:kw:uncached", "Target Song (Live)", "Artist", null, "uncached");
        MusicTrack cached = online(
                "lx:wy:cached", "Target Song (Vibe)", "Artist", null, "cached");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(uncached, cached), Set.of(cached.id()));

        assertEquals(List.of(cached, uncached), ordered);
    }

    @Test
    void distantCachedPhraseDoesNotOutrankANearerUncachedPhrase() {
        MusicTrack cachedDistant = online(
                "lx:kw:cached", "A Very Long Introduction Before Target Song", "Artist",
                null, "cached");
        MusicTrack uncachedNear = online(
                "lx:wy:uncached", "One Target Song", "Artist", null, "uncached");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(cachedDistant, uncachedNear), Set.of(cachedDistant.id()));

        assertEquals(List.of(uncachedNear, cachedDistant), ordered);
    }

    @Test
    void greaterTermCoverageOutranksCacheWithinPartialMatches() {
        MusicTrack cachedOneTerm = online(
                "lx:kw:cached", "Target", "Other", null, "cached");
        MusicTrack uncachedTwoTerms = online(
                "lx:wy:uncached", "Target", "Artist", null, "uncached");

        List<MusicTrack> ordered = rank("Target Artist Missing",
                List.of(cachedOneTerm, uncachedTwoTerms), Set.of(cachedOneTerm.id()));

        assertEquals(List.of(uncachedTwoTerms, cachedOneTerm), ordered);
    }

    @Test
    void localAndCachedTracksShareTheReadyToPlayPreference() {
        MusicTrack uncached = online(
                "lx:kw:uncached", "Target Song (C)", "Artist", null, "uncached");
        MusicTrack local = local("local.mp3", "Target Song (B)", "Artist", null);
        MusicTrack cached = online(
                "lx:wy:cached", "Target Song (A)", "Artist", null, "cached");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(uncached, local, cached), Set.of(cached.id()));

        assertEquals(List.of(cached, local, uncached), ordered);
    }

    @Test
    void deterministicMetadataOrderReplacesProviderTraversalOrder() {
        MusicTrack alpha = online(
                "lx:wy:alpha", "Alpha Target", "Artist", null, "alpha");
        MusicTrack bravo = online(
                "lx:kw:bravo", "Bravo Target", "Artist", null, "bravo");

        List<MusicTrack> ordered = rank("Target", List.of(bravo, alpha), Set.of());

        assertEquals(List.of(alpha, bravo), ordered);
    }

    @Test
    void normalizesCaseWidthAndPunctuationForExactMatching() {
        MusicTrack normalizedExact = online(
                "lx:wy:exact", "ＡＢＣ・Song", "Artist", null, "exact");
        MusicTrack prefix = online(
                "lx:kw:prefix", "ABC Song Live", "Artist", null, "prefix");

        List<MusicTrack> ordered = rank("abc song",
                List.of(prefix, normalizedExact), Set.of(prefix.id()));

        assertEquals(List.of(normalizedExact, prefix), ordered);
    }

    @Test
    void cacheDoesNotBiasResultsWithoutAnyLiteralMatchEvidence() {
        MusicTrack zuluCached = online(
                "lx:kw:zulu", "Zulu", "Artist", null, "zulu");
        MusicTrack alphaUncached = online(
                "lx:wy:alpha", "Alpha", "Artist", null, "alpha");

        List<MusicTrack> ordered = rank("Missing",
                List.of(zuluCached, alphaUncached), Set.of(zuluCached.id()));

        assertEquals(List.of(alphaUncached, zuluCached), ordered);
    }

    @Test
    void localPrefilterUsesTheSameNormalizedMatchingAsRanking() {
        MusicTrack track = local("song.mp3", "ＡＢＣ・Song", "Artist", null);

        assertTrue(MusicSearchRanker.matches("abc-song", track));
        assertFalse(MusicSearchRanker.matches("missing", track));
    }

    private static List<MusicTrack> rank(String keyword, List<MusicTrack> tracks,
                                         Set<String> cached) {
        return MusicSearchRanker.rank(keyword, tracks, track -> cached.contains(track.id()));
    }

    private static MusicTrack local(String id, String title, String artist, String album) {
        return new MusicTrack(id,
                new TrackDetails(title, artist, album, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }

    private static MusicTrack online(String id, String title, String artist,
                                     String album, String songId) {
        String source = id.split(":")[1];
        return new MusicTrack(id,
                new TrackDetails(title, artist, album,
                        "LX/" + source.toUpperCase(), AudioProperties.EMPTY),
                new TrackTarget.Lx(source, songId, List.of("320k"),
                        "{\"source\":\"" + source + "\",\"meta\":{\"songId\":\""
                                + songId + "\"}}"));
    }
}
