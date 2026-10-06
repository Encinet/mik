package org.encinet.mik.integration.dreamdisplays;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.encinet.mik.module.afk.viewing.PhysicalScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DreamDisplaysScreenSourceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final UUID WORLD_ID = new UUID(0, 1);
    private DreamDisplaysScreenSource source;
    private PaperDisplayFixture display;

    @BeforeEach
    void setUp() throws Exception {
        DisplayManagerFixture.INSTANCE.displays.clear();
        TimelineManagerFixture.INSTANCE.timelines.clear();
        PartyManagerFixture.INSTANCE.sessions.clear();
        ViewerTrackerFixture.INSTANCE.viewers.clear();
        source = new DreamDisplaysScreenSource(fixtureLoader(false));
        display = new PaperDisplayFixture();
        DisplayManagerFixture.INSTANCE.displays.add(display);
        TimelineManagerFixture.INSTANCE.timelines.put(display.getId(), new TimelineFixture());
    }

    @Test
    void readsAuthoritativePlayingAndPausedStates() throws Exception {
        assertEquals(PhysicalScreen.Playback.PLAYING, snapshot().playback());
        timeline().paused = true;
        assertEquals(PhysicalScreen.Playback.PAUSED, snapshot().playback());
    }

    @Test
    void localVideoUrlIsNotProofOfPlayback() throws Exception {
        display.mode = Mode.LOCAL;
        assertEquals(PhysicalScreen.Playback.UNKNOWN, snapshot().playback());
    }

    @Test
    void watchPartyCannotReuseAnUnrelatedBaseTimeline() throws Exception {
        PartyManagerFixture.INSTANCE.sessions.add(display.getId());
        assertEquals(PhysicalScreen.Playback.UNKNOWN, snapshot().playback());
    }

    @Test
    void missingTimelineAndMissingContentFailClosed() throws Exception {
        TimelineManagerFixture.INSTANCE.timelines.clear();
        assertEquals(PhysicalScreen.Playback.UNKNOWN, snapshot().playback());
        display.url = " ";
        assertEquals(PhysicalScreen.Playback.INACTIVE, snapshot().playback());
    }

    @Test
    void endedVideoIsInactiveUnlessTheAuthoritativeClockLoops() throws Exception {
        timeline().position = 100_000;
        assertEquals(PhysicalScreen.Playback.INACTIVE, snapshot().playback());
        timeline().loop = true;
        assertEquals(PhysicalScreen.Playback.PLAYING, snapshot().playback());
    }

    @Test
    void virtualScreensAreNeverCandidates() throws Exception {
        display.virtual = true;
        assertTrue(source.snapshot(NOW).isEmpty());
    }

    @Test
    void conformingAndDeepScreensAreConservativelyExcluded() throws Exception {
        display.conforming = true;
        assertTrue(source.snapshot(NOW).isEmpty());
        display.conforming = false;
        display.bounds = new BoundingBox(0, 0, 0, 4, 3, 2);
        assertTrue(source.snapshot(NOW).isEmpty());
    }

    @Test
    void olderInterfaceWithoutConformingPropertyStillBinds() throws Exception {
        DreamDisplaysScreenSource legacy = new DreamDisplaysScreenSource(fixtureLoader(true));
        assertEquals(1, legacy.snapshot(NOW).size());
    }

    @Test
    void onlyNegotiatedDreamDisplaysClientsAreEligible() throws Exception {
        UUID viewer = new UUID(0, 25);
        assertFalse(source.supportsViewer(viewer));
        ViewerTrackerFixture.INSTANCE.viewers.add(viewer);
        assertTrue(source.supportsViewer(viewer));
    }

    @Test
    void screenIdentityIncludesWorldAndChangingContent() throws Exception {
        PhysicalScreen before = snapshot();
        assertEquals(WORLD_ID, before.worldId());
        assertEquals(-0.02, before.geometry().center().z(), 1.0e-9);
        display.url = "https://example.invalid/another.mp4";
        assertNotEquals(before.contentKey(), snapshot().contentKey());
    }

    @Test
    void missingPublicInterfaceFailsAtBinding() {
        ClassLoader missing = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        assertThrows(ReflectiveOperationException.class, () -> new DreamDisplaysScreenSource(missing));
    }

    @Test
    void readFailuresPropagateRatherThanFabricatePlayingState() {
        DisplayManagerFixture.INSTANCE.displays.add(new Object());
        assertThrows(IllegalArgumentException.class, () -> source.snapshot(NOW));
    }

    private PhysicalScreen snapshot() throws Exception { return source.snapshot(NOW).getFirst(); }
    private TimelineFixture timeline() { return TimelineManagerFixture.INSTANCE.timelines.get(display.getId()); }

    private static ClassLoader fixtureLoader(boolean legacy) {
        Map<String, Class<?>> types = Map.of(
                "com.dreamdisplays.platform.server.managers.DisplayManager", DisplayManagerFixture.class,
                "com.dreamdisplays.platform.server.playback.TimelineManager", TimelineManagerFixture.class,
                "com.dreamdisplays.platform.server.playback.WatchPartyManager", PartyManagerFixture.class,
                "com.dreamdisplays.platform.server.utils.net.V2PlayerTracker", ViewerTrackerFixture.class,
                "com.dreamdisplays.platform.server.datatypes.display.DisplayData", legacy ? LegacyDisplayFixture.class : DisplayFixture.class,
                "com.dreamdisplays.platform.server.datatypes.display.PaperDisplayData", PaperDisplayFixture.class,
                "com.dreamdisplays.api.playback.model.Timeline", TimelineFixture.class);
        return new ClassLoader(DreamDisplaysScreenSourceTest.class.getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                Class<?> fixture = types.get(name);
                return fixture != null ? fixture : super.loadClass(name);
            }
        };
    }

    public enum Mode { LOCAL, SYNCED, BROADCAST }

    public interface LegacyDisplayFixture {
        UUID getId();
        boolean getVirtual();
        String getUrl();
        Mode getMode();
    }

    public interface DisplayFixture extends LegacyDisplayFixture {
        boolean getConforming();
    }

    public static class PaperDisplayFixture implements DisplayFixture {
        private final UUID id = UUID.randomUUID();
        private boolean virtual;
        private boolean conforming;
        private String url = "https://example.invalid/movie.mp4";
        private Mode mode = Mode.SYNCED;
        private BoundingBox bounds = new BoundingBox(0, 0, 0, 4, 3, 1);

        public UUID getId() { return id; }
        public boolean getVirtual() { return virtual; }
        public boolean getConforming() { return conforming; }
        public String getUrl() { return url; }
        public Mode getMode() { return mode; }
        public BoundingBox getBox() { return bounds; }
        public BlockFace getFacing() { return BlockFace.NORTH; }
        public Location getPos1() {
            World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUID" -> WORLD_ID;
                        case "getName" -> "cinema";
                        case "hashCode" -> WORLD_ID.hashCode();
                        case "equals" -> proxy == arguments[0];
                        default -> null;
                    });
            return new Location(world, 0, 0, 0);
        }
    }

    public static class DisplayManagerFixture {
        public static final DisplayManagerFixture INSTANCE = new DisplayManagerFixture();
        private final List<Object> displays = new ArrayList<>();
        public List<Object> getDisplays() { return displays; }
    }

    public static class TimelineManagerFixture {
        public static final TimelineManagerFixture INSTANCE = new TimelineManagerFixture();
        private final Map<UUID, TimelineFixture> timelines = new HashMap<>();
        public TimelineFixture timelineOf(UUID displayId) { return timelines.get(displayId); }
    }

    public static class PartyManagerFixture {
        public static final PartyManagerFixture INSTANCE = new PartyManagerFixture();
        private final Set<UUID> sessions = new HashSet<>();
        public boolean hasSession(UUID displayId) { return sessions.contains(displayId); }
    }

    public static class ViewerTrackerFixture {
        public static final ViewerTrackerFixture INSTANCE = new ViewerTrackerFixture();
        private final Set<UUID> viewers = new HashSet<>();
        public boolean isV2(UUID playerId) { return viewers.contains(playerId); }
    }

    public static class TimelineFixture {
        private boolean paused;
        private long position = 10_000;
        private boolean loop;
        public boolean getPaused() { return paused; }
        public long getDurationMs() { return 60_000; }
        public boolean getLoop() { return loop; }
        public long positionAt(long now) { return loop ? position % getDurationMs() : position; }
    }
}
