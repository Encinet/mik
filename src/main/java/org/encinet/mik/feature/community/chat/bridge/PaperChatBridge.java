package org.encinet.mik.feature.community.chat.bridge;

import com.google.protobuf.ByteString;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.game.ChatRelay;
import org.encinet.mik.feature.community.chat.game.GameChatDirectory.GamePeer;
import org.encinet.mik.feature.community.chat.game.GameChatDirectory.WebPeer;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatPresence;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatGamePeer;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.PaperChatDelivery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Translates chat records and transports live batches; the game owns their audience. */
public final class PaperChatBridge implements ChatRelay, AutoCloseable {
    record Inbound(String id, Runnable dispatch) { }

    private final Plugin plugin;
    private final RequestPort daemon;
    private final Consumer<ChatDelivery> gameOutput;
    private final BiConsumer<ChatPresence, Optional<Set<UUID>>> presenceOutput;
    private volatile Thread worker;

    public PaperChatBridge(Plugin plugin, RequestPort daemon, Consumer<ChatDelivery> gameOutput,
            BiConsumer<ChatPresence, Optional<Set<UUID>>> presenceOutput) {
        this.plugin = plugin;
        this.daemon = daemon;
        this.gameOutput = gameOutput;
        this.presenceOutput = presenceOutput;
    }

    public void start() {
        if (worker != null) throw new IllegalStateException("Paper chat bridge is already running");
        worker = Thread.ofVirtual().name("mik-paper-chat").unstarted(this::run);
        worker.start();
    }

    private void run() {
        var owner = Thread.currentThread();
        while (!owner.isInterrupted()) {
            try {
                var pending = daemon.pollChat();
                List<PaperChatDelivery> entries;
                try { entries = pending.get(4, TimeUnit.SECONDS); }
                finally { if (!pending.isDone()) pending.cancel(false); }

                var batch = decodeBatch(entries);
                if (batch.isEmpty()) continue;
                var delivery = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    if (worker == owner) deliverBatch(batch);
                    return null;
                });
                try { delivery.get(2, TimeUnit.SECONDS); }
                finally { if (!delivery.isDone()) delivery.cancel(false); }
            } catch (InterruptedException stopped) {
                owner.interrupt();
            } catch (ExecutionException | TimeoutException unavailable) {
                try { Thread.sleep(500); }
                catch (InterruptedException stopped) { owner.interrupt(); }
            } catch (RuntimeException stopped) {
                if (worker == owner) plugin.getLogger().log(Level.WARNING, "Paper chat polling stopped", stopped);
                return;
            }
        }
    }

    List<Inbound> decodeBatch(List<PaperChatDelivery> entries) {
        var batch = new ArrayList<Inbound>(entries.size());
        for (var delivery : entries) {
            var event = delivery.getEvent();
            try {
                if (event.getRecord().hasPresence()) {
                    var notice = ChatRecordCodec.presence(event);
                    var visibility = ChatRecordCodec.visibility(event);
                    batch.add(new Inbound(event.getMessageId(), () -> presenceOutput.accept(notice, visibility)));
                } else {
                    var message = ChatRecordCodec.delivery(delivery);
                    batch.add(new Inbound(event.getMessageId(), () -> gameOutput.accept(message)));
                }
            } catch (RuntimeException invalid) {
                failed("decode", event.getMessageId(), invalid);
            }
        }
        return batch;
    }

    void deliverBatch(List<Inbound> batch) {
        for (var delivery : batch) {
            try {
                delivery.dispatch().run();
            } catch (RuntimeException failure) {
                failed("display", delivery.id(), failure);
            }
        }
    }

    private void failed(String stage, String id, RuntimeException failure) {
        plugin.getLogger().warning("Could not " + stage + " mikd chat " + id + " (" + failure.getClass().getSimpleName() + ")");
    }

    public static ChatGameRoster encodeRoster(List<GamePeer> peers) {
        Map<UUID, Integer> positions = new HashMap<>();
        for (int index = 0; index < peers.size(); index++) positions.put(peers.get(index).id(), index);
        var roster = ChatGameRoster.newBuilder();
        for (var peer : peers) {
            var wire = ChatGamePeer.newBuilder()
                    .setPlayer(ChatPlayer.newBuilder().setId(peer.id().toString()).setName(peer.name()))
                    .setEligible(peer.eligible()).setStaff(peer.staff());
            peer.viewers().ifPresent(viewers -> {
                var bits = new BitSet(peers.size());
                for (UUID viewer : viewers) bits.set(positions.get(viewer));
                wire.setVisibleTo(ByteString.copyFrom(bits.toByteArray()));
            });
            roster.addPlayers(wire);
        }
        return roster.build();
    }

    public static List<WebPeer> decodeRoster(ChatWebRoster roster) {
        return roster.getPlayersList().stream()
                .map(peer -> new WebPeer(UUID.fromString(peer.getPlayer().getId()), peer.getPlayer().getName(),
                        peer.getConnections(), peer.getStaff())).toList();
    }

    @Override public CompletionStage<Outcome> publish(ChatMessage message, Optional<Set<UUID>> visibility,
            boolean requireRemoteRecipient, BooleanSupplier authorized) {
        return daemon.publishChat(ChatRecordCodec.publication(message, visibility, requireRemoteRecipient), authorized)
                .handle((_, failure) -> failure == null ? Outcome.SAVED
                        : RequestPort.definitelyNotSubmitted(failure) ? Outcome.NOT_SUBMITTED : Outcome.UNCONFIRMED);
    }

    @Override public void publishPresence(ChatPresence notice, Optional<Set<UUID>> visibility) {
        if (worker != null)
            daemon.publishChat(ChatRecordCodec.publication(notice, visibility), () -> worker != null);
    }

    @Override public void close() {
        var thread = worker;
        worker = null;
        if (thread != null) thread.interrupt();
    }
}
