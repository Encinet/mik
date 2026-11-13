package org.encinet.mik.feature.entertainment.music.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxRhythmReadiness;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmDifficulty;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmGameMode;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmGameService;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmRoom;

/** Single-device rooms. Nearby members need no device-control permission to participate. */
public final class MusicRooms implements AutoCloseable {
    private final MusicPlaybackController devices;
    private final MusicDeviceRegistry registry;
    private final JukeboxPlaybackService backend;
    private final JukeboxSettingsStore settings;
    private final RhythmGameService games;
    private final BiPredicate<Player, Location> permission;
    private final Map<UUID, RhythmRoom> rooms = new HashMap<>();
    private final Map<UUID, MusicTrack> songs = new HashMap<>();
    private BiConsumer<Player, UUID> returnToRoom = (_, _) -> {};
    public MusicRooms(MusicPlaybackController devices, MusicDeviceRegistry registry,
            JukeboxPlaybackService backend, JukeboxSettingsStore settings, RhythmGameService games,
            BiPredicate<Player, Location> permission) {
        this.devices = devices;
        this.registry = registry;
        this.backend = backend;
        this.settings = settings;
        this.games = games;
        this.permission = permission;
        games.setParticipationEnded((player, location) -> {
            UUID id = find(location);
            if (id != null) {
                RhythmRoom r = rooms.get(id);
                if (r != null)
                    r.finish(player);
                publishAudience();
            }
        });
        games.setRoomReturn(player -> {
            RhythmRoom room = forPlayer(player.getUniqueId());
            if (room != null)
                returnToRoom.accept(player, room.device());
        });
    }
    public void setReturnToRoom(BiConsumer<Player, UUID> callback) {
        returnToRoom = callback;
    }
    public record View(UUID device, UUID controller, List<RhythmRoom.Member> members) {
        public View {
            members = List.copyOf(members);
        }
        public RhythmRoom.Member member(UUID id) {
            return members.stream().filter(m -> m.player().equals(id)).findFirst().orElse(null);
        }
    }
    public View get(UUID id) {
        RhythmRoom room = rooms.get(id);
        return room == null ? null : new View(room.device(), room.controller(), room.members());
    }
    public View current(UUID player) {
        RhythmRoom room = forPlayer(player);
        return room == null ? null : get(room.device());
    }
    public record Entry(MusicDevice device, View room, MusicProblem.Code restriction) { }
    /** Rooms address physical jukeboxes, including individual members of a sync group. */
    public List<Entry> nearby(Player player) {
        return registry.snapshot().devices().values().stream()
                .filter(device -> registry.local(player, device) && registry.online(device))
                .sorted(Comparator.comparing(MusicDevice::name).thenComparing(MusicDevice::id))
                .map(device -> {
                    MusicProblem.Code restriction = null;
                    try { authorizeEntry(player, device); }
                    catch (MusicProblem problem) { restriction = problem.code(); }
                    return new Entry(device, get(device.id()), restriction);
                }).toList();
    }
    public void preference(Player player, UUID id, RhythmGameMode mode, RhythmDifficulty difficulty) {
        requireLocal(player, device(id));
        RhythmRoom room = required(id);
        if (room.member(player.getUniqueId()) == null)
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        room.preference(player.getUniqueId(), mode, difficulty);
    }
    private RhythmRoom forPlayer(UUID player) {
        return rooms.values().stream().filter(r -> r.member(player) != null).findFirst().orElse(null);
    }
    public MusicTrack song(UUID id) {
        return songs.get(id);
    }
    public void open(Player player, UUID device) {
        MusicDevice d = device(device);
        authorizeEntry(player, d);
        RhythmRoom room = rooms.get(device);
        if (room == null) {
            UUID target = d.group() == null ? d.id() : d.group();
            MusicTrack current = devices.current(target);
            devices.suspendOutput(d);
            settings.writeExperienceMode(registry.block(d), JukeboxExperienceMode.RHYTHM);
            room = new RhythmRoom(device, player.getUniqueId());
            rooms.put(device, room);
            if (current != null)
                select(player, device, current);
        } else {
            RhythmRoom old = forPlayer(player.getUniqueId());
            if (old != null && !old.device().equals(device))
                leave(player);
            room.join(player.getUniqueId());
        }
        publishAudience();
    }
    public void calibrate(Player player, UUID device) {
        RhythmRoom.Member member = required(device).member(player.getUniqueId());
        if (member == null) throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        if (member.playing()) throw new MusicProblem(MusicProblem.Code.BUSY);
        MusicDevice d = device(device);
        requireLocal(player, d);
        games.openCalibration(player, registry.location(d));
    }
    public void select(Player player, UUID device, MusicTrack track) {
        if (track.target() instanceof org.encinet.mik.feature.entertainment.music.catalog.TrackTarget.NativeRecord)
            throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
        RhythmRoom room = required(device);
        controller(player, room);
        if (room.members().stream().anyMatch(RhythmRoom.Member::playing))
            throw new MusicProblem(MusicProblem.Code.BUSY);
        MusicDevice d = device(device);
        requireLocal(player, d);
        Jukebox box = registry.block(d);
        backend.stop(box.getBlock());
        songs.put(device, track);
        room.changedTrack();
        if (!backend.playProgram(null, box, track, UUID.randomUUID(), () -> {}))
            throw new MusicProblem(MusicProblem.Code.UNAVAILABLE);
        backend.prepareRhythm(box.getBlock());
    }
    public void ready(Player player, UUID device) {
        RhythmRoom room = required(device);
        requireLocal(player, device(device));
        if (!games.readyToPlay(player))
            return;
        Jukebox box = registry.block(device(device));
        if (box == null || !backend.rhythmReadiness(box.getBlock()).playable())
            throw new MusicProblem(MusicProblem.Code.BUSY);
        RhythmRoom.Member member = room.member(player.getUniqueId());
        if (member == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        room.ready(player.getUniqueId(), !member.ready());
        publishAudience();
    }
    public void start(Player player, UUID device) {
        RhythmRoom room = required(device);
        controller(player, room);
        Jukebox box = registry.block(device(device));
        if (box == null || !backend.rhythmReadiness(box.getBlock()).playable())
            throw new MusicProblem(MusicProblem.Code.BUSY);
        for (RhythmRoom.Member member : room.members())
            if (member.ready()) {
                Player p = Bukkit.getPlayer(member.player());
                if (p == null || !registry.local(p, device(device)) || !games.readyToPlay(p))
                    room.ready(member.player(), false);
            }
        List<RhythmRoom.Member> members = room.start(player.getUniqueId());
        if (members.isEmpty())
            throw new MusicProblem(MusicProblem.Code.INVALID);
        publishAudience();
        for (RhythmRoom.Member member : members) {
            Player p = Bukkit.getPlayer(member.player());
            if (p != null
                    && !games.launch(
                            p, registry.location(device(device)), member.mode(), member.difficulty()))
                room.finish(member.player());
        }
        publishAudience();
    }
    public void lateJoin(Player player, UUID device) {
        RhythmRoom room = required(device);
        requireLocal(player, device(device));
        RhythmRoom.Member member = room.member(player.getUniqueId());
        if (member == null || member.playing())
            return;
        if (!room.members().stream().anyMatch(RhythmRoom.Member::playing))
            throw new MusicProblem(MusicProblem.Code.BUSY);
        if (games.launch(player, registry.location(device(device)), member.mode(), member.difficulty()))
            room.markPlaying(player.getUniqueId());
        publishAudience();
    }
    public void leave(Player player) {
        RhythmRoom room = forPlayer(player.getUniqueId());
        if (room == null)
            return;
        games.leaveRoomGame(player);
        room.leave(player.getUniqueId());
        if (room.members().isEmpty())
            endInternal(room);
        publishAudience();
    }
    public void end(Player player, UUID device) {
        RhythmRoom room = required(device);
        controller(player, room);
        endInternal(room);
    }
    private void endInternal(RhythmRoom room) {
        rooms.remove(room.device());
        for (RhythmRoom.Member member : room.members()) {
            Player p = Bukkit.getPlayer(member.player());
            if (p != null)
                games.leaveRoomGame(p);
        }
        MusicDevice d = registry.snapshot().devices().get(room.device());
        if (d != null) {
            Jukebox box = registry.block(d);
            if (box != null) {
                backend.stop(box.getBlock());
                settings.writeExperienceMode(box, JukeboxExperienceMode.MUSIC);
            }
        }
        songs.remove(room.device());
        publishAudience();
    }
    public void unavailable(Location location) {
        UUID id = find(location);
        if (id != null)
            endInternal(rooms.get(id));
    }
    public JukeboxRhythmReadiness readiness(UUID device) {
        Jukebox box = registry.block(device(device));
        return box == null ? JukeboxRhythmReadiness.UNAVAILABLE : backend.rhythmReadiness(box.getBlock());
    }
    private RhythmRoom required(UUID id) {
        RhythmRoom room = rooms.get(id);
        if (room == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        return room;
    }
    private MusicDevice device(UUID id) {
        MusicDevice d = registry.snapshot().devices().get(id);
        if (d == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        return d;
    }
    private void requireLocal(Player player, MusicDevice device) {
        if (registry.block(device) == null)
            throw new MusicProblem(MusicProblem.Code.OFFLINE);
        if (!registry.local(player, device))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
    }
    private void authorizeEntry(Player player, MusicDevice device) {
        requireLocal(player, device);
        if (!rooms.containsKey(device.id()) && !permission.test(player, registry.location(device)))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
    }
    private void controller(Player p, RhythmRoom room) {
        requireLocal(p, device(room.device()));
        if (!room.controller().equals(p.getUniqueId())
                || !permission.test(p, registry.location(device(room.device()))))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
    }
    private UUID find(Location location) {
        return rooms.keySet()
                .stream()
                .filter(id -> {
                    MusicDevice d = registry.snapshot().devices().get(id);
                    return d != null && location.equals(registry.location(d));
                })
                .findFirst()
                .orElse(null);
    }
    private void publishAudience() {
        Map<Location, Set<UUID>> audience = new HashMap<>();
        for (RhythmRoom room : rooms.values()) {
            Set<UUID> members = new HashSet<>();
            room.members().stream().filter(RhythmRoom.Member::playing).forEach(m -> members.add(m.player()));
            audience.put(registry.location(device(room.device())), members);
        }
        devices.roomAudience(audience);
    }
    @Override
    public void close() {
        for (RhythmRoom room : List.copyOf(rooms.values())) endInternal(room);
    }
}
