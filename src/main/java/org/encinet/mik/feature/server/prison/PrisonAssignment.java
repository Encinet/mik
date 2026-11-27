package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.server.ban.BanSeverity;
import org.encinet.mik.feature.server.prison.PrisonCell.Entrance;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

record PrisonAssignment(BanRecord ban, Plot plot, Plot cell, Entrance entrance) {
    static PrisonAssignment select(BanRecord ban, List<Region> prisons, Function<Plot, Entrance> entranceFor) {
        UUID player = ban.playerUuid();
        int size = prisons.size();
        if (player == null || size == 0) return null;
        int index = PrisonCell.prisonIndex(player, size);
        for (int attempt = 0; attempt < size; attempt++) {
            Region region = prisons.get(index);
            Plot cell = PrisonCell.cellOf(region, player);
            Entrance entrance = entranceFor.apply(cell);
            if (entrance != null) return new PrisonAssignment(ban, region.plot(), cell, entrance);
            if (++index == size) index = 0;
        }
        return null;
    }

    UUID playerId() { return ban.playerUuid(); }

    String playerName() {
        return ban.playerName() == null || ban.playerName().isBlank() ? playerId().toString() : ban.playerName();
    }

    String reason() { return BanSeverity.userReason(ban.reason()); }

    boolean isPermanent() { return ban.expiresAt() == null; }
}
