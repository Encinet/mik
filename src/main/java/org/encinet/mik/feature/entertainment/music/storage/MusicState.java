package org.encinet.mik.feature.entertainment.music.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;

/** Immutable published snapshot. Only the store's serialized transaction executor edits copies. */
public record MusicState(Map<String, StoredTrack> tracks, Map<UUID, Playlist> playlists,
        Map<UUID, MusicDevice> devices, Map<String, Binding> bindings, Map<UUID, Group> groups,
        Map<UUID, PlaybackProgram> programs, Map<String, Favorite> favorites,
        Map<UUID, Recent> recents, Map<UUID, ImportReceipt> imports, Map<UUID, Checkpoint> checkpoints) {
    public MusicState {
        tracks = Map.copyOf(tracks);
        playlists = Map.copyOf(playlists);
        devices = Map.copyOf(devices);
        bindings = Map.copyOf(bindings);
        groups = Map.copyOf(groups);
        programs = Map.copyOf(programs);
        favorites = Map.copyOf(favorites);
        recents = Map.copyOf(recents);
        imports = Map.copyOf(imports);
        checkpoints = Map.copyOf(checkpoints);
    }
    public static MusicState empty() {
        return new MusicState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
    public static String key(UUID player, Object subject) {
        return player + ":" + subject;
    }
    public record Checkpoint(UUID entry, long position, boolean paused, long savedAt) {}
    public record Binding(UUID player, UUID target, String alias) {
        public Binding { Objects.requireNonNull(player); Objects.requireNonNull(target); Objects.requireNonNull(alias); }
    }
    public record Favorite(UUID player, UUID playlist, long at) {
        public Favorite { Objects.requireNonNull(player); Objects.requireNonNull(playlist); }
    }
    public record Recent(UUID id, UUID player, String track, UUID target, long at) {}
    public record ImportReceipt(UUID id, UUID player, String source, String reference, UUID playlist,
            int loaded, int total, long at) {}
    public record Recovery(PlaybackProgram program, Checkpoint checkpoint) {
        public Recovery { Objects.requireNonNull(program); }
    }
    public record Group(UUID id, String name, List<UUID> members, Map<UUID, Recovery> recovery,
            int gain, long version) {
        public Group {
            members = List.copyOf(members);
            recovery = Map.copyOf(recovery);
        }
    }
    public Editor edit() {
        return new Editor(this);
    }
    public static final class Editor {
        public final Map<String, StoredTrack> tracks;
        public final Map<UUID, Playlist> playlists;
        public final Map<UUID, MusicDevice> devices;
        public final Map<String, Binding> bindings;
        public final Map<UUID, Group> groups;
        public final Map<UUID, PlaybackProgram> programs;
        public final Map<String, Favorite> favorites;
        public final Map<UUID, Recent> recents;
        public final Map<UUID, ImportReceipt> imports;
        public final Map<UUID, Checkpoint> checkpoints;
        private Editor(MusicState s) {
            tracks = new HashMap<>(s.tracks);
            playlists = new HashMap<>(s.playlists);
            devices = new HashMap<>(s.devices);
            bindings = new HashMap<>(s.bindings);
            groups = new HashMap<>(s.groups);
            programs = new HashMap<>(s.programs);
            favorites = new HashMap<>(s.favorites);
            recents = new HashMap<>(s.recents);
            imports = new HashMap<>(s.imports);
            checkpoints = new HashMap<>(s.checkpoints);
        }
        public MusicState snapshot() {
            return new MusicState(tracks, playlists, devices, bindings, groups, programs, favorites,
                    recents, imports, checkpoints);
        }
    }
}
