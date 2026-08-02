package org.encinet.mik.module.music.ui;

import org.bukkit.Location;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread-owned browser and temporary jukebox-search state. */
final class MusicBrowserSessions {

    private static final long JUKEBOX_SEARCH_CONTEXT_MILLIS = 2 * 60 * 1000L;

    private final Map<UUID, Section> sections = new HashMap<>();
    private final Map<UUID, JukeboxContext> jukeboxContexts = new HashMap<>();

    Session showLibrary(UUID playerId, Session session, List<MusicTrack> tracks, int page,
                        MusicPlaybackStats playbackStats) {
        session.generation++;
        session.view = View.LIBRARY;
        session.page = page;
        session.keyword = null;
        session.sourceTracks = List.copyOf(tracks);
        session.sort = MusicBrowserSort.DEFAULT;
        session.section = sections.getOrDefault(playerId, Section.ALL);
        refreshTracks(session, playbackStats);
        session.loading = false;
        session.requestError = null;
        session.partialFailures = 0;
        return session;
    }

    Session beginSearch(UUID playerId, Session session, String keyword) {
        session.generation++;
        session.view = View.ONLINE_SONGS;
        session.page = 0;
        session.keyword = keyword;
        session.sourceTracks = List.of();
        session.sort = MusicBrowserSort.DEFAULT;
        session.section = sections.getOrDefault(playerId, Section.ALL);
        refreshTracks(session, MusicPlaybackStats.EMPTY);
        session.loading = true;
        session.requestError = null;
        session.partialFailures = 0;
        return session;
    }

    boolean completeSearch(Session expected, int generation,
                           List<MusicTrack> tracks,
                           String requestError, int partialFailures,
                           MusicPlaybackStats playbackStats) {
        if (expected.generation != generation) {
            return false;
        }
        expected.sourceTracks = List.copyOf(tracks);
        refreshTracks(expected, playbackStats);
        expected.loading = false;
        expected.requestError = requestError;
        expected.partialFailures = partialFailures;
        return true;
    }

    void setPage(Session session, int page) {
        session.page = page;
    }

    void focusTrack(Session session, String trackId) {
        session.focusedTrackId = trackId == null || trackId.isBlank() ? null : trackId;
    }

    /** Keeps focus on a visible track and otherwise selects the page's first result. */
    MusicTrack focusedTrackOnPage(Session session, int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex < fromIndex || toIndex > session.tracks.size()) {
            throw new IllegalArgumentException("Invalid visible track range");
        }
        List<MusicTrack> visible = session.tracks.subList(fromIndex, toIndex);
        MusicTrack focused = visible.stream()
                .filter(track -> track.id().equals(session.focusedTrackId))
                .findFirst().orElse(visible.isEmpty() ? null : visible.getFirst());
        session.focusedTrackId = focused == null ? null : focused.id();
        return focused;
    }

    void cycleSort(Session session, MusicPlaybackStats playbackStats) {
        session.sort = session.sort.next();
        refreshTracks(session, playbackStats);
        session.page = 0;
    }

    void cycleSection(UUID playerId, Session session, MusicPlaybackStats playbackStats) {
        session.section = session.section.next();
        sections.put(playerId, session.section);
        refreshTracks(session, playbackStats);
        session.page = 0;
    }

    List<MusicTrack> tracksInSection(UUID playerId, List<MusicTrack> tracks) {
        return sections.getOrDefault(playerId, Section.ALL).filter(tracks);
    }

    void removePlayer(UUID playerId) {
        jukeboxContexts.remove(playerId);
        sections.remove(playerId);
    }

    void setJukeboxContext(UUID playerId, Location location) {
        if (location == null) {
            jukeboxContexts.remove(playerId);
            return;
        }
        jukeboxContexts.put(playerId,
                new JukeboxContext(location.getBlock().getLocation(), 0));
    }

    Location jukeboxContext(UUID playerId) {
        JukeboxContext context = currentJukeboxContext(playerId);
        return context == null ? null : context.location.clone();
    }

    boolean hasJukeboxContext(UUID playerId) {
        return currentJukeboxContext(playerId) != null;
    }

    void prepareJukeboxSearch(UUID playerId) {
        jukeboxContexts.computeIfPresent(playerId, (ignored, context) ->
                new JukeboxContext(context.location,
                        System.currentTimeMillis() + JUKEBOX_SEARCH_CONTEXT_MILLIS));
    }

    void resumeJukeboxSearch(UUID playerId) {
        JukeboxContext context = jukeboxContexts.get(playerId);
        if (context == null || context.pendingUntilMillis <= System.currentTimeMillis()) {
            jukeboxContexts.remove(playerId);
            return;
        }
        jukeboxContexts.replace(playerId, context, new JukeboxContext(context.location, 0));
    }

    private static void refreshTracks(Session session, MusicPlaybackStats playbackStats) {
        session.tracks = session.sort.order(
                session.section.filter(session.sourceTracks), playbackStats);
    }

    private JukeboxContext currentJukeboxContext(UUID playerId) {
        JukeboxContext context = jukeboxContexts.get(playerId);
        if (context != null && context.pendingUntilMillis > 0
                && context.pendingUntilMillis < System.currentTimeMillis()) {
            jukeboxContexts.remove(playerId, context);
            return null;
        }
        return context;
    }

    enum View {
        LIBRARY,
        ONLINE_SONGS
    }

    enum Section {
        ALL,
        NBS;

        Section next() {
            return this == ALL ? NBS : ALL;
        }

        List<MusicTrack> filter(List<MusicTrack> tracks) {
            if (tracks == null || tracks.isEmpty()) {
                return List.of();
            }
            if (this == ALL) {
                return List.copyOf(tracks);
            }
            return tracks.stream()
                    .filter(track -> track != null
                            && track.target() instanceof TrackTarget.NbsFile)
                    .toList();
        }
    }

    static final class Session {
        private View view = View.LIBRARY;
        private int page;
        private int generation;
        private String keyword;
        private List<MusicTrack> sourceTracks = List.of();
        private List<MusicTrack> tracks = List.of();
        private MusicBrowserSort sort = MusicBrowserSort.DEFAULT;
        private Section section = Section.ALL;
        private boolean loading;
        private String requestError;
        private int partialFailures;
        private String focusedTrackId;

        View view() { return view; }
        int page() { return page; }
        int generation() { return generation; }
        String keyword() { return keyword; }
        List<MusicTrack> tracks() { return tracks; }
        MusicBrowserSort sort() { return sort; }
        Section section() { return section; }
        boolean loading() { return loading; }
        String requestError() { return requestError; }
        int partialFailures() { return partialFailures; }
        String focusedTrackId() { return focusedTrackId; }
    }

    private static final class JukeboxContext {
        private final Location location;
        private final long pendingUntilMillis;

        private JukeboxContext(Location location, long pendingUntilMillis) {
            this.location = location.clone();
            this.pendingUntilMillis = pendingUntilMillis;
        }
    }
}
