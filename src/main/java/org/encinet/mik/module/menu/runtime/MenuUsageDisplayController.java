package org.encinet.mik.module.menu.runtime;


import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.integration.axiom.AxiomGizmoService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Projects an active floating-menu session as a localized status above its player. */
final class MenuUsageDisplayController {

    private static final String RHYTHM_GAME_SCREEN_ID = "jukebox-rhythm";
    private static final String RHYTHM_CALIBRATION_SCREEN_ID =
            "jukebox-rhythm-calibration";
    // AFK uses 0.55, so simultaneous statuses remain readable instead of overlapping.
    private static final double DISPLAY_Y_OFFSET = 0.85D;
    private static final double DISPLAY_SYNC_RANGE = 64.0D;
    private static final double DISPLAY_SYNC_RANGE_SQUARED = DISPLAY_SYNC_RANGE * DISPLAY_SYNC_RANGE;
    private static final int BACKGROUND_COLOR_ARGB = 0x60000000;
    private static final int LINE_WIDTH = 180;
    private static final int TEXT_METADATA_INDEX = 23;
    private static final int LINE_WIDTH_METADATA_INDEX = 24;
    private static final int BACKGROUND_METADATA_INDEX = 25;
    private static final int TEXT_OPACITY_METADATA_INDEX = 26;
    private static final int TEXT_STYLE_FLAGS_METADATA_INDEX = 27;
    private static final int BILLBOARD_METADATA_INDEX = 15;
    private static final int VIEW_RANGE_METADATA_INDEX = 17;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte TEXT_SHADOW_FLAG = 0x01;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final LanguageService languageService;
    private final AxiomGizmoService.Scope axiomGizmos;
    private final Map<UUID, VirtualDisplay> displays = new HashMap<>();

    MenuUsageDisplayController(LanguageService languageService,
                               AxiomGizmoService.Scope axiomGizmos) {
        this.languageService = languageService;
        this.axiomGizmos = axiomGizmos;
    }

    void update(Player player, String screenId) {
        VirtualDisplay display = displays.computeIfAbsent(player.getUniqueId(),
                ignored -> new VirtualDisplay(player.getWorld()));
        syncDisplay(player, display, Usage.fromScreenId(screenId),
                OnlineView.capture());
    }

    void updateTrackedPlayers(Map<UUID, String> activeScreens) {
        Set<UUID> active = Set.copyOf(activeScreens.keySet());
        for (UUID displayedPlayerId : Set.copyOf(displays.keySet())) {
            if (!active.contains(displayedPlayerId)) {
                remove(displayedPlayerId);
            }
        }
        if (active.isEmpty()) {
            return;
        }

        OnlineView online = OnlineView.capture();
        for (UUID playerId : active) {
            ViewerSnapshot subject = online.byId.get(playerId);
            if (subject == null) {
                remove(playerId);
                continue;
            }
            VirtualDisplay display = displays.computeIfAbsent(playerId,
                    ignored -> new VirtualDisplay(subject.world));
            syncDisplay(subject.player, display,
                    Usage.fromScreenId(activeScreens.get(playerId)), online);
        }
    }

    void remove(UUID playerId) {
        VirtualDisplay display = displays.remove(playerId);
        if (display != null) {
            destroyDisplay(display);
        }
    }

    void disable() {
        displays.values().forEach(this::destroyDisplay);
        displays.clear();
        axiomGizmos.close();
    }

    void forgetViewer(UUID viewerId) {
        for (VirtualDisplay display : displays.values()) {
            display.viewers.remove(viewerId);
        }
        axiomGizmos.forgetViewer(viewerId);
    }

    void refreshViewerLanguage(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        for (VirtualDisplay display : displays.values()) {
            if (display.viewers.contains(viewerId)) {
                sendMetadata(viewer, display);
            }
        }
    }

    private Location displayLocation(Player player) {
        return player.getLocation().add(0.0D, player.getHeight() + DISPLAY_Y_OFFSET, 0.0D);
    }

    private Component displayText(Player viewer, Usage usage) {
        if (usage == Usage.RHYTHM_GAME) {
            return languageService.text(viewer, Message.MENU_RHYTHM_GAME_DISPLAY,
                            NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true);
        }
        return MINI_MESSAGE.deserialize(languageService.t(
                viewer, Message.MENU_USAGE_DISPLAY_MM));
    }

    private void syncDisplay(Player subject, VirtualDisplay display, Usage usage,
                             OnlineView online) {
        Location location = displayLocation(subject);
        boolean worldChanged = display.world != null && !display.world.equals(subject.getWorld());
        if (worldChanged) {
            destroyDisplay(display);
        }
        boolean contentChanged = display.updateUsage(usage);
        boolean moved = display.updateLocation(location);

        Iterator<UUID> tracked = display.viewers.iterator();
        while (tracked.hasNext()) {
            UUID viewerId = tracked.next();
            ViewerSnapshot viewer = online.byId.get(viewerId);
            if (!canSeeDisplay(viewer, subject, location)) {
                if (viewer != null) {
                    destroy(viewer.player, display);
                }
                tracked.remove();
                continue;
            }
            if (moved) {
                teleport(viewer.player, display);
            }
            if (contentChanged) {
                sendMetadata(viewer.player, display);
            }
        }

        for (ViewerSnapshot viewer : online.viewersIn(subject.getWorld())) {
            UUID viewerId = viewer.player.getUniqueId();
            if (display.viewers.contains(viewerId) || !canSeeDisplay(viewer, subject, location)) {
                continue;
            }
            spawn(viewer.player, display);
            sendMetadata(viewer.player, display);
            display.viewers.add(viewerId);
        }
    }

    private boolean canSeeDisplay(ViewerSnapshot viewer, Player subject, Location location) {
        if (viewer == null || !viewer.player.isOnline()) {
            return false;
        }
        if (!viewer.world.equals(subject.getWorld())) {
            return false;
        }
        if (!viewer.player.getUniqueId().equals(subject.getUniqueId()) && !viewer.player.canSee(subject)) {
            return false;
        }
        double dx = viewer.x - location.getX();
        double dy = viewer.y - location.getY();
        double dz = viewer.z - location.getZ();
        return dx * dx + dy * dy + dz * dz <= DISPLAY_SYNC_RANGE_SQUARED;
    }

    private void destroyDisplay(VirtualDisplay display) {
        for (UUID viewerId : display.viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                destroy(viewer, display);
            }
        }
        display.viewers.clear();
    }

    private void spawn(Player viewer, VirtualDisplay display) {
        axiomGizmos.synchronize(viewer, display.entityUuid, Set.of(display.entityUuid));
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerSpawnEntity(
                        display.entityId,
                        display.entityUuid,
                        EntityTypes.TEXT_DISPLAY,
                        packetLocation(display.x, display.y, display.z),
                        0.0F,
                        0,
                        Vector3d.zero()
                ));
    }

    private void sendMetadata(Player viewer, VirtualDisplay display) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerEntityMetadata(display.entityId,
                        metadata(viewer, display)));
    }

    private void teleport(Player viewer, VirtualDisplay display) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerEntityTeleport(
                        display.entityId,
                        new Vector3d(display.x, display.y, display.z),
                        0.0F,
                        0.0F,
                        false
                ));
    }

    private void destroy(Player viewer, VirtualDisplay display) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerDestroyEntities(display.entityId));
        axiomGizmos.remove(viewer, display.entityUuid);
    }

    private List<EntityData<?>> metadata(Player viewer, VirtualDisplay display) {
        return List.of(
                new EntityData<>(BILLBOARD_METADATA_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(VIEW_RANGE_METADATA_INDEX, EntityDataTypes.FLOAT, 32.0F),
                new EntityData<>(TEXT_METADATA_INDEX, EntityDataTypes.ADV_COMPONENT,
                        displayText(viewer, display.usage)),
                new EntityData<>(LINE_WIDTH_METADATA_INDEX, EntityDataTypes.INT, LINE_WIDTH),
                new EntityData<>(BACKGROUND_METADATA_INDEX, EntityDataTypes.INT, BACKGROUND_COLOR_ARGB),
                new EntityData<>(TEXT_OPACITY_METADATA_INDEX, EntityDataTypes.BYTE, (byte) -1),
                new EntityData<>(TEXT_STYLE_FLAGS_METADATA_INDEX, EntityDataTypes.BYTE, TEXT_SHADOW_FLAG)
        );
    }

    private com.github.retrooper.packetevents.protocol.world.Location packetLocation(
            double x, double y, double z) {
        return new com.github.retrooper.packetevents.protocol.world.Location(x, y, z, 0.0F, 0.0F);
    }

    private static final class VirtualDisplay {
        private final int entityId;
        private final UUID entityUuid = UUID.randomUUID();
        private final Set<UUID> viewers = new HashSet<>();
        private World world;
        private double x;
        private double y;
        private double z;
        private Usage usage = Usage.MENU;

        private VirtualDisplay(World world) {
            this.entityId = SpigotReflectionUtil.generateEntityId(world);
        }

        private boolean updateLocation(Location location) {
            boolean changed = world == null
                    || !world.equals(location.getWorld())
                    || x != location.getX()
                    || y != location.getY()
                    || z != location.getZ();
            world = location.getWorld();
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            return changed;
        }

        private boolean updateUsage(Usage next) {
            if (usage == next) return false;
            usage = next;
            return true;
        }
    }

    private enum Usage {
        MENU,
        RHYTHM_GAME;

        private static Usage fromScreenId(String screenId) {
            return RHYTHM_GAME_SCREEN_ID.equals(screenId)
                    || RHYTHM_CALIBRATION_SCREEN_ID.equals(screenId)
                    ? RHYTHM_GAME : MENU;
        }
    }

    private record ViewerSnapshot(Player player, World world, double x, double y, double z) {

        private static ViewerSnapshot capture(Player player) {
            Location location = player.getLocation();
            return new ViewerSnapshot(
                    player,
                    location.getWorld(),
                    location.getX(),
                    location.getY(),
                    location.getZ()
            );
        }
    }

    private static final class OnlineView {
        private final Map<UUID, ViewerSnapshot> byId = new HashMap<>();
        private final Map<World, List<ViewerSnapshot>> byWorld = new HashMap<>();

        private static OnlineView capture() {
            OnlineView online = new OnlineView();
            for (Player player : Bukkit.getOnlinePlayers()) {
                ViewerSnapshot viewer = ViewerSnapshot.capture(player);
                online.byId.put(player.getUniqueId(), viewer);
                online.byWorld.computeIfAbsent(viewer.world, ignored -> new ArrayList<>()).add(viewer);
            }
            return online;
        }

        private Collection<ViewerSnapshot> viewersIn(World world) {
            return byWorld.getOrDefault(world, List.of());
        }
    }
}
