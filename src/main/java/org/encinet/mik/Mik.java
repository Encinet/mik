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
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.module.musicdisc.MusicDiscModule;
import org.encinet.mik.module.performance.PerformanceModule;
import org.encinet.mik.module.performance.TPSBarModule;
import org.encinet.mik.module.player.BackModule;
import org.encinet.mik.module.player.GameModeSwitchModule;
import org.encinet.mik.module.player.HomeModule;
import org.encinet.mik.module.player.InvisibilityNotifyModule;
import org.encinet.mik.module.player.NameTagModule;
import org.encinet.mik.module.player.PlayerBoundaryModule;
import org.encinet.mik.module.player.MainMenuModule;
import org.encinet.mik.module.player.TabListModule;
import org.encinet.mik.module.player.TeleportPreferenceModule;
import org.encinet.mik.module.presentation.BrandingModule;
import org.encinet.mik.module.presentation.MotdModule;
import org.encinet.mik.module.presentation.ServerLinksModule;
import org.encinet.mik.module.safety.FixBugModule;
import org.encinet.mik.module.safety.GrieferModule;

/**
 * Main plugin class - orchestrates all modules
 */
public final class Mik extends JavaPlugin {

    public static final String GROUP_MEMBER = "member";
    public static final String GROUP_HELPER = "helper";

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
    private MenuNavigation menuNavigation;

    @Override
    public void onLoad() {
        // Initialize and load branding module (PacketEvents must be loaded here)
        brandingModule = new BrandingModule(this);
        brandingModule.load();
    }

    @Override
    public void onEnable() {
        // Enable branding module
        brandingModule.enable();

        // Initialize and register server links
        serverLinksModule = new ServerLinksModule(this);
        serverLinksModule.register();

        // Initialize and enable idle module
        afkModule = new AfkModule(this);
        afkModule.enable();
        afkModule.registerCommands(this.getLifecycleManager());

        // Initialize and start performance module
        performanceModule = new PerformanceModule(this, afkModule);
        performanceModule.start();

        // main menu navigation
        menuNavigation = new MenuNavigation();

        // mention notifications
        mentionModule = new MentionModule(this, afkModule, menuNavigation);
        mentionModule.enable();

        // teleport preferences
        teleportPreferenceModule = new TeleportPreferenceModule(this, afkModule, menuNavigation);
        teleportPreferenceModule.enable();

        // player main menu
        mainMenuModule = new MainMenuModule(this, afkModule, mentionModule, teleportPreferenceModule, menuNavigation);
        mainMenuModule.enable();
        mainMenuModule.registerCommands(this.getLifecycleManager());

        // Initialize music disc module and load music files
        musicDiscModule = new MusicDiscModule(this);
        musicDiscModule.loadMusicFiles();
        musicDiscModule.registerCommands(this.getLifecycleManager());
        musicDiscModule.enableMusicChests();

        // Initialize staff chat module
        staffChatModule = new StaffChatModule(this);
        staffChatModule.registerCommands(this.getLifecycleManager());

        // Initialize and register commands module
        commandsModule = new SimpleFeaturesModule();
        commandsModule.registerCommands(this.getLifecycleManager());

        // Initialize and enable auto-promote module
        autoPromoteModule = new AutoPromoteModule(this);
        autoPromoteModule.enable();
        autoPromoteModule.registerCommands(this.getLifecycleManager());

        // Initialize and enable command restriction module
        restrictionModule = new RestrictionModule(this);
        restrictionModule.enable();

        // Initialize and enable game mode switch module
        gameModeSwitchModule = new GameModeSwitchModule(this);
        gameModeSwitchModule.enable();

        // Initialize and enable player boundary module
        playerBoundaryModule = new PlayerBoundaryModule(this);
        playerBoundaryModule.enable();

        // Initialize and start TPS bar module
        tpsBarModule = new TPSBarModule(this);
        tpsBarModule.start();
        tpsBarModule.registerCommands(this.getLifecycleManager());

        // Initialize and enable tab list module
        tabListModule = new TabListModule(this, afkModule);
        tabListModule.enable();

        // Initialize and enable tab list module
        fixBugModule = new FixBugModule(this);
        fixBugModule.enable();

        // Initialize and enable griefer module
        grieferModule = new GrieferModule(this);
        grieferModule.enable();

        // announcements (must be before API module)
        announcementModule = new AnnouncementModule(this, menuNavigation);
        announcementModule.enable();
        announcementModule.registerCommands(this.getLifecycleManager());

        // tips
        tipModule = new TipModule(this);
        tipModule.enable();
        tipModule.registerCommands(this.getLifecycleManager());

        // Initialize and start API module
        apiModule = new ApiModule(this);
        apiModule.setAnnouncementModule(announcementModule);
        apiModule.start(35353);

        // Initialize and enable whitelist chat module
        whitelistModule = new WhitelistModule(this);
        whitelistModule.enable();
        whitelistModule.registerCommands(this.getLifecycleManager());

        // Initialize and enable MOTD module
        motdModule = new MotdModule(this);
        motdModule.enable();

        // home
        homeModule = new HomeModule(this, menuNavigation);
        homeModule.enable();
        homeModule.registerCommands(this.getLifecycleManager());

        // back history
        backModule = new BackModule(this);
        backModule.enable();
        backModule.registerCommands(this.getLifecycleManager());

        // player prefix/suffix
        prefixSuffixModule = new NameTagModule(this);
        prefixSuffixModule.enable();
        prefixSuffixModule.registerCommands(this.getLifecycleManager());

        // invisibility actionbar notice
        invisibilityNotifyModule = new InvisibilityNotifyModule(this);
        invisibilityNotifyModule.enable();

    }

    @Override
    public void onDisable() {
        // Stop performance monitoring
        if (performanceModule != null) {
            performanceModule.stop();
        }

        if (afkModule != null) {
            afkModule.disable();
        }

        // Stop TPS bar module
        if (tpsBarModule != null) {
            tpsBarModule.stop();
        }

        // Disable tab list module
        if (tabListModule != null) {
            tabListModule.disable();
        }

        // Disable branding module
        if (brandingModule != null) {
            brandingModule.disable();
        }

        // Stop API server
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
