package org.encinet.mik.feature.world.buildings;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildingMapModuleTest {
    @Test
    void synchronizesAndQueriesTheCatalogWithoutAnyMapPlugin() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var plugins = mock(PluginManager.class);
        var languages = mock(LanguageService.class);
        var source = mock(BuildingCatalogSource.class);
        var player = mock(Player.class);
        var world = mock(World.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("Pl3xMap")).thenReturn(null);
        when(languages.language(player)).thenReturn(Language.ZH_CN);
        when(player.getLocation()).thenReturn(new Location(world, 2681, 79, -1094));
        when(world.getName()).thenReturn("world");
        String json = "[" + BuildingCatalogTest.ENTRY + "]";
        var download = new BuildingCatalogSource.Download(BuildingCatalog.parse(json), json);
        var fetchThread = new AtomicReference<Thread>();
        var saved = new CountDownLatch(1);
        when(source.fetch()).thenAnswer(_ -> {
            fetchThread.set(Thread.currentThread());
            return download;
        });
        doAnswer(_ -> { saved.countDown(); return null; }).when(source).save(download);
        var module = new BuildingMapModule(plugin, languages, () -> source);

        try {
            module.enable();
            assertTrue(saved.await(5, TimeUnit.SECONDS), "catalog refresh did not finish");
            assertNotSame(Thread.currentThread(), fetchThread.get());
            var prepared = module.prepareNearby(player, new NearbyBuildings.Search("prelude", 0, 8));
            NearbyBuildings result;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                result = executor.submit(() -> prepared.get().orElseThrow()).get(5, TimeUnit.SECONDS);
            }
            assertEquals("樱：序曲", result.buildings().getFirst().name());
            assertEquals(0, result.buildings().getFirst().distanceBlocks());
            verify(world).getName();
            verifyNoMoreInteractions(world);
            verify(server, never()).getScheduler();
        } finally {
            module.disable();
        }
        verify(source).close();
        assertTrue(module.prepareNearby(player, new NearbyBuildings.Search("", 0, 8)).get().isEmpty());
    }
}
