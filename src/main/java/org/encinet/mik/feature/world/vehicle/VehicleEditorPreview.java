package org.encinet.mik.feature.world.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Main-thread owner of private preview meshes, labels and the corresponding published pick targets. */
final class VehicleEditorPreview {
    record Preview(Location origin, double scale, List<Display> displays, List<VehicleDefinition.Part> parts, long expires,
                   Map<String, TextDisplay> labels, List<VehicleEditorScene.Handle> handles) {
        Preview { origin = origin.clone(); displays = List.copyOf(displays); parts = List.copyOf(parts); handles = List.copyOf(handles); }
        @Override public Location origin() { return origin.clone(); }
    }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final NamespacedKey previewKey;
    private final Runnable changed;
    private final Map<UUID, Preview> previews = new HashMap<>();

    VehicleEditorPreview(JavaPlugin plugin, LanguageService language, Runnable changed) {
        this.plugin = plugin;
        this.language = language;
        this.changed = changed;
        previewKey = new NamespacedKey(plugin, "vehicle_model_preview");
    }

    Preview get(UUID viewer) { return previews.get(viewer); }
    Set<UUID> viewers() { return Set.copyOf(previews.keySet()); }
    boolean isPreview(Entity entity) { return entity.getPersistentDataContainer().has(previewKey); }
    Set<UUID> ids(UUID viewer) {
        Preview preview = get(viewer);
        if (preview == null) return Set.of();
        var ids = new java.util.HashSet<UUID>();
        preview.displays().forEach(entity -> ids.add(entity.getUniqueId()));
        preview.labels().values().forEach(entity -> ids.add(entity.getUniqueId()));
        return Set.copyOf(ids);
    }

    void show(Player player, VehicleModelDraft draft, Location origin) { show(player, draft, origin, 1); }
    void show(Player player, VehicleModelDraft draft, Location origin, double scale) {
        List<VehicleDefinition.Part> parts = draft.parts();
        if (previews.entrySet().stream().filter(entry -> !entry.getKey().equals(player.getUniqueId()))
                .mapToInt(entry -> entry.getValue().displays().size()).sum() + parts.size() > 512)
            throw new IllegalArgumentException("Preview display quota reached");
        if (!origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) throw new IllegalArgumentException("Preview chunk unavailable");
        Preview existing = get(player.getUniqueId());
        Matrix4f rotation = new Matrix4f().rotate(new Quaternionf().set(VehicleModel.orientation(origin.getYaw()))).scale((float) scale);
        if (existing != null && existing.origin().equals(origin) && existing.parts().size() == parts.size()
                && existing.displays().stream().allMatch(Entity::isValid)
                && java.util.stream.IntStream.range(0, parts.size()).allMatch(index -> sameAppearance(existing.parts().get(index), parts.get(index)))) {
            for (int index = 0; index < parts.size(); index++) {
                existing.displays().get(index).setTransformationMatrix(new Matrix4f(rotation).mul(parts.get(index).transform()));
                existing.displays().get(index).setInterpolationDelay(0);
            }
            previews.put(player.getUniqueId(), new Preview(origin.clone(), scale, existing.displays(), parts, expiry(), existing.labels(), List.of()));
            return;
        }
        var displays = new ArrayList<Display>();
        try {
            Location anchor = origin.clone(); anchor.setYaw(0); anchor.setPitch(0);
            for (var part : parts) {
                Display display = VehicleEntityGroup.createDisplay(anchor, part, entity -> {
                    privateDisplay(entity, player);
                    entity.setTransformationMatrix(new Matrix4f(rotation).mul(part.transform()));
                });
                displays.add(display);
                player.showEntity(plugin, display);
            }
            previews.put(player.getUniqueId(), new Preview(origin.clone(), scale, displays, parts, expiry(), new HashMap<>(), List.of()));
            if (existing != null) remove(existing);
            changed.run();
        } catch (RuntimeException exception) { displays.forEach(Entity::remove); throw exception; }
    }

    void scene(Player player, VehicleEditorScene scene) {
        Preview preview = get(player.getUniqueId());
        if (preview == null) return;
        var active = new java.util.HashSet<String>();
        var published = new ArrayList<VehicleEditorScene.Handle>();
        var visible = FloatingMenus.worldOverlayVisibility(player);
        boolean changedLabels = false;
        int selectedPart = scene.handles().stream().filter(handle -> handle.key().equals("selected"))
                .map(handle -> ((VehicleEditorScene.Select) handle.action()).route())
                .filter(route -> route instanceof VehicleEditorRoute.ObjectView).mapToInt(route -> ((VehicleEditorRoute.ObjectView) route).index()).findFirst().orElse(-1);
        for (int index = 0; index < preview.displays().size(); index++) {
            Display display = preview.displays().get(index);
            if (display.isGlowing() != (index == selectedPart)) display.setGlowing(index == selectedPart);
        }
        int totalLabels = previews.values().stream().mapToInt(value -> value.labels().size()).sum();
        for (var handle : scene.handles()) {
            Location point = VehicleEditorGeometry.world(preview.origin(), VehicleEditorScene.labelPoint(handle, preview.scale()).multiply(preview.scale()));
            if (handle.point().multiply(preview.scale()).length() > 31 || !point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)
                    || !visible.test(point)) continue;
            TextDisplay label = preview.labels().get(handle.key());
            if (label == null || !label.isValid()) {
                if (totalLabels >= 512) continue;
                label = point.getWorld().spawn(point, TextDisplay.class, entity -> {
                    privateDisplay(entity, player);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setBrightness(new Display.Brightness(15, 15));
                    entity.setBackgroundColor(Color.fromARGB(0xA0000000));
                    entity.setDefaultBackground(false);
                    entity.setSeeThrough(true);
                    entity.setShadowed(true);
                    entity.setLineWidth(140);
                    entity.setTransformationMatrix(new Matrix4f().scale(0.38f));
                });
                preview.labels().put(handle.key(), label);
                player.showEntity(plugin, label);
                totalLabels++;
                changedLabels = true;
            } else if (!label.getLocation().equals(point)) label.teleport(point);
            var text = language.text(player, handle.label(), color(handle.ink()), handle.arguments().toArray());
            if (!text.equals(label.text())) label.text(text);
            active.add(handle.key());
            published.add(handle);
            marker(player, preview.origin(), handle.point().multiply(preview.scale()), handle.ink());
        }
        for (String key : List.copyOf(preview.labels().keySet())) if (!active.contains(key)) {
            preview.labels().remove(key).remove(); changedLabels = true;
        }
        // Bound the particle work even for sixteen very large collision boxes.
        int budget = 320;
        for (var line : scene.lines()) {
            int samples = Math.max(1, Math.min(12, (int) Math.ceil(line.to().subtract(line.from()).length() / 0.3)));
            for (int sample = 0; sample <= samples && budget > 0; sample++, budget--) {
                VehicleVector point = line.from().add(line.to().subtract(line.from()).multiply(sample / (double) samples));
                if (visible.test(VehicleEditorGeometry.world(preview.origin(), point.multiply(preview.scale())))) marker(player, preview.origin(), point.multiply(preview.scale()), line.ink());
            }
            if (budget == 0) break;
        }
        previews.put(player.getUniqueId(), new Preview(preview.origin(), preview.scale(), preview.displays(), preview.parts(), preview.expires(), preview.labels(), published));
        if (changedLabels) changed.run();
    }

    void clear(UUID viewer) {
        Preview preview = previews.remove(viewer);
        if (preview != null) { remove(preview); changed.run(); }
    }
    void close() { for (UUID viewer : viewers()) clear(viewer); }

    private void privateDisplay(Display entity, Player player) {
        entity.setVisibleByDefault(false);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(previewKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }
    private static void remove(Preview preview) {
        preview.displays().forEach(Entity::remove);
        preview.labels().values().forEach(Entity::remove);
    }
    private static long expiry() { return System.nanoTime() + 300_000_000_000L; }
    private static boolean sameAppearance(VehicleDefinition.Part first, VehicleDefinition.Part second) {
        return first.kind() == second.kind() && first.payload().equals(second.payload()) && first.billboard().equals(second.billboard())
                && first.itemTransform().equals(second.itemTransform()) && first.alignment().equals(second.alignment())
                && first.blockLight() == second.blockLight() && first.skyLight() == second.skyLight() && first.background() == second.background()
                && first.lineWidth() == second.lineWidth() && first.textOpacity() == second.textOpacity()
                && first.shadow() == second.shadow() && first.seeThrough() == second.seeThrough();
    }
    static TextColor color(VehicleEditorScene.Ink ink) {
        return switch (ink) {
            case RED -> NamedTextColor.RED;
            case GREEN -> NamedTextColor.GREEN;
            case BLUE -> TextColor.color(85, 170, 255);
            case ORANGE -> NamedTextColor.GOLD;
            case YELLOW -> NamedTextColor.YELLOW;
            case PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case WHITE -> NamedTextColor.WHITE;
        };
    }
    static void marker(Player player, Location origin, VehicleVector point, VehicleEditorScene.Ink ink) {
        if (point.length() > 32) return;
        Color color = Color.fromRGB(color(ink).value());
        player.spawnParticle(Particle.DUST, VehicleEditorGeometry.world(origin, point), 1, 0, 0, 0, 0, new Particle.DustOptions(color, 0.8f));
    }
}
