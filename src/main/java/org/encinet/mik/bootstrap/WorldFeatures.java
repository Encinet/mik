package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BannedLoginAdmission;
import org.encinet.mik.feature.server.prison.PrisonModule;
import org.encinet.mik.feature.world.buildings.BuildingMapModule;
import org.encinet.mik.feature.world.elevator.IronElevatorModule;
import org.encinet.mik.feature.world.heads.HeadsModule;
import org.encinet.mik.feature.world.plot.PlotModule;
import org.encinet.mik.feature.world.regen.AsyncRegenModule;
import org.encinet.mik.feature.world.riding.MobRidingModule;
import org.encinet.mik.feature.world.space.NonEuclideanSpaceModule;
import org.encinet.mik.feature.world.spawn.SpawnBeaconColorModule;
import org.encinet.mik.feature.world.vehicle.VehicleModule;

record WorldFeatures(
        PlotModule plots,
        NonEuclideanSpaceModule spaces,
        java.util.function.Consumer<org.bukkit.entity.Player> vehicles,
        BuildingMapModule buildings,
        IronElevatorModule elevators,
        HeadsModule heads,
        PrisonModule prison) {

    static WorldFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, ServerFeatures server) {
        var languageService = shared.language();
        var axiomGizmoService = shared.axiom();
        var floatingMenuService = shared.menus();

        AsyncRegenModule asyncRegenModule = new AsyncRegenModule(plugin, languageService);
        lifecycle.startManaged("async-regen", asyncRegenModule::enable, asyncRegenModule::disable);
        asyncRegenModule.registerCommands(plugin.getLifecycleManager());

        NonEuclideanSpaceModule nonEuclideanSpaceModule = new NonEuclideanSpaceModule(plugin);
        lifecycle.startManaged("non-euclidean-space", nonEuclideanSpaceModule::enable, nonEuclideanSpaceModule::disable);
        nonEuclideanSpaceModule.registerCommands(plugin.getLifecycleManager());

        PlotModule plotModule = new PlotModule(plugin, languageService);
        lifecycle.startManaged("plot", plotModule::enable, plotModule::disable);
        plotModule.registerCommands(plugin.getLifecycleManager());

        PrisonModule prison = new PrisonModule(plugin, languageService, server.bans().manager(),
                plotModule.prisons(), lifecycle.own("prison-panels", floatingMenuService.worldPanels()));
        MobRidingModule mobRidingModule = new MobRidingModule(plugin, plotModule::mayRide);
        prison.onConfinement(player -> {
            server.monitor().endForConfinement(player);
            mobRidingModule.endRide(player);
            floatingMenuService.close(player);
        });
        lifecycle.startManaged("prison", () -> {
            prison.enable();
            players.flight().confinement(prison::confined);
            plotModule.prisons().changeGuard(prison::mayPublishPlots);
            server.bans().bannedLoginAdmission(prison.admission());
            server.bans().prisonBrowser(prison::openMenu);
        }, () -> {
            server.bans().prisonBrowser(null);
            server.bans().bannedLoginAdmission(BannedLoginAdmission.NONE);
            players.flight().confinement(_ -> false);
            plotModule.prisons().changeGuard(null);
            prison.disable();
        });

        lifecycle.startManaged("mob-riding", mobRidingModule::enable, mobRidingModule::disable);

        IronElevatorModule ironElevatorModule = new IronElevatorModule(
                plugin, languageService, lifecycle.own("iron-elevator-panels", floatingMenuService.worldPanels()),
                lifecycle.own("iron-elevator-tips", players.guidance().scope()));
        ironElevatorModule.confinement(prison::confined);
        lifecycle.startManaged("iron-elevator", ironElevatorModule::enable, ironElevatorModule::disable);

        VehicleModule vehicleModule = new VehicleModule(plugin, languageService, axiomGizmoService);
        lifecycle.startManaged("vehicle", vehicleModule::enable, vehicleModule::disable);
        vehicleModule.registerCommands(plugin.getLifecycleManager());

        SpawnBeaconColorModule spawnBeaconColorModule =
                new SpawnBeaconColorModule(plugin, languageService);
        lifecycle.startManaged("spawn-beacon-color", spawnBeaconColorModule::enable, spawnBeaconColorModule::disable);
        spawnBeaconColorModule.registerCommands(plugin.getLifecycleManager());

        BuildingMapModule buildingMapModule = new BuildingMapModule(plugin, languageService);
        lifecycle.startManaged("building-map", buildingMapModule::enable, buildingMapModule::disable);

        HeadsModule heads = new HeadsModule(plugin, languageService);
        lifecycle.startManaged("heads", heads::enable, heads::disable);
        heads.registerCommands();

        return new WorldFeatures(plotModule, nonEuclideanSpaceModule, vehicleModule::openMenu,
                buildingMapModule, ironElevatorModule, heads, prison);
    }
}
