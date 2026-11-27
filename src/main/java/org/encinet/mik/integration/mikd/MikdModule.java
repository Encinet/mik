package org.encinet.mik.integration.mikd;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns IPC connections and sends the latest application projections and server metrics. */
public final class MikdModule {
    public record GameRosters(GameRoster publicRoster, ChatGameRoster chatRoster) { }
    private static final int MAX_PLAYERS = 256;
    private record WebObservation(long receivedAt, ChatWebRoster roster) {}

    private final JavaPlugin plugin;
    private final AtomicLong lastWarning = new AtomicLong();
    private final long started = System.nanoTime();
    private MikdClient client;
    private BukkitTask snapshotTask;
    private long version;
    private volatile boolean enabled;
    private ChatWebRoster appliedWeb = ChatWebRoster.getDefaultInstance();
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
    }

    public void startGameState(Supplier<GameRosters> gameProjection, Consumer<ChatWebRoster> webProjection) {
        if (!enabled || snapshotTask != null) throw new IllegalStateException("Cannot start mikd state publication");
        snapshotTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> publishState(gameProjection, webProjection), 1, 10);
    }

    private void publishState(Supplier<GameRosters> gameProjection, Consumer<ChatWebRoster> webProjection) {
        var web = latestWeb;
        var roster = web != null && System.nanoTime() - web.receivedAt() < TimeUnit.SECONDS.toNanos(3)
                ? web.roster() : ChatWebRoster.getDefaultInstance();
        if (!appliedWeb.equals(roster)) {
            try {
                webProjection.accept(roster);
                appliedWeb = roster;
            } catch (IllegalArgumentException invalid) {
                warn("Invalid mikd web roster; ignoring the projection");
                webProjection.accept(ChatWebRoster.getDefaultInstance());
                appliedWeb = ChatWebRoster.getDefaultInstance();
            }
        }
        var rosters = gameProjection.get();
        if (rosters.chatRoster().getPlayersCount() > MAX_PLAYERS || rosters.publicRoster().getPlayersCount() > MAX_PLAYERS) {
            warn("Online player snapshot exceeds the protocol limit; mikd will expire its roster");
            return;
        }
        var server = plugin.getServer();
        var snapshot = PaperStateUpdate.newBuilder().setVersion(++version).setPublicRoster(rosters.publicRoster())
                .setChatRoster(rosters.chatRoster())
                .setServer(GameStatusView.newBuilder().setVersion(server.getMinecraftVersion())
                        .setMaxPlayers(server.getMaxPlayers()).setTps(server.getTPS()[0])
                        .setMspt(server.getAverageTickTime())
                        .setUptimeSeconds(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)));
        client.publish(snapshot.build()).whenComplete((accepted, error) -> {
            if (error != null && enabled) warn("mikd snapshot unavailable; bridge will reconnect");
            if (accepted != null && enabled) latestWeb = new WebObservation(System.nanoTime(), accepted.getWebRoster());
        });
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
        appliedWeb = ChatWebRoster.getDefaultInstance();
    }

    public RequestPort requests() {
        if (client == null) throw new IllegalStateException("mikd bridge is unavailable");
        return client;
    }

    public ConsolePort console() {
        if (client == null) throw new IllegalStateException("mikd bridge is unavailable");
        return client;
    }

    public SocketChannel openMusicChannel() throws IOException {
        var current = client;
        if (current == null) throw new IOException("mikd client is unavailable");
        return current.openMusicChannel();
    }
}
