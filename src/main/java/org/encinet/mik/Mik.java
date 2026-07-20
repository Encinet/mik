package org.encinet.mik;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.access.AutoPromoteModule;
import org.encinet.mik.module.ban.BanModule;
import org.encinet.mik.module.access.RestrictionModule;
import org.encinet.mik.module.access.WhitelistModule;
import org.encinet.mik.module.afk.AfkModule;
import org.encinet.mik.module.api.ApiModule;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.chat.ChatModule;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.commands.SimpleFeaturesModule;
import org.encinet.mik.module.communication.AnnouncementModule;
import org.encinet.mik.module.communication.TipModule;
import org.encinet.mik.module.event.FifthAnniversaryEventModule;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.module.musicdisc.MusicDiscModule;
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
import org.encinet.mik.module.player.PlayerAddressModule;
import org.encinet.mik.module.player.PlayerAssociationNotifier;
import org.encinet.mik.module.player.PlayerPresenceModule;
import org.encinet.mik.module.player.MainMenuModule;
import org.encinet.mik.module.player.WelcomeModule;
import org.encinet.mik.module.pvp.PvpModule;
import org.encinet.mik.module.player.TabListModule;
import org.encinet.mik.module.player.TeleportPreferenceModule;
import org.encinet.mik.module.presentation.BrandingModule;
import org.encinet.mik.module.presentation.MotdModule;
import org.encinet.mik.module.presentation.ServerLinksModule;
import org.encinet.mik.module.presentation.SpawnBeaconColorModule;
import org.encinet.mik.module.safety.FixBugModule;
import org.encinet.mik.module.safety.GrieferModule;

public final class Mik extends JavaPlugin {

    public static final String GROUP_MEMBER = "member";
    public static final String GROUP_HELPER = "helper";
    public static final String GROUP_MANAGER = "manager";

    private BrandingModule brandingModule;
    private ServerLinksModule serverLinksModule;
    private AfkModule afkModule;
    private PerformanceModule performanceModule;
    private NetworkEgressModule networkEgressModule;
    private MusicDiscModule musicDiscModule;
    private MentionService mentionService;
    private ChatSettingsStore chatSettingsStore;
    private ChatModule chatModule;
    private SimpleFeaturesModule commandsModule;
    private AutoPromoteModule autoPromoteModule;
    private BanModule banModule;
    private RestrictionModule restrictionModule;
    private FlightModule flightModule;
    private GameModeSwitchModule gameModeSwitchModule;
    private PlayerBoundaryModule playerBoundaryModule;
    private TPSBarModule tpsBarModule;
    private TabListModule tabListModule;
    private FixBugModule fixBugModule;
    private GrieferModule grieferModule;
    private ApiModule apiModule;
    private WhitelistModule whitelistModule;
    private MotdModule motdModule;
    private HomeModule homeModule;
    private BackModule backModule;
    private AnnouncementModule announcementModule;
    private TipModule tipModule;
    private NameTagModule prefixSuffixModule;
    private MainMenuModule mainMenuModule;
    private InvisibilityNotifyModule invisibilityNotifyModule;
    private TeleportPreferenceModule teleportPreferenceModule;
    private PvpModule pvpModule;
    private ClientVersionReminderModule clientVersionReminderModule;
    private WelcomeModule welcomeModule;
    private PlayerPresenceModule playerPresenceModule;
    private MenuNavigation menuNavigation;
    private LanguageService languageService;
    private PlayerAddressModule playerAddressModule;
    private PlayerAssociationNotifier playerAssociationNotifier;
    private SpawnBeaconColorModule spawnBeaconColorModule;
    private FifthAnniversaryEventModule fifthAnniversaryEventModule;

    @Override
    public void onLoad() {
        // PacketEvents requires BrandingModule to load before onEnable.
        brandingModule = new BrandingModule(this);
        brandingModule.load();
    }

    @Override
    public void onEnable() {
        brandingModule.enable();

        menuNavigation = new MenuNavigation();

        languageService = new LanguageService(this, menuNavigation);
        languageService.enable();
        languageService.registerCommands(this.getLifecycleManager());

        playerAddressModule = new PlayerAddressModule(this);
        playerAddressModule.enable();

        playerAssociationNotifier = new PlayerAssociationNotifier(this, languageService, playerAddressModule);
        playerAssociationNotifier.enable();

        banModule = new BanModule(this, languageService, playerAddressModule);
        banModule.enable();
        banModule.registerCommands(this.getLifecycleManager());

        serverLinksModule = new ServerLinksModule(languageService);
        serverLinksModule.register(this);

        afkModule = new AfkModule(this, languageService);
        afkModule.enable();
        afkModule.registerCommands(this.getLifecycleManager());

        fifthAnniversaryEventModule = new FifthAnniversaryEventModule(this, afkModule, languageService);
        fifthAnniversaryEventModule.enable();
        fifthAnniversaryEventModule.registerCommands(this.getLifecycleManager());

        performanceModule = new PerformanceModule(this, afkModule);
        performanceModule.start();

        pvpModule = new PvpModule(this, menuNavigation, languageService);
        pvpModule.enable();
        pvpModule.registerCommands(this.getLifecycleManager());

        networkEgressModule = new NetworkEgressModule(this);
        networkEgressModule.enable();
        networkEgressModule.registerCommands(this.getLifecycleManager());

        chatSettingsStore = new ChatSettingsStore(this);
        chatSettingsStore.enable();

        mentionService = new MentionService(this, afkModule, languageService, chatSettingsStore,
                ChatDisplayRenderer::playerName);
        mentionService.enable();

        chatModule = new ChatModule(this, mentionService, languageService, chatSettingsStore, menuNavigation);
        chatModule.enable();
        chatModule.registerCommands(this.getLifecycleManager());

        teleportPreferenceModule = new TeleportPreferenceModule(this, afkModule, menuNavigation, languageService);
        teleportPreferenceModule.enable();

        welcomeModule = new WelcomeModule(this, languageService);
        welcomeModule.enable();

        playerPresenceModule = new PlayerPresenceModule(this, languageService);
        playerPresenceModule.enable();

        if (getServer().getPluginManager().isPluginEnabled("ViaVersion")) {
            clientVersionReminderModule = new ClientVersionReminderModule(this, languageService);
            clientVersionReminderModule.enable();
        } else {
            getLogger().warning("ViaVersion not found! ClientVersionReminderModule disabled.");
        }

        mainMenuModule = new MainMenuModule(this, afkModule, chatModule, teleportPreferenceModule,
                pvpModule, menuNavigation, languageService, clientVersionReminderModule);
        mainMenuModule.enable();
        mainMenuModule.registerCommands(this.getLifecycleManager());

        musicDiscModule = new MusicDiscModule(this, languageService);
        musicDiscModule.loadMusicFiles();
        musicDiscModule.registerCommands(this.getLifecycleManager());
        musicDiscModule.enableMusicChests();

        commandsModule = new SimpleFeaturesModule(this, languageService);
        commandsModule.enable();
        commandsModule.registerCommands(this.getLifecycleManager());

        autoPromoteModule = new AutoPromoteModule(this, languageService);
        autoPromoteModule.enable();
        autoPromoteModule.registerCommands(this.getLifecycleManager());

        restrictionModule = new RestrictionModule(this, languageService);
        restrictionModule.enable();

        gameModeSwitchModule = new GameModeSwitchModule(this);
        gameModeSwitchModule.enable();

        flightModule = new FlightModule(this, languageService);
        flightModule.enable();
        flightModule.registerCommands(this.getLifecycleManager());

        playerBoundaryModule = new PlayerBoundaryModule(this, languageService);
        playerBoundaryModule.enable();

        tpsBarModule = new TPSBarModule(this, languageService);
        tpsBarModule.start();
        tpsBarModule.registerCommands(this.getLifecycleManager());

        tabListModule = new TabListModule(this, afkModule, languageService);
        tabListModule.enable();

        fixBugModule = new FixBugModule(this);
        fixBugModule.enable();

        grieferModule = new GrieferModule(this, banModule.manager());
        grieferModule.enable();

        announcementModule = new AnnouncementModule(this, menuNavigation);
        announcementModule.enable();
        announcementModule.registerCommands(this.getLifecycleManager());

        tipModule = new TipModule(this, languageService);
        tipModule.enable();
        tipModule.registerCommands(this.getLifecycleManager());

        // Announcement data is exposed by the API module.
        apiModule = new ApiModule(this, languageService, banModule.manager());
        apiModule.setAnnouncementModule(announcementModule);
        apiModule.start(35353);
        apiModule.registerCommands(this.getLifecycleManager());

        whitelistModule = new WhitelistModule(this, languageService);
        whitelistModule.enable();
        whitelistModule.registerCommands(this.getLifecycleManager());

        motdModule = new MotdModule(this, afkModule, languageService, playerAddressModule);
        motdModule.enable();

        homeModule = new HomeModule(this, menuNavigation, languageService);
        homeModule.enable();
        homeModule.registerCommands(this.getLifecycleManager());

        backModule = new BackModule(this, languageService);
        backModule.enable();
        backModule.registerCommands(this.getLifecycleManager());

        prefixSuffixModule = new NameTagModule(this, languageService);
        prefixSuffixModule.enable();
        prefixSuffixModule.registerCommands(this.getLifecycleManager());

        invisibilityNotifyModule = new InvisibilityNotifyModule(this, languageService);
        invisibilityNotifyModule.enable();

        spawnBeaconColorModule = new SpawnBeaconColorModule(this, languageService);
        spawnBeaconColorModule.enable();
        spawnBeaconColorModule.registerCommands(this.getLifecycleManager());

    }

    @Override
    public void onDisable() {
        if (fifthAnniversaryEventModule != null) {
            fifthAnniversaryEventModule.disable();
        }

        if (performanceModule != null) {
            performanceModule.stop();
        }

        if (networkEgressModule != null) {
            networkEgressModule.disable();
        }

        if (afkModule != null) {
            afkModule.disable();
        }

        if (tpsBarModule != null) {
            tpsBarModule.stop();
        }

        if (tabListModule != null) {
            tabListModule.disable();
        }

        if (brandingModule != null) {
            brandingModule.disable();
        }

        if (apiModule != null) {
            apiModule.stop();
        }

        if (motdModule != null) {
            motdModule.disable();
        }

        if (tipModule != null) {
            tipModule.disable();
        }

        if (announcementModule != null) {
            announcementModule.disable();
        }

        if (invisibilityNotifyModule != null) {
            invisibilityNotifyModule.disable();
        }

        if (playerAddressModule != null) {
            playerAddressModule.disable();
        }

        if (spawnBeaconColorModule != null) {
            spawnBeaconColorModule.disable();
        }

        if (flightModule != null) {
            flightModule.disable();
        }

        if (playerBoundaryModule != null) {
            playerBoundaryModule.disable();
        }

        if (banModule != null) {
            banModule.disable();
        }
    }
}
