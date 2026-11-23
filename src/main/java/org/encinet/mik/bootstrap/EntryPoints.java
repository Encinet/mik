package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.ai.AiModule;
import org.encinet.mik.feature.community.ai.game.GameToolCatalog;
import org.encinet.mik.feature.community.ai.game.VanillaGameTools;
import org.encinet.mik.feature.player.profile.GameProfileFacts;
import org.encinet.mik.feature.player.security.WebLoginCommand;
import org.encinet.mik.feature.world.buildings.BuildingAssistantTool;
import org.encinet.mik.feature.world.elevator.ElevatorAssistantTool;
import org.encinet.mik.feature.world.plot.PlotAssistantTool;
import org.encinet.mik.integration.mikd.GameQueryWorker;
import org.encinet.mik.shell.mikd.GameQueries;
import org.encinet.mik.shell.mikd.MikdStatusCommand;
import org.encinet.mik.shell.menu.LanguageMenu;
import org.encinet.mik.shell.menu.MainMenuModule;
import org.encinet.mik.shell.menu.SettingsMenu;
import org.encinet.mik.shell.skript.MikSkriptModule;

import java.util.ArrayList;

final class EntryPoints {

    static void enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, ServerFeatures server,
            CommunityFeatures community, WorldFeatures world) {
        var languageService = shared.language();
        var afkModule = players.afk();
        var pvpModule = players.pvp();
        var teleportPreferenceModule = players.teleports();
        var clientVersions = players.clientVersions();
        var chatModule = community.chat();
        var governanceModule = community.governance();
        var announcementModule = community.announcements();
        var banModule = server.bans();
        var prisonModule = server.prison();
        var plotModule = world.plots();
        var nonEuclideanSpaceModule = world.spaces();

        // Prison starts as a server feature before the plot registry exists; wire the two together here.
        plotModule.prisonHost().addToggleGuard(prisonModule.inmateFreeGuard());
        prisonModule.bind(plotModule.prisonHost(),
                lifecycle.own("prison-panels", shared.menus().worldPanels()));

        LanguageMenu languageMenu = new LanguageMenu(languageService);
        languageMenu.registerCommands(plugin.getLifecycleManager());

        if (plugin.getServer().getPluginManager().isPluginEnabled("Skript")) {
            MikSkriptModule skriptModule = new MikSkriptModule(
                    plugin, languageService,
                    player -> clientVersions.version(player).map(version -> version.name()).orElse("unknown"),
                    afkModule, pvpModule,
                    nonEuclideanSpaceModule);
            lifecycle.startManaged("skript", skriptModule::enable, skriptModule::disable);
            skriptModule.registerCommands(plugin.getLifecycleManager());
        } else {
            plugin.getLogger().info("Skript not found; MIK Skript expressions are disabled.");
        }

        SettingsMenu settingsMenu = new SettingsMenu(languageService, languageMenu, chatModule,
                teleportPreferenceModule, pvpModule, players.customNameTags(), players.security());
        MainMenuModule mainMenuModule = new MainMenuModule(plugin, afkModule,
                pvpModule, languageService, settingsMenu, clientVersions,
                plotModule, governanceModule, announcementModule, world.vehicles(), prisonModule::openMenu);
        lifecycle.startManaged("main-menu", mainMenuModule::enable, mainMenuModule::disable);
        mainMenuModule.registerCommands(plugin.getLifecycleManager());

        var requests = shared.mikd().requests();
        var tools = new ArrayList<>(VanillaGameTools.tools());
        tools.add(BuildingAssistantTool.create(world.buildings()::prepareNearby));
        tools.add(PlotAssistantTool.create(plotModule::playerContext));
        tools.add(ElevatorAssistantTool.create(world.elevators()::status));
        var ai = new AiModule(plugin, languageService, requests, shared.geyser(),
                player -> players.security().authentication(player).getAsBoolean()
                        && banModule.manager().active(player.getUniqueId(), player.getName()).isEmpty(),
                new GameToolCatalog(tools));
        lifecycle.startManaged("ai", ai::enable, ai::disable);
        ai.registerCommands(plugin.getLifecycleManager());
        var gameQueries = new GameQueries(plugin,
                banModule.manager()::activeRecords,
                players.security()::gameAccountFacts,
                (id, name) -> banModule.manager().active(id, name).isEmpty(),
                new GameProfileFacts(afkModule),
                ai::executeTool);
        lifecycle.own("game-queries", new GameQueryWorker("mik-game-queries", requests::gameQuery, gameQueries::execute));
        lifecycle.own("account-queries", new GameQueryWorker("mik-account-queries", requests::accountQuery, gameQueries::execute));
        players.security().onCredentialsChanged(id -> {
            community.paperChat().invalidate(id);
            community.governance().credentialChanged(id);
        });
        new WebLoginCommand(plugin, languageService, players.security(), requests).register();
        new MikdStatusCommand(plugin, requests).register();
    }
}
