package org.encinet.mik.module.afk;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class AfkCollisionController {

    private static final String TEAM_NAME = "mik_afk_nocoll";

    private final Set<String> afkEntries = new LinkedHashSet<>();
    private final Set<UUID> viewersWithTeam = new LinkedHashSet<>();

    void add(Player player) {
        if (afkEntries.add(player.getName())) {
            syncAllViewers();
        }
    }

    void remove(Player player) {
        if (afkEntries.remove(player.getName())) {
            syncAllViewers();
        }
        forgetViewer(player);
    }

    void syncViewer(Player viewer) {
        if (afkEntries.isEmpty()) {
            if (viewersWithTeam.remove(viewer.getUniqueId())) {
                removeTeam(viewer);
            }
            return;
        }
        if (viewersWithTeam.contains(viewer.getUniqueId())) {
            removeTeam(viewer);
        }
        createTeam(viewer);
        viewersWithTeam.add(viewer.getUniqueId());
    }

    void clear() {
        for (UUID viewerId : Set.copyOf(viewersWithTeam)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                removeTeam(viewer);
            }
        }
        afkEntries.clear();
        viewersWithTeam.clear();
    }

    void forgetViewer(Player viewer) {
        viewersWithTeam.remove(viewer.getUniqueId());
    }

    private void syncAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            syncViewer(viewer);
        }
    }

    private void createTeam(Player viewer) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                        TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.CREATE,
                        teamInfo(),
                        afkEntries
                )
        );
    }

    private void removeTeam(Player viewer) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                        TEAM_NAME,
                        WrapperPlayServerTeams.TeamMode.REMOVE,
                        (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                        Set.of()
                )
        );
    }

    private WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo() {
        return new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                NamedTextColor.WHITE,
                WrapperPlayServerTeams.OptionData.NONE
        );
    }
}
