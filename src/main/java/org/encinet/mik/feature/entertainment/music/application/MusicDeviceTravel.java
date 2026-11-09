package org.encinet.mik.feature.entertainment.music.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;

/** Player-requested visits; chunk work is asynchronous, identity and landing checks stay on the main thread. */
public final class MusicDeviceTravel implements AutoCloseable {
    private static final int RADIUS = 2;
    private static final double EPSILON = 1e-5;
    private final MusicDeviceRegistry registry;
    private final MusicMainThread main;
    private final ConcurrentHashMap<UUID,CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public MusicDeviceTravel(MusicDeviceRegistry registry, MusicMainThread main) {
        this.registry = registry; this.main = main;
    }

    public boolean pending(UUID player) { return pending.containsKey(player); }

    /** Availability does not inspect or load chunks, and playback/rhythm state does not prevent a visit. */
    public MusicProblem.Code failure(Player player, UUID device) {
        if (closed) return MusicProblem.Code.UNAVAILABLE;
        if (pending(player.getUniqueId())) return MusicProblem.Code.BUSY;
        try { registry.authorizeVisit(player,device); return null; }
        catch (MusicProblem problem) { return problem.code(); }
    }

    public CompletableFuture<Void> visit(Player player, UUID device) {
        if (closed || !player.isOnline()) return CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.UNAVAILABLE));
        UUID actor = player.getUniqueId();
        var request = new CompletableFuture<Void>();
        if (pending.putIfAbsent(actor,request) != null)
            return CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.BUSY));
        try {
            MusicDevice expected = registry.authorizeVisit(player,device);
            Location anchor = registry.location(expected);
            Size size = size(player);
            int reach = RADIUS + (int)Math.ceil(Math.max(size.width(),size.depth())/2);
            var loads = new ArrayList<CompletableFuture<?>>();
            for (int x = (expected.x()-reach)>>4; x <= (expected.x()+reach)>>4; x++)
                for (int z = (expected.z()-reach)>>4; z <= (expected.z()+reach)>>4; z++)
                    loads.add(anchor.getWorld().getChunkAtAsync(x,z,false));
            main.after(CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)), () -> {
                if (closed || pending.get(actor) != request || !player.isOnline()) throw new CancellationException();
                MusicDevice current = registry.authorizeVisit(player,device);
                if (!samePlace(expected,current)) throw new MusicProblem(MusicProblem.Code.CONFLICT);
                if (!registry.location(current).isChunkLoaded()) throw new MusicProblem(MusicProblem.Code.OFFLINE);
                if (registry.block(current) == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
                Location destination = arrival(player,registry.location(current));
                if (destination == null) throw new MusicProblem(MusicProblem.Code.NO_SAFE_DESTINATION);
                player.teleportAsync(destination).whenComplete((success,error) -> {
                    finish(actor,request,error != null ? error : Boolean.TRUE.equals(success) ? null
                            : new MusicProblem(MusicProblem.Code.TELEPORT_FAILED));
                });
            }).whenComplete((_,error) -> { if (error != null) finish(actor,request,error); });
        } catch (Exception error) { finish(actor,request,error); }
        return request;
    }

    private void finish(UUID actor, CompletableFuture<Void> request, Throwable error) {
        if (!pending.remove(actor,request)) return;
        if (error == null) request.complete(null);
        else request.completeExceptionally(error);
    }

    public void cancel(UUID player) {
        var request = pending.remove(player);
        if (request != null) request.completeExceptionally(new CancellationException());
    }

    @Override public void close() { closed = true; List.copyOf(pending.keySet()).forEach(this::cancel); }

    private static boolean samePlace(MusicDevice a, MusicDevice b) {
        return a.world().equals(b.world()) && a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
    }

    private record Size(double width, double height, double depth) {
        BoundingBox at(Location feet) {
            return new BoundingBox(feet.getX()-width/2,feet.getY(),feet.getZ()-depth/2,
                    feet.getX()+width/2,feet.getY()+height,feet.getZ()+depth/2);
        }
    }
    private static Size size(Player player) {
        var body = player.getBoundingBox();
        var size = new Size(body.getWidthX(),Math.max(body.getHeight(),body.getWidthX()*3),body.getWidthZ());
        if (size.width() <= 0 || size.depth() <= 0 || size.height() <= 0 || size.width() > 4 || size.depth() > 4 || size.height() > 8)
            throw new MusicProblem(MusicProblem.Code.NO_SAFE_DESTINATION);
        return size;
    }
    private record Offset(int x, int z) { int distance() { return x*x+z*z; } }

    /** Bounded neighbours, including slab tops; never reads an unloaded block or falls back inside the jukebox. */
    static Location arrival(Player player, Location anchor) {
        World world = anchor.getWorld();
        Size size = size(player);
        var offsets = new ArrayList<Offset>();
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++)
            if (x != 0 || z != 0) offsets.add(new Offset(x,z));
        Location source = player.getLocation();
        offsets.sort(Comparator.comparingInt(Offset::distance).thenComparingDouble(offset -> world.equals(source.getWorld())
                ? square(anchor.getBlockX()+offset.x()+.5-source.getX())+square(anchor.getBlockZ()+offset.z()+.5-source.getZ()) : 0)
                .thenComparingInt(Offset::x).thenComparingInt(Offset::z));
        for (var offset : offsets) {
            int x = anchor.getBlockX()+offset.x(), z = anchor.getBlockZ()+offset.z();
            if (!world.isChunkLoaded(x>>4,z>>4)) continue;
            for (int dy : new int[]{-1,0,-2}) {
                int y = anchor.getBlockY()+dy;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                Block support = world.getBlockAt(x,y,z);
                if (hazard(support)) continue;
                for (var box : support.getCollisionShape().getBoundingBoxes().stream()
                        .filter(box -> box.getMinX() <= .5 && box.getMaxX() >= .5 && box.getMinZ() <= .5 && box.getMaxZ() >= .5)
                        .sorted(Comparator.comparingDouble(BoundingBox::getMaxY).reversed()).toList()) {
                    var feet = new Location(world,x+.5,y+box.getMaxY(),z+.5);
                    if (!safe(feet,size)) continue;
                    feet.setDirection(anchor.clone().add(.5,.5,.5).toVector().subtract(feet.clone().add(0,player.getEyeHeight(),0).toVector()));
                    return feet;
                }
            }
        }
        return null;
    }

    private static boolean safe(Location feet, Size size) {
        World world = feet.getWorld();
        BoundingBox body = size.at(feet);
        if (body.getMinY() < world.getMinHeight() || body.getMaxY() > world.getMaxHeight()) return false;
        for (double x : new double[]{body.getMinX()+EPSILON,body.getMaxX()-EPSILON})
            for (double z : new double[]{body.getMinZ()+EPSILON,body.getMaxZ()-EPSILON}) {
                Location edge = new Location(world,x,feet.getY(),z);
                if (!world.getWorldBorder().isInside(edge) || !supported(edge)) return false;
            }
        for (int x = (int)Math.floor(body.getMinX()); x < body.getMaxX(); x++)
            for (int z = (int)Math.floor(body.getMinZ()); z < body.getMaxZ(); z++) {
                if (!world.isChunkLoaded(x>>4,z>>4)) return false;
                for (int y = (int)Math.floor(body.getMinY()-EPSILON); y < body.getMaxY(); y++) {
                    Block block = world.getBlockAt(x,y,z);
                    if (hazard(block) || block.getCollisionShape().overlaps(body.clone().shift(-x,-y,-z))) return false;
                }
            }
        return true;
    }

    private static boolean supported(Location feet) {
        World world = feet.getWorld();
        int x = feet.getBlockX(), y = (int)Math.floor(feet.getY()-EPSILON), z = feet.getBlockZ();
        if (y < world.getMinHeight() || !world.isChunkLoaded(x>>4,z>>4)) return false;
        Block block = world.getBlockAt(x,y,z);
        if (hazard(block)) return false;
        return block.getCollisionShape().getBoundingBoxes().stream().anyMatch(box -> Math.abs(y+box.getMaxY()-feet.getY()) < EPSILON
                && feet.getX()-x >= box.getMinX() && feet.getX()-x <= box.getMaxX()
                && feet.getZ()-z >= box.getMinZ() && feet.getZ()-z <= box.getMaxZ());
    }

    private static boolean hazard(Block block) {
        return block.isLiquid() || switch (block.getType()) {
            case FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW,
                    SWEET_BERRY_BUSH, WITHER_ROSE, POINTED_DRIPSTONE, NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
    }
    private static double square(double value) { return value*value; }
}
