package org.encinet.mik.feature.entertainment.music.application;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;

/** Owns atomic membership changes. A group has one program; members only own output settings. */
public final class MusicGroupService {
    public static final int MIN_MEMBERS = 2;
    public static final int MAX_MEMBERS = 16;
    public enum ContentPolicy { RESTORE_PREVIOUS, KEEP_GROUP }
    public interface Outputs {
        void stop(DeviceTarget target);
        void validateSound(DeviceTarget target);
        void settingsChanged(MusicDevice device);
        void joined(MusicDevice device);
        void departed(MusicDevice device);
        MusicState.Checkpoint checkpoint(UUID target);
    }
    private final MusicStore store;
    private final MusicDeviceRegistry registry;
    private final Outputs outputs;
    private final MusicMainThread tasks;
    public MusicGroupService(MusicStore store, MusicDeviceRegistry registry, Outputs outputs, MusicMainThread tasks) {
        this.store = store;
        this.registry = registry;
        this.outputs = outputs;
        this.tasks = tasks;
    }
    private PlaybackProgram program(UUID id) {
        return store.snapshot().programs().getOrDefault(id, PlaybackProgram.empty());
    }
    private MusicState.Recovery recovery(UUID id) {
        return new MusicState.Recovery(program(id), outputs.checkpoint(id));
    }
    private MusicState.Group group(UUID id, long version) {
        var group = store.snapshot().groups().get(id);
        if (group == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        if (group.version() != version) throw new MusicProblem(MusicProblem.Code.CONFLICT);
        return group;
    }
    private List<DeviceTarget> standalone(Player player, Set<UUID> selection) {
        if (selection.isEmpty() || selection.size() > MAX_MEMBERS) throw new MusicProblem(MusicProblem.Code.INVALID);
        var targets = selection.stream().map(registry::playbackTarget)
                .sorted(Comparator.comparing(DeviceTarget::name).thenComparing(DeviceTarget::id)).toList();
        for (var target : targets) {
            if (store.snapshot().groups().containsKey(target.id()) || target.members().size() != 1
                    || target.members().getFirst().group() != null) throw new MusicProblem(MusicProblem.Code.INVALID);
            registry.authorize(player, target);
        }
        return targets;
    }
    private static void restore(MusicState.Editor editor, UUID member, MusicState.Recovery content) {
        editor.programs.put(member, content.program());
        if (content.checkpoint() == null) editor.checkpoints.remove(member);
        else editor.checkpoints.put(member, content.checkpoint());
    }
    private static void forgetGroup(MusicState.Editor editor, UUID id) {
        editor.groups.remove(id);
        editor.programs.remove(id);
        editor.checkpoints.remove(id);
        editor.bindings.values().removeIf(binding -> binding.target().equals(id));
    }
    public CompletableFuture<Void> rename(Player player, UUID id, String name) {
        var target = registry.target(id);
        registry.authorize(player, target);
        String clean = Playlist.text(name, 64);
        return store.transact(editor -> {
            var group = editor.groups.get(id);
            if (group != null) editor.groups.put(id, new MusicState.Group(id, clean, group.members(),
                    group.recovery(), group.gain(), group.version() + 1));
            else {
                var device = editor.devices.get(id);
                if (device == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
                editor.devices.put(id, device.settings(clean, device.volume(), device.range()));
            }
            return null;
        });
    }
    public CompletableFuture<UUID> create(Player player, Set<UUID> selection, UUID source, String name) {
        var targets = standalone(player, Set.copyOf(selection));
        if (targets.size() < MIN_MEMBERS || source == null || !selection.contains(source)) throw new MusicProblem(MusicProblem.Code.INVALID);
        String title = Playlist.text(name, 64);
        UUID owner = player.getUniqueId();
        UUID id = UUID.randomUUID();
        Map<UUID, MusicState.Recovery> recovery = new HashMap<>();
        targets.forEach(target -> recovery.put(target.id(), recovery(target.id())));
        var saved = store.transact(editor -> {
            for (var target : targets) {
                var device = editor.devices.get(target.id());
                if (device == null || device.group() != null
                        || editor.programs.getOrDefault(device.id(), PlaybackProgram.empty()).version()
                                != recovery.get(device.id()).program().version()) throw new MusicProblem(MusicProblem.Code.CONFLICT);
                editor.devices.put(device.id(), device.grouped(id));
            }
            editor.groups.put(id, new MusicState.Group(id, title, targets.stream().map(DeviceTarget::id).toList(), recovery, 100, 0));
            editor.bindings.put(MusicState.key(owner, id), new MusicState.Binding(owner, id, ""));
            restore(editor, id, recovery.get(source));
            return id;
        });
        return tasks.after(saved, () -> targets.forEach(outputs::stop));
    }
    public CompletableFuture<Void> addMembers(Player player, UUID id, long version, Set<UUID> selection) {
        var group = group(id, version);
        registry.authorize(player, registry.playbackTarget(id));
        var additions = standalone(player, Set.copyOf(selection));
        if (group.members().size() + additions.size() > MAX_MEMBERS) throw new MusicProblem(MusicProblem.Code.LIMIT);
        Map<UUID, MusicState.Recovery> saved = new HashMap<>();
        additions.forEach(target -> saved.put(target.id(), recovery(target.id())));
        return tasks.after(store.transact(editor -> {
            if (!group.equals(editor.groups.get(id))) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            var members = new ArrayList<>(group.members());
            var recovery = new HashMap<>(group.recovery());
            for (var target : additions) {
                var device = editor.devices.get(target.id());
                if (device == null || device.group() != null
                        || editor.programs.getOrDefault(device.id(), PlaybackProgram.empty()).version()
                                != saved.get(device.id()).program().version()) throw new MusicProblem(MusicProblem.Code.CONFLICT);
                members.add(device.id());
                recovery.put(device.id(), saved.get(device.id()));
                editor.devices.put(device.id(), device.grouped(id));
            }
            editor.groups.put(id, new MusicState.Group(id, group.name(), members, recovery, group.gain(), version + 1));
            return null;
        }), () -> {
            for (var target : additions) {
                outputs.stop(target);
                outputs.joined(store.snapshot().devices().get(target.id()));
            }
        });
    }
    public CompletableFuture<Void> removeMember(Player player, UUID id, long version, UUID member, ContentPolicy policy) {
        var group = group(id, version);
        var target = registry.playbackTarget(id);
        registry.authorize(player, target);
        if (!group.members().contains(member)) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        var previous = store.snapshot().devices().get(member);
        var shared = recovery(id);
        return tasks.after(store.transact(editor -> {
            if (!group.equals(editor.groups.get(id)) || programVersion(editor, id) != shared.program().version())
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            var recovery = new HashMap<>(group.recovery());
            var before = recovery.remove(member);
            editor.devices.put(member, editor.devices.get(member).grouped(null));
            restore(editor, member, policy == ContentPolicy.KEEP_GROUP ? shared : Objects.requireNonNull(before));
            var members = group.members().stream().filter(value -> !value.equals(member)).toList();
            if (members.isEmpty()) forgetGroup(editor, id);
            else editor.groups.put(id, new MusicState.Group(id, group.name(), members, recovery, group.gain(), version + 1));
            return null;
        }), () -> {
            outputs.departed(previous);
            if (!store.snapshot().groups().containsKey(id)) outputs.stop(target);
            outputs.settingsChanged(store.snapshot().devices().get(member));
        });
    }
    public CompletableFuture<Void> dissolve(Player player, UUID id, long version, ContentPolicy policy) {
        var group = group(id, version);
        var target = registry.playbackTarget(id);
        registry.authorize(player, target);
        var shared = recovery(id);
        return tasks.after(store.transact(editor -> {
            if (!group.equals(editor.groups.get(id)) || programVersion(editor, id) != shared.program().version())
                throw new MusicProblem(MusicProblem.Code.CONFLICT);
            for (UUID member : group.members()) {
                editor.devices.put(member, editor.devices.get(member).grouped(null));
                restore(editor, member, policy == ContentPolicy.KEEP_GROUP ? shared : group.recovery().get(member));
            }
            forgetGroup(editor, id);
            return null;
        }), () -> {
            outputs.stop(target);
            group.members().forEach(member -> outputs.settingsChanged(store.snapshot().devices().get(member)));
        });
    }
    private static long programVersion(MusicState.Editor editor, UUID id) {
        return editor.programs.getOrDefault(id, PlaybackProgram.empty()).version();
    }
    public CompletableFuture<Void> groupGain(Player player, UUID id, int gain) {
        if (gain < 0 || gain > 100) throw new MusicProblem(MusicProblem.Code.INVALID);
        var target = registry.playbackTarget(id);
        registry.authorize(player, target);
        var group = store.snapshot().groups().get(id);
        if (group == null) throw new MusicProblem(MusicProblem.Code.INVALID);
        outputs.validateSound(target);
        return tasks.after(store.transact(editor -> {
            if (!group.equals(editor.groups.get(id))) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            editor.groups.put(id, new MusicState.Group(id, group.name(), group.members(), group.recovery(), gain, group.version() + 1));
            return null;
        }), () -> registry.target(id).members().forEach(outputs::settingsChanged));
    }
}
