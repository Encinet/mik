package org.encinet.mik.feature.entertainment.music.application;

import static org.encinet.mik.feature.entertainment.music.application.MusicDeviceTestSupport.proxy;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MusicDeviceTravelTest {
    @TempDir Path directory;
    final UUID actor = UUID.randomUUID(), device = UUID.randomUUID(), worldId = UUID.randomUUID();
    final NamespacedKey identity = new NamespacedKey("mik","music_device_id");
    final Map<NamespacedKey,Object> data = new HashMap<>();
    final Map<String,Material> blocks = new HashMap<>();
    final List<Location> arrivals = new ArrayList<>();
    final List<String> loadedChunks = new ArrayList<>();
    final CompletableFuture<Chunk> loading = new CompletableFuture<>();
    MusicStore store;
    MusicDeviceRegistry registry;
    MusicDeviceTravel travel;
    MusicMainThread main;
    World world;
    Player player;
    boolean allowed = true, loaded, present = true, teleportAllowed = true, safeFloor = true, insideBorder = true;
    int reads;
    double floorTop = 1;
    double playerWidth = .6;

    @BeforeEach void open() {
        data.put(identity,device.toString());
        var pdc = proxy(PersistentDataContainer.class,(method,args) -> method.equals("get") ? data.get(args[0]) : null);
        var box = proxy(Jukebox.class,(method,_) -> method.equals("getPersistentDataContainer") ? pdc : null);
        var border = proxy(WorldBorder.class,(method,_) -> method.equals("isInside") ? insideBorder : null);
        world = proxy(World.class,(method,args) -> switch (method) {
            case "getUID" -> worldId;
            case "getName" -> "test";
            case "isChunkLoaded" -> loaded;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getWorldBorder" -> border;
            case "getChunkAtAsync" -> { loadedChunks.add(args[0]+","+args[1]); assertEquals(false,args[2]); yield loading; }
            case "getBlockAt" -> {
                assertTrue(loaded,"must not inspect an unloaded block"); reads++;
                int x = args[0] instanceof Location at ? at.getBlockX() : (int)args[0];
                int y = args[0] instanceof Location at ? at.getBlockY() : (int)args[1];
                int z = args[0] instanceof Location at ? at.getBlockZ() : (int)args[2];
                Material material = blocks.getOrDefault(x+","+y+","+z,
                        x == 0 && y == 64 && z == 0 && present ? Material.JUKEBOX : y == 63 && safeFloor ? Material.STONE : Material.AIR);
                var shapes = material == Material.AIR || material == Material.WATER || material == Material.LAVA || material == Material.FIRE
                        ? List.<BoundingBox>of() : List.of(new BoundingBox(0,0,0,1,y == 63 ? floorTop : 1,1));
                VoxelShape shape = proxy(VoxelShape.class,(name,values) -> switch (name) {
                    case "getBoundingBoxes" -> shapes;
                    case "overlaps" -> shapes.stream().anyMatch(part -> part.overlaps((BoundingBox)values[0]));
                    default -> null;
                });
                yield proxy(Block.class,(name,_) -> switch (name) {
                    case "getType" -> material;
                    case "getState" -> material == Material.JUKEBOX ? box : null;
                    case "isLiquid" -> material == Material.WATER || material == Material.LAVA;
                    case "getCollisionShape" -> shape;
                    default -> null;
                });
            }
            default -> null;
        });
        player = proxy(Player.class,(method,args) -> switch (method) {
            case "getUniqueId" -> actor;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world,100,64,100);
            case "getBoundingBox" -> new BoundingBox(100-playerWidth/2,64,100-playerWidth/2,100+playerWidth/2,65.8,100+playerWidth/2);
            case "getEyeHeight" -> 1.62;
            case "isOnline" -> true;
            case "teleportAsync" -> { arrivals.add(((Location)args[0]).clone()); yield CompletableFuture.completedFuture(teleportAllowed); }
            default -> null;
        });
        store = new MusicStore();store.open(directory.resolve("music.sqlite")).join();
        store.transact(editor -> {
            editor.devices.put(device,new MusicDevice(device,worldId,"test",0,64,0,"Room",100,64,null,0));
            editor.bindings.put(MusicState.key(actor,device),new MusicState.Binding(actor,device,""));return null;
        }).join();
        registry = new MusicDeviceRegistry(store,new JukeboxSettingsStore(),(_,_) -> allowed,id -> worldId.equals(id) ? world : null);
        main = new MusicMainThread(Runnable::run);
        travel = new MusicDeviceTravel(registry,main);
    }
    @AfterEach void close() { travel.close();main.close();store.close(); }
    private void load() { loaded = true; loading.complete(proxy(Chunk.class,(_,_) -> null)); }
    private void error(CompletableFuture<?> future, MusicProblem.Code expected) {
        assertEquals(expected,MusicProblem.code(assertThrows(Exception.class,future::join)));
        assertTrue(arrivals.isEmpty());assertFalse(travel.pending(actor));
    }

    @Test void recordedOfflineDeviceLoadsOnlyOnRequestAndArrivesBesideItFacingItsCenter() {
        assertNull(travel.failure(player,device));assertEquals(0,reads);assertTrue(loadedChunks.isEmpty());
        var request = travel.visit(player,device);
        assertFalse(request.isDone());assertTrue(travel.pending(actor));assertEquals(0,reads);
        assertEquals(4,loadedChunks.size());
        load();request.join();assertFalse(travel.pending(actor));assertEquals(1,arrivals.size());
        Location at = arrivals.getFirst();assertEquals(64,at.getY());
        assertFalse(at.getBlockX() == 0 && at.getBlockZ() == 0);
        var toward = new Location(world,.5,64.5,.5).toVector().subtract(at.clone().add(0,1.62,0).toVector()).normalize();
        assertEquals(1,toward.dot(at.getDirection()),1e-6);
    }
    @Test void unauthorizedVisitsCannotLoadChunks() {
        allowed = false;assertEquals(MusicProblem.Code.FORBIDDEN,travel.failure(player,device));
        error(travel.visit(player,device),MusicProblem.Code.FORBIDDEN);assertTrue(loadedChunks.isEmpty());assertEquals(0,reads);
    }
    @Test void permissionAndPersonalRecordAreRecheckedAfterLoading() {
        var request = travel.visit(player,device);allowed = false;load();error(request,MusicProblem.Code.FORBIDDEN);
        allowed = true;registry.unbind(actor,device).join();error(travel.visit(player,device),MusicProblem.Code.FORBIDDEN);
    }
    @Test void aRemovedOrReplacedJukeboxCannotReceiveAStaleVisit() {
        var request = travel.visit(player,device);data.put(identity,UUID.randomUUID().toString());load();
        error(request,MusicProblem.Code.NOT_FOUND);
        present = false;error(travel.visit(player,device),MusicProblem.Code.NOT_FOUND);
    }
    @Test void cancellingOrClosingWhileLoadingPreventsALateTeleport() {
        var first = travel.visit(player,device);travel.cancel(actor);
        assertThrows(Exception.class,first::join);assertFalse(travel.pending(actor));
        var second = travel.visit(player,device);travel.close();load();
        assertThrows(Exception.class,second::join);assertTrue(arrivals.isEmpty());assertFalse(travel.pending(actor));
    }
    @Test void onePendingVisitCannotBeDuplicatedOrReplacedByAnotherTarget() {
        var first = travel.visit(player,device);
        assertEquals(MusicProblem.Code.BUSY,MusicProblem.code(assertThrows(Exception.class,() -> travel.visit(player,UUID.randomUUID()).join())));
        assertEquals(4,loadedChunks.size());load();first.join();assertEquals(1,arrivals.size());
    }
    @Test void groupsRequireAConcreteMemberButAGroupRecordAuthorizesThatMember() {
        UUID group = UUID.randomUUID();store.transact(editor -> {
            editor.devices.put(device,editor.devices.get(device).grouped(group));
            editor.groups.put(group,new MusicState.Group(group,"Room",List.of(device),Map.of(),100,0));
            editor.bindings.remove(MusicState.key(actor,device));
            editor.bindings.put(MusicState.key(actor,group),new MusicState.Binding(actor,group,""));return null;
        }).join();
        error(travel.visit(player,group),MusicProblem.Code.NOT_FOUND);
        var request = travel.visit(player,device);load();request.join();assertEquals(1,arrivals.size());
    }
    @Test void lackOfSolidGroundOrWorldBorderSpaceProducesAClearFailure() {
        safeFloor = false;var request = travel.visit(player,device);load();error(request,MusicProblem.Code.NO_SAFE_DESTINATION);
        safeFloor = true;insideBorder = false;error(travel.visit(player,device),MusicProblem.Code.NO_SAFE_DESTINATION);
    }
    @Test void aSlabCanSupportArrivalAndObstaclesOrHazardsAreSkipped() {
        loaded = true;floorTop = .5;
        var slab = MusicDeviceTravel.arrival(player,new Location(world,0,64,0));assertNotNull(slab);assertEquals(63.5,slab.getY());
        floorTop = 1;blocks.put("0,63,1",Material.MAGMA_BLOCK);blocks.put("1,64,0",Material.LAVA);
        var safe = MusicDeviceTravel.arrival(player,new Location(world,0,64,0));assertNotNull(safe);
        assertFalse(safe.getBlockX() == 0 && safe.getBlockZ() == 1);assertFalse(safe.getBlockX() == 1 && safe.getBlockZ() == 0);
        blocks.clear();for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) blocks.put(x+",65,"+z,Material.STONE);
        assertNull(MusicDeviceTravel.arrival(player,new Location(world,0,64,0)),"head clearance is required");
    }
    @Test void blockInspectionAndTeleportWaitForTheMainThreadDispatch() {
        var queue = new ArrayList<Runnable>();travel.close();main.close();main = new MusicMainThread(queue::add);travel = new MusicDeviceTravel(registry,main);
        var request = travel.visit(player,device);load();assertEquals(0,reads);assertTrue(arrivals.isEmpty());
        List.copyOf(queue).forEach(Runnable::run);request.join();assertEquals(1,arrivals.size());
    }
    @Test void aScaledPlayersWholeFootprintMustAvoidHazardousGround() {
        loaded = true;playerWidth = 2.2;
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) blocks.put(x+",63,"+z,Material.MAGMA_BLOCK);
        for (String point : List.of("2,63,0","1,63,-1","1,63,1","3,63,-1","3,63,1")) blocks.put(point,Material.STONE);
        assertNull(MusicDeviceTravel.arrival(player,new Location(world,0,64,0)),
                "four supported corners cannot make hazardous ground inside a wide player's footprint safe");
    }
    @Test void rejectedNativeTeleportsAreReportedAndDoNotLeaveTheVisitBusy() {
        teleportAllowed = false;var request = travel.visit(player,device);load();
        assertEquals(MusicProblem.Code.TELEPORT_FAILED,MusicProblem.code(assertThrows(Exception.class,request::join)));
        assertFalse(travel.pending(actor));assertEquals(1,arrivals.size());
    }
}
