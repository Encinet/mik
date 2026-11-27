package org.encinet.mik.feature.server.ban.prison;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.menu.runtime.space.WallPanelPlacement;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiLength;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

final class PrisonPanelView {
    private static final UiKey PANEL = UiKey.named("prison-ban");
    private static final int STEPS_PER_TICK = 8;
    private static final int QUEUE_LIMIT = 64;

    private final PrisonService service;
    private final WorldMenuScope panels;
    private final LanguageService language;
    private final WallPanelPlacement placement = new WallPanelPlacement();
    private final Map<UUID, Entry> viewers = new HashMap<>();
    private final ArrayDeque<Step> steps = new ArrayDeque<>();

    PrisonPanelView(PrisonService service, WorldMenuScope panels, LanguageService language) {
        this.service = service;
        this.panels = panels;
        this.language = language;
    }

    void tick() {
        for (int i = 0; i < STEPS_PER_TICK && !steps.isEmpty(); i++) {
            Step step = steps.removeFirst();
            if (!step.cancelled && !step.work.getAsBoolean()) steps.addLast(step);
        }
    }

    private WallPanelPlacement.Scheduler.Task submit(BooleanSupplier work) {
        if (steps.size() >= QUEUE_LIMIT) return null;
        Step step = new Step(work);
        steps.addLast(step);
        return () -> step.cancelled = true;
    }

    void refresh(Player player, PrisonAssignment inmate) {
        UUID id = player.getUniqueId();
        var anchor = service.panelAnchor(id);
        if (anchor == null) {
            clear(id);
            return;
        }
        Entry entry = viewers.get(id);
        if (entry == null || !entry.source.matches(inmate.cell(), anchor)) {
            clear(id);
            entry = new Entry(PrisonPanelSource.forCell(inmate.cell(), anchor));
            viewers.put(id, entry);
        }
        if (entry.plan != null && entry.plan.valid()) {
            show(player, inmate, entry);
        } else if (entry.request == null || !entry.request.active()) {
            Entry target = entry;
            entry.request = placement.prepare(entry.source, this::submit, plan -> {
                if (plan == null || viewers.get(id) != target) return;
                target.plan = plan;
                current(id, target).ifPresent(current -> show(player, current, target));
            });
        }
    }

    void clear(UUID player) {
        Entry entry = viewers.remove(player);
        if (entry == null) return;
        if (entry.request != null) entry.request.cancel();
        panels.clear(player);
    }

    void invalidate(Block block) { placement.invalidate(block); }

    void invalidateChunk(UUID world, int x, int z) { placement.invalidateChunk(world, x, z); }

    void invalidateWorld(UUID world) { placement.invalidateWorld(world); }

    void close() {
        placement.clear();
        steps.clear();
        viewers.clear();
    }

    private void show(Player player, PrisonAssignment inmate, Entry entry) {
        var plan = entry.plan;
        String reason = inmate.reason();
        if (reason == null || reason.isBlank()) reason = language.t(player, Message.BAN_UNKNOWN_REASON);
        var scene = MenuScene.screen("prison-ban:" + inmate.playerId())
                .content(Ui.column().gap(UiLength.ui(4))
                        .child(UiKey.named("title"), Ui.heading(label(player, Message.PRISON_PANEL_TITLE)).build())
                        .child(UiKey.named("reason"), Ui.text(label(player, Message.PRISON_PANEL_BAN_REASON)
                                .append(Component.text(" " + reason, NamedTextColor.WHITE))).build())
                        .child(UiKey.named("remaining"), Ui.text(label(player, Message.PRISON_PANEL_REMAINING)
                                .append(Component.space()).append(remaining(player, inmate))).build())
                        .build())
                .appearance(FloatingMenuAppearance.NO_BACKGROUNDS).animation(FloatingMenuAnimation.PLANAR)
                .build();
        panels.show(player, PANEL, plan.surface(), scene, _ -> current(inmate.playerId(), entry).isPresent() && plan.valid());
    }

    private Component remaining(Player player, PrisonAssignment inmate) {
        if (inmate.isPermanent()) return language.text(player, Message.PRISON_PANEL_PERMANENT, NamedTextColor.YELLOW);
        return Component.text(PrisonDuration.remaining(inmate.ban().expiresAt().toEpochMilli(),
                System.currentTimeMillis()), NamedTextColor.YELLOW);
    }

    private Optional<PrisonAssignment> current(UUID id, Entry entry) {
        return service.prisoner(id).filter(inmate -> entry.source.matches(inmate.cell(), service.panelAnchor(id)));
    }

    private Component label(Player player, Message message) {
        return language.text(player, message, NamedTextColor.GOLD);
    }

    private static final class Entry {
        private final PrisonPanelSource source;
        private WallPanelPlacement.Plan plan;
        private WallPanelPlacement.Request request;

        private Entry(PrisonPanelSource source) { this.source = source; }
    }

    private static final class Step {
        private final BooleanSupplier work;
        private boolean cancelled;

        private Step(BooleanSupplier work) { this.work = work; }
    }
}
