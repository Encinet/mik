package org.encinet.mik.feature.server.prison;

import com.mojang.brigadier.Command;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class PrisonModule {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final BanManager bans;
    private final Consumer<Predicate<UUID>> jailedLoginAdmission;
    private PrisonService service;
    private PrisonMenu menu;
    private PrisonListener listener;
    private PrisonPanelView panelView;
    private WorldMenuScope panelScope;
    private org.bukkit.scheduler.BukkitTask task;

    public PrisonModule(JavaPlugin plugin, LanguageService language, BanManager bans,
                        Consumer<Predicate<UUID>> jailedLoginAdmission) {
        this.plugin = plugin;
        this.language = language;
        this.bans = bans;
        this.jailedLoginAdmission = jailedLoginAdmission;
    }

    public void enable() {
        try {
            Path folder = plugin.getDataFolder().toPath();
            PrisonSettings settings = PrisonSettings.load(folder.resolve("prison-settings.yml"));
            Path dataFile = folder.resolve("prison-data.yml");
            service = new PrisonService(plugin, language, bans, settings,
                    new PrisonStore(dataFile));
            try {
                service.load();
            } catch (IOException exception) {
                backupAndStartEmpty(dataFile, exception);
            }
            menu = new PrisonMenu(language, service);
            listener = new PrisonListener(service, language, this::onBlocksChanged,
                    this::onChunkUnloaded, this::onQuit);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            jailedLoginAdmission.accept(service::allowsBannedLogin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, settings.checkIntervalTicks(),
                    settings.checkIntervalTicks());
            plugin.getLogger().info("PrisonModule enabled");
        } catch (IOException | RuntimeException exception) {
            if (listener != null) HandlerList.unregisterAll(listener);
            jailedLoginAdmission.accept(null);
            throw new IllegalStateException("PrisonModule could not initialize", exception);
        }
    }

    /**
     * Binds the prison service to the plot registry; called once from bootstrap when both the
     * server and world features exist. Idempotent; the panel scope may be null.
     */
    public synchronized void bind(PrisonPlotHost plotHost, WorldMenuScope panelScope) {
        if (service == null || plotHost == null) return;
        service.bind(plotHost);
        if (panelScope != null && this.panelScope == null) {
            this.panelScope = panelScope;
            panelView = new PrisonPanelView(service, plotHost, panelScope, language);
        }
    }

    /** Veto guard registered on the plot host: refuse toggling off or deleting a plot with inmates. */
    public Predicate<Plot> inmateFreeGuard() {
        return service != null ? service.inmateFreeGuard() : _ -> true;
    }

    /** Forwards world changes to the panel placement cache. */
    void onBlocksChanged(Block block) {
        PrisonPanelView view = panelView;
        if (view != null && block != null && block.getWorld() != null) view.invalidate(block);
    }

    /** Forwards a chunk unload to the panel placement cache. */
    void onChunkUnloaded(org.bukkit.event.world.ChunkUnloadEvent event) {
        PrisonPanelView view = panelView;
        if (view != null) view.invalidateChunk(event.getWorld().getUID(),
                event.getChunk().getX(), event.getChunk().getZ());
    }

    /** Clears a viewer's panel on quit. */
    void onQuit(UUID playerId) {
        if (panelView != null) panelView.clear(playerId);
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        if (task != null) shutdown.attempt("prison update task", task::cancel);
        if (listener != null) shutdown.attempt("prison listener", () -> HandlerList.unregisterAll(listener));
        if (panelView != null) shutdown.attempt("prison panels", panelView::close);
        // The panel scope is owned by the bootstrap lifecycle, which closes it after this module.
        shutdown.attempt("prison login admission", () -> jailedLoginAdmission.accept(null));
        if (service != null) shutdown.attempt("prison data", service::save);
        shutdown.finish("PrisonModule shutdown failed");
    }

    public void openMenu(Player player) {
        if (menu != null) menu.open(player);
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                io.papermc.paper.command.brigadier.Commands.literal("prison")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            if (sender instanceof Player player) openMenu(player);
                            else sender.sendMessage(Component.text(
                                    language.t(Language.DEFAULT, Message.PLAYER_ONLY),
                                    NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                language.t(Language.DEFAULT, Message.PRISON_COMMAND_DESCRIPTION)));
    }

    private void tick() {
        try {
            service.tick();
            PrisonPanelView view = panelView;
            if (view != null) {
                view.tick();
                view.reconcile();
                for (PrisonerRecord inmate : service.prisoners()) {
                    Player online = Bukkit.getPlayer(inmate.playerId());
                    if (online != null && service.location(inmate).isPresent())
                        view.panel(online, inmate, inmate.plot());
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Prison update task stopped after an error", exception);
            if (task != null) task.cancel();
        }
    }

    private void backupAndStartEmpty(Path dataFile, IOException cause) {
        plugin.getLogger().log(Level.SEVERE,
                "Prison data file is not in the current format; backing it up and starting with empty data", cause);
        try {
            if (Files.exists(dataFile)) {
                Path backup = dataFile.resolveSibling("prison-data-" + Instant.now().toEpochMilli() + ".yml.bak");
                Files.move(dataFile, backup);
            }
            service.load();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Prison data backup or re-initialization failed", failure);
        }
    }
}
