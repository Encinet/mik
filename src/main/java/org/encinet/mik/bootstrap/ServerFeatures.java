package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.access.MaintenanceModule;
import org.encinet.mik.feature.server.access.RestrictionModule;
import org.encinet.mik.feature.server.access.WhitelistModule;
import org.encinet.mik.feature.server.ban.BanModule;
import org.encinet.mik.feature.server.performance.NetworkEgressModule;
import org.encinet.mik.feature.server.performance.PerformanceModule;
import org.encinet.mik.feature.server.performance.TPSBarModule;
import org.encinet.mik.feature.server.presentation.MotdModule;
import org.encinet.mik.feature.server.presentation.ServerLinksModule;
import org.encinet.mik.feature.server.safety.BanItemGuardModule;
import org.encinet.mik.feature.server.safety.EnderPearlGuard;
import org.encinet.mik.feature.server.safety.FixBugModule;
import org.encinet.mik.feature.server.safety.OversizedEntityGuard;
import org.encinet.mik.feature.server.safety.TrampleProtectionModule;

record ServerFeatures(BanModule bans) {

    static ServerFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, boolean resumeFromCrash) {
        var languageService = shared.language();
        var afkModule = players.afk();
        var playerAddressModule = players.addresses();

        BanModule banModule = new BanModule(plugin, languageService, playerAddressModule);
        lifecycle.startManaged("ban", banModule::enable, banModule::disable);
        banModule.registerCommands(plugin.getLifecycleManager());

        PerformanceModule performanceModule = new PerformanceModule(plugin, afkModule);
        lifecycle.startManaged("performance", performanceModule::start, performanceModule::stop);
        performanceModule.registerCommands(plugin.getLifecycleManager());

        NetworkEgressModule networkEgressModule = new NetworkEgressModule(plugin);
        lifecycle.startManaged("network-egress", networkEgressModule::enable, networkEgressModule::disable);
        networkEgressModule.registerCommands(plugin.getLifecycleManager());

        RestrictionModule restrictionModule = new RestrictionModule(plugin, languageService);
        lifecycle.startListener("restriction", restrictionModule, restrictionModule::enable);

        MaintenanceModule maintenanceModule = new MaintenanceModule(plugin, languageService);
        lifecycle.startListener("maintenance", maintenanceModule, maintenanceModule::enable);
        if (resumeFromCrash) {
            maintenanceModule.setMaintenanceEnabled(true);
            plugin.getLogger().warning("Detected unclean shutdown; maintenance mode enabled automatically.");
        }
        maintenanceModule.registerCommands(plugin.getLifecycleManager());

        TPSBarModule tpsBarModule = new TPSBarModule(plugin, languageService,
                lifecycle.own("tps-bar-tips", players.guidance().scope()));
        lifecycle.startManaged("tps-bar", tpsBarModule::start, tpsBarModule::stop);
        tpsBarModule.registerCommands(plugin.getLifecycleManager());

        BanItemGuardModule banItemGuardModule = new BanItemGuardModule(plugin);
        lifecycle.startManaged("ban-item-guard", banItemGuardModule::enable, banItemGuardModule::disable);

        FixBugModule fixBugModule = new FixBugModule(plugin);
        lifecycle.startListener("bug-guard", fixBugModule, fixBugModule::enable);

        EnderPearlGuard enderPearlGuard = new EnderPearlGuard(plugin);
        lifecycle.startListener("ender-pearl-guard", enderPearlGuard, enderPearlGuard::enable);

        OversizedEntityGuard oversizedEntityGuard = new OversizedEntityGuard(plugin);
        lifecycle.startManaged("oversized-entity-guard", oversizedEntityGuard::enable,
                oversizedEntityGuard::disable);

        TrampleProtectionModule trampleProtectionModule = new TrampleProtectionModule(plugin);
        lifecycle.startManaged("trample-protection", trampleProtectionModule::enable, trampleProtectionModule::disable);

        WhitelistModule whitelistModule = new WhitelistModule(plugin, languageService);
        lifecycle.startListener("whitelist", whitelistModule, whitelistModule::enable);
        whitelistModule.registerCommands(plugin.getLifecycleManager());

        MotdModule motdModule = new MotdModule(plugin, afkModule, languageService, playerAddressModule);
        lifecycle.startManaged("motd", motdModule::enable, motdModule::disable);

        ServerLinksModule serverLinksModule = new ServerLinksModule(languageService);
        lifecycle.startListener("server-links", serverLinksModule,
                () -> serverLinksModule.register(plugin));

        return new ServerFeatures(banModule);
    }
}
