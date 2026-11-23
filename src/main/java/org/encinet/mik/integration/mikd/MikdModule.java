package org.encinet.mik.integration.mikd;

import com.google.protobuf.ByteString;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.PlayerSnapshot;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Captures bounded immutable facts on the Paper thread and owns the backend bridge. */
public final class MikdModule {
    public record ObservedPlayer(Player player, boolean concealed, ByteString visibleTo) {}
    private record WebObservation(long receivedAt, ChatWebRoster roster) {}

    private final JavaPlugin plugin;
    private final AtomicLong lastWarning = new AtomicLong();
    private final long started = System.nanoTime();
    private MikdClient client;
    private BukkitTask snapshotTask;
    private long version;
    private volatile boolean enabled;
    private Predicate<Player> eligible;
    private Predicate<Player> afk;
    private Function<List<ObservedPlayer>, ChatGameRoster> chatProjection;
    private Consumer<ChatWebRoster> webProjection;
    private volatile WebObservation latestWeb;

    public MikdModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Path path = plugin.getDataFolder().toPath().resolve("mikd.yml");
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(path)) {
            config.load(reader);
        } catch (NoSuchFileException ignored) {
            // Standard installations use the service unit's socket and account.
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load " + path, error);
        }
        client = new MikdClient(Path.of(config.getString("socket", "/run/mik/mikd.sock")),
                config.getString("daemon-user", "mikd"), config.getString("game-instance", "paper"));
        client.start();
        enabled = true;
        snapshotTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::capture, 1, 10);
    }

    private void capture() {
        if (eligible == null) return;
        var web = latestWeb;
        if (webProjection != null) webProjection.accept(web != null && System.nanoTime() - web.receivedAt() < TimeUnit.SECONDS.toNanos(3)
                ? web.roster() : ChatWebRoster.getDefaultInstance());
        var server = plugin.getServer();
        var online = server.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getUniqueId)).toList();
        if (online.size() > 256) {
            warn("Online player snapshot exceeds the configured protocol limit");
            return;
        }
        var publicRoster = GameRoster.newBuilder();
        var observed = new ArrayList<ObservedPlayer>(online.size());
        for (var player : online) {
            var visibleTo = new BitSet(online.size());
            for (int index = 0; index < online.size(); index++) {
                var viewer = online.get(index);
                if (viewer.equals(player) || viewer.canSee(player)) visibleTo.set(index);
            }
            boolean concealed = !player.isVisibleByDefault() || visibleTo.cardinality() != online.size();
            observed.add(new ObservedPlayer(player, concealed, concealed ? ByteString.copyFrom(visibleTo.toByteArray()) : ByteString.EMPTY));
            if (concealed || !eligible.test(player)) continue;
            publicRoster.addPlayers(PlayerSnapshot.newBuilder().setPlayerId(player.getUniqueId().toString())
                    .setName(player.getName())
                    .setJoinedAt(Instant.ofEpochMilli(player.getLastLogin()).toString()).setAfk(afk.test(player)));
        }
        var snapshot = PaperStateUpdate.newBuilder().setVersion(++version).setPublicRoster(publicRoster)
                .setChatRoster(chatProjection == null ? ChatGameRoster.getDefaultInstance() : chatProjection.apply(observed))
                .setServer(GameStatusView.newBuilder().setVersion(server.getMinecraftVersion())
                        .setMaxPlayers(server.getMaxPlayers()).setTps(server.getTPS()[0])
                        .setMspt(server.getAverageTickTime())
                        .setUptimeSeconds(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)));
        client.publish(snapshot.build()).whenComplete((accepted, error) -> {
            if (error != null && enabled) warn("mikd snapshot unavailable; bridge will reconnect");
            if (accepted != null && enabled) latestWeb = new WebObservation(System.nanoTime(), accepted.getWebRoster());
        });
    }

    public void configureGameState(Predicate<Player> eligible, Predicate<Player> afk) {
        this.eligible = eligible;
        this.afk = afk;
    }

    public void configureChatState(Function<List<ObservedPlayer>, ChatGameRoster> chat, Consumer<ChatWebRoster> web) {
        chatProjection = chat;
        webProjection = web;
    }

    private void warn(String message) {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= java.time.Duration.ofMinutes(1).toNanos())
                && lastWarning.compareAndSet(previous, now)) plugin.getLogger().warning(message);
    }

    public void disable() {
        enabled = false;
        if (snapshotTask != null) snapshotTask.cancel();
        snapshotTask = null;
        if (client != null) client.close();
        client = null;
        latestWeb = null;
    }

    public RequestPort requests() {
        if (client == null) throw new IllegalStateException("mikd bridge is unavailable");
        return client;
    }

    public SocketChannel openMusicChannel() throws IOException {
        var current = client;
        if (current == null) throw new IOException("mikd client is unavailable");
        return current.openMusicChannel();
    }
}
