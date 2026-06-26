package org.encinet.mik;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.access.AutoPromoteModule;
import org.encinet.mik.module.access.RestrictionModule;
import org.encinet.mik.module.access.WhitelistModule;
import org.encinet.mik.module.afk.AfkModule;
import org.encinet.mik.module.api.ApiModule;
import org.encinet.mik.module.commands.SimpleFeaturesModule;
import org.encinet.mik.module.communication.AnnouncementModule;
import org.encinet.mik.module.communication.StaffChatModule;
import org.encinet.mik.module.communication.MentionModule;
import org.encinet.mik.module.communication.TipModule;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.module.musicdisc.MusicDiscModule;
import org.encinet.mik.module.performance.PerformanceModule;
import org.encinet.mik.module.performance.TPSBarModule;
import org.encinet.mik.module.player.BackModule;
import org.encinet.mik.module.player.ClientVersionReminderModule;
import org.encinet.mik.module.player.GameModeSwitchModule;
import org.encinet.mik.module.player.HomeModule;
import org.encinet.mik.module.player.InvisibilityNotifyModule;
import org.encinet.mik.module.player.NameTagModule;
import org.encinet.mik.module.player.PlayerBoundaryModule;
import org.encinet.mik.module.player.MainMenuModule;
import org.encinet.mik.module.player.PvpModule;
import org.encinet.mik.module.player.TabListModule;
import org.encinet.mik.module.player.TeleportPreferenceModule;
import org.encinet.mik.module.presentation.BrandingModule;
import org.encinet.mik.module.presentation.MotdModule;
import org.encinet.mik.module.presentation.ServerLinksModule;
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
    private MusicDiscModule musicDiscModule;
    private StaffChatModule staffChatModule;
    private SimpleFeaturesModule commandsModule;
    private AutoPromoteModule autoPromoteModule;
    private RestrictionModule restrictionModule;
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
    private MentionModule mentionModule;
    private TipModule tipModule;
    private NameTagModule prefixSuffixModule;
    private MainMenuModule mainMenuModule;
    private InvisibilityNotifyModule invisibilityNotifyModule;
    private TeleportPreferenceModule teleportPreferenceModule;
    private PvpModule pvpModule;
    private ClientVersionReminderModule clientVersionReminderModule;
    private MenuNavigation menuNavigation;
    private LanguageService languageService;

    @Override
    public void onLoad() {
        // PacketEvents requires BrandingModule to load before onEnable.
        brandingModule = new BrandingModule(this);
        brandingModule.load();
    }

    @Override
    public void onEnable() {
        brandingModule.enable();

        serverLinksModule = new ServerLinksModule(this);
        serverLinksModule.register();

        menuNavigation = new MenuNavigation();

        languageService = new LanguageService(this, menuNavigation);
        languageService.enable();
        languageService.registerCommands(this.getLifecycleManager());

        afkModule = new AfkModule(this, languageService);
        afkModule.enable();
        afkModule.registerCommands(this.getLifecycleManager());

        performanceModule = new PerformanceModule(this, afkModule);
        performanceModule.start();

        mentionModule = new MentionModule(this, afkModule, menuNavigation, languageService);
        mentionModule.enable();

        teleportPreferenceModule = new TeleportPreferenceModule(this, afkModule, menuNavigation, languageService);
        teleportPreferenceModule.enable();

        pvpModule = new PvpModule(this, menuNavigation, languageService);
        pvpModule.enable();
        pvpModule.registerCommands(this.getLifecycleManager());

        if (getServer().getPluginManager().isPluginEnabled("ViaVersion")) {
            clientVersionReminderModule = new ClientVersionReminderModule(this, languageService);
            clientVersionReminderModule.enable();
        } else {
            getLogger().warning("ViaVersion not found! ClientVersionReminderModule disabled.");
        }

        mainMenuModule = new MainMenuModule(this, afkModule, mentionModule, teleportPreferenceModule,
                pvpModule, menuNavigation, languageService, clientVersionReminderModule);
        mainMenuModule.enable();
        mainMenuModule.registerCommands(this.getLifecycleManager());

        musicDiscModule = new MusicDiscModule(this, languageService);
        musicDiscModule.loadMusicFiles();
        musicDiscModule.registerCommands(this.getLifecycleManager());
        musicDiscModule.enableMusicChests();

        staffChatModule = new StaffChatModule(this, languageService);
        staffChatModule.enable();
        staffChatModule.registerCommands(this.getLifecycleManager());

        commandsModule = new SimpleFeaturesModule(languageService);
        commandsModule.registerCommands(this.getLifecycleManager());

        autoPromoteModule = new AutoPromoteModule(this, languageService);
        autoPromoteModule.enable();
        autoPromoteModule.registerCommands(this.getLifecycleManager());

        restrictionModule = new RestrictionModule(this, languageService);
        restrictionModule.enable();

        gameModeSwitchModule = new GameModeSwitchModule(this);
        gameModeSwitchModule.enable();

        playerBoundaryModule = new PlayerBoundaryModule(this, languageService);
        playerBoundaryModule.enable();

        tpsBarModule = new TPSBarModule(this, languageService);
        tpsBarModule.start();
        tpsBarModule.registerCommands(this.getLifecycleManager());

        tabListModule = new TabListModule(this, afkModule, languageService);
        tabListModule.enable();

        fixBugModule = new FixBugModule(this);
        fixBugModule.enable();

        grieferModule = new GrieferModule(this);
        grieferModule.enable();

        announcementModule = new AnnouncementModule(this, menuNavigation);
        announcementModule.enable();
        announcementModule.registerCommands(this.getLifecycleManager());

        tipModule = new TipModule(this, languageService);
        tipModule.enable();
        tipModule.registerCommands(this.getLifecycleManager());

        // Announcement data is exposed by the API module.
        apiModule = new ApiModule(this);
        apiModule.setAnnouncementModule(announcementModule);
        apiModule.start(35353);
        apiModule.registerCommands(this.getLifecycleManager());

        whitelistModule = new WhitelistModule(this, languageService);
        whitelistModule.enable();
        whitelistModule.registerCommands(this.getLifecycleManager());

        motdModule = new MotdModule(this, afkModule);
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

    }

    @Override
    public void onDisable() {
        if (performanceModule != null) {
            performanceModule.stop();
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

        if (invisibilityNotifyModule != null) {
            invisibilityNotifyModule.disable();
        }
    }
}
