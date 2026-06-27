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
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
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
        syncDisplay(player, display, metadataChanged);
    }

    void updateTrackedDisplays(Collection<AfkState> states) {
        for (AfkState state : states) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player == null || !player.isOnline()) {
                remove(state.playerId());
                continue;
            }
            update(player, state);
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

    private void syncDisplay(Player subject, VirtualDisplay display, boolean metadataChanged) {
        Location location = displayLocation(subject);
        boolean worldChanged = display.world != null && !display.world.equals(subject.getWorld());
        if (worldChanged) {
            destroyDisplay(display);
        }
        boolean moved = display.updateLocation(location);

        Iterator<UUID> tracked = display.viewers.iterator();
        while (tracked.hasNext()) {
            UUID viewerId = tracked.next();
            Player viewer = Bukkit.getPlayer(viewerId);
            if (!canSeeDisplay(viewer, subject, location)) {
                if (viewer != null && viewer.isOnline()) {
                    destroy(viewer, display);
                }
                tracked.remove();
                continue;
            }
            if (metadataChanged) {
                sendMetadata(viewer, display);
            }
            if (moved) {
                teleport(viewer, display);
            }
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID viewerId = viewer.getUniqueId();
            if (display.viewers.contains(viewerId) || !canSeeDisplay(viewer, subject, location)) {
                continue;
            }
            spawn(viewer, display);
            sendMetadata(viewer, display);
            display.viewers.add(viewerId);
        }
    }

    private boolean canSeeDisplay(Player viewer, Player subject, Location location) {
        if (viewer == null || !viewer.isOnline()) {
            return false;
        }
        if (!viewer.getWorld().equals(subject.getWorld())) {
            return false;
        }
        if (!viewer.getUniqueId().equals(subject.getUniqueId()) && !viewer.canSee(subject)) {
            return false;
        }
        return viewer.getLocation().distanceSquared(location) <= DISPLAY_SYNC_RANGE_SQUARED;
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
}
