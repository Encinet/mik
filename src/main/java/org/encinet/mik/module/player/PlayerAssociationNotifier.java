package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.player.PlayerAssociationLookup.PlayerAssociation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerAssociationNotifier implements Listener {

    static final int MAX_CANDIDATES = 5;
    static final int MAX_TIMES_PER_CANDIDATE = 3;

    private static final String HELPER_PERMISSION = "group." + Mik.GROUP_HELPER;
    private static final long NOTIFY_DELAY_TICKS = 1L;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final PlayerAssociationLookup associationLookup;
    private final ZoneId zoneId;

    public PlayerAssociationNotifier(
            JavaPlugin plugin,
            LanguageService languageService,
            PlayerAssociationLookup associationLookup
    ) {
        this(plugin, languageService, associationLookup, ZoneId.systemDefault());
    }

    PlayerAssociationNotifier(
            JavaPlugin plugin,
            LanguageService languageService,
            PlayerAssociationLookup associationLookup,
            ZoneId zoneId
    ) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.associationLookup = associationLookup;
        this.zoneId = zoneId;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("PlayerAssociationNotifier enabled");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID subjectId = event.getPlayer().getUniqueId();
        String subjectName = event.getPlayer().getName();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> notifyHelpers(subjectId, subjectName, Instant.now()),
                NOTIFY_DELAY_TICKS);
    }

    private void notifyHelpers(UUID subjectId, String subjectName, Instant now) {
        List<PlayerAssociation> associations = associationLookup.findAssociations(subjectId);
        if (associations.isEmpty()) {
            return;
        }

        Map<UUID, String> candidateNames = resolveCandidateNames(associations);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission(HELPER_PERMISSION)) {
                viewer.sendMessage(render(viewer, subjectName, associations, candidateNames, now));
            }
        }
    }

    Component render(
            Player viewer,
            String subjectName,
            List<PlayerAssociation> associations,
            Map<UUID, String> candidateNames,
            Instant now
    ) {
        int displayedCount = Math.min(associations.size(), MAX_CANDIDATES);
        TextComponent.Builder report = Component.text()
                .append(languageService.text(viewer, Message.PLAYER_ASSOCIATION_HEADER,
                        NamedTextColor.GOLD, subjectName, associations.size())
                        .decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(languageService.text(
                                viewer,
                                Message.PLAYER_ASSOCIATION_HOVER,
                                NamedTextColor.GRAY))));

        for (int index = 0; index < displayedCount; index++) {
            PlayerAssociation association = associations.get(index);
            String candidateName = candidateNames.getOrDefault(
                    association.playerId(),
                    shortPlayerId(association.playerId()));
            report.append(Component.newline())
                    .append(languageService.text(viewer, Message.PLAYER_ASSOCIATION_ENTRY,
                            NamedTextColor.GRAY,
                            index + 1,
                            candidateName,
                            association.sharedAddressCount(),
                            formatTimes(viewer, association.matchedLastSeenAt(), now)));
        }

        int remainingPlayers = associations.size() - displayedCount;
        if (remainingPlayers > 0) {
            report.append(Component.newline())
                    .append(languageService.text(viewer, Message.PLAYER_ASSOCIATION_MORE_PLAYERS,
                            NamedTextColor.DARK_GRAY, remainingPlayers));
        }
        return report.build();
    }

    private String formatTimes(Player viewer, List<Instant> matchedTimes, Instant now) {
        Map<Long, Integer> occurrencesByDay = new LinkedHashMap<>();
        for (Instant matchedTime : matchedTimes) {
            long days = daysAgo(matchedTime, now, zoneId);
            occurrencesByDay.merge(days, 1, Integer::sum);
        }

        List<String> displayed = new ArrayList<>();
        int displayedAddressCount = 0;
        for (Map.Entry<Long, Integer> entry : occurrencesByDay.entrySet()) {
            if (displayed.size() >= MAX_TIMES_PER_CANDIDATE) {
                break;
            }
            int occurrenceCount = entry.getValue();
            String relativeTime = entry.getKey() == 0
                    ? languageService.t(viewer, Message.PLAYER_ASSOCIATION_TIME_TODAY)
                    : languageService.t(viewer, Message.PLAYER_ASSOCIATION_TIME_DAYS, entry.getKey());
            if (occurrenceCount > 1) {
                relativeTime = languageService.t(viewer, Message.PLAYER_ASSOCIATION_TIME_OCCURRENCES,
                        relativeTime, occurrenceCount);
            }
            displayed.add(relativeTime);
            displayedAddressCount += occurrenceCount;
        }

        int remainingAddresses = matchedTimes.size() - displayedAddressCount;
        if (remainingAddresses > 0) {
            displayed.add(languageService.t(viewer, Message.PLAYER_ASSOCIATION_MORE_ADDRESSES,
                    remainingAddresses));
        }
        return String.join(languageService.t(viewer, Message.PLAYER_ASSOCIATION_TIME_SEPARATOR), displayed);
    }

    private Map<UUID, String> resolveCandidateNames(List<PlayerAssociation> associations) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (PlayerAssociation association : associations.stream().limit(MAX_CANDIDATES).toList()) {
            UUID candidateId = association.playerId();
            Player online = Bukkit.getPlayer(candidateId);
            if (online != null) {
                names.put(candidateId, online.getName());
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(candidateId);
            String name = offline.getName();
            names.put(candidateId, name == null || name.isBlank() ? shortPlayerId(candidateId) : name);
        }
        return names;
    }

    static long daysAgo(Instant occurredAt, Instant now, ZoneId zoneId) {
        if (occurredAt == null || now == null || zoneId == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(
                occurredAt.atZone(zoneId).toLocalDate(),
                now.atZone(zoneId).toLocalDate());
        return Math.max(0L, days);
    }

    private static String shortPlayerId(UUID playerId) {
        return playerId.toString().substring(0, 8);
    }
}
