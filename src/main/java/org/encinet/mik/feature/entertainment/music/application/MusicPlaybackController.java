package org.encinet.mik.feature.entertainment.music.application;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackOrigin;
import org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackIntents;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;

/** Main-thread application coordinator. Persistent commands serialize through MusicStore. */
public final class MusicPlaybackController implements MusicGroupService.Outputs, AutoCloseable {
    public enum Action { PAUSE, RESUME, STOP, NEXT, PREVIOUS, CLEAR }
    private static final class Running {
        final UUID generation = UUID.randomUUID();
        UUID preparation = UUID.randomUUID();
        final Set<UUID> outputs = new HashSet<>();
        final Map<UUID, MusicProblem.Code> failed = new HashMap<>();
        boolean recorded;
        boolean paused;
        MusicTrack recordTrack;
        UUID recordSource;
        int recordGain;
    }
    private final JavaPlugin plugin;
    private final MusicStore store;
    private final JukeboxPlaybackService backend;
    private final JukeboxSettingsStore settings;
    private final MusicDeviceRegistry registry;
    private final MusicMainThread tasks;
    private final MusicDiscResolver discs;

    private final Map<UUID, Running> running = new HashMap<>();
    private final PlaybackIntents intents = new PlaybackIntents();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Deque<QueueUndo>> undo = new HashMap<>();
    private record QueueUndo(UUID target, long at, PlaybackProgram before, PlaybackProgram after) {}
    private volatile Map<Location, Set<UUID>> audience = Map.of();
    private volatile Map<Location, Set<UUID>> roomListeners = Map.of();
    private volatile Set<UUID> isolated = Set.of();
    private BukkitTask audienceTask;
    private int checkpointTicks;
    private boolean closed;
    public MusicPlaybackController(JavaPlugin plugin, MusicStore store, JukeboxPlaybackService backend,
            JukeboxSettingsStore settings, MusicDeviceRegistry registry, MusicMainThread tasks, MusicDiscResolver discs) {
        this.plugin = plugin;
        this.store = store;
        this.backend = backend;
        this.settings = settings;
        this.registry = registry;
        this.tasks = tasks;
        this.discs = discs;
        backend.setCompletionListener(this::ended);
        backend.setPhysicalStarted((location, track, actor) -> {
            UUID target = store.snapshot()
                                  .devices()
                                  .values()
                                  .stream()
                                  .filter(d -> location.equals(registry.location(d)))
                                  .map(device -> device.group() == null ? device.id() : device.group())
                                  .findFirst()
                                  .orElse(null);
            store.transact(e -> {
                e.tracks.put(track.id(), StoredTrack.of(track));
                if (actor != null && target != null) {
                    UUID id = UUID.randomUUID();
                    e.recents.put(id,
                            new MusicState.Recent(id, actor, track.id(), target, System.currentTimeMillis()));
                }
                return null;
            });
        });
        backend.setOutputAudience((location, player) -> {
            Set<UUID> room = roomListeners.get(location);
            if (room != null)
                return room.contains(player);
            if (isolated.contains(player))
                return false;
            Set<UUID> listeners = audience.get(location);
            return listeners == null || listeners.contains(player);
        });
    }
    public void roomAudience(Map<Location, Set<UUID>> listeners) {
        Map<Location, Set<UUID>> copy = new HashMap<>();
        listeners.forEach((l, p) -> copy.put(l, Set.copyOf(p)));
        roomListeners = Map.copyOf(copy);
        isolated = listeners.values()
                           .stream()
                           .flatMap(Set::stream)
                           .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public void suspendOutput(MusicDevice d) {
        UUID id = d.group() == null ? d.id() : d.group();
        Running r = running.get(id);
        if (r != null && d.id().equals(r.recordSource)) {
            try { stop(registry.target(id)); }
            catch (MusicProblem removed) { running.remove(id); }
            Jukebox source = registry.block(d);
            if (source != null) backend.stop(source.getBlock());
            return;
        }
        if (r != null) {
            r.outputs.remove(d.id());
            if (r.outputs.isEmpty())
                running.remove(id);
        }
        Jukebox b = registry.block(d);
        if (b != null)
            backend.stop(b.getBlock());
    }
    public void enable() {
        audienceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAudience, 1, 5);
    }

    public PlaybackProgram program(UUID id) {
        return store.snapshot().programs().getOrDefault(id, PlaybackProgram.empty());
    }
    public JukeboxPlaybackSnapshot status(UUID id) {
        return status(registry.playbackTarget(id), store.snapshot());
    }
    private JukeboxPlaybackSnapshot status(DeviceTarget t, MusicState state) {
        JukeboxPlaybackSnapshot active = null;
        for (MusicDevice d : t.members()) {
            Jukebox b = registry.block(d);
            if (b != null && backend.isPlaying(b.getBlock())) {
                var sample = backend.snapshot(b.getBlock());
                if (sample.status() == PlaybackStatus.PLAYING) return sample;
                if (active == null || sample.status() == PlaybackStatus.PAUSED) active = sample;
            }
        }
        if (active != null) return active;
        MusicState.Checkpoint saved = state.checkpoints().get(t.id());
        QueueEntry current = state.programs().getOrDefault(t.id(), PlaybackProgram.empty()).current();
        return saved != null && current != null && saved.entry().equals(current.id())
                ? new JukeboxPlaybackSnapshot(PlaybackStatus.PAUSED, saved.position())
                : new JukeboxPlaybackSnapshot(PlaybackStatus.STOPPED, 0);
    }
    public MusicTrack current(UUID id) {
        return current(registry.playbackTarget(id), store.snapshot());
    }
    private MusicTrack current(DeviceTarget target, MusicState state) {
        var physical = physical(target);
        return physical == null ? queuedTrack(target.id(), state) : physical.track();
    }
    private JukeboxPlaybackService.PhysicalPlayback physical(DeviceTarget target) {
        var active = running.get(target.id());
        if (active != null && active.recordTrack != null)
            return new JukeboxPlaybackService.PhysicalPlayback(active.generation,active.recordTrack);
        for (MusicDevice d : target.members()) {
            Jukebox box = registry.block(d);
            if (box != null) {
                var physical = backend.physicalPlayback(box.getBlock());
                if (physical != null)
                    return physical;
            }
        }
        return null;
    }
    private static MusicTrack queuedTrack(UUID id, MusicState state) {
        QueueEntry c = state.programs().getOrDefault(id, PlaybackProgram.empty()).current();
        StoredTrack t = c == null ? null : state.tracks().get(c.trackId());
        return t == null ? null : t.track();
    }
    public MusicPlaybackSnapshot snapshot(UUID id) {
        var target = registry.playbackTarget(id);
        var state = store.snapshot();
        var status = status(target, state);
        var physical = physical(target);
        var entry = physical == null ? state.programs().getOrDefault(id, PlaybackProgram.empty()).current() : null;
        var active = running.get(id);
        var track = physical == null ? queuedTrack(id, state) : physical.track();
        UUID generation = physical != null ? physical.id() : active == null ? null : active.generation;
        boolean seekable = generation != null && track != null && (physical != null || entry != null && entry.trackId().equals(track.id()))
                && (status.status() == PlaybackStatus.PLAYING || status.status() == PlaybackStatus.PAUSED)
                && target.members().stream().map(registry::block).filter(Objects::nonNull).anyMatch(box -> backend.seekable(box.getBlock()));
        return new MusicPlaybackSnapshot(id, entry == null ? null : entry.id(), generation,
                track, status.status(), status.positionMillis(), System.nanoTime(), seekable,
                status.status() != PlaybackStatus.LOADING || busy.contains(id) ? null
                        : physical != null ? physical.id() : active == null ? null : active.preparation);
    }

    /** Cancels media preparation after its queue commit, never an in-flight durable edit or later run. */
    public boolean cancelPreparation(Player player, MusicPlaybackSnapshot expected) {
        var target = registry.playbackTarget(expected.target());
        registry.authorize(player, target);
        if (busy.contains(target.id()) || !expected.samePreparation(snapshot(target.id()))) return false;
        stop(target);
        return true;
    }
    public CompletableFuture<Void> submit(Player player, MusicSubmission request) {
        request.begin(player.getUniqueId());
        UUID id = request.target();
        CompletableFuture<Void> result;
        try {
            registry.authorize(player, registry.playbackTarget(id));
            result = request.attempt(() -> program(id), intent -> intents.current(id, intent), committed ->
                    change(player, id, p -> p.submit(request.tracks().stream()
                                    .map(track -> QueueEntry.create(track.id(), player.getUniqueId())).toList(), request.placement()),
                            request.tracks(), request.placement() == PlaybackProgram.Submission.INSERT, false, committed),
                    () -> retryOutputs(player, id));
        } catch (Exception error) {
            request.failed();
            throw error;
        }
        return result.whenComplete((_, error) -> {
            if (error == null) request.finish();
            else request.failed();
        });
    }
    public CompletableFuture<Void> control(Player player, UUID id, Action action) {
        if (busy.contains(id)) throw new MusicProblem(MusicProblem.Code.BUSY);
        DeviceTarget t = registry.playbackTarget(id);
        registry.authorize(player, t);
        if (action == Action.PAUSE || action == Action.RESUME) {
            Running r = running.get(id);
            boolean pause = action == Action.PAUSE;
            if (r == null) {
                var physical = physical(t);
                if (physical != null) {
                    if (pause && status(id).status() == PlaybackStatus.LOADING) {
                        stop(t);
                        return CompletableFuture.completedFuture(null);
                    }
                    boolean applied = false;
                    for (MusicDevice d : t.members()) {
                        Jukebox box = registry.block(d);
                        if (box != null) applied |= backend.setPaused(box.getBlock(), physical.track().id(), pause);
                    }
                    if (!applied && status(id).status() != (pause ? PlaybackStatus.PAUSED : PlaybackStatus.PLAYING))
                        throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
                    return CompletableFuture.completedFuture(null);
                }
                if (pause) {
                    intents.cancel(id);
                    return CompletableFuture.completedFuture(null);
                }
                return change(player, id,
                        p
                        -> p.current() == null
                                ? p.advance(false, java.util.random.RandomGenerator.getDefault())
                                : p,
                        List.of(), true, false);
            }
            if (pause && status(id).status() == PlaybackStatus.LOADING) {
                stop(t);
                return CompletableFuture.completedFuture(null);
            }
            if (!pause && r.outputs.isEmpty()) {
                retryOutputs(player, id);
                return CompletableFuture.completedFuture(null);
            }
            if (!pause) checkOverlap(t, null);
            boolean applied = false;
            for (MusicDevice d : t.members()) {
                Jukebox b = registry.block(d);
                MusicTrack track = current(id);
                if (b != null && track != null) applied |= backend.setPaused(b.getBlock(), track.id(), pause);
            }
            if (!applied && status(id).status() != (pause ? PlaybackStatus.PAUSED : PlaybackStatus.PLAYING))
                throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
            r.paused = pause;
            return CompletableFuture.completedFuture(null);
        }
        if (action == Action.STOP) {
            QueueEntry current = physical(t) == null ? program(id).current() : null;
            stop(t);
            if (current == null) return CompletableFuture.completedFuture(null);
            return store.transact(editor -> {
                if (Objects.equals(editor.programs.getOrDefault(id, PlaybackProgram.empty()).current(), current))
                    editor.checkpoints.put(id, new MusicState.Checkpoint(current.id(), 0, true, System.currentTimeMillis()));
                return null;
            });
        }
        if (action == Action.CLEAR)
            return change(player, id, PlaybackProgram::clearPending, List.of(), false, true);
        if (action == Action.PREVIOUS) {
            long position = status(id).positionMillis();
            return change(player, id, p -> p.previous(position), List.of(), true, false);
        }
        return change(player, id,
                p
                -> p.advance(false, java.util.random.RandomGenerator.getDefault()),
                List.of(), true, false);
    }
    private MusicProblem.Code failure(UUID target, Throwable error) {
        return MusicProblem.report(plugin.getLogger(), "Music playback failed for target " + target, error);
    }
    private CompletableFuture<Void> change(Player player, UUID id, UnaryOperator<PlaybackProgram> mutation,
            List<MusicTrack> tracks, boolean restart, boolean reversible) {
        return change(player, id, mutation, tracks, restart, reversible, (_, _) -> {});
    }
    private CompletableFuture<Void> change(Player player, UUID id, UnaryOperator<PlaybackProgram> mutation,
            List<MusicTrack> tracks, boolean restart, boolean reversible, BiConsumer<PlaybackProgram, Long> committed) {
        DeviceTarget t = registry.playbackTarget(id);
        registry.authorize(player, t);
        if (!busy.add(id))
            throw new MusicProblem(MusicProblem.Code.BUSY);
        PlaybackProgram before = program(id);
        PlaybackProgram after;
        try {
            after = mutation.apply(before);
            if (restart && after.current() != null)
                checkOverlap(t, null);
        } catch (Exception error) {
            busy.remove(id);
            throw error;
        }
        if (after == before && tracks.isEmpty() && !restart) {
            busy.remove(id);
            return CompletableFuture.completedFuture(null);
        }
        long intent = intents.begin(id);
        UUID actor = player.getUniqueId();
        MusicState.Group expectedGroup = store.snapshot().groups().get(id);
        CompletableFuture<Void> saved = store.transact(e -> {
            if (!Objects.equals(expectedGroup, e.groups.get(id))
                    || e.programs.getOrDefault(id, PlaybackProgram.empty()).version() != before.version())
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            for (MusicDevice device : t.members()) {
                MusicDevice current = e.devices.get(device.id());
                if (current == null || !Objects.equals(device.group(), current.group()))
                    throw new MusicProblem(MusicProblem.Code.CONFLICT);
            }
            tracks.forEach(track -> e.tracks.put(track.id(), StoredTrack.of(track)));
            e.programs.put(id, after);
            if (restart && !Objects.equals(before.current(), after.current()))
                e.checkpoints.remove(id);
            return null;
        });
        return main(saved, () -> {
            committed.accept(after, intent);
            if (!intents.current(id, intent))
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            registry.authorize(player, registry.playbackTarget(id));
            if (reversible) {
                Deque<QueueUndo> journal = undo.computeIfAbsent(actor, _ -> new ArrayDeque<>());
                journal.addLast(new QueueUndo(id, System.currentTimeMillis(), before, after));
                while (journal.size() > 20) journal.removeFirst();
            }
            if (restart) {
                start(player, registry.playbackTarget(id), false);
            }
        }).whenComplete((_, _) -> onMain(() -> busy.remove(id)));
    }
    public CompletableFuture<Void> mode(Player player, UUID id, PlaybackProgram.Mode mode) {
        return change(player, id, p -> p.withMode(mode), List.of(), false, false);
    }
    public CompletableFuture<Void> removeQueue(Player player, UUID id, long expected, Set<UUID> entries) {
        var selected = Set.copyOf(entries);
        return editQueue(player, id, expected, program -> program.removePending(selected));
    }
    public CompletableFuture<Void> moveQueue(Player player, UUID id, long expected, Set<UUID> entries, EntryEdits.Destination destination) {
        var selected = Set.copyOf(entries);
        return editQueue(player, id, expected, program -> program.movePending(selected, destination));
    }
    private CompletableFuture<Void> editQueue(Player player, UUID id, long expected, UnaryOperator<PlaybackProgram> operation) {
        return change(player, id, program -> {
            if (program.version() != expected) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            try { return operation.apply(program); }
            catch (IllegalStateException stale) { throw new MusicProblem(MusicProblem.Code.CONFLICT); }
            catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
        }, List.of(), false, true);
    }
    public CompletableFuture<Void> undo(Player player, UUID target) {
        Deque<QueueUndo> journal = undo.get(player.getUniqueId());
        if (journal == null || journal.isEmpty())
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        QueueUndo edit = journal.stream().filter(item -> item.target().equals(target))
                .reduce((_, last) -> last).orElseThrow(() -> new MusicProblem(MusicProblem.Code.NOT_FOUND));
        if (System.currentTimeMillis() - edit.at() > 600000
                || program(edit.target()).version() != edit.after().version())
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        return change(player, edit.target(), p -> {
            if (!Objects.equals(p.current(), edit.after().current()))
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            return p.edit(edit.before().base(), edit.before().priority());
        }, List.of(), false, false).thenRun(() -> journal.remove(edit));
    }
    public boolean canUndo(Player player, UUID target) {
        Deque<QueueUndo> journal = undo.get(player.getUniqueId());
        return journal != null && journal.stream().anyMatch(edit -> edit.target().equals(target)
                && System.currentTimeMillis() - edit.at() <= 600000 && program(target).version() == edit.after().version());
    }
    public boolean seek(Player player, MusicPlaybackSnapshot expected, long millis) {
        UUID id = expected.target();
        DeviceTarget t = registry.playbackTarget(id);
        registry.authorize(player, t);
        var current = snapshot(id);
        if (!expected.seekable() || !expected.samePlayback(current)) throw new MusicProblem(MusicProblem.Code.CONFLICT);
        if (millis < 0 || !current.seekable() || current.durationMillis() <= 0)
            return false;
        MusicTrack track = current.track();
        for (MusicDevice d : t.members()) {
            Jukebox b = registry.block(d);
            if (b != null && backend.seekable(b.getBlock())) {
                if (current.entry() == null && (running.get(id) == null || running.get(id).recordTrack == null)) {
                    var physical = backend.physicalPlayback(b.getBlock());
                    if (physical == null || !physical.id().equals(current.generation())) continue;
                }
                return backend.seek(b.getBlock(), track.id(), Math.min(millis, current.durationMillis() - 1));
            }
        }
        return false;
    }
    public void eject(Player player, UUID id) {
        DeviceTarget t = registry.playbackTarget(id);
        registry.authorize(player, t);
        stop(t);
        for (MusicDevice d : t.members()) {
            Jukebox b = registry.block(d);
            if (b != null && settings.readExperienceMode(b) != JukeboxExperienceMode.RHYTHM)
                backend.stopAndEject(b.getBlock());
        }
    }

    private MusicDevice device(Jukebox box) {
        String raw = box.getPersistentDataContainer().get(new NamespacedKey("mik","music_device_id"),PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            var device = store.snapshot().devices().get(UUID.fromString(raw));
            return device != null && box.getLocation().equals(registry.location(device)) ? device : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    public boolean grouped(Jukebox box) {
        var device = device(box);
        return device != null && device.group() != null;
    }
    /** Called after the inventory transaction stages one real record in the clicked member. */
    public boolean playInsertedDisc(Player player, Jukebox box, PlaybackOrigin origin) {
        var device = device(box);
        if (device == null || device.group() == null) {
            unavailable(box.getLocation(),false);
            return backend.playInsertedDisc(player,box,origin);
        }
        var target = registry.playbackTarget(device.group());
        if (player != null) registry.authorize(player,target);
        if (busy.contains(target.id())) throw new MusicProblem(MusicProblem.Code.BUSY);
        var track = discs.resolve(box.getRecord());
        if (track == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        checkOverlap(target,null);
        requireCapacity(target);
        var previous = running.get(target.id());
        if (previous != null && previous.recordTrack == null) {
            var saved = checkpoint(target.id());
            if (saved != null) store.transact(editor -> {
                var current = editor.programs.getOrDefault(target.id(),PlaybackProgram.empty()).current();
                if (current != null && current.id().equals(saved.entry())) editor.checkpoints.put(target.id(),saved);
                return null;
            }).exceptionally(error -> { failure(target.id(),error); return null; });
        }
        var record = new Running(); record.recordSource = device.id(); record.recordTrack = track;
        restartRecord(player,target,record,origin);
        return !record.outputs.isEmpty();
    }
    private void restartRecord(Player player, DeviceTarget target, Running record, PlaybackOrigin origin) {
        stop(target);
        record.recordGain = store.snapshot().groups().get(target.id()).gain();
        running.put(target.id(),record);
        var source = target.members().stream().filter(member -> member.id().equals(record.recordSource)).findFirst().orElseThrow();
        startRecordOutput(player,target,record,source,origin);
        if (origin == PlaybackOrigin.PLAYER && !record.outputs.contains(source.id())) {
            stop(target); record.outputs.clear(); return;
        }
        for (var member : target.members()) {
            if (member.id().equals(record.recordSource)) continue;
            var box = registry.block(member);
            if (box != null && !member.id().equals(record.recordSource)
                    && settings.readExperienceMode(box) != JukeboxExperienceMode.RHYTHM && box.hasRecord())
                backend.stopAndEject(box.getBlock());
            startRecordOutput(player,target,record,member,PlaybackOrigin.AUTOMATIC);
        }
        updateAudience();
        if (record.outputs.isEmpty()) running.remove(target.id(),record);
    }
    private void restartNativeRecord(Player player, DeviceTarget target, Running active) {
        var record = new Running(); record.recordTrack = active.recordTrack; record.recordSource = active.recordSource;
        restartRecord(player,target,record,PlaybackOrigin.AUTOMATIC);
    }
    private void startRecordOutput(Player player, DeviceTarget target, Running record, MusicDevice member, PlaybackOrigin origin) {
        var box = registry.block(member);
        if (box == null) { record.failed.put(member.id(),MusicProblem.Code.OFFLINE); return; }
        if (settings.readExperienceMode(box) == JukeboxExperienceMode.RHYTHM) {
            record.failed.put(member.id(),MusicProblem.Code.BUSY); return;
        }
        try {
            applySound(member);
            record.outputs.add(member.id());
            if (backend.playRecordOutput(player,box,record.recordTrack,record.generation,member.id().equals(record.recordSource),origin))
                record.failed.remove(member.id());
            else { record.outputs.remove(member.id()); record.failed.put(member.id(),MusicProblem.Code.UNAVAILABLE); }
        } catch (Exception failed) {
            record.outputs.remove(member.id()); record.failed.put(member.id(),failure(target.id(),failed));
        }
    }
    /** An empty member is an equally valid handle for taking the group's real record out. */
    public boolean ejectInsertedDisc(Player player, Jukebox box) {
        var device = device(box);
        if (device == null || device.group() == null) return false;
        var target = registry.playbackTarget(device.group());
        if (target.members().stream().map(registry::block).filter(Objects::nonNull).noneMatch(Jukebox::hasRecord)) return false;
        eject(player,target.id());
        return true;
    }
    public void recordRemoved(Jukebox box) {
        var device = device(box);
        var record = device == null || device.group() == null ? null : running.get(device.group());
        if (record != null && device.id().equals(record.recordSource)) stop(registry.target(device.group()));
        else if (record == null) backend.stop(box.getBlock());
    }
    public CompletableFuture<Void> sound(Player player, UUID id, Integer volume, Integer range) {
        DeviceTarget t = registry.target(id);
        registry.authorize(player, t);
        Map<UUID, MusicDevice> proposed = new HashMap<>();
        for (MusicDevice d : t.members())
            proposed.put(d.id(),
                    d.settings(d.name(), volume == null ? d.volume() : volume,
                            range == null ? d.range() : range));
        checkOverlap(t, proposed);
        return main(store.transact(e -> {
            for (MusicDevice before : t.members())
                if (!before.equals(e.devices.get(before.id()))) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            e.devices.putAll(proposed);
            return null;
        }),
                () -> {
                    registry.authorize(player, registry.target(id));
                    for (MusicDevice d : proposed.values()) applySound(d);
                    UUID target = t.members().getFirst().group() == null ? id : t.members().getFirst().group();
                    var record = running.get(target);
                    if (record != null && record.recordTrack != null && record.recordTrack.target() instanceof TrackTarget.NativeRecord)
                        restartNativeRecord(player,registry.target(target),record);
                });
    }

    private void applySound(MusicDevice d) {
        Jukebox b = registry.block(d);
        if (b != null && settings.readExperienceMode(b) != JukeboxExperienceMode.RHYTHM) {
            int gain = d.group() == null ? 100 : store.snapshot().groups().get(d.group()).gain();
            JukeboxSoundSettings s = new JukeboxSoundSettings(d.volume() * gain / 100, d.range());
            settings.write(b, s);
            backend.updateSettings(b.getBlock(), s);
        }
    }
    private void checkOverlap(DeviceTarget proposed, Map<UUID, MusicDevice> replacement) {
        for (MusicDevice first : proposed.members())
            for (UUID active : running.keySet()) {
                if (active.equals(proposed.id()) || active.equals(first.group())
                        || running.get(active).paused)
                    continue;
                for (MusicDevice other : registry.target(active).members()) {
                    MusicDevice a = replacement == null ? first : replacement.get(first.id());
                    if (a == null)
                        a = first;
                    if (!running.get(active).outputs.contains(other.id()))
                        continue;
                    if (!a.world().equals(other.world()) || a.volume() == 0 || other.volume() == 0)
                        continue;
                    double radius = a.range() * .5 * Math.sqrt(a.volume() / 100.0)
                            + other.range() * .5 * Math.sqrt(other.volume() / 100.0);
                    double dx = (double) a.x() - other.x(), dy = (double) a.y() - other.y(),
                           dz = (double) a.z() - other.z();
                    if (dx * dx + dy * dy + dz * dz <= radius * radius)
                        throw new MusicProblem(MusicProblem.Code.OVERLAP);
                }
            }
    }
    private void start(Player actor, DeviceTarget t, boolean automatic) {
        PlaybackProgram p = program(t.id());
        if (p.current() == null) {
            stopOutput(t);
            return;
        }
        StoredTrack stored = store.snapshot().tracks().get(p.current().trackId());
        MusicTrack track = stored == null ? null : stored.track();
        if (track == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        checkOverlap(t, null);
        requireCapacity(t);
        stopOutput(t);
        Running r = new Running();
        running.put(t.id(), r);
        for (MusicDevice device : t.members()) startOutput(actor, t, r, device, automatic);
        if (r.outputs.isEmpty()) throw new MusicProblem(MusicProblem.Code.OFFLINE);
    }
    private void requireCapacity(DeviceTarget t) {
        if (!running.containsKey(t.id())
                && (running.size() >= 16
                        || running.values().stream().mapToInt(r -> r.outputs.size()).sum()
                                        + t.members().size()
                                > 64))
            throw new MusicProblem(MusicProblem.Code.LIMIT);
    }
    private void startOutput(Player actor, DeviceTarget target, Running running, MusicDevice device, boolean automatic) {
        var box = registry.block(device);
        if (box == null || (automatic && box.hasRecord())) {
            running.failed.put(device.id(), MusicProblem.Code.OFFLINE);
            return;
        }
        if (settings.readExperienceMode(box) == JukeboxExperienceMode.RHYTHM) {
            running.failed.put(device.id(), MusicProblem.Code.BUSY);
            return;
        }
        var current = program(target.id()).current();
        var stored = current == null ? null : store.snapshot().tracks().get(current.trackId());
        if (stored == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        try {
            applySound(device);
            if (!automatic && box.hasRecord()) backend.stopAndEject(box.getBlock());
            running.outputs.add(device.id());
            boolean started = backend.playProgram(automatic ? null : actor, box, stored.track(), running.generation,
                    () -> onMain(() -> recordStarted(target, running, current)));
            if (started) running.failed.remove(device.id());
            else {
                running.outputs.remove(device.id());
                running.failed.put(device.id(), MusicProblem.Code.UNAVAILABLE);
            }
        } catch (Exception error) {
            running.outputs.remove(device.id());
            running.failed.put(device.id(), failure(target.id(), error));
        }
    }
    /** Reconnect missing members to the existing clock without editing the queue or restarting healthy outputs. */
    public void retryOutputs(Player player, UUID id) {
        var target = registry.playbackTarget(id);
        registry.authorize(player, target);
        if (busy.contains(id)) throw new MusicProblem(MusicProblem.Code.BUSY);
        var active = running.get(id);
        if (active == null) { start(player, target, false); return; }
        checkOverlap(target, null);
        var missing = target.members().stream().filter(device -> !active.outputs.contains(device.id())).toList();
        if (active.recordTrack != null) {
            if (!missing.isEmpty() && active.recordTrack.target() instanceof TrackTarget.NativeRecord) {
                restartNativeRecord(player,target,active);
            } else for (var device : missing) startRecordOutput(player,target,active,device,PlaybackOrigin.AUTOMATIC);
            return;
        }
        if (!missing.isEmpty()) active.preparation = UUID.randomUUID();
        for (var device : missing)
            startOutput(player, target, active, device, false);
        if (active.outputs.isEmpty()) throw new MusicProblem(MusicProblem.Code.OFFLINE);
    }
    public Map<UUID, MusicProblem.Code> outputFailures(UUID id) {
        var active = running.get(id);
        return active == null ? Map.of() : Map.copyOf(active.failed);
    }
    @Override
    public MusicState.Checkpoint checkpoint(UUID id) {
        var current = program(id).current();
        if (current == null) return null;
        var record = running.get(id);
        if (record != null && record.recordTrack != null) {
            var saved = store.snapshot().checkpoints().get(id);
            return saved != null && saved.entry().equals(current.id()) ? saved
                    : new MusicState.Checkpoint(current.id(),0,true,System.currentTimeMillis());
        }
        for (var device : registry.playbackTarget(id).members()) {
            var box = registry.block(device);
            if (box != null && backend.physicalPlayback(box.getBlock()) != null) {
                var saved = store.snapshot().checkpoints().get(id);
                return saved != null && saved.entry().equals(current.id()) ? saved
                        : new MusicState.Checkpoint(current.id(), 0, true, System.currentTimeMillis());
            }
        }
        var position = status(id);
        return new MusicState.Checkpoint(current.id(), position.positionMillis(), true, System.currentTimeMillis());
    }
    private void recordStarted(DeviceTarget t, Running r, QueueEntry entry) {
        if (running.get(t.id()) != r || r.recorded)
            return;
        r.recorded = true;
        MusicState.Checkpoint saved = store.snapshot().checkpoints().get(t.id());
        if (saved != null && saved.entry().equals(entry.id()) && saved.position() > 0) {
            for (MusicDevice d : t.members()) {
                Jukebox b = registry.block(d);
                if (b != null && backend.seekable(b.getBlock())) {
                    backend.seek(b.getBlock(), entry.trackId(), saved.position());
                    break;
                }
            }
        }
        if (entry.actor() == null)
            return;
        UUID recent = UUID.randomUUID();
        store.transact(e -> {
                 e.recents.put(recent,
                         new MusicState.Recent(
                                 recent, entry.actor(), entry.trackId(), t.id(), System.currentTimeMillis()));
                 List<MusicState.Recent> personal =
                         e.recents.values()
                                 .stream()
                                 .filter(v -> v.player().equals(entry.actor()))
                                 .sorted(Comparator.comparingLong(MusicState.Recent::at).reversed())
                                 .toList();
                 personal.stream().skip(500).forEach(v -> e.recents.remove(v.id()));
                 return null;
             }).exceptionally(error -> {
            plugin.getLogger().warning("Music history commit failed: " + error);
            return null;
        });
    }
    private void ended(Location location, MusicTrack track, boolean failed) {
        MusicDevice device = store.snapshot()
                                     .devices()
                                     .values()
                                     .stream()
                                     .filter(d -> {
                                         Location l = registry.location(d);
                                         return location.equals(l);
                                     })
                                     .findFirst()
                                     .orElse(null);
        if (device == null)
            return;
        UUID id = device.group() == null ? device.id() : device.group();
        Running r = running.get(id);
        if (r != null && r.recordTrack != null) {
            if (!r.recordTrack.id().equals(track.id()) || !r.outputs.remove(device.id())) return;
            if (!failed || device.id().equals(r.recordSource)) stop(registry.target(id));
            else r.failed.put(device.id(),MusicProblem.Code.UNAVAILABLE);
            return;
        }
        if (!Objects.equals(program(id).current() == null
                            ? null
                            : program(device.group() == null ? device.id() : device.group())
                                      .current()
                                      .trackId(),
                    track.id()))
            return;
        if (r == null || !r.outputs.remove(device.id()))
            return;
        if (failed) {
            r.failed.put(device.id(), MusicProblem.Code.UNAVAILABLE);
            return;
        }
        DeviceTarget t = registry.target(id);
        stop(t);
        PlaybackProgram before = program(id);
        long intent = intents.begin(id);
        PlaybackProgram next = before.advance(true, java.util.random.RandomGenerator.getDefault());
        main(store.transact(e -> {
            if (e.programs.getOrDefault(id, PlaybackProgram.empty()).version() != before.version())
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            e.programs.put(id, next);
            return null;
        }),
                () -> {
                    if (!closed && intents.current(id, intent) && next.current() != null)
                        start(null, registry.target(id), true);
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Music progression stopped: " + error);
                    return null;
                });
    }
    @Override
    public void stop(DeviceTarget t) {
        intents.cancel(t.id());
        stopOutput(t);
    }
    private void stopOutput(DeviceTarget t) {
        running.remove(t.id());
        for (MusicDevice d : t.members()) {
            Jukebox b = registry.block(d);
            if (b != null && settings.readExperienceMode(b) != JukeboxExperienceMode.RHYTHM)
                backend.stop(b.getBlock());
        }
    }
    public void unavailable(Location l, boolean destroyed) {
        for (MusicDevice d : store.snapshot().devices().values())
            if (l.equals(registry.location(d))) {
                UUID id = d.group() == null ? d.id() : d.group();
                Running r = running.get(id);
                if (destroyed && r != null && d.id().equals(r.recordSource)) {
                    stop(registry.target(id)); r = null;
                }
                if (r != null) {
                    r.outputs.remove(d.id());
                    r.failed.put(d.id(), MusicProblem.Code.OFFLINE);
                }
                backend.stop(l.getBlock());
                if (destroyed)
                    store.transact(e -> {
                        e.devices.remove(d.id());
                        e.programs.remove(d.id());
                        e.checkpoints.remove(d.id());
                        if (d.group() != null) {
                            MusicState.Group g = e.groups.get(d.group());
                            if (g != null) {
                                List<UUID> members = g.members()
                                                             .stream()
                                                             .filter(member -> !member.equals(d.id()))
                                                             .toList();
                                Map<UUID, MusicState.Recovery> recovery = new HashMap<>(g.recovery());
                                recovery.remove(d.id());
                                if (members.isEmpty()) {
                                    e.groups.remove(g.id());
                                    e.programs.remove(g.id());
                                    e.checkpoints.remove(g.id());
                                    e.bindings.values().removeIf(binding -> binding.target().equals(g.id()));
                                } else
                                    e.groups.put(g.id(),
                                            new MusicState.Group(g.id(), g.name(), members, recovery,
                                                    g.gain(), g.version() + 1));
                            }
                        }
                        e.bindings.values().removeIf(b -> b.target().equals(d.id()));
                        return null;
                    });
            }
    }
    @Override
    public void validateSound(DeviceTarget target) {
        checkOverlap(target, null);
    }
    @Override
    public void settingsChanged(MusicDevice device) {
        applySound(device);
        var active = device.group() == null ? null : running.get(device.group());
        if (active != null && active.recordTrack != null && active.recordTrack.target() instanceof TrackTarget.NativeRecord
                && active.recordGain != store.snapshot().groups().get(device.group()).gain())
            restartNativeRecord(null,registry.target(device.group()),active);
    }
    @Override
    public void joined(MusicDevice device) {
        Jukebox box = registry.block(device);
        if (box != null)
            loaded(box);
    }
    @Override
    public void departed(MusicDevice device) {
        suspendOutput(device);
    }
    public void loaded(Jukebox box) {
        String raw = box.getPersistentDataContainer().get(
                new NamespacedKey("mik", "music_device_id"), PersistentDataType.STRING);
        if (raw == null)
            return;
        MusicDevice d;
        try {
            d = store.snapshot().devices().get(UUID.fromString(raw));
        } catch (IllegalArgumentException invalid) {
            return;
        }
        if (d == null || d.group() == null || settings.readExperienceMode(box) == JukeboxExperienceMode.RHYTHM)
            return;
        Running r = running.get(d.group());
        if (r != null && r.recordTrack != null && !r.outputs.contains(d.id())) {
            if (r.recordTrack.target() instanceof TrackTarget.NativeRecord) {
                restartNativeRecord(null,registry.target(d.group()),r);
            } else startRecordOutput(null,registry.target(d.group()),r,d,PlaybackOrigin.AUTOMATIC);
            return;
        }
        if (box.hasRecord()) return;
        MusicTrack track = current(d.group());
        if (r != null && track != null && !r.outputs.contains(d.id()))
            startOutput(null, registry.target(d.group()), r, d, true);
    }
    private void checkpoint() {
        if (!store.ready())
            return;
        Map<UUID, MusicState.Checkpoint> captured = new HashMap<>();
        for (var active : running.entrySet()) {
            if (active.getValue().recordTrack != null) continue;
            QueueEntry current = program(active.getKey()).current();
            if (current == null)
                continue;
            JukeboxPlaybackSnapshot status = status(active.getKey());
            captured.put(active.getKey(),
                    new MusicState.Checkpoint(current.id(), status.positionMillis(), active.getValue().paused,
                            System.currentTimeMillis()));
        }
        if (!captured.isEmpty())
            store.transact(e -> {
                     captured.forEach((id, value) -> {
                         QueueEntry current = e.programs.getOrDefault(id, PlaybackProgram.empty()).current();
                         if (current != null && current.id().equals(value.entry()))
                             e.checkpoints.put(id, value);
                     });
                     return null;
                 }).exceptionally(error -> {
                plugin.getLogger().warning("Music checkpoint failed: " + error);
                return null;
            });
    }
    private void updateAudience() {
        if (++checkpointTicks >= 40) {
            checkpointTicks = 0;
            checkpoint();
        }
        Map<Location, Set<UUID>> next = new HashMap<>();
        for (var entry : running.entrySet()) {
            DeviceTarget target;
            try {
                target = registry.target(entry.getKey());
            } catch (MusicProblem missing) {
                continue;
            }
            if (target.members().size() < 2)
                continue;
            List<MusicDevice> outputs =
                    target.members().stream().filter(d -> entry.getValue().outputs.contains(d.id())).toList();
            outputs.forEach(d -> next.put(registry.location(d), new HashSet<>()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                MusicDevice nearest =
                        outputs.stream()
                                .filter(d -> d.world().equals(p.getWorld().getUID()) && d.volume() > 0)
                                .filter(d
                                        -> p.getLocation().distanceSquared(registry.location(d))
                                                <= d.range() * (double) d.range())
                                .min(Comparator.comparingDouble(
                                        d -> p.getLocation().distanceSquared(registry.location(d))))
                                .orElse(null);
                if (nearest != null)
                    next.get(registry.location(nearest)).add(p.getUniqueId());
            }
        }
        Map<Location, Set<UUID>> frozen = new HashMap<>();
        next.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        audience = Map.copyOf(frozen);
    }
    private void onMain(Runnable action) {
        if (closed)
            return;
        if (Bukkit.isPrimaryThread())
            action.run();
        else if (plugin.isEnabled())
            Bukkit.getScheduler().runTask(plugin, action);
    }
    private <T> CompletableFuture<T> main(CompletableFuture<T> future, Runnable action) {
        return tasks.after(future, action);
    }
    @Override
    public void close() {
        checkpoint();
        closed = true;
        if (audienceTask != null)
            audienceTask.cancel();
        for (UUID id : List.copyOf(running.keySet())) {
            try {
                stop(registry.target(id));
            } catch (MusicProblem ignored) {
            }
        }
        audience = Map.of();
        intents.clear();
    }
}
