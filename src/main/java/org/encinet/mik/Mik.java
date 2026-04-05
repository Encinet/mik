package org.encinet.mik;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.*;

/**
 * Main plugin class - orchestrates all modules
 */
public final class Mik extends JavaPlugin {

    public static final String GROUP_MEMBER = "member";
    public static final String GROUP_HELPER = "helper";

    private BrandingModule brandingModule;
    private ServerLinksModule serverLinksModule;
    private PerformanceModule performanceModule;
    private MusicDiscModule musicDiscModule;
    private StaffChatModule staffChatModule;
    private SimpleFeaturesModule commandsModule;
    private AutoPromoteModule autoPromoteModule;
    private CommandRestrictionModule commandRestrictionModule;
    private GameModeSwitchModule gameModeSwitchModule;
    private PlayerBoundaryModule playerBoundaryModule;
    private TPSBarModule tpsBarModule;
    private TabListModule tabListModule;
    private GrieferModule grieferModule;
    private ApiModule apiModule;
    private WhitelistModule whitelistModule;
    private MotdModule motdModule;
    private HomeModule homeModule;
    private AnnouncementModule announcementModule;
    private NameTagModule prefixSuffixModule;

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

        // Initialize and start performance module
        performanceModule = new PerformanceModule(this);
        performanceModule.start();

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
        commandRestrictionModule = new CommandRestrictionModule(this);
        commandRestrictionModule.enable();

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
        tabListModule = new TabListModule(this);
        tabListModule.enable();

        // Initialize and enable tab list module
        grieferModule = new GrieferModule(this);
        grieferModule.enable();

        // announcements (must be before API module)
        announcementModule = new AnnouncementModule(this);
        announcementModule.enable();
        announcementModule.registerCommands(this.getLifecycleManager());

        // Initialize and start API module
        apiModule = new ApiModule(this);
        apiModule.setAnnouncementModule(announcementModule);
        apiModule.start(35353);

        // Initialize and enable whitelist chat module
        whitelistModule = new WhitelistModule(this);
        whitelistModule.enable();

        // Initialize and enable MOTD module
        motdModule = new MotdModule(this);
        motdModule.enable();

        // home
        homeModule = new HomeModule(this);
        homeModule.enable();
        homeModule.registerCommands(this.getLifecycleManager());

        // player prefix/suffix
        prefixSuffixModule = new NameTagModule(this);
        prefixSuffixModule.enable();
        prefixSuffixModule.registerCommands(this.getLifecycleManager());
    }

    @Override
    public void onDisable() {
        // Stop performance monitoring
        if (performanceModule != null) {
            performanceModule.stop();
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
    }
}
