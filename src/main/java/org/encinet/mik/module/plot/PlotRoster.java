package org.encinet.mik.module.plot;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

final class PlotRoster {
    record Entry(UUID id, PlotAccessPolicy.Group group) {
        boolean owner() { return group == PlotAccessPolicy.Group.OWNER; }

        PlotAccessPolicy.Subject permissionSubject() {
            return owner() ? PlotAccessPolicy.Subject.group(group) : PlotAccessPolicy.Subject.player(id);
        }
    }

    private PlotRoster() { }

    static Entry entry(Plot plot, UUID player) {
        if (!plot.owner().equals(player) && !plot.members().containsKey(player)) return null;
        return new Entry(player, PlotAccessPolicy.actorGroup(plot, player, false));
    }

    static int size(Plot plot) {
        return plot.members().size() + (plot.members().containsKey(plot.owner()) ? 0 : 1);
    }

    static List<Entry> entries(Plot plot) {
        return Stream.concat(Stream.of(entry(plot, plot.owner())),
                plot.members().keySet().stream().filter(player -> !plot.owner().equals(player))
                        .map(player -> entry(plot, player))).toList();
    }
}
