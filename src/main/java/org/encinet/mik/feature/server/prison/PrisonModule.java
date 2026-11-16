package org.encinet.mik.feature.server.prison;

import com.mojang.brigadier.Command;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.io.IOException;
import java.nio.file.Path;
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
            service = new PrisonService(plugin, language, bans, settings,
                    new PrisonStore(folder.resolve("prison-data.yml")));
            service.load();
            menu = new PrisonMenu(language, service);
            listener = new PrisonListener(service, language);
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

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        if (task != null) shutdown.attempt("prison update task", task::cancel);
        if (listener != null) shutdown.attempt("prison listener", () -> HandlerList.unregisterAll(listener));
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
                                    language.t(org.encinet.mik.shared.i18n.Language.DEFAULT, Message.PLAYER_ONLY),
                                    NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                language.t(org.encinet.mik.shared.i18n.Language.DEFAULT, Message.PRISON_COMMAND_DESCRIPTION)));
    }

    private void tick() {
        try {
            service.tick();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Prison update task stopped after an error", exception);
            if (task != null) task.cancel();
        }
    }
}
