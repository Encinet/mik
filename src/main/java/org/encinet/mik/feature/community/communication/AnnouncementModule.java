package org.encinet.mik.feature.community.communication;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.MenuScene;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AnnouncementModule implements Listener, AnnouncementHighlights {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final Supplier<CompletableFuture<AnnouncementSnapshot>> fetchSnapshot;
    private final FloatingMenuScreen<AnnouncementMenu.Position> menuScreen;
    private AnnouncementSnapshot snapshot = AnnouncementSnapshot.UNAVAILABLE;
    private BukkitTask refreshTask;

    public AnnouncementModule(JavaPlugin plugin, LanguageService language,
                              Supplier<CompletableFuture<AnnouncementSnapshot>> fetchSnapshot) {
        this.plugin = plugin;
        this.language = language;
        this.fetchSnapshot = fetchSnapshot;
        menuScreen = new FloatingMenuScreen<>("announcements", context -> AnnouncementMenu.build(snapshot, language, context));
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, 20 * 30);
    }

    public void disable() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        HandlerList.unregisterAll(this);
        menuScreen.forgetAll();
    }

    private void refresh() {
        var task = refreshTask;
        fetchSnapshot.get().whenComplete((next, failure) -> {
            if (!plugin.isEnabled()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (refreshTask == task) applySnapshot(failure == null ? next : snapshot.retainAfterFailure());
                });
            } catch (IllegalPluginAccessException disabled) {
                if (plugin.isEnabled()) throw disabled;
            }
        });
    }

    private void applySnapshot(AnnouncementSnapshot next) {
        if (snapshot.equals(next)) return;
        boolean entriesChanged = !snapshot.entries().equals(next.entries());
        snapshot = next;
        menuScreen.updateWhere(_ -> true, position -> entriesChanged ? AnnouncementMenu.Position.START : position);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        menuScreen.forget(event.getPlayer());
    }

    private void open(Player player) {
        menuScreen.open(player, AnnouncementMenu.Position.START);
    }

    @Override public MenuPanel latestPanel(Player player) {
        return () -> {
            var entries = snapshot.entries();
            var title = language.text(player, Message.ANNOUNCEMENT_LATEST, NamedTextColor.GOLD);
            if (snapshot.state() == AnnouncementSnapshot.State.STALE) {
                title = title.append(Component.newline())
                        .append(language.text(player, Message.ANNOUNCEMENT_STALE, NamedTextColor.YELLOW));
            }
            return MenuScene.screen("latest-announcement").content(AnnouncementPreview.content(
                    entries.isEmpty() ? null : entries.getFirst(), title,
                    language.text(player, Message.ANNOUNCEMENT_VIEW_ALL, NamedTextColor.AQUA),
                    (viewer, _) -> open(viewer))).build();
        };
    }

    @Override public Object revision() {
        return snapshot;
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("announcements")
                        .executes(ctx -> {
                            if (ctx.getSource().getSender() instanceof Player player) open(player);
                            return Command.SINGLE_SUCCESS;
                        }).build(), language.t(Language.DEFAULT, Message.ANNOUNCEMENT_VIEW_COMMAND_DESCRIPTION)));
    }
}
