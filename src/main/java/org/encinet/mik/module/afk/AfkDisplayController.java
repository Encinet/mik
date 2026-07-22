package org.encinet.mik.module.afk;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class AfkDisplayController {

    private static final double DISPLAY_Y_OFFSET = 0.55D;
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
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_000_000_000);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage SAFE_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()
            ))
            .build();

    private final LanguageService languageService;
    private final Map<UUID, VirtualDisplay> displays = new HashMap<>();

    AfkDisplayController(LanguageService languageService) {
        this.languageService = languageService;
    }

    void update(Player player, AfkState state) {
        VirtualDisplay display = displays.computeIfAbsent(player.getUniqueId(), VirtualDisplay::new);
        boolean metadataChanged = display.state != state;
        display.state = state;
        syncDisplay(player, display, metadataChanged, OnlineView.capture());
    }

    void updateTrackedDisplays(Collection<AfkState> states) {
        if (states.isEmpty()) {
            return;
        }

        OnlineView online = OnlineView.capture();

        for (AfkState state : states) {
            ViewerSnapshot subject = online.byId.get(state.playerId());
            if (subject == null) {
                remove(state.playerId());
                continue;
            }
            Player player = subject.player;
            VirtualDisplay display = displays.computeIfAbsent(player.getUniqueId(), VirtualDisplay::new);
            boolean metadataChanged = display.state != state;
            display.state = state;
            syncDisplay(player, display, metadataChanged, online);
        }
    }

    void remove(UUID playerId) {
        VirtualDisplay display = displays.remove(playerId);
        if (display != null) {
            destroyDisplay(display);
        }
    }

    void removeAll() {
        displays.values().forEach(this::destroyDisplay);
        displays.clear();
    }

    void forgetViewer(UUID viewerId) {
        for (VirtualDisplay display : displays.values()) {
            display.viewers.remove(viewerId);
        }
    }

    void refreshViewerLanguage(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        for (VirtualDisplay display : displays.values()) {
            if (display.viewers.contains(viewerId) && display.state != null) {
                sendMetadata(viewer, display);
            }
        }
    }

    private Location displayLocation(Player player) {
        Location location = player.getLocation();
        location.add(0.0D, player.getHeight() + DISPLAY_Y_OFFSET, 0.0D);
        return location;
    }

    private Component displayText(Player player, AfkState state) {
        if (!state.hasCustomMessage()) {
            return MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_DISPLAY_TITLE_MM));
        }
        return MINI_MESSAGE.deserialize(
                languageService.t(player, Message.AFK_DISPLAY_WITH_MESSAGE_MM),
                Placeholder.component("message", SAFE_MESSAGE.deserialize(state.message())));
    }

    private void syncDisplay(
            Player subject,
            VirtualDisplay display,
            boolean metadataChanged,
            OnlineView online
    ) {
        Location location = displayLocation(subject);
        boolean worldChanged = display.world != null && !display.world.equals(subject.getWorld());
        if (worldChanged) {
            destroyDisplay(display);
        }
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
            if (metadataChanged) {
                sendMetadata(viewer.player, display);
            }
            if (moved) {
                teleport(viewer.player, display);
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
                new WrapperPlayServerEntityMetadata(display.entityId, metadata(viewer, display.state)));
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
    }

    private List<EntityData<?>> metadata(Player viewer, AfkState state) {
        return List.of(
                new EntityData<>(BILLBOARD_METADATA_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(VIEW_RANGE_METADATA_INDEX, EntityDataTypes.FLOAT, 32.0F),
                new EntityData<>(TEXT_METADATA_INDEX, EntityDataTypes.ADV_COMPONENT, displayText(viewer, state)),
                new EntityData<>(LINE_WIDTH_METADATA_INDEX, EntityDataTypes.INT, LINE_WIDTH),
                new EntityData<>(BACKGROUND_METADATA_INDEX, EntityDataTypes.INT, BACKGROUND_COLOR_ARGB),
                new EntityData<>(TEXT_OPACITY_METADATA_INDEX, EntityDataTypes.BYTE, (byte) -1),
                new EntityData<>(TEXT_STYLE_FLAGS_METADATA_INDEX, EntityDataTypes.BYTE, TEXT_SHADOW_FLAG)
        );
    }

    private com.github.retrooper.packetevents.protocol.world.Location packetLocation(double x, double y, double z) {
        return new com.github.retrooper.packetevents.protocol.world.Location(x, y, z, 0.0F, 0.0F);
    }

    private static final class VirtualDisplay {
        private final int entityId = NEXT_ENTITY_ID.getAndDecrement();
        private final UUID entityUuid = UUID.randomUUID();
        private final Set<UUID> viewers = new HashSet<>();
        private org.bukkit.World world;
        private AfkState state;
        private double x;
        private double y;
        private double z;

        private VirtualDisplay(UUID playerId) {
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
