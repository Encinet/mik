package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.UiRotation;
import java.util.UUID;

/** Detached backend data only: no scene, UI node, callback, input or feature resources. */
record MenuVisualSnapshot(int entityId, UUID uuid, Location location, Paint paint) {
    MenuVisualSnapshot { location = location.clone(); }
    @Override public Location location() { return location.clone(); }

    sealed interface Paint permits Text, Surface, Model, Volume {
        void render(VirtualMenuEntityRenderer renderer, Player viewer, int id, double remaining);
    }

    record Text(Component label, int background, float width, float height, int lineWidth,
                float scale, FloatingMenuDecoration.Alignment alignment, boolean seeThrough,
                double reveal, double alpha, UiRotation rotation) implements Paint {
        public void render(VirtualMenuEntityRenderer renderer, Player viewer, int id, double remaining) {
            renderer.text(viewer,id,label,background,width,height,lineWidth,scale,alignment,seeThrough,reveal * remaining,alpha,rotation);
        }
    }
    record Surface(int background, float width, float height, double reveal, UiRotation rotation) implements Paint {
        public void render(VirtualMenuEntityRenderer renderer, Player viewer, int id, double remaining) {
            renderer.surface(viewer,id,background,width,height,reveal * remaining,rotation);
        }
    }
    record Model(ItemStack item, boolean block, float scale, UiRotation rotation) implements Paint {
        Model { item = item.clone(); }
        @Override public ItemStack item() { return item.clone(); }
        public void render(VirtualMenuEntityRenderer renderer, Player viewer, int id, double remaining) {
            renderer.visual(viewer,id,item,block,(float) (scale * remaining),rotation);
        }
    }
    record Volume(BlockData block, float width, float height, float depth, UiRotation rotation) implements Paint {
        Volume { block = block.clone(); }
        @Override public BlockData block() { return block.clone(); }
        public void render(VirtualMenuEntityRenderer renderer, Player viewer, int id, double remaining) {
            renderer.volume(viewer,id,block,(float) (width * remaining),(float) (height * remaining),(float) (depth * remaining),rotation);
        }
    }
}
