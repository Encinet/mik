package org.encinet.mik.feature.server.ban.prison;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BannedLoginAdmission;
import org.encinet.mik.feature.world.plot.PlotPrisons;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class PrisonModule {
    private static final int STATUS_INTERVAL_TICKS = 40;

    private final JavaPlugin plugin;
    private final PrisonService service;
    private final PrisonMenu menu;
    private final PrisonPanelView panels;
    private final PrisonListener listener;
    private final PrisonAdmission admission;
    private final PrisonRestrictions restrictions;
    private final PrisonPackets packets;
    private final Set<UUID> pendingAccounts = new HashSet<>();
    private BukkitTask task;
    private int ticks;

    public PrisonModule(JavaPlugin plugin, LanguageService language, BanManager bans,
                        PlotPrisons plots, WorldMenuScope panelScope) {
        this.plugin = plugin;
        service = new PrisonService(plugin, language, bans, plots);
        menu = new PrisonMenu(language, service);
        panels = new PrisonPanelView(service, panelScope, language);
        listener = new PrisonListener(service, language, panels, this::joined, this::left);
        admission = new PrisonAdmission(service);
        restrictions = new PrisonRestrictions(service::confined, language);
        packets = new PrisonPackets(service::confined);
    }

    public void enable() {
        service.loadWorlds();
        PacketEvents.getAPI().getEventManager().registerListener(packets);
        Bukkit.getPluginManager().registerEvents(restrictions, plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        Bukkit.getPluginManager().registerEvents(admission, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (confined(id)) pendingAccounts.add(id);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        if (task != null) shutdown.attempt("prison update task", task::cancel);
        shutdown.attempt("prison packet listener",
                () -> PacketEvents.getAPI().getEventManager().unregisterListener(packets));
        shutdown.attempt("prison restrictions listener", () -> HandlerList.unregisterAll(restrictions));
        shutdown.attempt("prison player settings", restrictions::close);
        shutdown.attempt("prison listener", () -> HandlerList.unregisterAll(listener));
        shutdown.attempt("prison admission listener", () -> HandlerList.unregisterAll(admission));
        shutdown.attempt("prison admission", admission::close);
        shutdown.attempt("prison landing", service::close);
        shutdown.attempt("prison panels", panels::close);
        pendingAccounts.clear();
        shutdown.finish("prison module");
    }

    public boolean mayPublishPlots(List<PlotPrisons.Region> before, List<PlotPrisons.Region> after) {
        return service.mayPublishPlots(before, after);
    }

    public boolean confined(UUID playerId) { return service.confined(playerId); }

    public void onConfinement(Consumer<Player> action) { restrictions.onEnter(action); }

    public BannedLoginAdmission admission() { return admission; }

    public void openMenu(Player player) { menu.open(player); }

    public void accountChanged(UUID id) { pendingAccounts.add(id); }

    private void joined(Player player) {
        var id = player.getUniqueId();
        if (!confined(id)) return;
        service.track(id);
        if (player.getGameMode() != GameMode.ADVENTURE) service.unavailable(player);
        else service.onJoin(player);
    }

    private void left(UUID id) {
        pendingAccounts.remove(id);
        service.clear(id);
        panels.clear(id);
    }

    private void tick() {
        service.tickLandings();
        boolean refresh = ++ticks >= STATUS_INTERVAL_TICKS;
        if (refresh) ticks = 0;
        for (UUID id : pendingAccounts) {
            if (confined(id) && Bukkit.getPlayer(id) != null) service.track(id);
        }
        pendingAccounts.clear();
        for (UUID id : service.trackedAccounts()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !restrictions.enforce(player)) {
                left(id);
            } else if (player.getGameMode() != GameMode.ADVENTURE) {
                service.unavailable(player);
            } else {
                PrisonAssignment inmate = service.update(player, refresh);
                if (inmate == null) panels.clear(id);
                else if (refresh) panels.refresh(player, inmate);
            }
        }
        panels.tick();
    }
}
