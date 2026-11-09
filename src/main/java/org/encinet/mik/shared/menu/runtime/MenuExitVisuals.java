package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.encinet.mik.shared.menu.FloatingMenuEasing;

/** View-owned, frozen exit drawings share the live entity budget and cannot accept input. */
final class MenuExitVisuals {
    static final int MAX_ENTITIES = 128, MAX_TICKS = 8;
    private record Exit(List<MenuVisualSnapshot> parts, MenuSurfaceAttachment.Surface bounds, long started, int ticks) { }
    private final List<Exit> exits = new ArrayList<>();

    int size() { return exits.stream().mapToInt(exit -> exit.parts().size()).sum(); }
    List<MenuSurfaceAttachment.Surface> bounds() { return exits.stream().map(Exit::bounds).toList(); }
    Set<UUID> uuids() { return exits.stream().flatMap(exit -> exit.parts().stream()).map(MenuVisualSnapshot::uuid)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()); }

    boolean retain(List<MenuVisualSnapshot> parts, MenuSurfaceAttachment.Surface bounds, long tick, int ticks, int liveEntities) {
        if (parts.isEmpty() || bounds == null || ticks <= 0 || size() + parts.size() > Math.min(MAX_ENTITIES, MenuEntityBudget.remaining(liveEntities))) return false;
        exits.add(new Exit(List.copyOf(parts),bounds,tick,Math.min(MAX_TICKS,ticks)));
        return true;
    }

    void trim(int liveEntities, Consumer<List<MenuVisualSnapshot>> destroy) {
        while (!exits.isEmpty() && size() > Math.min(MAX_ENTITIES,MenuEntityBudget.remaining(liveEntities))) destroy.accept(exits.removeFirst().parts());
    }

    void advance(long tick, int liveEntities, boolean reducedMotion, Predicate<MenuSurfaceAttachment.Surface> safe,
                 Consumer<List<MenuVisualSnapshot>> destroy, BiConsumer<MenuVisualSnapshot,Double> render) {
        trim(liveEntities,destroy);
        for (var iterator = exits.iterator(); iterator.hasNext();) {
            var exit = iterator.next();
            if (reducedMotion || tick - exit.started() >= exit.ticks() || !safe.test(exit.bounds())) {
                destroy.accept(exit.parts()); iterator.remove(); continue;
            }
            double remaining = 1 - FloatingMenuEasing.CUBIC_OUT.apply((double) (tick - exit.started()) / exit.ticks());
            for (var part : exit.parts()) render.accept(part,remaining);
        }
    }

    void clear(Consumer<List<MenuVisualSnapshot>> destroy) {
        var retired = List.copyOf(exits);
        exits.clear();
        var shutdown = new org.encinet.mik.shared.util.ShutdownSequence();
        for (var exit : retired) shutdown.attempt("menu exit copy",() -> destroy.accept(exit.parts()));
        shutdown.finish("menu exit displays");
    }
}
