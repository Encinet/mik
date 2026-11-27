package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.game.ChatRelay.Outcome;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatPresence;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects game delivery and the shared copy from the same audience decision. */
public final class GameChatService {
    private final GameChatDirectory directory;
    private final Function<Player, BooleanSupplier> authentication;
    private final Consumer<Runnable> primaryThread;
    private final BiConsumer<String, RuntimeException> failure;
    private volatile ChatRelay relay;

    record Scope(Optional<Set<Player>> viewers, boolean mayShare) {
        static final Scope CURRENT = new Scope(Optional.empty(), true);

        Map<UUID, Player> select(Map<UUID, Player> expected) {
            if (viewers.isEmpty()) return expected;
            var allowed = viewers.orElseThrow();
            return expected.entrySet().stream().filter(entry -> allowed.contains(entry.getValue()))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        static Scope nativeEvent(Set<Player> finalViewers, Set<Player> initialAudience) {
            return new Scope(Optional.of(finalViewers), sameSessions(finalViewers, initialAudience));
        }

        static Scope limited(Set<Player> initialViewers) {
            return new Scope(Optional.of(initialViewers), false);
        }

        boolean mayShareWith(Map<UUID, Player> expected) {
            return mayShare && viewers.map(actual -> sameSessions(actual, expected.values())).orElse(true);
        }
    }

    static Set<Player> sessions(Collection<Player> players) {
        var sessions = Collections.newSetFromMap(new IdentityHashMap<Player, Boolean>());
        sessions.addAll(players);
        return Collections.unmodifiableSet(sessions);
    }

    static boolean sameSessions(Set<Player> sessions, Collection<Player> candidates) {
        return sessions.size() == candidates.size() && sessions.containsAll(candidates);
    }

    record Recipients(Map<UUID, Player> players, boolean unrestricted) { }

    public GameChatService(GameChatDirectory directory, Function<Player, BooleanSupplier> authentication,
            Consumer<Runnable> primaryThread, BiConsumer<String, RuntimeException> failure) {
        this.directory = directory;
        this.authentication = authentication;
        this.primaryThread = primaryThread;
        this.failure = failure;
    }

    public void connect(ChatRelay relay) { this.relay = relay; }

    public void disconnect() { relay = null; }

    void publish(ChatMessage message, Player sender, Scope scope, Consumer<Recipients> output,
            Consumer<Outcome> feedback) {
        var admitted = relay;
        if (admitted == null) return;
        if (message.origin() != ChatOrigin.Player.MINECRAFT)
            throw new IllegalArgumentException("A game publication requires its Minecraft sender");
        UUID author = message.sender().minecraftId().orElseThrow();
        primaryThread.accept(() -> {
            if (relay != admitted || !current(sender, author)) return;
            var session = authentication.apply(sender);
            BooleanSupplier authorized = () -> relay == admitted && session.getAsBoolean()
                    && !directory.gameOnly(author) && directory.allowed(author, message.sender().minecraftName());
            if (directory.gameOnly(author)) {
                if (!session.getAsBoolean() || message.channel() != ChatChannel.PUBLIC || scope.mayShare()) return;
            } else if (!authorized.getAsBoolean()) {
                return;
            }
            commit(message, sender, author, scope, admitted, authorized, output, feedback);
        });
    }

    private void commit(ChatMessage message, Player sender, UUID author, Scope scope, ChatRelay admitted,
            BooleanSupplier authorized, Consumer<Recipients> output, Consumer<Outcome> feedback) {
        var audience = message.audience();
        var expected = directory.gameRecipients(audience, author, Optional.empty());
        if (expected.get(author) != sender) return;
        var viewers = scope.select(expected);
        // Session identity matters: never move the native audience to a replacement connection.
        boolean share = scope.mayShareWith(expected);
        UUID target = message.targetId();
        var visibility = directory.visibilityLimit(audience, author);
        if (target == null || expected.containsKey(target)) {
            deliver(message, new Recipients(viewers, share && visibility.isEmpty()), output);
            if (share && authorized.getAsBoolean()) submit(admitted, message, visibility, false, authorized);
            return;
        }

        // A browser-only private route cannot inherit an unauthenticated game connection's visibility.
        BooleanSupplier maySubmit = () -> authorized.getAsBoolean() && directory.webAvailable(target);
        if (!share || !directory.canSee(null, author) || !directory.canSee(author, target)
                || (visibility.isPresent() && !visibility.orElseThrow().contains(target))
                || !maySubmit.getAsBoolean()) {
            feedback.accept(Outcome.NOT_SUBMITTED);
            return;
        }
        submit(admitted, message, visibility, true, maySubmit).whenComplete((outcome, error) -> {
            if (relay != admitted) return;
            primaryThread.accept(() -> {
                if (!authorized.getAsBoolean() || !current(sender, author)) return;
                Outcome confirmed = error == null && outcome != null ? outcome : Outcome.UNCONFIRMED;
                if (confirmed == Outcome.SAVED)
                    deliver(message, new Recipients(Map.of(author, sender), false), output);
                feedback.accept(confirmed);
            });
        });
    }

    Recipients routedRecipients(ChatDelivery delivery) {
        var message = delivery.message();
        var sender = message.sender();
        UUID author = sender.minecraftId().orElse(null);
        if (author != null && !directory.allowed(author, sender.minecraftName())) return null;
        return new Recipients(directory.gameRecipients(message.audience(), author, delivery.visibility()),
                delivery.visibility().isEmpty() && directory.globallyVisible(message.audience(), author));
    }

    Map<UUID, Player> presenceRecipients(ChatPresence notice, Optional<Set<UUID>> visibility) {
        return directory.gameRecipients(ChatAudience.PUBLIC, notice.player(), visibility);
    }

    void publishPresence(ChatPresence notice) {
        var admitted = relay;
        if (admitted != null) {
            admitted.publishPresence(notice, directory.visibilityLimit(ChatAudience.PUBLIC, notice.player()));
        }
    }

    private static boolean current(Player sender, UUID id) {
        return sender.isOnline() && Bukkit.getPlayer(id) == sender;
    }

    private CompletionStage<Outcome> submit(ChatRelay relay, ChatMessage message, Optional<Set<UUID>> visibility,
            boolean remote, BooleanSupplier authorized) {
        try {
            return relay.publish(message, visibility, remote, authorized);
        } catch (RuntimeException error) {
            failure.accept("publish " + message.id().value(), error);
            return CompletableFuture.completedFuture(Outcome.UNCONFIRMED);
        }
    }

    private void deliver(ChatMessage message, Recipients recipients, Consumer<Recipients> output) {
        try { output.accept(recipients); }
        catch (RuntimeException error) { failure.accept("display " + message.id().value(), error); }
    }
}
