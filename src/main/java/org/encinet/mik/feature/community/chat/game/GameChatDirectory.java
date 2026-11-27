package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex;
import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.shared.role.RolePermissions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Owns Paper's presence, visibility and recipient rules. Only immutable facts leave the game thread. */
public final class GameChatDirectory {
    public record GamePeer(UUID id, String name, Instant joinedAt, boolean eligible, boolean staff,
                           Optional<Set<UUID>> viewers) {
        public GamePeer { viewers = viewers.map(Set::copyOf); }
    }

    record Participant(UUID playerUuid, String name) { }

    public record WebPeer(UUID id, String name, int connections, boolean staff) { }

    private record Connection(Player player, UUID id, String name, boolean eligible, boolean staff) { }

    private record Roster(List<Connection> connections, Map<Player, Connection> bySession, Map<UUID, GamePeer> byId,
                          List<GamePeer> peers) {
        private static final Roster EMPTY = new Roster(List.of(), Map.of(), Map.of(), List.of());
    }

    private final Predicate<Player> authenticated;
    private final BiPredicate<UUID, String> allowed;
    private record Snapshot(Roster game, Map<UUID, WebPeer> web, PlayerMentionIndex names) {
        private static final Snapshot EMPTY = new Snapshot(Roster.EMPTY, Map.of(), PlayerMentionIndex.empty());
    }

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private PlayerMentionIndex liveNames = PlayerMentionIndex.empty();
    private BukkitTask sampling;

    public GameChatDirectory(Predicate<Player> authenticated, BiPredicate<UUID, String> allowed) {
        this.authenticated = authenticated;
        this.allowed = allowed;
    }

    public void enable(Plugin plugin) {
        refresh();
        sampling = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, 10);
    }

    public void disable() {
        if (sampling != null) sampling.cancel();
        sampling = null;
        snapshot = Snapshot.EMPTY;
        liveNames = PlayerMentionIndex.empty();
    }

    /** Sample once for both local async work and any external state projections. */
    void refresh() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Game chat sampling requires the primary thread");
        List<Connection> online = liveConnections().stream().sorted(Comparator.comparing(Connection::id)).toList();
        var peers = new ArrayList<GamePeer>(online.size());
        var byId = new HashMap<UUID, GamePeer>();
        var bySession = new IdentityHashMap<Player, Connection>();
        for (var subject : online) {
            var visibleTo = new HashSet<UUID>();
            for (var viewer : online) {
                if (viewer.player() == subject.player() || viewer.player().canSee(subject.player())) visibleTo.add(viewer.id());
            }
            boolean visible = subject.player().isVisibleByDefault() && visibleTo.size() == online.size();
            var peer = new GamePeer(subject.id(), subject.name(), Instant.ofEpochMilli(subject.player().getLastLogin()),
                    subject.eligible(), subject.staff(), visible ? Optional.empty() : Optional.of(visibleTo));
            peers.add(peer);
            byId.put(peer.id(), peer);
            bySession.put(subject.player(), subject);
        }
        var roster = new Roster(online, Collections.unmodifiableMap(bySession), Map.copyOf(byId), List.copyOf(peers));
        var browsers = snapshot.web();
        snapshot = new Snapshot(roster, browsers, indexNames(online, browsers));
    }

    public List<GamePeer> gamePeers() { return snapshot.game().peers(); }

    List<WebPeer> webPeers() { return List.copyOf(snapshot.web().values()); }

    public void replaceWebPeers(List<WebPeer> peers) {
        var next = peers.stream().collect(Collectors.toUnmodifiableMap(WebPeer::id, peer -> peer));
        var current = snapshot;
        if (!current.web().equals(next))
            snapshot = new Snapshot(current.game(), next, indexNames(current.game().connections(), next));
    }

    public List<UUID> disallowedWebPlayers() {
        return snapshot.web().values().stream().filter(peer -> !allowed(peer.id(), peer.name())).map(WebPeer::id).toList();
    }

    public boolean webAvailable(UUID id) {
        var peer = snapshot.web().get(id);
        return peer != null && active(peer);
    }

    private boolean active(WebPeer peer) {
        return peer.connections() > 0 && allowed(peer.id(), peer.name());
    }

    public boolean allowed(UUID id, String name) { return allowed.test(id, name); }

    boolean eligible(Player player) {
        if (Bukkit.isPrimaryThread()) return player.isOnline() && allowed(player.getUniqueId(), player.getName())
                && authenticated.test(player);
        var connection = snapshot.game().bySession().get(player);
        return connection != null && connection.eligible();
    }

    boolean staff(Player player) {
        if (Bukkit.isPrimaryThread()) return RolePermissions.canModerate(player);
        var connection = snapshot.game().bySession().get(player);
        return connection != null && connection.staff();
    }

    List<Participant> peers(UUID viewer) {
        var current = snapshot;
        return peers(viewer, current).values().stream().sorted(Comparator.comparing(Participant::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Participant::playerUuid)).toList();
    }

    Participant peer(UUID viewer, UUID target) {
        var current = snapshot;
        if (!visibility(current).test(viewer, target)) return null;
        if (Bukkit.isPrimaryThread()) {
            var player = Bukkit.getPlayer(target);
            if (player != null && eligible(player)) return new Participant(target, player.getName());
        } else {
            var peer = current.game().byId().get(target);
            if (peer != null && peer.eligible()) return new Participant(target, peer.name());
        }
        var browser = current.web().get(target);
        return browser != null && active(browser) ? new Participant(target, browser.name()) : null;
    }

    Participant findPeer(UUID viewer, String name) {
        var matches = peers(viewer).stream().filter(peer -> peer.name().equalsIgnoreCase(name)).limit(2).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private Map<UUID, Participant> peers(UUID viewer, Snapshot current) {
        var visibility = visibility(current);
        var peers = new HashMap<UUID, Participant>();
        for (var peer : current.web().values()) {
            if (active(peer) && visibility.test(viewer, peer.id()))
                peers.put(peer.id(), new Participant(peer.id(), peer.name()));
        }
        for (var peer : connections(current)) {
            if (peer.eligible() && visibility.test(viewer, peer.id()))
                peers.put(peer.id(), new Participant(peer.id(), peer.name()));
        }
        return peers;
    }

    PlayerMentions mentions(UUID author, ChatAudience audience, Set<UUID> gameViewers, boolean includeWeb) {
        var current = snapshot;
        var browsers = current.web();
        var local = connections(current);
        var visible = visibility(current);
        Set<UUID> subjects = subjects(audience, author);
        var permitted = new HashSet<UUID>(gameViewers);
        if (includeWeb && !browsers.isEmpty()) {
            var activeGame = local.stream().filter(Connection::eligible).map(Connection::id).collect(Collectors.toSet());
            for (var peer : browsers.values()) {
                if (active(peer) && audience.includes(peer.id(), peer.staff())
                        && visible.test(author, peer.id()) && canRead(peer.id(), subjects, visible)
                        // A second endpoint does not put an excluded game player back in this event.
                        && !activeGame.contains(peer.id())) {
                    permitted.add(peer.id());
                }
            }
        }
        permitted.removeIf(id -> id.equals(author) || !visible.test(author, id));
        var index = Bukkit.isPrimaryThread() ? indexNames(local, browsers) : current.names();
        return new PlayerMentions(index, permitted, author);
    }

    private PlayerMentionIndex indexNames(List<Connection> local, Map<UUID, WebPeer> browsers) {
        var targets = new TreeMap<UUID, Target>();
        browsers.values().forEach(peer -> targets.put(peer.id(), new Target(peer.id(), peer.name())));
        local.forEach(peer -> targets.put(peer.id(), new Target(peer.id(), peer.name())));
        liveNames = liveNames.withTargets(targets.values());
        return liveNames;
    }

    Map<UUID, Player> gameRecipients(ChatAudience audience, UUID author, Optional<Set<UUID>> visibility) {
        var current = snapshot;
        var visible = visibility(current);
        var subjects = subjects(audience, author);
        return connections(current).stream()
                .filter(Connection::eligible)
                .filter(peer -> audience.includes(peer.id(), peer.staff()))
                .filter(peer -> visibility.isEmpty() || visibility.orElseThrow().contains(peer.id()))
                .filter(peer -> canRead(peer.id(), subjects, visible))
                .collect(Collectors.toUnmodifiableMap(Connection::id, Connection::player));
    }

    /** A shared copy may narrow, but never extend, the logical audience. */
    Optional<Set<UUID>> visibilityLimit(ChatAudience audience, UUID author) {
        var current = snapshot;
        var visible = visibility(current);
        var subjects = subjects(audience, author);
        if (canRead(null, subjects, visible)) return Optional.empty();
        var candidates = new HashSet<>(audience.participants());
        if (audience.channel() != ChatChannel.PRIVATE) {
            candidates.addAll(current.web().keySet());
            connections(current).stream().map(Connection::id).forEach(candidates::add);
        }
        return Optional.of(candidates.stream().filter(viewer -> canRead(viewer, subjects, visible)).collect(Collectors.toUnmodifiableSet()));
    }

    boolean globallyVisible(ChatAudience audience, UUID author) {
        return canRead(null, subjects(audience, author), visibility(snapshot));
    }

    private static Set<UUID> subjects(ChatAudience audience, UUID author) {
        if (audience.channel() == ChatChannel.PRIVATE) return audience.participants();
        return author == null ? Set.of() : Set.of(author);
    }

    private static boolean canRead(UUID viewer, Set<UUID> subjects, BiPredicate<UUID, UUID> visible) {
        return subjects.stream().allMatch(subject -> visible.test(viewer, subject));
    }

    public boolean canSee(UUID viewer, UUID target) {
        return visibility(snapshot).test(viewer, target);
    }

    private static BiPredicate<UUID, UUID> visibility(Snapshot current) {
        return Bukkit.isPrimaryThread() ? GameChatDirectory::canSeeLive : (viewer, target) -> {
            var subject = current.game().byId().get(target);
            return target.equals(viewer) || subject == null || subject.viewers().isEmpty()
                    || viewer != null && subject.viewers().orElseThrow().contains(viewer);
        };
    }

    private static boolean canSeeLive(UUID viewer, UUID target) {
        if (target.equals(viewer)) return true;
        Player subject = Bukkit.getPlayer(target);
        if (subject == null) return true;
        Player observer = viewer == null ? null : Bukkit.getPlayer(viewer);
        if (observer != null) return observer.canSee(subject);
        return subject.isVisibleByDefault() && Bukkit.getOnlinePlayers().stream()
                .allMatch(player -> player == subject || player.canSee(subject));
    }

    private List<Connection> connections(Snapshot current) {
        return Bukkit.isPrimaryThread() ? liveConnections() : current.game().connections();
    }

    private List<Connection> liveConnections() {
        return Bukkit.getOnlinePlayers().stream().map(this::connection).toList();
    }

    private Connection connection(Player player) {
        return new Connection(player, player.getUniqueId(), player.getName(), eligible(player), RolePermissions.canModerate(player));
    }
}
