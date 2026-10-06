package org.encinet.mik.integration.dreamdisplays;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.encinet.mik.module.afk.viewing.PhysicalScreen;
import org.encinet.mik.module.afk.viewing.PhysicalScreenSource;
import org.encinet.mik.module.afk.viewing.ScreenGeometry;
import org.encinet.mik.module.afk.viewing.ScreenGeometry.Point;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only optional adapter for the public server methods found in DreamDisplays
 * 1.9.6 and 1.10.0-preview.3 source. These are implementation methods, not a stable
 * Bukkit API; signatures are checked at binding and an incompatible plugin fails
 * closed without disabling ordinary AFK detection.
 *
 * <p>Only public methods and Kotlin's public INSTANCE fields are accessed. No
 * private party maps, fabricated playback events or client-only API are used.
 * Virtual and conforming/deep screens are excluded. SYNCED/BROADCAST clocks describe
 * a server-authorized viewing opportunity, not successful client decoding or human
 * attention. LOCAL playback and watch parties are unknown to this adapter and do
 * not grant exemptions merely because a screen has a video URL.</p>
 */
public final class DreamDisplaysScreenSource implements PhysicalScreenSource {
    private static final String SERVER = "com.dreamdisplays.platform.server.";

    private final Object displays;
    private final Object timelines;
    private final Object parties;
    private final Object viewers;
    private final Method getDisplays;
    private final Method timelineOf;
    private final Method hasParty;
    private final Method isV2;
    private final Method id;
    private final Method virtual;
    private final Method url;
    private final Method mode;
    private final Method box;
    private final Method position;
    private final Method facing;
    private final Method conforming;
    private final Method paused;
    private final Method duration;
    private final Method loop;
    private final Method positionAt;
    private final Class<?> paperDisplayType;

    public DreamDisplaysScreenSource(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> displayManager = loader.loadClass(SERVER + "managers.DisplayManager");
        Class<?> timelineManager = loader.loadClass(SERVER + "playback.TimelineManager");
        Class<?> partyManager = loader.loadClass(SERVER + "playback.WatchPartyManager");
        Class<?> viewerTracker = loader.loadClass(SERVER + "utils.net.V2PlayerTracker");
        Class<?> displayType = loader.loadClass(SERVER + "datatypes.display.DisplayData");
        paperDisplayType = loader.loadClass(SERVER + "datatypes.display.PaperDisplayData");
        Class<?> timelineType = loader.loadClass("com.dreamdisplays.api.playback.model.Timeline");
        displays = displayManager.getField("INSTANCE").get(null);
        timelines = timelineManager.getField("INSTANCE").get(null);
        parties = partyManager.getField("INSTANCE").get(null);
        viewers = viewerTracker.getField("INSTANCE").get(null);
        getDisplays = displayManager.getMethod("getDisplays");
        timelineOf = timelineManager.getMethod("timelineOf", UUID.class);
        hasParty = partyManager.getMethod("hasSession", UUID.class);
        isV2 = viewerTracker.getMethod("isV2", UUID.class);
        id = displayType.getMethod("getId");
        virtual = displayType.getMethod("getVirtual");
        url = displayType.getMethod("getUrl");
        mode = displayType.getMethod("getMode");
        box = paperDisplayType.getMethod("getBox");
        position = paperDisplayType.getMethod("getPos1");
        facing = paperDisplayType.getMethod("getFacing");
        Method optionalConforming;
        try {
            optionalConforming = displayType.getMethod("getConforming");
        } catch (NoSuchMethodException ignored) {
            optionalConforming = null;
        }
        conforming = optionalConforming;
        paused = timelineType.getMethod("getPaused");
        duration = timelineType.getMethod("getDurationMs");
        loop = timelineType.getMethod("getLoop");
        positionAt = timelineType.getMethod("positionAt", long.class);
    }

    @Override
    public List<PhysicalScreen> snapshot(Instant now)
            throws ReflectiveOperationException {
        List<PhysicalScreen> result = new ArrayList<>();
        for (Object display : (List<?>) getDisplays.invoke(displays)) {
            UUID screenId = (UUID) id.invoke(display);
            if (!paperDisplayType.isInstance(display) || (boolean) virtual.invoke(display)
                    || (conforming != null && (boolean) conforming.invoke(display))) continue;
            Location location = (Location) position.invoke(display);
            if (location.getWorld() == null) continue;
            BoundingBox bounds = (BoundingBox) box.invoke(display);
            BlockFace face = (BlockFace) facing.invoke(display);
            ScreenGeometry.Face physicalFace;
            try {
                physicalFace = ScreenGeometry.Face.valueOf(face.name());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            double depth = switch (physicalFace) {
                case NORTH, SOUTH -> bounds.getWidthZ();
                case EAST, WEST -> bounds.getWidthX();
                case UP, DOWN -> bounds.getHeight();
            };
            if (Math.abs(depth - 1) > 1.0e-6) continue;
            ScreenGeometry geometry = ScreenGeometry.fromBox(
                    new Point(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()),
                    new Point(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()), physicalFace);
            String videoUrl = (String) url.invoke(display);
            String playbackMode = ((Enum<?>) mode.invoke(display)).name();
            PhysicalScreen.Playback playback = playbackOf(screenId, videoUrl, playbackMode, now);
            result.add(new PhysicalScreen(screenId, location.getWorld().getUID(), geometry,
                    playbackMode + ":" + videoUrl, playback));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean supportsViewer(UUID playerId) throws ReflectiveOperationException {
        return (boolean) isV2.invoke(viewers, playerId);
    }

    private PhysicalScreen.Playback playbackOf(UUID screenId, String videoUrl, String playbackMode,
                                               Instant now)
            throws ReflectiveOperationException {
        if (videoUrl.isBlank()) return PhysicalScreen.Playback.INACTIVE;
        if ((boolean) hasParty.invoke(parties, screenId)) return PhysicalScreen.Playback.UNKNOWN;
        if (!playbackMode.equals("SYNCED") && !playbackMode.equals("BROADCAST")) {
            return PhysicalScreen.Playback.UNKNOWN;
        }
        Object timeline = timelineOf.invoke(timelines, screenId);
        if (timeline == null) return PhysicalScreen.Playback.UNKNOWN;
        long durationMillis = ((Number) duration.invoke(timeline)).longValue();
        long playbackPosition = ((Number) positionAt.invoke(timeline, now.toEpochMilli())).longValue();
        if (durationMillis > 0 && !(boolean) loop.invoke(timeline) && playbackPosition >= durationMillis) {
            return PhysicalScreen.Playback.INACTIVE;
        }
        return (boolean) paused.invoke(timeline) ? PhysicalScreen.Playback.PAUSED : PhysicalScreen.Playback.PLAYING;
    }
}
