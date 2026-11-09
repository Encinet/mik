package org.encinet.mik.feature.entertainment.music.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.feature.entertainment.music.application.MusicDeviceTestSupport.proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.encinet.mik.feature.entertainment.music.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MusicGroupServiceTest {
    @TempDir Path directory;
    MusicStore store;
    MusicMainThread main;
    MusicDeviceRegistry registry;
    MusicGroupService groups;
    Player player;
    World world;
    final UUID worldId = UUID.randomUUID(), actor = UUID.randomUUID();
    final List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    final Map<UUID, PlaybackProgram> original = new HashMap<>();
    final Map<UUID, MusicState.Checkpoint> positions = new HashMap<>();
    final Set<Integer> denied = new HashSet<>();
    final List<UUID> stopped = new CopyOnWriteArrayList<>(), joined = new CopyOnWriteArrayList<>(), departed = new CopyOnWriteArrayList<>();
    @BeforeEach void open() {
        var blocks = new HashMap<Integer, Block>();
        world = proxy(World.class, (method, args) -> switch (method) {
            case "getUID" -> worldId;
            case "getName" -> "test";
            case "isChunkLoaded" -> true;
            case "getBlockAt" -> blocks.get(args[0] instanceof Location location ? location.getBlockX() : ((Number) args[0]).intValue());
            default -> null;
        });
        player = proxy(Player.class, (method, _) -> switch (method) {
            case "getUniqueId" -> actor;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 64, 0);
            default -> null;
        });
        for (int index = 0; index < ids.size(); index++) {
            int x = index;
            var id = ids.get(index);
            var pdc = proxy(PersistentDataContainer.class, (method, args) -> method.equals("get")
                    && args[0].equals(new NamespacedKey("mik", "music_device_id")) ? id.toString() : null);
            var box = proxy(Jukebox.class, (method, _) -> switch (method) {
                case "getPersistentDataContainer" -> pdc;
                case "getLocation" -> new Location(world, x, 64, 0);
                case "getBlock" -> blocks.get(x);
                default -> null;
            });
            blocks.put(x, proxy(Block.class, (method, _) -> switch (method) {
                case "getType" -> Material.JUKEBOX;
                case "getState" -> box;
                case "getLocation" -> new Location(world, x, 64, 0);
                default -> null;
            }));
            var program = PlaybackProgram.empty().submit(List.of(QueueEntry.create("song-" + index, actor)), PlaybackProgram.Submission.INSERT);
            original.put(id, program);
            positions.put(id, new MusicState.Checkpoint(program.current().id(), (index + 1) * 12000L, false, 1));
        }
        store = new MusicStore(); store.open(directory.resolve("music.sqlite")).join();
        store.transact(editor -> {
            for (int index = 0; index < ids.size(); index++) {
                UUID id = ids.get(index);
                editor.devices.put(id, new MusicDevice(id, worldId, "test", index, 64, 0, "Device " + index, 50 + index * 20, 64, null, 0));
                editor.programs.put(id, original.get(id));
                editor.checkpoints.put(id, positions.get(id));
            }
            return null;
        }).join();
        registry = new MusicDeviceRegistry(store, new JukeboxSettingsStore(), (_, location) -> !denied.contains(location.getBlockX()), _ -> world);
        main = new MusicMainThread(Runnable::run);
        groups = new MusicGroupService(store, registry, new MusicGroupService.Outputs() {
            public void stop(DeviceTarget target) { stopped.add(target.id()); }
            public void validateSound(DeviceTarget target) { }
            public void settingsChanged(MusicDevice device) { }
            public void joined(MusicDevice device) { joined.add(device.id()); }
            public void departed(MusicDevice device) { departed.add(device.id()); }
            public MusicState.Checkpoint checkpoint(UUID target) { return positions.getOrDefault(target, store.snapshot().checkpoints().get(target)); }
        }, main);
    }
    @AfterEach void close() { main.close(); store.close(); }
    private UUID create() { return groups.create(player, Set.of(ids.get(0), ids.get(1)), ids.get(1), "Room").join(); }

    @Test void creationRecordsTheGroupForItsCreatorAndDissolutionRemovesThatRecord() {
        UUID id = create();
        assertTrue(registry.bound(actor,id));
        var remote = proxy(Player.class,(method,_) -> switch (method) {
            case "getUniqueId" -> actor;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world,1000,64,1000);
            default -> null;
        });
        assertEquals(List.of(id),registry.targets(remote).stream().map(DeviceTarget::id).toList());
        assertTrue(registry.mayControl(remote,id));
        groups.dissolve(remote,id,0,MusicGroupService.ContentPolicy.KEEP_GROUP).join();
        assertFalse(registry.bound(actor,id));
        assertTrue(registry.targets(remote).isEmpty());
    }

    @Test void aGroupHasOneSourceProgramAndProgressWhileEveryOriginalIsRecoverable() {
        UUID group = create();
        var state = store.snapshot();
        assertEquals(original.get(ids.get(1)), state.programs().get(group));
        assertEquals(24000, state.checkpoints().get(group).position());
        for (UUID id : ids.subList(0, 2)) {
            assertEquals(group, state.devices().get(id).group());
            assertEquals(original.get(id), state.groups().get(group).recovery().get(id).program());
            assertEquals(positions.get(id), state.groups().get(group).recovery().get(id).checkpoint());
            assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> registry.playbackTarget(id)).code());
        }
        assertEquals(Set.of(ids.get(0), ids.get(1)), Set.copyOf(stopped));
        assertTrue(joined.isEmpty(), "creating alone must not start output");
    }
    @Test void groupProgramsProgressAndRecoverySurviveReopeningTheStore() {
        UUID group = create();
        var expected = store.snapshot().groups().get(group);
        registry.bind(player, group, "Saved room").join();
        store.close();
        store = new MusicStore(); store.open(directory.resolve("music.sqlite")).join();
        assertEquals(expected, store.snapshot().groups().get(group));
        assertEquals(original.get(ids.get(1)), store.snapshot().programs().get(group));
        assertEquals(24000, store.snapshot().checkpoints().get(group).position());
        assertEquals("Saved room", store.snapshot().bindings().get(MusicState.key(actor, group)).alias());
    }
    @Test void addingMembersKeepsTheSharedQueueAndPositionAndOnlyAttachesTheNewOutput() {
        UUID group = create();
        var before = store.snapshot();
        groups.addMembers(player, group, 0, Set.of(ids.get(2))).join();
        var after = store.snapshot();
        assertEquals(before.programs().get(group), after.programs().get(group));
        assertEquals(before.checkpoints().get(group), after.checkpoints().get(group));
        assertEquals(List.of(ids.get(2)), joined);
        assertEquals(group, after.devices().get(ids.get(2)).group());
        assertEquals(original.get(ids.get(2)), after.groups().get(group).recovery().get(ids.get(2)).program());
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class,
                () -> groups.removeMember(player, group, 0, ids.get(2), MusicGroupService.ContentPolicy.RESTORE_PREVIOUS)).code());
        assertEquals(3, store.snapshot().groups().get(group).members().size());
    }
    @Test void removingOneMemberPreservesTheGroupIdentityAndTheLastRemovalCleansItsRecord() {
        UUID group = create();
        var shared = store.snapshot().programs().get(group);
        registry.bind(player, group, "My room").join();
        groups.removeMember(player, group, 0, ids.get(0), MusicGroupService.ContentPolicy.KEEP_GROUP).join();
        assertEquals(shared, store.snapshot().programs().get(ids.get(0)));
        assertEquals(List.of(ids.get(1)), store.snapshot().groups().get(group).members());
        assertEquals(group, registry.playbackTarget(group).id(), "one-member groups remain groups");
        assertEquals(List.of(ids.get(0)), departed);
        groups.removeMember(player, group, 1, ids.get(1), MusicGroupService.ContentPolicy.RESTORE_PREVIOUS).join();
        assertFalse(store.snapshot().groups().containsKey(group));
        assertFalse(store.snapshot().programs().containsKey(group));
        assertFalse(registry.bound(actor, group));
        assertEquals(original.get(ids.get(1)), store.snapshot().programs().get(ids.get(1)));
        assertEquals(positions.get(ids.get(1)), store.snapshot().checkpoints().get(ids.get(1)));
    }
    @ParameterizedTest @EnumSource(MusicGroupService.ContentPolicy.class)
    void dissolutionAppliesTheChosenContentPolicyToEveryMember(MusicGroupService.ContentPolicy policy) {
        UUID group = create();
        var shared = original.get(ids.get(2));
        store.transact(editor -> { editor.programs.put(group, shared); editor.checkpoints.put(group, positions.get(ids.get(2))); return null; }).join();
        groups.dissolve(player, group, 0, policy).join();
        for (UUID member : ids.subList(0, 2)) {
            assertNull(store.snapshot().devices().get(member).group());
            assertEquals(policy == MusicGroupService.ContentPolicy.KEEP_GROUP ? shared : original.get(member), store.snapshot().programs().get(member));
            assertEquals(policy == MusicGroupService.ContentPolicy.KEEP_GROUP ? positions.get(ids.get(2)) : positions.get(member), store.snapshot().checkpoints().get(member));
        }
        assertFalse(store.snapshot().groups().containsKey(group));
        assertTrue(stopped.contains(group));
        assertTrue(joined.isEmpty());
    }
    @Test void aPermissionFailureCannotPartiallyRegroupDevices() {
        denied.add(1);
        assertEquals(MusicProblem.Code.FORBIDDEN, assertThrows(MusicProblem.class, this::create).code());
        assertTrue(store.snapshot().groups().isEmpty());
        ids.forEach(id -> assertNull(store.snapshot().devices().get(id).group()));
        assertTrue(stopped.isEmpty());
    }
    @Test void aSourceChangedBeforeCommitLeavesTheWholeTopologyUntouched() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var blocked = store.transact(_ -> {
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("writer timeout"); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            return null;
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        var changed = store.transact(editor -> { editor.programs.put(ids.get(1), original.get(ids.get(1)).clearPending()); return null; });
        var creating = groups.create(player, Set.of(ids.get(0), ids.get(1)), ids.get(1), "Room");
        release.countDown(); blocked.join(); changed.join();
        var error = assertThrows(CompletionException.class, creating::join);
        assertEquals(MusicProblem.Code.CONFLICT, ((MusicProblem) error.getCause()).code());
        assertTrue(store.snapshot().groups().isEmpty());
        ids.forEach(id -> assertNull(store.snapshot().devices().get(id).group()));
        assertTrue(stopped.isEmpty());
    }
    @Test void groupGainNeverOverwritesTheIndividualOutputVolumes() {
        UUID group = create();
        groups.groupGain(player, group, 40).join();
        assertEquals(40, store.snapshot().groups().get(group).gain());
        assertEquals(50, store.snapshot().devices().get(ids.get(0)).volume());
        assertEquals(70, store.snapshot().devices().get(ids.get(1)).volume());
    }
}
