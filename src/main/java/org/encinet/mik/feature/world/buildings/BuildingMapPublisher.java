package org.encinet.mik.feature.world.buildings;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.Vector;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.registry.IconRegistry;
import net.pl3x.map.core.world.World;
import org.encinet.mik.shared.i18n.LanguageService;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Optional Pl3xMap types stay inside this adapter. Only prepareIcon performs file I/O. */
final class BuildingMapPublisher implements AutoCloseable {
    static final String LAYER_ID = "mik-buildings";
    private static final String ICON_ID = "mik-building-pin";
    private final Pl3xMap map;
    private final BuildingMapPopup popup;
    private final Map<World, BuildingLayer> layers = new IdentityHashMap<>();
    private volatile IconRegistry readyIcons;

    BuildingMapPublisher(LanguageService languages) {
        map = Pl3xMap.api();
        if (!map.isEnabled()) throw new IllegalStateException("Pl3xMap API is not enabled");
        popup = new BuildingMapPopup(languages);
    }

    void prepareIcon() {
        if (!map.isEnabled()) return;
        IconRegistry icons = map.getIconRegistry();
        if (icons == readyIcons && icons.has(ICON_ID)) return;
        // IconRegistry writes the PNG into Pl3xMap's public directory; run on the owned worker.
        icons.register(new IconImage(ICON_ID, pin(), "png"));
        readyIcons = icons;
    }

    void publish(BuildingCatalog catalog) {
        if (!map.isEnabled() || map.getIconRegistry() != readyIcons || !readyIcons.has(ICON_ID)) return;
        var worlds = List.copyOf(map.getWorldRegistry().values());
        layers.keySet().removeIf(world -> worlds.stream().noneMatch(current -> current == world));
        for (World world : worlds) {
            if (!world.isEnabled()) continue;
            List<BuildingCatalog.Entry> entries = catalog.entries().stream()
                    .filter(entry -> entry.world().equals(world.getName())).toList();
            BuildingLayer layer = layers.get(world);
            if (entries.isEmpty()) {
                if (layer != null) {
                    remove(world, layer);
                    layers.remove(world);
                }
                continue;
            }
            if (layer == null || world.getLayerRegistry().get(LAYER_ID) != layer) {
                layer = new BuildingLayer(popup.label());
                layer.setShowControls(true).setDefaultHidden(false).setPriority(30).setZIndex(550)
                        .setUpdateInterval(10).setLiveUpdate(true).setCss(BuildingMapStyle.CSS);
                layer.replace(entries, popup);
                world.getLayerRegistry().register(layer);
                layers.put(world, layer);
                world.getMarkerTask().run();
            } else if (layer.replace(entries, popup)) {
                // Pl3xMap dispatches serialization and disk writes to its own executor.
                world.getMarkerTask().run();
            }
        }
    }

    @Override
    public void close() {
        layers.forEach(BuildingMapPublisher::remove);
        layers.clear();
    }

    private static void remove(World world, BuildingLayer layer) {
        if (world.getLayerRegistry().get(LAYER_ID) == layer) {
            world.getLayerRegistry().unregister(LAYER_ID);
            if (world.isEnabled()) world.getMarkerTask().run();
        }
    }

    private static BufferedImage pin() {
        BufferedImage image = new BufferedImage(64, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D shape = new Path2D.Double();
            shape.moveTo(32, 77);
            shape.curveTo(26, 67, 5, 46, 5, 30);
            shape.curveTo(5, -5, 59, -5, 59, 30);
            shape.curveTo(59, 46, 38, 67, 32, 77);
            shape.closePath();
            graphics.setColor(new Color(0xBCD3B5));
            graphics.fill(shape);
            graphics.setColor(new Color(0x27382C));
            graphics.setStroke(new BasicStroke(3));
            graphics.draw(shape);
            graphics.fillRoundRect(20, 18, 24, 29, 3, 3);
            graphics.setColor(new Color(0xF5F8F0));
            for (int x : new int[]{25, 35}) {
                for (int y : new int[]{23, 32}) graphics.fillRect(x, y, 4, 4);
            }
            graphics.fillRect(29, 41, 6, 7);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Pl3xMap's serializer reads this immutable collection from its own worker. */
    private static final class BuildingLayer extends Layer {
        private List<BuildingCatalog.Entry> entries = List.of();
        private volatile Collection<Marker<?>> markers = List.of();

        BuildingLayer(String label) {
            super(LAYER_ID, () -> label);
        }

        boolean replace(List<BuildingCatalog.Entry> next, BuildingMapPopup popup) {
            if (entries.equals(next)) return false;
            Collection<Marker<?>> prepared = next.stream().<Marker<?>>map(entry -> Marker.icon(
                    "mik-building-" + entry.id(), Point.of(entry.x(), entry.z()), ICON_ID, BuildingMapStyle.ICON_WIDTH, BuildingMapStyle.ICON_HEIGHT)
                    .setAnchor(Vector.of(BuildingMapStyle.ICON_WIDTH / 2.0 + BuildingMapStyle.ICON_PADDING_X,
                            BuildingMapStyle.ICON_HEIGHT + BuildingMapStyle.ICON_PADDING_Y)).setOptions(Options.builder()
                            .tooltipContent(popup.tooltip(entry)).tooltipDirection(Tooltip.Direction.TOP)
                            .tooltipOffset(Point.of(0, -BuildingMapStyle.ICON_HEIGHT))
                            .popupContent(popup.render(entry)).popupMaxWidth(BuildingMapStyle.POPUP_WIDTH).popupMinWidth(180)
                            .popupMaxHeight(BuildingMapStyle.POPUP_MAX_HEIGHT).build())).toList();
            markers = prepared;
            entries = next;
            return true;
        }

        @Override
        public Collection<Marker<?>> getMarkers() {
            return markers;
        }
    }
}
