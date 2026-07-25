package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** Ranks merged local and online results without using provider traversal order. */
final class MusicSearchRanker {

    private static final Comparator<RankedTrack> ORDER = Comparator
            .comparingInt((RankedTrack ranked) -> ranked.match().tier()).reversed()
            .thenComparing(Comparator.comparingInt(
                    (RankedTrack ranked) -> ranked.match().matchedTerms()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RankedTrack ranked) -> ranked.match().titleTerms()).reversed())
            .thenComparingInt(ranked -> ranked.match().positionBand())
            .thenComparingInt(ranked -> ranked.match().compactnessBand())
            .thenComparingInt(ranked -> ranked.readyPreference() ? 0 : 1)
            .thenComparingInt(ranked -> ranked.match().position())
            .thenComparingInt(ranked -> ranked.match().excessLength())
            .thenComparing(ranked -> ranked.track().details().title(),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> text(ranked.track().details().artist()),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> text(ranked.track().details().album()),
                    String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.track().id(), String.CASE_INSENSITIVE_ORDER);

    private MusicSearchRanker() {
    }

    static List<MusicTrack> rank(String keyword, List<MusicTrack> tracks,
                                 Predicate<MusicTrack> cachedTrack) {
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(cachedTrack, "cachedTrack");
        String query = normalize(keyword);
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Search keyword must not be blank");
        }
        List<String> terms = List.of(query.split(" "));
        List<RankedTrack> ranked = new ArrayList<>(tracks.size());
        for (MusicTrack track : tracks) {
            if (track == null) {
                continue;
            }
            boolean readyToPlay = !(track.target() instanceof TrackTarget.Lx)
                    || cachedTrack.test(track);
            ranked.add(new RankedTrack(track, match(track, query, terms), readyToPlay));
        }
        ranked.sort(ORDER);
        return ranked.stream().map(RankedTrack::track).toList();
    }

    static boolean matches(String keyword, MusicTrack track) {
        Objects.requireNonNull(track, "track");
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return false;
        }
        Match match = match(track, query, List.of(query.split(" ")));
        return match.tier() > MatchTier.NO_LITERAL_MATCH.score();
    }

    private static Match match(MusicTrack track, String query, List<String> terms) {
        TrackDetails details = track.details();
        String title = normalize(details.title());
        String artist = normalize(details.artist());
        String album = normalize(details.album());
        String id = normalize(track.id());
        String titleArtist = join(title, artist);
        String artistTitle = join(artist, title);
        String metadata = join(title, artist, album, id);
        int titleTerms = countTerms(title, terms);
        int metadataTerms = countTerms(metadata, terms);

        if (title.equals(query)) {
            return matched(MatchTier.EXACT_TITLE, terms.size(), titleTerms, title, query);
        }
        if (titleArtist.equals(query) || artistTitle.equals(query)) {
            String field = titleArtist.equals(query) ? titleArtist : artistTitle;
            return matched(MatchTier.EXACT_TITLE_AND_ARTIST,
                    terms.size(), titleTerms, field, query);
        }
        if (title.startsWith(query)) {
            return matched(MatchTier.TITLE_PREFIX, terms.size(), titleTerms, title, query);
        }
        if (title.contains(query)) {
            return matched(MatchTier.TITLE_PHRASE, terms.size(), titleTerms, title, query);
        }
        if (titleTerms == terms.size()) {
            return matched(MatchTier.ALL_TERMS_IN_TITLE,
                    metadataTerms, titleTerms, title, terms.getFirst());
        }
        if (titleTerms > 0 && allTermsIn(join(title, artist), terms)) {
            return matched(MatchTier.ALL_TERMS_IN_TITLE_AND_ARTIST,
                    metadataTerms, titleTerms, titleArtist, terms.getFirst());
        }
        if (artist.equals(query)) {
            return matched(MatchTier.EXACT_ARTIST,
                    terms.size(), titleTerms, artist, query);
        }
        if (artist.startsWith(query) || artist.contains(query)) {
            return matched(MatchTier.ARTIST_PHRASE,
                    metadataTerms, titleTerms, artist, query);
        }
        if (album.equals(query)) {
            return matched(MatchTier.EXACT_ALBUM,
                    terms.size(), titleTerms, album, query);
        }
        if (metadataTerms == terms.size()) {
            return matched(MatchTier.ALL_TERMS_IN_METADATA,
                    metadataTerms, titleTerms, metadata, terms.getFirst());
        }
        if (album.contains(query) || id.contains(query)) {
            String field = album.contains(query) ? album : id;
            return matched(MatchTier.METADATA_PHRASE,
                    metadataTerms, titleTerms, field, query);
        }
        if (metadataTerms > 0) {
            return matched(MatchTier.PARTIAL_TERMS,
                    metadataTerms, titleTerms, metadata, firstMatchedTerm(metadata, terms));
        }
        return new Match(MatchTier.NO_LITERAL_MATCH.score(), 0, 0,
                Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static Match matched(MatchTier tier, int matchedTerms, int titleTerms,
                                 String field, String needle) {
        int position = Math.max(0, field.indexOf(needle));
        int excessLength = Math.max(0, field.length() - needle.length());
        return new Match(tier.score(), matchedTerms, titleTerms,
                positionBand(position), compactnessBand(excessLength, needle.length()),
                position, excessLength);
    }

    private static int positionBand(int position) {
        if (position == 0) {
            return 0;
        }
        if (position <= 4) {
            return 1;
        }
        if (position <= 12) {
            return 2;
        }
        return 3;
    }

    private static int compactnessBand(int excessLength, int queryLength) {
        if (excessLength == 0) {
            return 0;
        }
        if (excessLength <= Math.max(4, queryLength / 2)) {
            return 1;
        }
        if (excessLength <= Math.max(12, queryLength)) {
            return 2;
        }
        if (excessLength <= Math.max(30, queryLength * 2)) {
            return 3;
        }
        return 4;
    }

    private static boolean allTermsIn(String value, List<String> terms) {
        return countTerms(value, terms) == terms.size();
    }

    private static int countTerms(String value, List<String> terms) {
        int matches = 0;
        for (String term : terms) {
            if (value.contains(term)) {
                matches++;
            }
        }
        return matches;
    }

    private static String firstMatchedTerm(String value, List<String> terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return term;
            }
        }
        return "";
    }

    private static String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(value);
        }
        return result.toString();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean separator = false;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                if (separator && !result.isEmpty()) {
                    result.append(' ');
                }
                result.appendCodePoint(codePoint);
                separator = false;
            } else {
                separator = true;
            }
        }
        return result.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private enum MatchTier {
        NO_LITERAL_MATCH(0),
        PARTIAL_TERMS(10),
        METADATA_PHRASE(20),
        ALL_TERMS_IN_METADATA(30),
        EXACT_ALBUM(40),
        ARTIST_PHRASE(50),
        EXACT_ARTIST(60),
        ALL_TERMS_IN_TITLE_AND_ARTIST(70),
        ALL_TERMS_IN_TITLE(80),
        TITLE_PHRASE(90),
        TITLE_PREFIX(100),
        EXACT_TITLE_AND_ARTIST(110),
        EXACT_TITLE(120);

        private final int score;

        MatchTier(int score) {
            this.score = score;
        }

        int score() {
            return score;
        }
    }

    private record Match(int tier, int matchedTerms, int titleTerms,
                         int positionBand, int compactnessBand,
                         int position, int excessLength) {
    }

    private record RankedTrack(MusicTrack track, Match match, boolean readyToPlay) {
        private boolean readyPreference() {
            return match.tier() > MatchTier.NO_LITERAL_MATCH.score() && readyToPlay;
        }
    }
}
