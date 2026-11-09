package org.encinet.mik.feature.entertainment.music.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;

/** Read-only, actor-filtered projection. Presentation never receives the transactional store. */
public final class MusicQueries {
    private final MusicStore store;
    public MusicQueries(MusicStore store) {
        this.store = store;
    }
    public boolean ready() {
        return store.ready();
    }
    public long revision() {
        return store.revision();
    }
    public MusicState snapshot(UUID actor) {
        MusicState s = store.snapshot();
        var lists = s.playlists()
                            .values()
                            .stream()
                            .filter(p -> p.visibleTo(actor))
                            .collect(Collectors.toUnmodifiableMap(Playlist::id, Function.identity()));
        var recents =
                s.recents()
                        .values()
                        .stream()
                        .filter(r -> r.player().equals(actor))
                        .collect(Collectors.toUnmodifiableMap(MusicState.Recent::id, Function.identity()));
        var favorites =
                s.favorites()
                        .entrySet()
                        .stream()
                        .filter(e
                                -> e.getValue().player().equals(actor)
                                        && lists.containsKey(e.getValue().playlist()))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var bindings = s.bindings()
                               .entrySet()
                               .stream()
                               .filter(e -> e.getValue().player().equals(actor))
                               .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var imports = s.imports()
                              .values()
                              .stream()
                              .filter(i -> i.player().equals(actor))
                              .collect(Collectors.toUnmodifiableMap(
                                      MusicState.ImportReceipt::id, Function.identity()));
        Set<String> visible = new HashSet<>();
        lists.values().forEach(p -> p.entries().forEach(e -> visible.add(e.trackId())));
        recents.values().forEach(r -> visible.add(r.track()));
        s.programs().values().forEach(p -> {
            p.base().forEach(e -> visible.add(e.trackId()));
            p.priority().forEach(e -> visible.add(e.trackId()));
            if (p.current() != null)
                visible.add(p.current().trackId());
        });
        var tracks = s.tracks()
                             .entrySet()
                             .stream()
                             .filter(e -> visible.contains(e.getKey()))
                             .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new MusicState(tracks, lists, s.devices(), bindings, s.groups(), s.programs(),
                favorites, recents, imports, s.checkpoints());
    }
}
