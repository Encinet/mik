package org.encinet.mik.module.ban;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.player.address.PlayerAddressLookup;
import org.encinet.mik.util.ShutdownSequence;

import java.io.File;
import java.time.ZoneId;

public final class BanModule {

    private final JavaPlugin plugin;
    private final PaperBanSynchronizer paperSynchronizer;
    private final BanService banService;
    private final BanCommandController commandController;
    private final BanLoginListener loginListener;

    public BanModule(
            JavaPlugin plugin,
            LanguageService languageService,
            PlayerAddressLookup addressLookup
    ) {
        this.plugin = plugin;
        ZoneId zoneId = ZoneId.systemDefault();
        this.paperSynchronizer = new PaperBanSynchronizer(plugin);
        this.banService = new BanService(
                new File(plugin.getDataFolder(), "bans.db"), paperSynchronizer, plugin.getLogger());
        this.paperSynchronizer.bind(banService);
        BanMessageRenderer renderer = new BanMessageRenderer(languageService, zoneId);
        BanAnnouncementBroadcaster announcementBroadcaster = new BanAnnouncementBroadcaster(languageService, renderer);
        BanAdmissionChecker admissionChecker = new BanAdmissionChecker(banService, addressLookup);
        BanDialogController dialogController = new BanDialogController(
                plugin, languageService, banService, renderer, announcementBroadcaster);
        this.commandController = new BanCommandController(
                plugin, languageService, paperSynchronizer, banService, renderer, announcementBroadcaster,
                dialogController);
        this.loginListener = new BanLoginListener(
                plugin, paperSynchronizer, languageService, renderer, admissionChecker);
    }

    public void enable() {
        try {
            banService.open();
            paperSynchronizer.start();
            Bukkit.getServicesManager().register(BanManager.class, banService, plugin, ServicePriority.Normal);
        } catch (BanServiceException | RuntimeException e) {
            paperSynchronizer.stop();
            try {
                banService.close();
            } catch (BanServiceException closeError) {
                e.addSuppressed(closeError);
            }
            throw new IllegalStateException("BanModule could not initialize", e);
        }
        Bukkit.getPluginManager().registerEvents(loginListener, plugin);
        plugin.getLogger().info("BanModule enabled (SQLite history and Paper synchronization active)");
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("ban login listener", () -> HandlerList.unregisterAll(loginListener));
        shutdown.attempt("ban service registration", () ->
                Bukkit.getServicesManager().unregister(BanManager.class, banService));
        shutdown.attempt("Paper ban synchronization", paperSynchronizer::stop);
        shutdown.attempt("ban database", banService::close);
        shutdown.finish("ban module");
    }

    public BanManager manager() {
        return banService;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        commandController.registerCommands(manager);
    }
}
