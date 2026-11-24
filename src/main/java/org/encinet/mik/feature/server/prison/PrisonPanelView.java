package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.WallPanelPlacement;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiLength;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Read-only floating menu on a wall next to a prisoner's cell: ban reason, sentence remaining,
 * release point. Placement reuses the shared {@link WallPanelPlacement} wall-search algorithm;
 * cooperative steps are budgeted per tick so many panels can mount at once.
 */
public final class PrisonPanelView implements WallPanelPlacement.Scheduler {
    private static final UiKey PANEL = UiKey.named("prison-ban");
    private static final int MAX_STEPS = 8;
    private static final int MAX_QUEUED = 64;

    private final PrisonService service;
    private final PrisonPlotHost host;
    private final WorldMenuScope panels;
    private final LanguageService language;
    private final WallPanelPlacement placement = new WallPanelPlacement();
    private final Map<UUID, Entry> viewers = new HashMap<>();
    private final ArrayDeque<Step> steps = new ArrayDeque<>();

    private static final class Step {
        final Task task;
        final BooleanSupplier work;
        Step(Task task, BooleanSupplier work) { this.task = task; this.work = work; }
    }

    private static final class Task implements WallPanelPlacement.Scheduler.Task {
        private volatile boolean cancelled;
        @Override public void cancel() { cancelled = true; }
        boolean cancelled() { return cancelled; }
    }

    private final class Entry {
        PrisonerRecord prisoner;
        WallPanelPlacement.Plan plan;
    }

    public PrisonPanelView(PrisonService service, PrisonPlotHost host, WorldMenuScope panels,
                           LanguageService language) {
        this.service = service;
        this.host = host;
        this.panels = panels;
        this.language = language;
    }

    @Override public Task submit(BooleanSupplier work) {
        if (steps.size() >= MAX_QUEUED) return null;
        Task task = new Task();
        steps.addLast(new Step(task, work));
        return task;
    }

    /** Drive cooperative placement work; call from the prison tick. */
    public void tick() {
        for (int step = 0; step < MAX_STEPS && !steps.isEmpty(); step++) {
            Step current = steps.peekFirst();
            if (current == null || current.task.cancelled()) { steps.pollFirst(); continue; }
            if (current.work.getAsBoolean()) {
                steps.pollFirst();
            } else {
                // Rotate the unfinished step to the back of the queue.
                steps.pollFirst();
                steps.addLast(current);
            }
        }
    }

    /** Renders or refreshes the panel for an online inmate; no-ops when the cell is unresolvable. */
    public void panel(Player player, PrisonerRecord prisoner, Plot plot) {
        PrisonPanelSource source = PrisonPanelSource.forCell(host, prisoner.playerId(), plot);
        if (source == null) {
            Entry stale = viewers.get(player.getUniqueId());
            if (stale != null && stale.prisoner == prisoner) clear(player, stale);
            return;
        }
        Entry entry = new Entry();
        entry.prisoner = prisoner;
        viewers.put(player.getUniqueId(), entry);
        placement.prepare(source, this, plan -> {
            Entry current = viewers.get(player.getUniqueId());
            if (current == null || current.prisoner != entry.prisoner || plan == null) return;
            current.plan = plan;
            show(player, current);
        });
    }

    /** Clears the panel for one inmate; called on release, plot removal, or quit. */
    public void clear(Player player, Entry view) {
        if (viewers.get(player.getUniqueId()) == view) {
            viewers.remove(player.getUniqueId());
            panels.clear(player, PANEL);
        }
    }

    public void clear(UUID player) {
        if (viewers.remove(player) != null) panels.clear(player);
    }

    /** Invalidates cached panel mounts touched by a block change; called from the block listeners. */
    public void invalidate(org.bukkit.block.Block block) {
        placement.invalidate(block);
    }

    /** Invalidates cached panel mounts for a whole chunk (chunk unload). */
    public void invalidateChunk(UUID world, int x, int z) {
        placement.invalidateChunk(world, x, z);
    }

    /** Releases every panel; called on module disable. */
    public void close() {
        placement.clear();
        steps.clear();
        viewers.clear();
    }

    private void show(Player player, Entry entry) {
        var record = entry.prisoner;
        var scene = MenuScene.screen("prison-ban:" + record.playerId())
                .content(Ui.column().gap(UiLength.ui(4))
                        .child(UiKey.named("title"), Ui.heading(label(player, Message.PRISON_PANEL_TITLE)).build())
                        .child(UiKey.named("reason"), Ui.text(label(player, Message.PRISON_PANEL_BAN_REASON)
                                .append(Component.text(" " + record.reason(), NamedTextColor.GRAY))).build())
                        .child(UiKey.named("remaining"), Ui.text(label(player, Message.PRISON_PANEL_REMAINING)
                                .append(Component.text(" ", NamedTextColor.YELLOW))
                                .append(inmateRemaining(player, record))).build())
                        .child(UiKey.named("exit"), Ui.text(label(player, Message.PRISON_PANEL_EXIT_LOCATION)
                                .append(Component.text(" " + exitLabel(record), NamedTextColor.GREEN))).build())
                        .build())
                .appearance(FloatingMenuAppearance.NO_BACKGROUNDS).animation(FloatingMenuAnimation.PLANAR)
                .build();
        panels.show(player, PANEL, entry.plan.surface(), scene,
                _ -> stillJailed(record) && entry.plan.valid());
    }

    /** A permanent ban has no remaining time, so it shows the permanent label instead of a countdown. */
    private Component inmateRemaining(Player player, PrisonerRecord record) {
        if (record.isPermanent()) return language.text(player, Message.PRISON_PANEL_PERMANENT, NamedTextColor.YELLOW);
        return Component.text(PrisonDuration.remaining(record.ban().expiresAt().toEpochMilli(),
                System.currentTimeMillis()), NamedTextColor.YELLOW);
    }

    private boolean stillJailed(PrisonerRecord record) {
        return service.prisoner(record.playerId()).isPresent();
    }

    private String exitLabel(PrisonerRecord record) {
        var exit = service.exitFor(record.plot().id());
        if (exit == null) return "—";
        var location = exit.resolve();
        return location == null ? "—" : String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f",
                location.getX(), location.getY(), location.getZ());
    }

    private Component label(Player player, Message message) {
        return language.text(player, message, NamedTextColor.GOLD);
    }

    /** Reconciles panels each tick: drop the released and the stale, keep live ones mounted. */
    public void reconcile() {
        Map<UUID, PrisonerRecord> current = new HashMap<>();
        for (PrisonerRecord inmate : service.prisoners()) current.put(inmate.playerId(), inmate);
        viewers.values().removeIf(entry -> {
            PrisonerRecord live = current.get(entry.prisoner.playerId());
            if (live != null && live.equals(entry.prisoner)) return false;
            Player online = Bukkit.getPlayer(entry.prisoner.playerId());
            if (online != null) panels.clear(online, PANEL);
            return true;
        });
    }
}
