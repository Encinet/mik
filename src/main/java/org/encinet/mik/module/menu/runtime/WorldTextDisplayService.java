package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDecoration;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.integration.axiom.AxiomGizmoService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Small, passive world labels. Each viewer receives their own localized virtual entity. */
public final class WorldTextDisplayService {
    // Minecraft text displays render one font pixel at 0.025 world units before
    // the entity scale. Keep this private world label inside one block face.
    public static final int LINE_WIDTH_PIXELS = 96;
    public static final float TEXT_SCALE = 0.36F;
    public static final double PIXEL_WORLD_SIZE = 0.025 * TEXT_SCALE;
    public static final double PANEL_WIDTH = (LINE_WIDTH_PIXELS + 4) * PIXEL_WORLD_SIZE;
    public static final double LINE_HEIGHT = 10 * PIXEL_WORLD_SIZE;

    private final VirtualMenuEntityRenderer renderer = new VirtualMenuEntityRenderer();
    private final AxiomGizmoService.Scope gizmos;
    private final Map<UUID, Map<String, Display>> viewers = new HashMap<>();
    private boolean disabled;

    WorldTextDisplayService(AxiomGizmoService.Scope gizmos) {
        this.gizmos = Objects.requireNonNull(gizmos, "gizmos");
    }

    public void show(Player viewer, String key, Location location, float yaw,
                     Component text, int background) {
        if (disabled) return;
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(text, "text");
        if (!viewer.isOnline() || !viewer.getWorld().equals(location.getWorld())) {
            clear(viewer, key);
            return;
        }
        Map<String, Display> labels = viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        Display display = labels.get(key);
        if (display != null && !display.location.getWorld().equals(location.getWorld())) {
            clear(viewer, key);
            labels = viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
            display = null;
        }
        if (display == null) {
            int entityId = VirtualMenuEntityRenderer.allocateId(viewer);
            UUID entityUuid = UUID.randomUUID();
            display = new Display(entityId, entityUuid, location.clone(), yaw, text, background);
            labels.put(key, display);
            renderer.spawn(viewer, entityId, entityUuid,
                    VirtualMenuEntityRenderer.Kind.TEXT, location, yaw);
            gizmos.synchronize(viewer, entityUuid, Set.of(entityUuid));
            updateText(viewer, display);
            return;
        }
        if (!display.location.equals(location) || display.yaw != yaw) {
            display.location = location.clone();
            display.yaw = yaw;
            renderer.teleport(viewer, display.entityId, location, yaw);
        }
        if (!display.text.equals(text) || display.background != background) {
            display.text = text;
            display.background = background;
            updateText(viewer, display);
        }
    }

    private void updateText(Player viewer, Display display) {
        renderer.text(viewer, display.entityId, display.text, display.background,
                1.0F, 0.8F, LINE_WIDTH_PIXELS, TEXT_SCALE,
                FloatingMenuDecoration.Alignment.CENTER, false);
    }

    public void clear(Player viewer, String key) {
        if (disabled) return;
        Map<String, Display> labels = viewers.get(viewer.getUniqueId());
        if (labels == null) return;
        Display display = labels.remove(key);
        if (display != null) {
            if (viewer.isOnline()) {
                renderer.destroy(viewer, display.entityId);
                gizmos.remove(viewer, display.entityUuid);
            } else {
                gizmos.forget(viewer.getUniqueId(), display.entityUuid);
            }
        }
        if (labels.isEmpty()) viewers.remove(viewer.getUniqueId());
    }

    void forget(Player viewer) {
        Map<String, Display> labels = viewers.remove(viewer.getUniqueId());
        if (labels == null) return;
        for (Display display : labels.values()) {
            if (viewer.isOnline()) {
                renderer.destroy(viewer, display.entityId);
                gizmos.remove(viewer, display.entityUuid);
            } else {
                gizmos.forget(viewer.getUniqueId(), display.entityUuid);
            }
        }
    }

    void disable() {
        for (UUID viewerId : Set.copyOf(viewers.keySet())) {
            Player viewer = org.bukkit.Bukkit.getPlayer(viewerId);
            if (viewer != null) forget(viewer);
            else viewers.remove(viewerId);
        }
        gizmos.close();
        disabled = true;
    }

    private static final class Display {
        private final int entityId;
        private final UUID entityUuid;
        private Location location;
        private float yaw;
        private Component text;
        private int background;

        private Display(int entityId, UUID entityUuid, Location location,
                        float yaw, Component text, int background) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.location = location;
            this.yaw = yaw;
            this.text = text;
            this.background = background;
        }
    }
}
