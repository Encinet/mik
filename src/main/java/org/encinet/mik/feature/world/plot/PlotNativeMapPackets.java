package org.encinet.mik.feature.world.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

/** A genuine FILLED_MAP and vanilla map-data packets; never allocates a saved world map or changes inventory. */
final class PlotNativeMapPackets implements PlotNativeMapViewer.Output {
    static PlotNativeMapColors colors() {
        int[] colors = new int[256];
        for (int index = 0; index < colors.length; index++)
            colors[index] = net.minecraft.world.level.material.MapColor.getColorFromPackedId(index);
        return new PlotNativeMapColors(colors);
    }

    // Ordinary saved maps have non-negative IDs. One private texture is reused for this client's editor.
    private static final MapId PRIVATE_MAP = new MapId(-0x4d494b);
    private static final int OFFHAND_SLOT = 40;

    @Override public void show(Player player, PlotEditorMap.Scene scene, byte[] pixels) {
        var connection = ((CraftPlayer) player).getHandle().connection;
        var patch = pixels == null ? null : new MapItemSavedData.MapPatch(0, 0, 128, 128, pixels);
        List<MapDecoration> cursors = new ArrayList<>();
        var viewport = scene.viewport();
        var position = player.getLocation();
        int at = viewport.index(position.getBlockX(), position.getBlockZ());
        int rotation = Math.floorMod(Math.round(position.getYaw() * 16 / 360), 16);
        if (at >= 0) cursors.add(cursor(MapDecorationTypes.PLAYER, at, rotation, null));
        else {
            int x = Math.clamp(((long) position.getBlockX() - viewport.minimumX()) * 2 / viewport.step() - 128, -128, 127);
            int z = Math.clamp(((long) position.getBlockZ() - viewport.minimumZ()) * 2 / viewport.step() - 128, -128, 127);
            cursors.add(new MapDecoration(MapDecorationTypes.PLAYER_OFF_MAP, (byte) x, (byte) z, (byte) 0, Optional.empty()));
        }
        boolean samePoint = scene.points().first() != null && scene.points().first().equals(scene.points().second());
        addCorner(cursors, scene.points().first(), scene, samePoint ? "A/B" : "A", MapDecorationTypes.LIME_BANNER);
        if (!samePoint) addCorner(cursors, scene.points().second(), scene, "B", MapDecorationTypes.YELLOW_BANNER);
        connection.send(new ClientboundMapItemDataPacket(PRIVATE_MAP,
                (byte) Math.min(4, Integer.numberOfTrailingZeros(viewport.step())), true, cursors, patch));
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, PRIVATE_MAP);
        connection.send(new ClientboundSetPlayerInventoryPacket(OFFHAND_SLOT, map));
    }

    private static void addCorner(List<MapDecoration> cursors, PlotPosition point, PlotEditorMap.Scene scene,
                                  String name, net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> type) {
        if (point == null || Math.floorDiv(point.y(), PlotGeometry.CELL) != Math.floorDiv(scene.layer(), PlotGeometry.CELL)) return;
        int index = scene.viewport().index(point.x(), point.z());
        if (index >= 0) cursors.add(cursor(type, index, 0, name));
    }

    private static MapDecoration cursor(net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType> type,
                                         int index, int rotation, String name) {
        return new MapDecoration(type, (byte) ((index % 128) * 2 - 128), (byte) ((index / 128) * 2 - 128),
                (byte) rotation, Optional.ofNullable(name).map(net.minecraft.network.chat.Component::literal));
    }

    @Override public void restore(Player player) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundSetPlayerInventoryPacket(OFFHAND_SLOT,
                CraftItemStack.asNMSCopy(player.getInventory().getItemInOffHand())));
    }
}
