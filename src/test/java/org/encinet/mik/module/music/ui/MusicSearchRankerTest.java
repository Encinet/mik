package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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
                List.of(cachedArtistMatch, uncachedExactTitle));

        assertEquals(List.of(uncachedExactTitle, cachedArtistMatch), ordered);
    }

    @Test
    void equalMatchesKeepFirstSeenChannelOrderInsteadOfUsingCacheState() {
        MusicTrack uncached = online(
                "lx:kw:uncached", "Target Song (Live)", "Artist", null, "uncached");
        MusicTrack cached = online(
                "lx:wy:cached", "Target Song (Vibe)", "Artist", null, "cached");

        List<MusicTrack> ordered = rank("Target Song", List.of(uncached, cached));

        assertEquals(List.of(uncached, cached), ordered);
    }

    @Test
    void distantCachedPhraseDoesNotOutrankANearerUncachedPhrase() {
        MusicTrack cachedDistant = online(
                "lx:kw:cached", "A Very Long Introduction Before Target Song", "Artist",
                null, "cached");
        MusicTrack uncachedNear = online(
                "lx:wy:uncached", "One Target Song", "Artist", null, "uncached");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(cachedDistant, uncachedNear));

        assertEquals(List.of(uncachedNear, cachedDistant), ordered);
    }

    @Test
    void greaterTermCoverageOutranksCacheWithinPartialMatches() {
        MusicTrack cachedOneTerm = online(
                "lx:kw:cached", "Target", "Other", null, "cached");
        MusicTrack uncachedTwoTerms = online(
                "lx:wy:uncached", "Target", "Artist", null, "uncached");

        List<MusicTrack> ordered = rank("Target Artist Missing",
                List.of(cachedOneTerm, uncachedTwoTerms));

        assertEquals(List.of(uncachedTwoTerms, cachedOneTerm), ordered);
    }

    @Test
    void equalLocalAndOnlineMatchesKeepFirstSeenChannelOrder() {
        MusicTrack uncached = online(
                "lx:kw:uncached", "Target Song (C)", "Artist", null, "uncached");
        MusicTrack local = local("local.mp3", "Target Song (B)", "Artist", null);
        MusicTrack cached = online(
                "lx:wy:cached", "Target Song (A)", "Artist", null, "cached");

        List<MusicTrack> ordered = rank("Target Song",
                List.of(uncached, local, cached));

        assertEquals(List.of(uncached, local, cached), ordered);
    }

    @Test
    void preservesSourceOrderWhenSourceHeadsHaveEqualMatchQuality() {
        MusicTrack alpha = online(
                "lx:wy:alpha", "Alpha Target", "Artist", null, "alpha");
        MusicTrack bravo = online(
                "lx:kw:bravo", "Bravo Target", "Artist", null, "bravo");

        List<MusicTrack> ordered = rank("Target", List.of(bravo, alpha));

        assertEquals(List.of(bravo, alpha), ordered);
    }

    @Test
    void comparesSourceHeadsWithoutReorderingTracksBehindEachHead() {
        MusicTrack kwPrefix = online(
                "lx:kw:prefix", "Target Live", "Artist", null, "kw-prefix");
        MusicTrack kwExact = online(
                "lx:kw:exact", "Target", "Artist", null, "exact");
        MusicTrack wyExact = online(
                "lx:wy:exact", "Target", "Artist", null, "wy-exact");
        MusicTrack wyPhrase = online(
                "lx:wy:phrase", "A Target Song", "Artist", null, "wy-phrase");

        List<MusicTrack> ordered = rank("Target",
                List.of(kwPrefix, kwExact, wyExact, wyPhrase));

        assertEquals(List.of(wyExact, kwPrefix, kwExact, wyPhrase), ordered);
    }

    @Test
    void treatsEachCustomSourceAsAnIndependentOrderedChannel() {
        MusicTrack firstProviderHead = onlineFromProvider(
                "lx:kw:first-head", "Target Live", "first.js");
        MusicTrack firstProviderNext = onlineFromProvider(
                "lx:kw:first-next", "Target", "first.js");
        MusicTrack secondProviderHead = onlineFromProvider(
                "lx:kw:second-head", "Target", "second.js");

        List<MusicTrack> ordered = rank("Target",
                List.of(firstProviderHead, firstProviderNext, secondProviderHead));

        assertEquals(List.of(secondProviderHead, firstProviderHead, firstProviderNext), ordered);
    }

    @Test
    void normalizesCaseWidthAndPunctuationForExactMatching() {
        MusicTrack normalizedExact = online(
                "lx:wy:exact", "ＡＢＣ・Song", "Artist", null, "exact");
        MusicTrack prefix = online(
                "lx:kw:prefix", "ABC Song Live", "Artist", null, "prefix");

        List<MusicTrack> ordered = rank("abc song",
                List.of(prefix, normalizedExact));

        assertEquals(List.of(normalizedExact, prefix), ordered);
    }

    @Test
    void cacheDoesNotBiasResultsWithoutAnyLiteralMatchEvidence() {
        MusicTrack zuluCached = online(
                "lx:kw:zulu", "Zulu", "Artist", null, "zulu");
        MusicTrack alphaUncached = online(
                "lx:wy:alpha", "Alpha", "Artist", null, "alpha");

        List<MusicTrack> ordered = rank("Missing",
                List.of(zuluCached, alphaUncached));

        assertEquals(List.of(zuluCached, alphaUncached), ordered);
    }

    @Test
    void localPrefilterUsesTheSameNormalizedMatchingAsRanking() {
        MusicTrack track = local("song.mp3", "ＡＢＣ・Song", "Artist", null);

        assertTrue(MusicSearchRanker.matches("abc-song", track));
        assertFalse(MusicSearchRanker.matches("missing", track));
    }

    @Test
    void originalAuthorIsSearchableAsArtistMetadata() {
        MusicTrack track = new MusicTrack("song.nbs",
                new TrackDetails("Arrangement", "Arranger", "Original Composer", null,
                        "NBS", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of("song.nbs")));

        assertTrue(MusicSearchRanker.matches("original composer", track));
    }

    private static List<MusicTrack> rank(String keyword, List<MusicTrack> tracks) {
        return MusicSearchRanker.rank(keyword, tracks);
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

    private static MusicTrack onlineFromProvider(String id, String title, String providerId) {
        String source = id.split(":")[1];
        String songId = id.substring(id.lastIndexOf(':') + 1);
        return new MusicTrack(id,
                new TrackDetails(title, "Artist", null,
                        "LX/" + source.toUpperCase(), AudioProperties.EMPTY),
                new TrackTarget.Lx(source, songId, List.of("320k"),
                        "{\"source\":\"" + source + "\",\"meta\":{\"songId\":\""
                                + songId + "\"}}", providerId));
    }
}
