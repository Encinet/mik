package org.encinet.mik.module.music.ui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread-owned browser and temporary jukebox-search state. */
final class MusicBrowserSessions {

    private static final long JUKEBOX_SEARCH_CONTEXT_MILLIS = 2 * 60 * 1000L;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, JukeboxContext> jukeboxContexts = new HashMap<>();

    Session showLibrary(UUID playerId, List<MusicTrack> tracks, int page,
                        MusicPlaybackStats playbackStats) {
        Session session = session(playerId);
        session.generation++;
        session.view = View.LIBRARY;
        session.page = page;
        session.keyword = null;
        session.sourceTracks = List.copyOf(tracks);
        session.sort = MusicBrowserSort.DEFAULT;
        session.tracks = session.sort.order(session.sourceTracks, playbackStats);
        session.loading = false;
        session.requestError = null;
        session.partialFailures = 0;
        return session;
    }

    Session beginSearch(UUID playerId, String keyword) {
        Session session = session(playerId);
        session.generation++;
        session.view = View.ONLINE_SONGS;
        session.page = 0;
        session.keyword = keyword;
        session.sourceTracks = List.of();
        session.tracks = List.of();
        session.sort = MusicBrowserSort.DEFAULT;
        session.loading = true;
        session.requestError = null;
        session.partialFailures = 0;
        return session;
    }

    boolean completeSearch(UUID playerId, Session expected, int generation,
                           Inventory currentInventory, List<MusicTrack> tracks,
                           String requestError, int partialFailures,
                           MusicPlaybackStats playbackStats) {
        if (!isCurrent(playerId, expected, generation, currentInventory)) {
            return false;
        }
        expected.sourceTracks = List.copyOf(tracks);
        expected.tracks = expected.sort.order(expected.sourceTracks, playbackStats);
        expected.loading = false;
        expected.requestError = requestError;
        expected.partialFailures = partialFailures;
        return true;
    }

    Session current(UUID playerId) {
        return sessions.get(playerId);
    }

    void setPage(Session session, int page) {
        session.page = page;
    }

    void cycleSort(Session session, MusicPlaybackStats playbackStats) {
        session.sort = session.sort.next();
        session.tracks = session.sort.order(session.sourceTracks, playbackStats);
        session.page = 0;
    }

    void attachInventory(Session session, Inventory inventory) {
        session.activeInventory = inventory;
    }

    boolean isCurrentInventory(UUID playerId, Inventory inventory, int generation,
                               View view, int page) {
        Session session = sessions.get(playerId);
        return session != null && session.activeInventory == inventory
                && session.generation == generation && session.view == view && session.page == page;
    }

    boolean isCurrent(UUID playerId, Session expected, int generation, Inventory inventory) {
        return sessions.get(playerId) == expected && expected.generation == generation
                && expected.activeInventory == inventory;
    }

    void removePlayer(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null) {
            session.generation++;
            session.activeInventory = null;
        }
        jukeboxContexts.remove(playerId);
    }

    void closeInventory(UUID playerId, Inventory inventory) {
        Session session = sessions.get(playerId);
        if (session == null || session.activeInventory != inventory) {
            return;
        }
        sessions.remove(playerId, session);
        session.generation++;
        session.activeInventory = null;
        JukeboxContext context = jukeboxContexts.get(playerId);
        if (context == null || context.pendingUntilMillis <= System.currentTimeMillis()) {
            jukeboxContexts.remove(playerId);
        }
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

    private Session session(UUID playerId) {
        return sessions.computeIfAbsent(playerId, ignored -> new Session());
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

    static final class Session {
        private View view = View.LIBRARY;
        private int page;
        private int generation;
        private String keyword;
        private List<MusicTrack> sourceTracks = List.of();
        private List<MusicTrack> tracks = List.of();
        private MusicBrowserSort sort = MusicBrowserSort.DEFAULT;
        private boolean loading;
        private String requestError;
        private int partialFailures;
        private Inventory activeInventory;

        View view() { return view; }
        int page() { return page; }
        int generation() { return generation; }
        String keyword() { return keyword; }
        List<MusicTrack> tracks() { return tracks; }
        MusicBrowserSort sort() { return sort; }
        boolean loading() { return loading; }
        String requestError() { return requestError; }
        int partialFailures() { return partialFailures; }
        Inventory activeInventory() { return activeInventory; }
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
