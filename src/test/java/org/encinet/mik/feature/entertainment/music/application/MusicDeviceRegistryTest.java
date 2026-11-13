package org.encinet.mik.feature.entertainment.music.application;

import static org.junit.jupiter.api.Assertions.*;

import static org.encinet.mik.feature.entertainment.music.application.MusicDeviceTestSupport.proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MusicDeviceRegistryTest {
    @TempDir Path directory;
    MusicStore store;
    MusicDeviceRegistry registry;
    World world;
    Block block;
    Jukebox box;
    Player player;
    final UUID worldId = UUID.randomUUID(), actor = UUID.randomUUID(), deviceId = UUID.randomUUID();
    final Map<NamespacedKey, Object> data = new HashMap<>();
    boolean loaded = true, allowed = true;
    double playerX;
    int blockReads;
    @BeforeEach
    void open() {
        PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
            case "get" -> data.get(args[0]);
            case "set" -> {
                data.put((NamespacedKey) args[0], args[2]);
                yield null;
            }
            case "remove" -> {
                data.remove(args[0]);
                yield null;
            }
            default -> null;
        });
        world = proxy(World.class, (method, _) -> switch (method) {
            case "getUID" -> worldId;
            case "getName" -> "test";
            case "isChunkLoaded" -> loaded;
            case "getBlockAt" -> {
                blockReads++;
                yield block;
            }
            default -> null;
        });
        box = proxy(Jukebox.class, (method, _) -> switch (method) {
            case "getPersistentDataContainer" -> pdc;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "getBlock" -> block;
            case "update" -> true;
            default -> null;
        });
        block = proxy(Block.class, (method, _) -> switch (method) {
            case "getType" -> Material.JUKEBOX;
            case "getState" -> box;
            case "getWorld" -> world;
            case "getY" -> 64;
            case "getLocation" -> new Location(world, 0, 64, 0);
            default -> null;
        });
        player = proxy(Player.class, (method, _) -> switch (method) {
            case "getUniqueId" -> actor;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, playerX, 64, 0);
            default -> null;
        });
        store = new MusicStore();
        store.open(directory.resolve("music.sqlite")).join();
        registry = new MusicDeviceRegistry(store, new JukeboxSettingsStore(),
                (_, _) -> allowed, id -> worldId.equals(id) ? world : null);
        data.put(new NamespacedKey("mik", "music_device_id"), deviceId.toString());
        store.transact(e -> {
                 e.devices.put(deviceId, device(deviceId, 0));
                 return null;
             }).join();
    }
    private MusicDevice device(UUID id, int x) {
        return new MusicDevice(id, worldId, "test", x, 64, 0, "device", 100, 64, null, 0);
    }
    @AfterEach
    void close() {
        store.close();
    }
    @Test void recordingEligibilityAndSubmissionRecheckTheSameSiteIdentityAndPermission() {
        assertTrue(registry.mayBind(player, deviceId));
        playerX = 100;
        assertFalse(registry.mayBind(player, deviceId));
        assertThrows(MusicProblem.class, () -> registry.bind(player, deviceId, ""));
        playerX = 0;
        allowed = false;
        assertFalse(registry.mayBind(player, deviceId));
        assertThrows(MusicProblem.class, () -> registry.bind(player, deviceId, ""));
        allowed = true;
        data.put(new NamespacedKey("mik", "music_device_id"), UUID.randomUUID().toString());
        assertFalse(registry.mayBind(player, deviceId));
        assertThrows(MusicProblem.class, () -> registry.bind(player, deviceId, ""));
        data.put(new NamespacedKey("mik", "music_device_id"), deviceId.toString());
        loaded = false;
        int reads = blockReads;
        assertFalse(registry.mayBind(player, deviceId));
        assertThrows(MusicProblem.class, () -> registry.bind(player, deviceId, ""));
        assertEquals(reads, blockReads, "Recording cannot force an unloaded chunk to load");
        assertFalse(registry.bound(actor, deviceId));
        loaded = true;
        assertTrue(registry.mayBind(player, deviceId));
        registry.bind(player, deviceId, "").join();
        assertTrue(registry.bound(actor, deviceId));
    }
    @Test
    void remoteUseRequiresRecordAndRechecksPermissionEveryTime() {
        assertTrue(registry.mayControl(player, deviceId));
        playerX = 100;
        assertFalse(registry.mayControl(player, deviceId));
        playerX = 0;
        registry.bind(player, deviceId, "").join();
        playerX = 100;
        assertTrue(registry.mayControl(player, deviceId));
        allowed = false;
        assertFalse(registry.mayControl(player, deviceId));
    }
    @Test
    void nearbyWayfindingDoesNotRequireOrCreateARemoteRecordAndRechecksTheBlock() {
        var device = device(deviceId, 0);
        playerX = 50;
        assertFalse(registry.mayControl(player, deviceId));
        assertTrue(registry.mayLocate(player, device));
        assertFalse(registry.bound(actor, deviceId));
        assertFalse(registry.mayControl(player, deviceId));
        allowed = false;
        assertFalse(registry.mayLocate(player, device));
        allowed = true;
        data.put(new NamespacedKey("mik", "music_device_id"), UUID.randomUUID().toString());
        assertFalse(registry.mayLocate(player, device));
        data.put(new NamespacedKey("mik", "music_device_id"), deviceId.toString());
        loaded = false;
        int reads = blockReads;
        assertFalse(registry.mayLocate(player, device));
        assertEquals(reads, blockReads, "Wayfinding cannot read or load an unloaded chunk");
        loaded = true;
        assertTrue(registry.mayLocate(player, device));
    }
    @Test void standingAtOneMemberAuthorizesTheWholeGroupButStillChecksEveryMembersPlot() {
        var group = UUID.randomUUID(); var remote = UUID.randomUUID();
        store.transact(editor -> {
            editor.devices.put(deviceId,device(deviceId,0).grouped(group));
            editor.devices.put(remote,device(remote,40).grouped(group));
            editor.groups.put(group,new MusicState.Group(group,"Room",java.util.List.of(deviceId,remote),Map.of(),100,0));
            return null;
        }).join();
        var checked = new java.util.ArrayList<Integer>();
        var onsiteRegistry = new MusicDeviceRegistry(store,new JukeboxSettingsStore(),(_,at) -> {
            checked.add(at.getBlockX()); return at.getBlockX() != 40 || allowed;
        },_ -> world);
        assertTrue(onsiteRegistry.mayControl(player,group));
        assertTrue(checked.contains(40)); assertFalse(onsiteRegistry.bound(actor,group));
        allowed = false; assertFalse(onsiteRegistry.mayControl(player,group));
        allowed = true; playerX = 100;
        assertFalse(onsiteRegistry.mayControl(player,group));
    }
    @Test
    void offlineQueriesNeverReadOrLoadBlock() {
        loaded = false;
        blockReads = 0;
        assertFalse(registry.online(device(deviceId, 0)));
        assertFalse(registry.mayControl(player, deviceId));
        assertEquals(0, blockReads);
    }

    @Test
    void groupingInvalidatesAnOldIndividualPlaybackTargetWithoutRemovingMemberSettingsAccess() {
        UUID group = UUID.randomUUID();
        store.transact(editor -> {
            editor.devices.put(deviceId, editor.devices.get(deviceId).grouped(group));
            editor.groups.put(group, new MusicState.Group(group, "group", java.util.List.of(deviceId), Map.of(), 100, 0));
            return null;
        }).join();
        assertEquals(group, registry.target(deviceId).members().getFirst().group());
        var failure = assertThrows(MusicProblem.class, () -> registry.playbackTarget(deviceId));
        assertEquals(MusicProblem.Code.CONFLICT, failure.code());
        assertEquals(group, registry.playbackTarget(group).id());
    }
    @Test
    void unbindingRemovesOnlyTheActorsRecord() {
        UUID other = UUID.randomUUID();
        registry.bind(player, deviceId, "").join();
        store.transact(e -> {
                 e.bindings.put(
                         MusicState.key(other, deviceId), new MusicState.Binding(other, deviceId, "other"));
                 return null;
             }).join();
        registry.unbind(actor, deviceId).join();
        assertFalse(registry.bound(actor, deviceId));
        assertTrue(registry.bound(other, deviceId));
    }
    @Test void personalDeviceAndGroupNamesMatchTheOutputPickerWithoutChangingPublicNames() {
        registry.bind(player, deviceId, "My speaker").join();
        assertEquals("My speaker", registry.name(player, deviceId));
        assertEquals("device", registry.target(deviceId).name());
        UUID group = UUID.randomUUID();
        store.transact(editor -> {
            editor.devices.put(deviceId, editor.devices.get(deviceId).grouped(group));
            editor.groups.put(group, new MusicState.Group(group, "Public group", java.util.List.of(deviceId), Map.of(), 100, 0));
            return null;
        }).join();
        registry.bind(player, group, "My room").join();
        assertEquals("My speaker", registry.name(player, deviceId));
        assertEquals("My room", registry.name(player, group));
        assertEquals("My room", registry.targets(player).getFirst().name());
        assertEquals("Public group", registry.target(group).name());
    }
    @Test void aGroupRecordAllowsMemberSoundControlWithoutCreatingAnIndependentPlaybackTarget() {
        UUID group = UUID.randomUUID();
        store.transact(editor -> {
            editor.devices.put(deviceId, editor.devices.get(deviceId).grouped(group));
            editor.groups.put(group, new MusicState.Group(group, "Room", java.util.List.of(deviceId), Map.of(), 100, 0));
            return null;
        }).join();
        registry.bind(player, group, "My room").join();
        playerX = 100;
        assertFalse(registry.bound(actor, deviceId));
        assertTrue(registry.mayControl(player, group));
        assertTrue(registry.mayControl(player, deviceId));
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> registry.playbackTarget(deviceId)).code());
        allowed = false;
        assertEquals(MusicProblem.Code.FORBIDDEN, registry.controlFailure(player, group));
        assertFalse(registry.mayControl(player, deviceId));
        allowed = true;
        registry.unbind(actor, group).join();
        assertFalse(registry.mayControl(player, group));
        assertFalse(registry.mayControl(player, deviceId));
    }
    @Test
    void copiedPersistentIdDoesNotInheritTheOriginalDevice() {
        store.transact(e -> {
                 e.devices.put(deviceId, device(deviceId, 30));
                 return null;
             }).join();
        MusicDevice discovered = registry.discover(box).join();
        assertNotEquals(deviceId, discovered.id());
        assertEquals(30, store.snapshot().devices().get(deviceId).x());
        assertEquals(0, discovered.x());
    }
    @Test
    void changedBlockIdentityInvalidatesOldRemoteRecord() {
        registry.bind(player, deviceId, "").join();
        data.put(new NamespacedKey("mik", "music_device_id"), UUID.randomUUID().toString());
        playerX = 100;
        assertFalse(registry.mayControl(player, deviceId));
    }
}
