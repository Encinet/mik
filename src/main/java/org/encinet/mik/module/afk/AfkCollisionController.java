package org.encinet.mik.module.afk;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class AfkCollisionController {

    private static final String TEAM_NAME = "mik_afk_nocoll";

    private final Set<String> afkEntries = new LinkedHashSet<>();
    private final Set<UUID> viewersWithTeam = new LinkedHashSet<>();

    void add(Player player) {
        String entry = player.getName();
        if (afkEntries.add(entry)) {
            syncMembershipChange(WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Set.of(entry));
        }
    }

    void addAll(Collection<? extends Player> players) {
        Set<String> addedEntries = new LinkedHashSet<>();
        for (Player player : players) {
            String entry = player.getName();
            if (afkEntries.add(entry)) {
                addedEntries.add(entry);
            }
        }
        if (!addedEntries.isEmpty()) {
            syncMembershipChange(WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, addedEntries);
        }
    }

    void remove(Player player) {
        String entry = player.getName();
        if (afkEntries.remove(entry)) {
            if (afkEntries.isEmpty()) {
                removeFromAllViewers();
            } else {
                syncMembershipChange(WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES, Set.of(entry));
            }
        }
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

    private void syncMembershipChange(
            WrapperPlayServerTeams.TeamMode mode,
            Collection<String> changedEntries
    ) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewersWithTeam.contains(viewer.getUniqueId())) {
                sendMembershipChange(viewer, mode, changedEntries);
            } else {
                createTeam(viewer);
                viewersWithTeam.add(viewer.getUniqueId());
            }
        }
    }

    private void removeFromAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewersWithTeam.remove(viewer.getUniqueId())) {
                removeTeam(viewer);
            }
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

    private void sendMembershipChange(
            Player viewer,
            WrapperPlayServerTeams.TeamMode mode,
            Collection<String> changedEntries
    ) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                        TEAM_NAME,
                        mode,
                        (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                        changedEntries
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
