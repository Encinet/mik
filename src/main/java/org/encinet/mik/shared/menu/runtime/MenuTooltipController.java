package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** One read-only companion per viewer; rendering and input use the same placed surface. */
final class MenuTooltipController {
    private static final double HIT_MARGIN = .01;
    private final VirtualMenuEntityRenderer renderer;
    private final AxiomGizmoService.Scope gizmos;
    private final Map<UUID, State> states = new HashMap<>();
    MenuTooltipController(VirtualMenuEntityRenderer renderer, AxiomGizmoService.Scope gizmos) {
        this.renderer = renderer; this.gizmos = gizmos;
    }

    record Source(UiTooltip tooltip, Vector center, float yaw, float pitch, float roll, double scale, FloatingMenuSize size, boolean followsPointer) {
        Source { Objects.requireNonNull(tooltip); Objects.requireNonNull(size); center = center.clone(); }
        @Override public Vector center() { return center.clone(); }
        MenuSurfaceAttachment.Surface surface(double menuScale) {
            return new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(center), UiRotation.degrees(yaw, pitch, roll), size, scale * menuScale);
        }
    }

    void update(Player player, String hoveredKey, long tick, double scale, double typography,
                FloatingMenuFieldOfView fieldOfView, UiThemeColors theme, boolean immediate, Supplier<List<MenuSurfaceAttachment.Surface>> surfaces,
                Function<String, Source> resolve) {
        State state = states.get(player.getUniqueId());
        if (hoveredKey != null) {
            Source hovered = resolve.apply(hoveredKey);
            if (hovered == null) { clear(player.getUniqueId()); return; }
            if (state == null || !state.key.equals(hoveredKey) || (!hovered.followsPointer() && !state.tooltip.equals(hovered.tooltip()))
                    || state.typography != typography || !state.fieldOfView.equals(fieldOfView) || !state.theme.equals(theme)) {
                clear(player.getUniqueId());
                state = new State(hoveredKey, hovered.tooltip(), tick, typography, fieldOfView, theme);
                states.put(player.getUniqueId(), state);
            }
            if (immediate) state.lifetime.reveal();
        }
        if (state == null) return;
        Source source = resolve.apply(state.key);
        if (source == null) { clear(player.getUniqueId()); return; }
        if (!source.tooltip().equals(state.tooltip)) {
            if (!source.followsPointer()) { clear(player.getUniqueId()); return; }
            state.tooltip = source.tooltip();
            state.anchor = null;
            state.textDirty = true;
        }
        boolean reading = state.view != null && hit(player, state.view);
        var phase = state.lifetime.update(tick, state.key.equals(hoveredKey), reading);
        switch (phase) {
            case CLOSED -> clear(player.getUniqueId());
            case SUPPRESSED -> hide(player, player.getUniqueId(), state);
            case WAITING -> { }
            case VISIBLE -> {
                View view = place(player, state, source, scale, typography, fieldOfView, surfaces.get());
                if (view == null) hide(player, player.getUniqueId(), state);
                else render(player, state, view);
            }
        }
    }

    private View place(Player player, State state, Source source, double scale, double typography, FloatingMenuFieldOfView fieldOfView,
                       List<MenuSurfaceAttachment.Surface> surfaces) {
        var surface = source.surface(scale);
        double textScale = surface.scale() * typography * .85;
        var environment = new Environment(surface, MenuSurfaceAttachment.point(player.getEyeLocation().toVector()), surfaces, textScale);
        if (state.anchor == null || source.followsPointer()) {
            var pointer = MenuSurfaceAttachment.pointer(player.getEyeLocation(), surface, fieldOfView).orElse(null);
            if (pointer == null) return null;
            var text = MenuTooltipText.fit(state.tooltip, pointer.available(), textScale);
            state.textDirty |= !Objects.equals(state.text, text);
            state.text = text;
            if (state.text == null) return null;
            var size = state.text.layout().size();
            state.anchor = MenuSurfaceAttachment.attach(pointer, surface,
                    new FloatingMenuSize(size.width() * textScale, size.height() * textScale), surfaces).orElse(null);
            if (state.anchor == null) return null;
        } else if (!environment.equals(state.environment)) {
            var size = state.text.layout().size();
            state.anchor = state.anchor.inFront(environment.eye(), surface,
                    new FloatingMenuSize(size.width() * textScale, size.height() * textScale), surfaces);
        }
        state.environment = environment;
        var size = state.text.layout().size();
        var placed = state.anchor.place(surface);
        var angles = placed.rotation().angles();
        textScale *= state.anchor.readingScale();
        return new View(MenuSurfaceAttachment.vector(placed.center()), (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                size.width() * textScale, size.height() * textScale, textScale, HIT_MARGIN * surface.scale() * state.anchor.readingScale());
    }

    boolean occludes(Player player) {
        State state = states.get(player.getUniqueId());
        return state != null && state.view != null && hit(player, state.view);
    }

    Optional<MenuSurfaceAttachment.Surface> surface(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.view == null) return Optional.empty();
        View view = state.view;
        return Optional.of(new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(view.center()),
                UiRotation.degrees(view.yaw(),view.pitch(),view.roll()), new FloatingMenuSize(view.width(),view.height()),1));
    }

    boolean ownsEntity(UUID playerId, int entityId) {
        State state = states.get(playerId);
        return state != null && state.hitboxId != null && state.hitboxId == entityId;
    }

    boolean holds(Player player, long tick) {
        State state = states.get(player.getUniqueId());
        return state != null && state.view != null && (state.lifetime.holds(tick) || hit(player, state.view));
    }

    boolean consumeClick(Player player, boolean primary) {
        if (!occludes(player)) return false;
        if (primary) {
            State state = states.get(player.getUniqueId());
            state.lifetime.dismiss();
            hide(player, player.getUniqueId(), state);
        }
        return true;
    }

    private static boolean hit(Player player, View view) {
        Location eye = player.getEyeLocation();
        return FloatingMenuSurfaceGeometry.intersect(eye.toVector(), eye.getDirection(), view.center(), view.yaw(), view.pitch(), view.roll(),
                        view.width(), view.height(), view.margin())
                .filter(intersection -> FloatingMenuWorldOcclusion.clearToSurface(eye, intersection.distance())).isPresent();
    }

    private void render(Player player, State state, View view) {
        double textScale = view.textScale();
        boolean spawn = state.textId == null;
        if (spawn) {
            state.textId = VirtualMenuEntityRenderer.allocateId(player);
            state.hitboxId = VirtualMenuEntityRenderer.allocateId(player);
            gizmos.synchronize(player, state.owner, Set.of(state.textEntity, state.hitboxEntity));
        }
        Location center = new Location(player.getWorld(), view.center().getX(), view.center().getY(), view.center().getZ());
        Location textAt = center.clone().subtract(FloatingMenuSurfaceGeometry.panelUp(view.yaw(), view.pitch(), view.roll()).multiply(
                FloatingMenuNodeGeometry.textAnchorDown(state.text.layout().renderedHeight(), textScale)));
        var catcher = FloatingMenuSurfaceGeometry.catcher(view.yaw(), view.pitch(), view.roll(),
                view.width() + view.margin() * 2, view.height() + view.margin() * 2);
        Location hitboxAt = center.clone().subtract(0, catcher.height() / 2, 0);
        if (spawn) {
            renderer.spawn(player, state.textId, state.textEntity, VirtualMenuEntityRenderer.Kind.TEXT, textAt, 0, 0);
            renderer.spawn(player, state.hitboxId, state.hitboxEntity, VirtualMenuEntityRenderer.Kind.INTERACTION, hitboxAt, 0, 0);
        } else if (!view.equals(state.view) || textScale != state.textScale) {
            renderer.teleport(player, state.textId, textAt, 0, 0);
            renderer.teleport(player, state.hitboxId, hitboxAt, 0, 0);
        }
        if (spawn || state.textDirty || textScale != state.textScale || view.yaw() != state.view.yaw() || view.pitch() != state.view.pitch() || view.roll() != state.view.roll()) {
            renderer.text(player, state.textId, state.text.content(), UiTheme.TOOLTIP.backgroundArgb(state.theme),
                    state.text.layout().displayWidth(), state.text.layout().displayHeight(), state.text.layout().lineWidthPixels(),
                    (float) (FloatingMenuNodeSizing.TEXT_SCALE * textScale), FloatingMenuDecoration.Alignment.LEFT, false,
                    1, 1, UiRotation.degrees(view.yaw(), view.pitch(), view.roll()));
            state.textDirty = false;
        }
        if (spawn || !view.equals(state.view)) {
            renderer.interaction(player, state.hitboxId, (float) catcher.width(), (float) catcher.height());
        }
        state.view = view;
        state.textScale = textScale;
    }

    private void hide(Player player, UUID playerId, State state) {
        if (state.textId == null && state.hitboxId == null) return;
        ShutdownSequence shutdown = new ShutdownSequence();
        if (player != null && player.isOnline()) {
            if (state.textId != null) shutdown.attempt("hint text", () -> renderer.destroy(player, state.textId));
            if (state.hitboxId != null) shutdown.attempt("hint input catcher", () -> renderer.destroy(player, state.hitboxId));
            shutdown.attempt("hint gizmos", () -> gizmos.remove(player, state.owner));
        } else gizmos.forget(playerId, state.owner);
        state.textId = state.hitboxId = null;
        state.view = null;
        shutdown.finish("menu hint");
    }

    void clear(UUID playerId) {
        State state = states.remove(playerId);
        if (state != null) hide(Bukkit.getPlayer(playerId), playerId, state);
    }
    void clearAll() {
        ShutdownSequence shutdown = new ShutdownSequence();
        for (UUID playerId : List.copyOf(states.keySet())) shutdown.attempt("viewer hint", () -> clear(playerId));
        shutdown.finish("menu hints");
    }
    void close() { try { clearAll(); } finally { gizmos.close(); } }

    private record View(Vector center, float yaw, float pitch, float roll, double width, double height, double textScale, double margin) { }
    private record Environment(MenuSurfaceAttachment.Surface source, UiVector3 eye, List<MenuSurfaceAttachment.Surface> surfaces, double textScale) { }

    private static final class State {
        final String key;
        UiTooltip tooltip;
        boolean textDirty;
        final MenuHintLifetime lifetime;
        final double typography;
        final FloatingMenuFieldOfView fieldOfView;
        final UiThemeColors theme;
        MenuTooltipText text;
        MenuSurfaceAttachment anchor;
        Environment environment;
        final UUID owner = UUID.randomUUID(), textEntity = UUID.randomUUID(), hitboxEntity = UUID.randomUUID();
        Integer textId, hitboxId;
        View view;
        double textScale;
        State(String key, UiTooltip tooltip, long since, double typography, FloatingMenuFieldOfView fieldOfView, UiThemeColors theme) {
            this.key = key; this.tooltip = tooltip; this.typography = typography; this.fieldOfView = fieldOfView; this.theme = theme;
            lifetime = new MenuHintLifetime(since, tooltip.delayTicks());
        }
    }
}
