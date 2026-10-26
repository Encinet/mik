package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shell.api.ApiModule;
import org.encinet.mik.shell.api.CommunityBoardView;
import org.encinet.mik.shell.menu.LanguageMenu;
import org.encinet.mik.shell.menu.MainMenuModule;
import org.encinet.mik.shell.skript.MikSkriptModule;

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
        var plotModule = world.plots();
        var nonEuclideanSpaceModule = world.spaces();

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

        MainMenuModule mainMenuModule = new MainMenuModule(plugin, afkModule, chatModule, teleportPreferenceModule,
                pvpModule, languageService, languageMenu, clientVersions,
                plotModule, governanceModule);
        lifecycle.startManaged("main-menu", mainMenuModule::enable, mainMenuModule::disable);
        mainMenuModule.registerCommands(plugin.getLifecycleManager());

        // Announcement data is exposed by the API module.
        ApiModule apiModule = new ApiModule(plugin, languageService,
                banModule.manager()::activeRecords,
                announcementModule::getAnnouncementsJsonBytes,
                new CommunityBoardView() {
                    @Override
                    public String listJson() throws java.sql.SQLException {
                        return plotModule.publicBoardListJson();
                    }

                    @Override
                    public String detailJson(String id) throws java.sql.SQLException {
                        return plotModule.publicBoardDetailJson(id);
                    }
                });
        lifecycle.startManaged("http-api", () -> apiModule.start(35353), apiModule::stop);
        apiModule.registerCommands(plugin.getLifecycleManager());
    }
}
