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
    private MusicChestModule musicChestModule;
    private PerformanceModule performanceModule;
    private MusicDiscModule musicDiscModule;
    private StaffChatModule staffChatModule;
    private CommandsModule commandsModule;
    private AutoPromoteModule autoPromoteModule;
    private CommandRestrictionModule commandRestrictionModule;
    private GameModeSwitchModule gameModeSwitchModule;
    private PlayerBoundaryModule playerBoundaryModule;

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

        // Initialize music chest module
        musicChestModule = new MusicChestModule(this, musicDiscModule);
        musicChestModule.enable();

        // Initialize staff chat module
        staffChatModule = new StaffChatModule(this);
        staffChatModule.registerCommands(this.getLifecycleManager());

        // Initialize and register commands module
        commandsModule = new CommandsModule(this);
        commandsModule.registerCommands(this.getLifecycleManager());

        // Initialize and enable auto-promote module
        autoPromoteModule = new AutoPromoteModule(this);
        autoPromoteModule.enable();

        // Initialize and enable command restriction module
        commandRestrictionModule = new CommandRestrictionModule(this);
        commandRestrictionModule.enable();

        // Initialize and enable game mode switch module
        gameModeSwitchModule = new GameModeSwitchModule(this);
        gameModeSwitchModule.enable();

        // Initialize and enable player boundary module
        playerBoundaryModule = new PlayerBoundaryModule(this);
        playerBoundaryModule.enable();
    }

    @Override
    public void onDisable() {
        // Stop performance monitoring
        if (performanceModule != null) {
            performanceModule.stop();
        }

        // Disable branding module
        if (brandingModule != null) {
            brandingModule.disable();
        }
    }
}
