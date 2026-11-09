package org.encinet.mik.feature.world.buildings;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.registry.IconRegistry;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.renderer.task.UpdateMarkerData;
import net.pl3x.map.core.world.World;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BuildingMapPublisherTest {
    @Test
    void publishesStablePixelSizedPoisAndReconcilesMovesDeletionsAndReloads() {
        Pl3xMap map = mock(Pl3xMap.class);
        IconRegistry icons = mock(IconRegistry.class);
        WorldRegistry worlds = mock(WorldRegistry.class);
        World overworld = world("world");
        World other = world("other");
        when(map.isEnabled()).thenReturn(true);
        when(map.getIconRegistry()).thenReturn(icons);
        when(map.getWorldRegistry()).thenReturn(worlds);
        when(icons.has(any())).thenReturn(true);
        when(icons.get(any())).thenReturn(mock(IconImage.class));
        when(worlds.values()).thenReturn(List.of(overworld, other));
        var languages = mock(LanguageService.class);
        when(languages.t(any(Language.class), any(), any(Object[].class))).thenReturn("Buildings");
        try (var api = mockStatic(Pl3xMap.class)) {
            api.when(Pl3xMap::api).thenReturn(map);
            try (var publisher = new BuildingMapPublisher(languages)) {
                publisher.prepareIcon();
                var catalog = BuildingCatalog.parse("[" + BuildingCatalogTest.ENTRY + "]");
                publisher.publish(catalog);
                Layer layer = overworld.getLayerRegistry().get(BuildingMapPublisher.LAYER_ID);
                Icon marker = (Icon) layer.getMarkers().iterator().next();
                assertEquals("mik-building-" + BuildingCatalogTest.ID, marker.getKey());
                assertEquals(2681, marker.getPoint().x());
                assertEquals(-1094, marker.getPoint().z());
                assertEquals(32, marker.getSize().x());
                assertEquals(44, marker.getSize().x() + 2 * BuildingMapStyle.ICON_PADDING_X);
                assertEquals(48, marker.getSize().z() + 2 * BuildingMapStyle.ICON_PADDING_Y);
                assertEquals(22, marker.getAnchor().x());
                assertEquals(44, marker.getAnchor().z());
                assertEquals(550, layer.getZIndex());
                assertFalse(layer.isDefaultHidden());
                assertTrue(layer.shouldShowControls());
                assertTrue(marker.getOptions().toJson().toString().contains("/buildings/" + BuildingCatalogTest.ID));
                publisher.publish(catalog);
                assertSame(marker, layer.getMarkers().iterator().next(), "Unchanged snapshots preserve markers");
                assertEquals(0, other.getLayerRegistry().size());

                var moved = BuildingCatalog.parse("[" + BuildingCatalogTest.ENTRY
                        .replace("\"world\":\"world\"", "\"world\":\"other\"").replace("2681", "42") + "]");
                publisher.publish(moved);
                assertEquals(0, overworld.getLayerRegistry().size());
                Layer movedLayer = other.getLayerRegistry().get(BuildingMapPublisher.LAYER_ID);
                assertEquals(42, ((Icon) movedLayer.getMarkers().iterator().next()).getPoint().x());

                World reloaded = world("other");
                when(worlds.values()).thenReturn(List.of(overworld, reloaded));
                publisher.publish(moved);
                assertNotSame(movedLayer, reloaded.getLayerRegistry().get(BuildingMapPublisher.LAYER_ID));
                publisher.publish(BuildingCatalog.parse("[]"));
                assertEquals(0, reloaded.getLayerRegistry().size());
            }
        }
    }

    @Test
    void escapesCatalogHtmlAndUsesOnlyTheTrustedStableDetailUrls() {
        String json = "[" + BuildingCatalogTest.ENTRY
                .replace("Sakura: Prelude", "<script>alert(1)</script>")
                .replace("Original author", "<img src=x onerror=alert(1)>") + "]";
        LanguageService languages = mock(LanguageService.class);
        when(languages.t(any(Language.class), any(), any(Object[].class))).thenAnswer(call -> {
            Object[] args = (Object[]) call.getRawArguments()[2];
            return args.length == 0 ? "View building" : "Author: " + args[0];
        });
        var entry = BuildingCatalog.parse(json).entries().getFirst();
        var renderer = new BuildingMapPopup(languages);
        String popup = renderer.render(entry);
        assertFalse(popup.contains("<script>"));
        assertFalse(popup.contains("<img src=x"));
        assertTrue(popup.contains("&lt;script&gt;"));
        assertTrue(popup.contains("/buildings/" + BuildingCatalogTest.ID));
        assertTrue(popup.contains("noopener noreferrer"));
        String tooltip = renderer.tooltip(entry);
        assertFalse(tooltip.contains("<script>"));
        assertTrue(tooltip.contains("&lt;script&gt;"));
        assertTrue(tooltip.contains("class=\"mik-building-tooltip\""));
    }

    @Test
    void cardKeepsSemanticLabelsAndOmitsMissingCovers() {
        var languages = mock(LanguageService.class);
        when(languages.t(any(Language.class), any(), any(Object[].class))).thenReturn("View building");
        var renderer = new BuildingMapPopup(languages);
        var entry = BuildingCatalog.parse("[" + BuildingCatalogTest.ENTRY + "]").entries().getFirst();
        var card = org.jsoup.Jsoup.parseBodyFragment(renderer.render(entry));
        assertEquals("zh-CN", card.selectFirst("article").attr("lang"));
        assertEquals(entry.nameZh(), card.selectFirst("img").attr("alt"));
        assertEquals("eager", card.selectFirst("img").attr("loading"));
        assertEquals("en", card.selectFirst("p[lang]").attr("lang"));
        assertEquals(1, card.select("a.mik-building-card-link").size());
        assertEquals("https://mcmik.top/buildings/" + entry.id(), card.selectFirst("a").attr("href"));
        assertEquals("_blank", card.selectFirst("a").attr("target"));
        var empty = BuildingCatalog.parse("[" + BuildingCatalogTest.ENTRY
                .replace("[\"https://images.example/cover.webp\"]", "[]") + "]").entries().getFirst();
        assertTrue(org.jsoup.Jsoup.parseBodyFragment(renderer.render(empty)).select("img").isEmpty());
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.isEnabled()).thenReturn(true);
        when(world.getLayerRegistry()).thenReturn(new Registry<>());
        when(world.getMarkerTask()).thenReturn(mock(UpdateMarkerData.class));
        return world;
    }
}
