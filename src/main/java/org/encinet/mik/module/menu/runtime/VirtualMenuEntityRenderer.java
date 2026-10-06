package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDecoration;

import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sends menu entities through Paper's native 26.3 packet codec.
 *
 * <p>The temporary NMS instances created for metadata are deliberately never
 * added to a {@code ServerLevel}. They exist only long enough to let the
 * server construct its exact metadata serializers, so every visible menu
 * entity remains private and client-only.</p>
 */
final class VirtualMenuEntityRenderer {
    private static final String NATIVE_PROTOCOL_VERSION = "26.3";
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    VirtualMenuEntityRenderer() {
        String actual = Bukkit.getMinecraftVersion();
        if (!NATIVE_PROTOCOL_VERSION.equals(actual)) {
            throw new IllegalStateException("Native floating-menu renderer targets Minecraft "
                    + NATIVE_PROTOCOL_VERSION + "; server is " + actual);
        }
    }

    enum Kind {
        TEXT,
        ITEM,
        BLOCK,
        INTERACTION
    }

    static int allocateId(Player viewer) {
        return ((CraftWorld) viewer.getWorld()).getHandle().getNextEntityId();
    }

    void spawn(Player viewer, int entityId, UUID uuid, Kind kind, Location location,
               float presentationYaw) {
        spawn(viewer, entityId, uuid, kind, location, presentationYaw, 0.0F);
    }

    void spawn(Player viewer, int entityId, UUID uuid, Kind kind, Location location,
               float presentationYaw, float presentationPitch) {
        EntityType<?> type = nativeType(kind);
        send(viewer, new ClientboundAddEntityPacket(
                entityId,
                uuid,
                location.getX(),
                location.getY(),
                location.getZ(),
                presentationPitch,
                presentationYaw,
                type,
                0,
                Vec3.ZERO,
                presentationYaw));
    }

    void text(Player viewer, int entityId, Component text, int background,
              float displayWidth, float displayHeight, float scale,
              FloatingMenuDecoration.Alignment alignment) {
        text(viewer, entityId, text, background, displayWidth, displayHeight,
                Math.max(80, Math.round(displayWidth * 64.0F)), scale, alignment,
                true);
    }

    void text(Player viewer, int entityId, Component text, int background,
              float displayWidth, float displayHeight, int lineWidthPixels, float scale,
              FloatingMenuDecoration.Alignment alignment) {
        text(viewer, entityId, text, background, displayWidth, displayHeight,
                lineWidthPixels, scale, alignment, true);
    }

    void text(Player viewer, int entityId, Component text, int background,
              float displayWidth, float displayHeight, int lineWidthPixels, float scale,
              FloatingMenuDecoration.Alignment alignment, boolean seeThrough) {
        text(viewer, entityId, text, background, displayWidth, displayHeight,
                lineWidthPixels, scale, alignment, seeThrough, 1);
    }

    void text(Player viewer, int entityId, Component text, int background,
              float displayWidth, float displayHeight, int lineWidthPixels, float scale,
              FloatingMenuDecoration.Alignment alignment, boolean seeThrough, double reveal) {
        if (lineWidthPixels < 1) throw new IllegalArgumentException("Line width must be positive");
        Entity entity = virtualEntity(viewer, Kind.TEXT, entityId);
        TextDisplay display = (TextDisplay) entity.getBukkitEntity();
        configureDisplay(display, displayWidth, displayHeight);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
        display.text(FloatingMenuText.readableOnDark(text));
        display.setLineWidth(lineWidthPixels);
        double progress = FloatingMenuMotion.progress(reveal);
        int alpha = (int) Math.round((background >>> 24) * progress);
        display.setBackgroundColor(Color.fromARGB((background & 0x00FFFFFF) | (alpha << 24)));
        display.setTextOpacity(FloatingMenuMotion.textOpacity(progress));
        display.setShadowed(true);
        display.setSeeThrough(seeThrough);
        display.setDefaultBackground(false);
        display.setAlignment(switch (alignment) {
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case CENTER -> TextDisplay.TextAlignment.CENTER;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
        });
        metadata(viewer, entityId, entity);
    }

    void visual(Player viewer, int entityId, ItemStack item, boolean block, float scale) {
        Kind kind = block ? Kind.BLOCK : Kind.ITEM;
        Entity entity = virtualEntity(viewer, kind, entityId);
        Display display = (Display) entity.getBukkitEntity();
        configureDisplay(display, 1.0F, 1.0F);
        Vector3f translation = block
                ? new Vector3f(-scale / 2.0F, -scale / 2.0F, -scale / 2.0F)
                : new Vector3f();
        display.setTransformation(new Transformation(
                translation,
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
        if (block) {
            ((BlockDisplay) display).setBlock(item.getType().createBlockData());
        } else {
            ItemDisplay itemDisplay = (ItemDisplay) display;
            itemDisplay.setItemStack(item);
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        }
        metadata(viewer, entityId, entity);
    }

    void interaction(Player viewer, int entityId, float width, float height) {
        Entity entity = virtualEntity(viewer, Kind.INTERACTION, entityId);
        Interaction interaction = (Interaction) entity.getBukkitEntity();
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        interaction.setResponsive(true);
        metadata(viewer, entityId, entity);
    }

    void volume(Player viewer, int entityId, BlockData block, float width, float height, float depth) {
        Entity entity = virtualEntity(viewer, Kind.BLOCK, entityId);
        BlockDisplay display = (BlockDisplay) entity.getBukkitEntity();
        configureDisplay(display, Math.max(width, depth), height);
        display.setTransformation(new Transformation(new Vector3f(-width / 2, -height / 2, -depth / 2),
                new Quaternionf(), new Vector3f(width, height, depth), new Quaternionf()));
        display.setBlock(block);
        metadata(viewer, entityId, entity);
    }

    void teleport(Player viewer, int entityId, Location location, float presentationYaw) {
        teleport(viewer, entityId, location, presentationYaw, 0.0F);
    }

    void teleport(Player viewer, int entityId, Location location,
                  float presentationYaw, float presentationPitch) {
        PositionMoveRotation position = new PositionMoveRotation(
                new Vec3(location.getX(), location.getY(), location.getZ()),
                Vec3.ZERO,
                presentationYaw,
                presentationPitch);
        send(viewer, ClientboundTeleportEntityPacket.teleport(entityId, position, Set.of(), false));
    }

    void destroy(Player viewer, int... entityIds) {
        send(viewer, new ClientboundRemoveEntitiesPacket(entityIds));
    }

    private static void configureDisplay(Display display, float width, float height) {
        // One stable local frame makes the menu read as a spatial object. If
        // every child uses CENTER, each glyph swivels independently and the
        // scene collapses back into a heads-up display.
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(FULL_BRIGHT);
        display.setViewRange(8.0F);
        display.setDisplayWidth(width);
        display.setDisplayHeight(height);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        // The service already animates positions every tick. Client-side teleport
        // lag would separate the visible node from its immediate interaction surface.
        display.setTeleportDuration(0);
    }

    private static Entity virtualEntity(Player viewer, Kind kind, int entityId) {
        ServerLevel level = ((CraftWorld) viewer.getWorld()).getHandle();
        Entity entity = switch (kind) {
            case TEXT -> new net.minecraft.world.entity.Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
            case ITEM -> new net.minecraft.world.entity.Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
            case BLOCK -> new net.minecraft.world.entity.Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            case INTERACTION -> new net.minecraft.world.entity.Interaction(EntityTypes.INTERACTION, level);
        };
        entity.setId(entityId);
        return entity;
    }

    private static EntityType<?> nativeType(Kind kind) {
        return switch (kind) {
            case TEXT -> EntityTypes.TEXT_DISPLAY;
            case ITEM -> EntityTypes.ITEM_DISPLAY;
            case BLOCK -> EntityTypes.BLOCK_DISPLAY;
            case INTERACTION -> EntityTypes.INTERACTION;
        };
    }

    private static void metadata(Player viewer, int entityId, Entity entity) {
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
                entity.getEntityData().packAll();
        send(viewer, new ClientboundSetEntityDataPacket(entityId, values));
    }

    private static void send(Player viewer, Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
