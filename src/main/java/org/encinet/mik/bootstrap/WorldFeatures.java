package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.world.elevator.IronElevatorModule;
import org.encinet.mik.feature.world.plot.PlotModule;
import org.encinet.mik.feature.world.regen.AsyncRegenModule;
import org.encinet.mik.feature.world.space.NonEuclideanSpaceModule;
import org.encinet.mik.feature.world.spawn.SpawnBeaconColorModule;
import org.encinet.mik.feature.world.vehicle.VehicleModule;

record WorldFeatures(
        PlotModule plots,
        NonEuclideanSpaceModule spaces) {

    static WorldFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, CommunityFeatures community) {
        var languageService = shared.language();
        var axiomGizmoService = shared.axiom();
        var floatingMenuService = shared.menus();
        var socialModule = community.social();

        AsyncRegenModule asyncRegenModule = new AsyncRegenModule(plugin, languageService);
        lifecycle.startManaged("async-regen", asyncRegenModule::enable, asyncRegenModule::disable);
        asyncRegenModule.registerCommands(plugin.getLifecycleManager());

        NonEuclideanSpaceModule nonEuclideanSpaceModule = new NonEuclideanSpaceModule(plugin);
        lifecycle.startManaged("non-euclidean-space", nonEuclideanSpaceModule::enable, nonEuclideanSpaceModule::disable);
        nonEuclideanSpaceModule.registerCommands(plugin.getLifecycleManager());

        PlotModule plotModule = new PlotModule(plugin, languageService, socialModule::notifyPlayer);
        lifecycle.startManaged("plot", plotModule::enable, plotModule::disable);
        plotModule.registerCommands(plugin.getLifecycleManager());

        IronElevatorModule ironElevatorModule = new IronElevatorModule(
                plugin, languageService, floatingMenuService.worldTextDisplays(),
                lifecycle.own("iron-elevator-tips", players.guidance().scope()));
        lifecycle.startManaged("iron-elevator", ironElevatorModule::enable, ironElevatorModule::disable);

        VehicleModule vehicleModule = new VehicleModule(plugin, languageService, axiomGizmoService);
        lifecycle.startManaged("vehicle", vehicleModule::enable, vehicleModule::disable);
        vehicleModule.registerCommands(plugin.getLifecycleManager());

        SpawnBeaconColorModule spawnBeaconColorModule =
                new SpawnBeaconColorModule(plugin, languageService);
        lifecycle.startManaged("spawn-beacon-color", spawnBeaconColorModule::enable, spawnBeaconColorModule::disable);
        spawnBeaconColorModule.registerCommands(plugin.getLifecycleManager());

        return new WorldFeatures(plotModule, nonEuclideanSpaceModule);
    }
}
