package org.encinet.mik;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.encinet.mik.module.governance.GovernanceModule;
import org.encinet.mik.module.ban.BanModule;
import org.encinet.mik.module.access.MaintenanceModule;
import org.encinet.mik.module.access.RestrictionModule;
import org.encinet.mik.module.access.WhitelistModule;
import org.encinet.mik.module.afk.AfkModule;
import org.encinet.mik.module.ai.AiModule;
import org.encinet.mik.module.api.ApiModule;
import org.encinet.mik.module.api.CommunityBoardView;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.chat.ChatModule;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.commands.SimpleFeaturesModule;
import org.encinet.mik.module.communication.AnnouncementModule;
import org.encinet.mik.module.communication.TipModule;
import org.encinet.mik.module.event.FifthAnniversaryEventModule;
import org.encinet.mik.module.elevator.IronElevatorModule;
import org.encinet.mik.module.vehicle.VehicleModule;
import org.encinet.mik.module.geyser.BedrockPlayerBadge;
import org.encinet.mik.module.geyser.GeyserService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.identity.IdentityBindingModule;
import org.encinet.mik.module.menu.runtime.FloatingMenuService;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.music.MusicModule;
import org.encinet.mik.module.performance.NetworkEgressModule;
import org.encinet.mik.module.performance.PerformanceModule;
import org.encinet.mik.module.performance.TPSBarModule;
import org.encinet.mik.module.player.BackModule;
import org.encinet.mik.module.player.ClientVersionReminderModule;
import org.encinet.mik.module.player.FlightModule;
import org.encinet.mik.module.player.GameModeSwitchModule;
import org.encinet.mik.module.player.HomeModule;
import org.encinet.mik.module.player.InvisibilityNotifyModule;
import org.encinet.mik.module.player.NameTagModule;
import org.encinet.mik.module.player.PlayerBoundaryModule;
import org.encinet.mik.module.player.address.PlayerAddressModule;
import org.encinet.mik.module.player.address.PlayerAssociationNotifier;
import org.encinet.mik.module.player.PlayerPresenceModule;
import org.encinet.mik.shell.MainMenuModule;
import org.encinet.mik.shell.LanguageMenu;
import org.encinet.mik.module.plot.PlotModule;
import org.encinet.mik.module.player.WelcomeModule;
import org.encinet.mik.module.player.identity.PlayerIdentityRenderer;
import org.encinet.mik.module.player.identity.PlayerNameTagRenderer;
import org.encinet.mik.module.pvp.PvpModule;
import org.encinet.mik.module.player.TabListModule;
import org.encinet.mik.module.player.TeleportPreferenceModule;
import org.encinet.mik.module.presentation.BrandingModule;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.module.presentation.MotdModule;
import org.encinet.mik.module.presentation.ServerLinksModule;
import org.encinet.mik.module.presentation.SpawnBeaconColorModule;
import org.encinet.mik.module.social.SocialModule;
import org.encinet.mik.module.social.game.BukkitSocialChatGateway;
import org.encinet.mik.module.safety.BanItemGuardModule;
import org.encinet.mik.module.safety.EnderPearlGuard;
import org.encinet.mik.module.safety.FixBugModule;
import org.encinet.mik.module.safety.GrieferModule;
import org.encinet.mik.module.safety.OversizedEntityGuard;
import org.encinet.mik.module.safety.TrampleProtectionModule;
import org.encinet.mik.module.skript.MikSkriptModule;
import org.encinet.mik.module.space.NonEuclideanSpaceModule;
import org.encinet.mik.module.world.regen.AsyncRegenModule;
import org.encinet.mik.util.ShutdownSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

@su.plo.voice.api.addon.annotation.Addon(
        id = "mik-music",
        name = "MIK Music",
        scope = su.plo.voice.api.addon.AddonLoaderScope.SERVER,
        version = "1.0",
        authors = {"Noctiro", "Aeolic"}
)
public final class Mik extends JavaPlugin {

    private static final String MAINTENANCE_CRASH_MARKER = "maintenance-unclean-shutdown.marker";

    private record ShutdownAction(String name, Runnable action) {
    }

    private final Deque<ShutdownAction> shutdownActions = new ArrayDeque<>();
    private boolean enableCompleted;

    private BrandingModule brandingModule;
    private Path maintenanceCrashMarker;

    @su.plo.voice.api.addon.InjectPlasmoVoice
    private su.plo.voice.api.server.PlasmoVoiceServer voiceServer;

    @Override
    public void onLoad() {
        brandingModule = new BrandingModule();
        su.plo.voice.api.server.PlasmoVoiceServer.getAddonsLoader().load(this);
    }

    @Override
    public void onEnable() {
        try {
            enableModules();
            enableCompleted = true;
        } catch (RuntimeException | LinkageError startupError) {
            stopManagedModules(startupError);
            try {
                getServer().getScheduler().cancelTasks(this);
            } catch (RuntimeException | LinkageError cleanupError) {
                startupError.addSuppressed(cleanupError);
            }
            throw startupError;
        }
    }

    private void enableModules() {
        startManaged("branding", brandingModule::enable, brandingModule::disable);

        maintenanceCrashMarker = getDataFolder().toPath().resolve(MAINTENANCE_CRASH_MARKER);
        boolean resumeFromCrash = false;
        try {
            Files.createDirectories(getDataFolder().toPath());
            resumeFromCrash = Files.exists(maintenanceCrashMarker);
            Files.writeString(maintenanceCrashMarker,
                    "running",
                    java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Unable to initialize maintenance crash marker: " + e.getMessage());
        }

        AxiomGizmoService axiomGizmoService = new AxiomGizmoService(this);
        startManaged("axiom", axiomGizmoService::enable, axiomGizmoService::disable);

        GeyserService geyserService = GeyserService.create(this);

        LanguageService languageService = new LanguageService(this);
        startManaged("language", languageService::enable, languageService::disable);

        AiModule aiModule = new AiModule(this, languageService);
        startManaged("ai", aiModule::enable, aiModule::disable);
        aiModule.registerCommands(this.getLifecycleManager());

        BedrockPlayerBadge bedrockPlayerBadge =
                new BedrockPlayerBadge(geyserService, languageService);
        PlayerNameTagRenderer playerNameTags = new PlayerNameTagRenderer(this);
        startManaged("player-name-tags", playerNameTags::enable, playerNameTags::disable);
        PlayerIdentityRenderer playerIdentities =
                new PlayerIdentityRenderer(bedrockPlayerBadge, playerNameTags);

        FloatingMenuService floatingMenuService = new FloatingMenuService(
                this, axiomGizmoService, geyserService, languageService);
        startManaged("floating-menu", () -> {
            floatingMenuService.enable();
            FloatingMenus.install(floatingMenuService);
        }, () -> {
            ShutdownSequence shutdown = new ShutdownSequence();
            shutdown.attempt("floating menu service", floatingMenuService::disable);
            shutdown.attempt("floating menu registration", FloatingMenus::uninstall);
            shutdown.finish("floating menu wiring");
        });
        LanguageMenu languageMenu = new LanguageMenu(languageService);
        languageMenu.registerCommands(this.getLifecycleManager());

        AsyncRegenModule asyncRegenModule = new AsyncRegenModule(this, languageService);
        startManaged("async-regen", asyncRegenModule::enable, asyncRegenModule::disable);
        asyncRegenModule.registerCommands(this.getLifecycleManager());

        NonEuclideanSpaceModule nonEuclideanSpaceModule = new NonEuclideanSpaceModule(this);
        startManaged("non-euclidean-space", nonEuclideanSpaceModule::enable, nonEuclideanSpaceModule::disable);
        nonEuclideanSpaceModule.registerCommands(this.getLifecycleManager());

        PlayerAddressModule playerAddressModule = new PlayerAddressModule(this);
        startManaged("player-address", playerAddressModule::enable, playerAddressModule::disable);

        PlayerAssociationNotifier playerAssociationNotifier =
                new PlayerAssociationNotifier(this, languageService, playerAddressModule);
        startListener("player-association", playerAssociationNotifier,
                playerAssociationNotifier::enable);

        BanModule banModule = new BanModule(this, languageService, playerAddressModule);
        startManaged("ban", banModule::enable, banModule::disable);
        banModule.registerCommands(this.getLifecycleManager());

        ServerLinksModule serverLinksModule = new ServerLinksModule(languageService);
        startListener("server-links", serverLinksModule,
                () -> serverLinksModule.register(this));

        AfkModule afkModule = new AfkModule(this, languageService, axiomGizmoService);
        startManaged("afk", afkModule::enable, afkModule::disable);
        afkModule.registerCommands(this.getLifecycleManager());

        FifthAnniversaryEventModule fifthAnniversaryEventModule =
                new FifthAnniversaryEventModule(this, afkModule, languageService);
        startManaged("fifth-anniversary", fifthAnniversaryEventModule::enable, fifthAnniversaryEventModule::disable);
        fifthAnniversaryEventModule.registerCommands(this.getLifecycleManager());

        PerformanceModule performanceModule = new PerformanceModule(this, afkModule);
        startManaged("performance", performanceModule::start, performanceModule::stop);
        performanceModule.registerCommands(this.getLifecycleManager());

        PvpModule pvpModule = new PvpModule(this, languageService);
        startManaged("pvp", pvpModule::enable, pvpModule::disable);
        pvpModule.registerCommands(this.getLifecycleManager());

        NetworkEgressModule networkEgressModule = new NetworkEgressModule(this);
        startManaged("network-egress", networkEgressModule::enable, networkEgressModule::disable);
        networkEgressModule.registerCommands(this.getLifecycleManager());

        ChatSettingsStore chatSettingsStore = new ChatSettingsStore(this);
        startManaged("chat-settings", chatSettingsStore::enable, chatSettingsStore::disable);

        MentionService mentionService = new MentionService(this, afkModule, languageService, chatSettingsStore,
                ChatDisplayRenderer::playerName);

        IdentityBindingModule identityBindingModule = new IdentityBindingModule(this, languageService);
        startManaged("identity-binding", identityBindingModule::enable, identityBindingModule::disable);
        identityBindingModule.registerCommands(this.getLifecycleManager());

        BukkitSocialChatGateway socialChatGateway =
                new BukkitSocialChatGateway(
                        this, identityBindingModule.manager(), playerNameTags);
        SocialModule socialModule = new SocialModule(
                this, identityBindingModule.manager(), languageService, afkModule,
                socialChatGateway, aiModule);

        ChatModule chatModule = new ChatModule(
                this, mentionService, languageService, chatSettingsStore, playerIdentities,
                socialModule.chatPublisher(), identityBindingModule.manager());
        socialChatGateway.bind(chatModule);
        startManaged("chat", chatModule::enable, chatModule::disable);
        chatModule.registerCommands(this.getLifecycleManager());

        startManaged("social", socialModule::enable, socialModule::disable);
        socialModule.registerCommands(this.getLifecycleManager());

        TeleportPreferenceModule teleportPreferenceModule =
                new TeleportPreferenceModule(this, afkModule, languageService);
        startListener("teleport-preference", teleportPreferenceModule,
                teleportPreferenceModule::enable);
        teleportPreferenceModule.registerCommands(this.getLifecycleManager());

        WelcomeModule welcomeModule = new WelcomeModule(this, languageService);
        startListener("welcome", welcomeModule, welcomeModule::enable);

        PlayerPresenceModule playerPresenceModule = new PlayerPresenceModule(this, languageService);
        startListener("player-presence", playerPresenceModule, playerPresenceModule::enable);

        ClientVersionReminderModule clientVersionReminderModule = null;
        if (getServer().getPluginManager().isPluginEnabled("ViaVersion")) {
            clientVersionReminderModule = new ClientVersionReminderModule(this, languageService);
            startListener("client-version-reminder", clientVersionReminderModule,
                    clientVersionReminderModule::enable);
        } else {
            getLogger().warning("ViaVersion not found! ClientVersionReminderModule disabled.");
        }

        if (getServer().getPluginManager().isPluginEnabled("Skript")) {
            MikSkriptModule skriptModule = new MikSkriptModule(
                    this, languageService,
                    clientVersionReminderModule == null ? null
                            : clientVersionReminderModule::clientVersionName,
                    afkModule, pvpModule,
                    nonEuclideanSpaceModule);
            startManaged("skript", skriptModule::enable, skriptModule::disable);
            skriptModule.registerCommands(this.getLifecycleManager());
        } else {
            getLogger().info("Skript not found; MIK Skript expressions are disabled.");
        }

        PlotModule plotModule = new PlotModule(this, languageService, socialModule::notifyPlayer);
        startManaged("plot", plotModule::enable, plotModule::disable);
        plotModule.registerCommands(this.getLifecycleManager());

        GovernanceModule governanceModule = new GovernanceModule(
                this, languageService, afkModule, banModule.manager());
        startManaged("governance", governanceModule::enable, governanceModule::disable);
        governanceModule.registerCommands(this.getLifecycleManager());

        MainMenuModule mainMenuModule = new MainMenuModule(this, afkModule, chatModule, teleportPreferenceModule,
                pvpModule, languageService, languageMenu, clientVersionReminderModule,
                plotModule, governanceModule);
        startManaged("main-menu", mainMenuModule::enable, mainMenuModule::disable);
        mainMenuModule.registerCommands(this.getLifecycleManager());

        MusicModule musicModule = new MusicModule(this, languageService, voiceServer,
                afkModule, floatingMenuService.worldTextDisplays());
        startManaged("music", musicModule::enable, musicModule::disable);
        musicModule.registerCommands(this.getLifecycleManager());

        SimpleFeaturesModule commandsModule = new SimpleFeaturesModule(this, languageService);
        startListener("simple-features", commandsModule, commandsModule::enable);
        commandsModule.registerCommands(this.getLifecycleManager());

        RestrictionModule restrictionModule = new RestrictionModule(this, languageService);
        startListener("restriction", restrictionModule, restrictionModule::enable);

        MaintenanceModule maintenanceModule = new MaintenanceModule(this, languageService);
        startListener("maintenance", maintenanceModule, maintenanceModule::enable);
        if (resumeFromCrash) {
            maintenanceModule.setMaintenanceEnabled(true);
            getLogger().warning("Detected unclean shutdown; maintenance mode enabled automatically.");
        }
        maintenanceModule.registerCommands(this.getLifecycleManager());

        GameModeSwitchModule gameModeSwitchModule = new GameModeSwitchModule(this);
        startManaged("game-mode-switch", gameModeSwitchModule::enable, gameModeSwitchModule::disable);

        FlightModule flightModule = new FlightModule(this, languageService);
        startManaged("flight", flightModule::enable, flightModule::disable);
        flightModule.registerCommands(this.getLifecycleManager());

        PlayerBoundaryModule playerBoundaryModule = new PlayerBoundaryModule(this, languageService);
        startManaged("player-boundary", playerBoundaryModule::enable, playerBoundaryModule::disable);

        IronElevatorModule ironElevatorModule = new IronElevatorModule(
                this, languageService, floatingMenuService.worldTextDisplays());
        startManaged("iron-elevator", ironElevatorModule::enable, ironElevatorModule::disable);

        VehicleModule vehicleModule = new VehicleModule(this, languageService, axiomGizmoService);
        startManaged("vehicle", vehicleModule::enable, vehicleModule::disable);
        vehicleModule.registerCommands(this.getLifecycleManager());

        TPSBarModule tpsBarModule = new TPSBarModule(this, languageService);
        startManaged("tps-bar", tpsBarModule::start, tpsBarModule::stop);
        tpsBarModule.registerCommands(this.getLifecycleManager());

        TabListModule tabListModule = new TabListModule(
                this, afkModule, languageService, playerIdentities);
        startManaged("tab-list", tabListModule::enable, tabListModule::disable);

        BanItemGuardModule banItemGuardModule = new BanItemGuardModule(this);
        startManaged("ban-item-guard", banItemGuardModule::enable, banItemGuardModule::disable);

        FixBugModule fixBugModule = new FixBugModule(this);
        startListener("bug-guard", fixBugModule, fixBugModule::enable);

        EnderPearlGuard enderPearlGuard = new EnderPearlGuard(this);
        startListener("ender-pearl-guard", enderPearlGuard, enderPearlGuard::enable);

        OversizedEntityGuard oversizedEntityGuard = new OversizedEntityGuard(this);
        startManaged("oversized-entity-guard", oversizedEntityGuard::enable,
                oversizedEntityGuard::disable);

        TrampleProtectionModule trampleProtectionModule = new TrampleProtectionModule(this);
        startManaged("trample-protection", trampleProtectionModule::enable, trampleProtectionModule::disable);

        GrieferModule grieferModule = new GrieferModule(this, banModule.manager());
        startManaged("griefer", grieferModule::enable, grieferModule::disable);

        AnnouncementModule announcementModule = new AnnouncementModule(this, languageService);
        startManaged("announcement", announcementModule::enable, announcementModule::disable);
        announcementModule.registerCommands(this.getLifecycleManager());

        TipModule tipModule = new TipModule(this, languageService);
        startManaged("tip", tipModule::enable, tipModule::disable);
        tipModule.registerCommands(this.getLifecycleManager());

        // Announcement data is exposed by the API module.
        ApiModule apiModule = new ApiModule(this, languageService,
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
        startManaged("http-api", () -> apiModule.start(35353), apiModule::stop);
        apiModule.registerCommands(this.getLifecycleManager());

        WhitelistModule whitelistModule = new WhitelistModule(this, languageService);
        startListener("whitelist", whitelistModule, whitelistModule::enable);
        whitelistModule.registerCommands(this.getLifecycleManager());

        MotdModule motdModule = new MotdModule(this, afkModule, languageService, playerAddressModule);
        startManaged("motd", motdModule::enable, motdModule::disable);

        HomeModule homeModule = new HomeModule(this, languageService);
        startManaged("home", homeModule::enable, homeModule::disable);
        homeModule.registerCommands(this.getLifecycleManager());

        BackModule backModule = new BackModule(this, languageService);
        startListener("back", backModule, backModule::enable);
        backModule.registerCommands(this.getLifecycleManager());

        NameTagModule prefixSuffixModule = new NameTagModule(this, languageService, playerIdentities);
        startManaged("name-tag", prefixSuffixModule::enable, prefixSuffixModule::disable);
        prefixSuffixModule.registerCommands(this.getLifecycleManager());

        InvisibilityNotifyModule invisibilityNotifyModule =
                new InvisibilityNotifyModule(this, languageService);
        startManaged("invisibility-notify", invisibilityNotifyModule::enable, invisibilityNotifyModule::disable);

        SpawnBeaconColorModule spawnBeaconColorModule =
                new SpawnBeaconColorModule(this, languageService);
        startManaged("spawn-beacon-color", spawnBeaconColorModule::enable, spawnBeaconColorModule::disable);
        spawnBeaconColorModule.registerCommands(this.getLifecycleManager());
    }

    @Override
    public void onDisable() {
        boolean cleanShutdown = enableCompleted;
        if (!stopManagedModules(null)) cleanShutdown = false;
        try {
            su.plo.voice.api.server.PlasmoVoiceServer.getAddonsLoader().unload(this);
        } catch (RuntimeException | LinkageError error) {
            cleanShutdown = false;
            getLogger().log(Level.SEVERE, "Could not unload Plasmo Voice addon", error);
        }
        if (cleanShutdown && maintenanceCrashMarker != null) {
            try {
                Files.deleteIfExists(maintenanceCrashMarker);
            } catch (IOException error) {
                getLogger().warning("Unable to clear maintenance crash marker: " + error.getMessage());
            }
        }
        enableCompleted = false;
    }

    private boolean stopManagedModules(Throwable startupError) {
        boolean cleanShutdown = true;
        while (!shutdownActions.isEmpty()) {
            ShutdownAction shutdown = shutdownActions.pop();
            try {
                shutdown.action().run();
            } catch (RuntimeException | LinkageError error) {
                cleanShutdown = false;
                getLogger().log(Level.SEVERE,
                        "Could not stop " + shutdown.name(), error);
                if (startupError != null) startupError.addSuppressed(error);
            }
        }
        return cleanShutdown;
    }

    private void registerShutdown(String name, Runnable action) {
        shutdownActions.push(new ShutdownAction(name, action));
    }

    private void startListener(String name, Listener listener, Runnable start) {
        startManaged(name, start, () -> HandlerList.unregisterAll(listener));
    }

    private void startManaged(String name, Runnable start, Runnable stop) {
        try {
            start.run();
        } catch (RuntimeException | LinkageError error) {
            try {
                stop.run();
            } catch (RuntimeException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
        registerShutdown(name, stop);
    }
}
