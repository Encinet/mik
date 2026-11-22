package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ChatRecordCodec;
import org.encinet.mik.feature.community.chat.bridge.ChatRelay;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatParticipant;
import org.encinet.mik.integration.mikd.MikdModule.ObservedPlayer;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatGamePeer;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatPoll;
import org.encinet.mik.protocol.ChatWebPeer;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.HistoryEntry;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PaperChatBridge implements ChatParticipants, ChatRelay, AutoCloseable {
    private static final ChatCommand POLL = ChatCommand.newBuilder().setPoll(ChatPoll.getDefaultInstance()).build();

    @FunctionalInterface
    public interface RoutedOutput {
        void deliver(ChatDelivery delivery, Set<UUID> recipients, boolean publicVisibility);
    }

    private final Plugin plugin;
    private final LanguageService language;
    private final RequestPort daemon;
    private final Predicate<Player> authenticated;
    private final BiPredicate<UUID, String> allowed;
    private final Consumer<List<ChatWebPeer>> webRoster;
    private final RoutedOutput gameOutput;
    private volatile ChatGameRoster game = ChatGameRoster.getDefaultInstance();
    private volatile Map<UUID, ChatWebPeer> web = Map.of();
    private volatile Thread worker;
    private ChatWebRoster lastWeb = ChatWebRoster.getDefaultInstance();

    public PaperChatBridge(Plugin plugin, LanguageService language, RequestPort daemon, Predicate<Player> authenticated,
            BiPredicate<UUID, String> allowed, Consumer<List<ChatWebPeer>> webRoster,
            RoutedOutput gameOutput) {
        this.plugin = plugin;
        this.language = language;
        this.daemon = daemon;
        this.authenticated = authenticated;
        this.allowed = allowed;
        this.webRoster = webRoster;
        this.gameOutput = gameOutput;
    }

    public void start() {
        worker = Thread.ofVirtual().name("mik-paper-chat").unstarted(this::run);
        worker.start();
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var pending = daemon.chat(POLL);
                List<HistoryEntry> entries;
                try { entries = pending.get(4, TimeUnit.SECONDS).getDeliveries().getEntriesList(); }
                finally { if (!pending.isDone()) pending.cancel(false); }
                if (entries.isEmpty()) continue;
                var delivery = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    if (worker != null) entries.forEach(this::apply);
                    return null;
                });
                try { delivery.get(2, TimeUnit.SECONDS); }
                finally { if (!delivery.isDone()) delivery.cancel(false); }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException unavailable) {
                try { Thread.sleep(500); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
            }
        }
    }

    public ChatGameRoster observe(List<ObservedPlayer> observed) {
        var roster = ChatGameRoster.newBuilder();
        for (var observation : observed) {
            var subject = observation.player();
            roster.addPlayers(ChatGamePeer.newBuilder()
                    .setPlayer(ChatPlayer.newBuilder().setId(subject.getUniqueId().toString()).setName(subject.getName()))
                    .setConcealed(observation.concealed()).setVisibleTo(observation.visibleTo())
                    .setEligible(eligible(subject)).setStaff(RolePermissions.canModerate(subject)));
        }
        web.forEach((id, peer) -> {
            if (!allowed.test(id, peer.getPlayer().getName())) invalidate(id);
        });
        game = roster.build();
        return game;
    }

    public void receiveWebRoster(ChatWebRoster roster) {
        if (lastWeb.equals(roster)) return;
        lastWeb = roster;
        var next = roster.getPlayersList().stream()
                .collect(Collectors.toUnmodifiableMap(peer -> UUID.fromString(peer.getPlayer().getId()), peer -> peer));
        if (!web.equals(next)) {
            web = next;
            webRoster.accept(roster.getPlayersList());
        }
    }

    private void apply(HistoryEntry entry) {
        if (entry.getRecord().hasNotice()) {
            var notice = entry.getRecord().getNotice();
            var subject = UUID.fromString(notice.getSubject().getId());
            var translation = switch (notice.getPresence().getKind()) {
                case WEB_JOINED -> Message.WEBCHAT_JOINED;
                case WEB_LEFT -> Message.WEBCHAT_LEFT;
                default -> throw new IllegalArgumentException("Invalid Paper notice");
            };
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (eligible(viewer) && canSee(viewer.getUniqueId(), subject)
                        && (!entry.getRestricted() || entry.getViewersList().contains(viewer.getUniqueId().toString()))) {
                    viewer.sendMessage(Component.text(language.t(viewer, translation, notice.getSubject().getName()), NamedTextColor.AQUA));
                }
            }
            return;
        }
        var delivery = ChatRecordCodec.delivery(entry);
        var sender = delivery.message().submission().sender();
        var author = sender.minecraftId();
        if (author.isPresent() && !allowed.test(author.get(), sender.minecraftName())) return;
        Set<UUID> recipients = gameRecipients(delivery.audience()).stream()
                .filter(viewer -> author.isEmpty() || canSee(viewer, author.get()))
                .filter(viewer -> delivery.audience().participants().stream().allMatch(subject -> canSee(viewer, subject)))
                .filter(viewer -> !entry.getRestricted() || entry.getViewersList().contains(viewer.toString()))
                .collect(Collectors.toUnmodifiableSet());
        gameOutput.deliver(delivery, recipients, !entry.getRestricted() && (author.isEmpty() || canSee(null, author.get())));
    }

    public void invalidate(UUID player) {
        daemon.chat(ChatCommand.newBuilder().setInvalidatePlayer(player.toString()).build());
    }

    @Override public CompletionStage<Outcome> publish(ChatDelivery delivery, boolean requireWebRecipient, BooleanSupplier authorized) {
        var sender = delivery.message().submission().sender();
        var author = sender.minecraftId().orElseThrow();
        UUID webTarget = requireWebRecipient ? delivery.target().minecraftId().orElseThrow() : null;
        boolean visibleAuthor = canSee(null, author);
        // A browser-only peer cannot inherit the visibility of an unauthenticated game connection.
        if (webTarget != null && (!visibleAuthor || !canSee(author, webTarget))) {
            return CompletableFuture.completedFuture(Outcome.NOT_SUBMITTED);
        }
        BooleanSupplier maySubmit = () -> authorized.getAsBoolean() && allowed.test(author, sender.minecraftName())
                && (webTarget == null || webAvailable(webTarget));
        if (!maySubmit.getAsBoolean()) return CompletableFuture.completedFuture(Outcome.NOT_SUBMITTED);

        var entry = ChatRecordCodec.publication(delivery).toBuilder();
        var audience = delivery.audience();
        var subjects = new HashSet<>(audience.participants());
        subjects.add(author);
        if (!visibleAuthor || audience.participants().stream().anyMatch(subject -> !author.equals(subject) && !canSee(null, subject))) {
            var viewerIds = new HashSet<>(audience.participants());
            if (audience.channel() != ChatChannel.PRIVATE) {
                viewerIds.addAll(web.keySet());
                Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).forEach(viewerIds::add);
            }
            entry.setRestricted(true).addAllViewers(viewerIds.stream()
                    .filter(viewer -> subjects.stream().allMatch(subject -> canSee(viewer, subject)))
                    .map(UUID::toString).toList());
        }

        if (webTarget != null && entry.getRestricted() && !entry.getViewersList().contains(webTarget.toString())) {
            return CompletableFuture.completedFuture(Outcome.NOT_SUBMITTED);
        }
        String messageId = entry.getMessageId();
        return daemon.chat(ChatCommand.newBuilder().setPublish(entry).build(), maySubmit).handle((result, failure) -> {
            if (failure != null) return RequestPort.definitelyNotSubmitted(failure) ? Outcome.NOT_SUBMITTED : Outcome.UNCONFIRMED;
            if (result == null || !result.hasPublished()) return Outcome.UNCONFIRMED;
            var saved = result.getPublished();
            return saved.getMessageId().equals(messageId) && !saved.getOrderKey().isBlank() ? Outcome.SAVED : Outcome.UNCONFIRMED;
        });
    }

    private boolean webAvailable(UUID id) {
        var peer = web.get(id);
        return peer != null && peer.getConnections() > 0 && allowed.test(id, peer.getPlayer().getName());
    }

    public void publishPresence(Player subject, boolean joined) {
        if (worker == null) return;
        Set<UUID> candidates = new HashSet<>(web.keySet());
        Bukkit.getOnlinePlayers().stream().filter(this::eligible).map(Player::getUniqueId).forEach(candidates::add);
        var viewers = candidates.stream().filter(viewer -> canSee(viewer, subject)).collect(Collectors.toUnmodifiableSet());
        boolean publicVisibility = viewers.size() == candidates.size() && canSee(null, subject);
        daemon.chat(ChatCommand.newBuilder().setNotice(ChatRecordCodec.gamePresence(subject.getUniqueId(), subject.getName(),
                joined, publicVisibility ? Optional.empty() : Optional.of(viewers))).build());
    }

    @Override public List<ChatParticipant> peers(UUID viewer) {
        var peers = new HashMap<UUID, ChatParticipant>();
        web.forEach((id, peer) -> {
            if (peer.getConnections() > 0 && allowed.test(id, peer.getPlayer().getName())) {
                peers.put(id, new ChatParticipant(id, peer.getPlayer().getName(), false, peer.getConnections()));
            }
        });
        if (Bukkit.isPrimaryThread()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (eligible(player)) addGamePeer(peers, player.getUniqueId(), player.getName());
            }
        } else {
            for (var peer : game.getPlayersList()) {
                if (peer.getEligible()) addGamePeer(peers, UUID.fromString(peer.getPlayer().getId()), peer.getPlayer().getName());
            }
        }
        return peers.values().stream().filter(peer -> canSee(viewer, peer.playerUuid()))
                .sorted(Comparator.comparing(ChatParticipant::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static void addGamePeer(Map<UUID, ChatParticipant> peers, UUID player, String name) {
        peers.compute(player, (id, previous) -> new ChatParticipant(id, name, true, previous == null ? 0 : previous.webConnections()));
    }

    @Override public ChatParticipant peer(UUID viewer, UUID target) {
        return peers(viewer).stream().filter(peer -> peer.playerUuid().equals(target)).findFirst().orElse(null);
    }

    @Override public List<ChatParticipant> channelPeers(UUID viewer, ChatChannel channel, UUID target) {
        if (channel == ChatChannel.STAFF && !staff(viewer)) return List.of();
        return peers(viewer).stream().filter(peer -> switch (channel) {
            case PUBLIC -> true;
            case STAFF -> staff(peer.playerUuid());
            case PRIVATE -> peer.playerUuid().equals(viewer) || peer.playerUuid().equals(target);
        }).toList();
    }

    private boolean staff(UUID id) {
        if (Bukkit.isPrimaryThread()) {
            var player = Bukkit.getPlayer(id);
            if (player != null && eligible(player) && RolePermissions.canModerate(player)) return true;
        } else if (game.getPlayersList().stream().anyMatch(peer -> peer.getStaff() && peer.getEligible() && peer.getPlayer().getId().equals(id.toString()))) {
            return true;
        }
        var peer = web.get(id);
        return peer != null && peer.getStaff();
    }

    @Override public Set<UUID> gameRecipients(ChatAudience audience) {
        if (Bukkit.isPrimaryThread()) {
            return Bukkit.getOnlinePlayers().stream().filter(this::eligible)
                    .filter(player -> audience.includes(player.getUniqueId(), audience.channel() == ChatChannel.STAFF && RolePermissions.canModerate(player)))
                    .map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
        }
        return game.getPlayersList().stream().filter(ChatGamePeer::getEligible)
                .filter(peer -> audience.includes(UUID.fromString(peer.getPlayer().getId()), peer.getStaff()))
                .map(peer -> UUID.fromString(peer.getPlayer().getId())).collect(Collectors.toUnmodifiableSet());
    }

    public boolean canSee(UUID viewer, UUID target) {
        if (target.equals(viewer)) return true;
        if (Bukkit.isPrimaryThread()) {
            Player subject = Bukkit.getPlayer(target);
            return subject == null || canSee(viewer, subject);
        }
        ChatGamePeer subject = null;
        int position = -1;
        var snapshot = game.getPlayersList();
        for (int index = 0; index < snapshot.size(); index++) {
            var peer = snapshot.get(index);
            UUID id = UUID.fromString(peer.getPlayer().getId());
            if (id.equals(target)) subject = peer;
            if (id.equals(viewer)) position = index;
        }
        if (subject == null || !subject.getConcealed()) return true;
        var visibility = subject.getVisibleTo();
        return position >= 0 && position / 8 < visibility.size() && (visibility.byteAt(position / 8) & (1 << (position % 8))) != 0;
    }

    public boolean canSee(UUID viewer, Player subject) {
        if (subject.getUniqueId().equals(viewer)) return true;
        Player observer = viewer == null ? null : Bukkit.getPlayer(viewer);
        if (observer != null) return observer.canSee(subject);
        return subject.isVisibleByDefault() && Bukkit.getOnlinePlayers().stream()
                .allMatch(player -> player.equals(subject) || player.canSee(subject));
    }

    private boolean eligible(Player player) {
        return player.isOnline() && allowed.test(player.getUniqueId(), player.getName()) && authenticated.test(player);
    }

    @Override public void close() {
        var thread = worker;
        worker = null;
        if (thread != null) thread.interrupt();
        web = Map.of();
        game = ChatGameRoster.getDefaultInstance();
        lastWeb = ChatWebRoster.getDefaultInstance();
    }
}
