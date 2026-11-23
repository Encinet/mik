package org.encinet.mik.feature.server.access;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.IdentityAcknowledgements;
import org.encinet.mik.protocol.IdentityCode;
import org.encinet.mik.protocol.IdentityCommand;
import org.encinet.mik.protocol.IdentityResult;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

public final class IdentityAccess implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final RequestPort requests;
    private final BiPredicate<UUID, PlayerConfigurationConnection> authenticated;
    private final BiFunction<UUID, Language, Component> maintenanceRefusal;
    private final BiFunction<Language, List<IdentityCode>, Component> challenge;
    private final UnaryOperator<String> platformName;
    private final ConcurrentHashMap<UUID, Attempt> configuring = new ConcurrentHashMap<>();
    private final Map<Player, Long> admittedThrough = new IdentityHashMap<>();
    private BukkitTask heartbeat;
    private CompletableFuture<IdentityResult> poll;
    private CompletableFuture<IdentityResult> acknowledgement;
    private volatile boolean closed;

    public IdentityAccess(JavaPlugin plugin, LanguageService languages, RequestPort requests,
                          BiPredicate<UUID, PlayerConfigurationConnection> authenticated,
                          BiFunction<UUID, Language, Component> maintenanceRefusal,
                          BiFunction<Language, List<IdentityCode>, Component> challenge,
                          UnaryOperator<String> platformName) {
        this.plugin = plugin;
        this.languages = languages;
        this.requests = requests;
        this.authenticated = authenticated;
        this.maintenanceRefusal = maintenanceRefusal;
        this.challenge = challenge;
        this.platformName = platformName;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, this::applyChanges, 1L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void whitelist(ProfileWhitelistVerifyEvent event) {
        event.setWhitelisted(!closed && event.getPlayerProfile().getId() != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void configure(AsyncPlayerConnectionConfigureEvent event) {
        var connection = event.getConnection();
        if (!connection.isConnected()) return;
        var profile = connection.getProfile();
        UUID id = profile.getId();
        String name = profile.getName();
        Language language = Language.fromId(connection.getClientOption(ClientOption.LOCALE)).orElse(Language.DEFAULT);
        if (closed || id == null || name == null || !authenticated.test(id, connection)) {
            connection.disconnect(unavailable(language));
            return;
        }
        if (disconnectForMaintenance(connection, id, language)) return;
        var attempt = new Attempt(connection);
        configuring.put(id, attempt);
        var request = requests.identity(IdentityCommand.newBuilder().setAdmit(
                ChatPlayer.newBuilder().setId(id.toString()).setName(name)).build(),
                () -> !closed && authenticated.test(id, connection));
        boolean admitted = false;
        try {
            IdentityResult result = request.get(4, TimeUnit.SECONDS);
            if (disconnectForMaintenance(connection, id, language)) return;
            if (closed || !authenticated.test(id, connection)) {
                connection.disconnect(unavailable(language));
            } else if (result.getOutcome() == IdentityResult.Outcome.ADMITTED) {
                attempt.through = result.getAdmittedThrough();
                admitted = true;
            } else {
                boolean unbound = result.getOutcome() == IdentityResult.Outcome.CHALLENGE;
                Component prompt = text(language, unbound
                        ? Message.IDENTITY_ADMISSION_REQUIRED : Message.IDENTITY_ADMISSION_PENDING);
                if (result.getCodesCount() > 0) {
                    prompt = Component.empty().append(prompt, Component.newline(),
                            challenge.apply(language, result.getCodesList()));
                }
                connection.disconnect(prompt);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (!disconnectForMaintenance(connection, id, language)) connection.disconnect(unavailable(language));
        } catch (ExecutionException | TimeoutException error) {
            if (!disconnectForMaintenance(connection, id, language)) connection.disconnect(unavailable(language));
        } finally {
            if (!admitted) configuring.remove(id, attempt);
            if (!request.isDone()) request.cancel(false);
        }
    }

    private boolean disconnectForMaintenance(PlayerConfigurationConnection connection, UUID id, Language language) {
        Component refusal = maintenanceRefusal.apply(id, language);
        if (refusal == null) return false;
        connection.disconnect(refusal);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Attempt attempt = configuring.remove(player.getUniqueId());
        if (attempt != null) admittedThrough.put(player, attempt.through);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        admittedThrough.remove(event.getPlayer());
    }

    private void applyChanges() {
        configuring.entrySet().removeIf(entry -> !entry.getValue().connection.isConnected());
        if (acknowledgement != null && !acknowledgement.isDone()) return;
        if (poll == null) {
            poll = requests.identity(IdentityCommand.newBuilder().setPoll(true).build(), () -> !closed);
            return;
        }
        if (!poll.isDone()) return;
        var completed = poll;
        poll = null;
        if (completed.isCompletedExceptionally()) return;
        var ack = IdentityAcknowledgements.newBuilder();
        for (var change : completed.join().getChangesList()) {
            UUID id = UUID.fromString(change.getPlayerId());
            if (configuring.containsKey(id)) continue;
            var online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline() && change.getSequence() > admittedThrough.getOrDefault(online, 0L)) {
                if (change.getRevoked()) {
                    online.kick(text(languages.language(online), Message.IDENTITY_ADMISSION_REVOKED));
                } else {
                    Message message = switch (change.getKind()) {
                        case LINKED -> Message.IDENTITY_NOTIFY_LINKED;
                        case UNLINKED -> Message.IDENTITY_NOTIFY_UNLINKED;
                        default -> null;
                    };
                    if (message != null) online.sendMessage(Component.text(languages.t(online, message,
                            platformName.apply(change.getPlatform())), NamedTextColor.YELLOW));
                }
            }
            ack.addOperationIds(change.getOperationId());
        }
        if (ack.getOperationIdsCount() != 0) {
            acknowledgement = requests.identity(IdentityCommand.newBuilder().setAcknowledge(ack).build(), () -> !closed);
        }
    }

    private Component unavailable(Language language) {
        return text(language, Message.IDENTITY_UNAVAILABLE);
    }

    private Component text(Language language, Message message) {
        return Component.text(languages.t(language, message), NamedTextColor.YELLOW);
    }

    @Override public void close() {
        closed = true;
        if (heartbeat != null) heartbeat.cancel();
        if (poll != null) poll.cancel(false);
        if (acknowledgement != null) acknowledgement.cancel(false);
        HandlerList.unregisterAll(this);
        configuring.clear();
        admittedThrough.clear();
    }

    private static final class Attempt {
        private final PlayerConfigurationConnection connection;
        private volatile long through;

        private Attempt(PlayerConfigurationConnection connection) { this.connection = connection; }
    }
}
