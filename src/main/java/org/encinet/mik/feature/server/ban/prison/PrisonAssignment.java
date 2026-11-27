package org.encinet.mik.feature.server.ban.prison;

import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.server.ban.BanSeverity;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

record PrisonAssignment(BanRecord ban, Plot plot, Plot cell) {
    static PrisonAssignment select(BanRecord ban, List<Region> prisons, Predicate<UUID> loaded) {
        UUID player = ban.playerUuid();
        if (player == null) return null;
        Region best = null;
        long bestScore = 0;
        for (Region region : prisons) {
            Plot plot = region.plot();
            if (!loaded.test(plot.world())) continue;
            long score = score(player, plot.id());
            if (best == null || wins(plot.id(), score, best.plot().id(), bestScore)) {
                best = region;
                bestScore = score;
            }
        }
        if (best == null) return null;
        Plot cell = best.plot();
        bestScore = 0;
        boolean found = false;
        for (Plot candidate : best.cells()) {
            long score = score(player, candidate.id());
            if (!found || wins(candidate.id(), score, cell.id(), bestScore)) {
                cell = candidate;
                bestScore = score;
                found = true;
            }
        }
        return new PrisonAssignment(ban, best.plot(), cell);
    }

    private static boolean wins(UUID id, long score, UUID best, long bestScore) {
        int order = Long.compareUnsigned(score, bestScore);
        return order > 0 || (order == 0 && id.compareTo(best) < 0);
    }

    private static long score(UUID player, UUID candidate) {
        long upper = mix(player.getMostSignificantBits() ^ candidate.getMostSignificantBits());
        long lower = mix(player.getLeastSignificantBits() ^ candidate.getLeastSignificantBits());
        return mix(upper ^ Long.rotateLeft(lower, 29));
    }

    private static long mix(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    boolean sameRoom(PrisonAssignment other) {
        return other != null && cell.id().equals(other.cell.id()) && cell.world().equals(other.cell.world())
                && cell.cells() == other.cell.cells();
    }

    UUID playerId() { return ban.playerUuid(); }

    String playerName() {
        return ban.playerName() == null || ban.playerName().isBlank() ? playerId().toString() : ban.playerName();
    }

    String reason() { return BanSeverity.userReason(ban.reason()); }

    boolean isPermanent() { return ban.expiresAt() == null; }
}
