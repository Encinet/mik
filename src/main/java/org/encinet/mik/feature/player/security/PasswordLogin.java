package org.encinet.mik.feature.player.security;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.PlayerRole;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Password-protected players remain in CONFIGURATION until verification completes. */
final class PasswordLogin implements Listener, AutoCloseable {
    interface Prompt {
        CompletableFuture<String> login(PlayerConfigurationConnection connection, Language locale);
        void cancel(io.papermc.paper.connection.PlayerCommonConnection connection);
    }

    private static final class Admission {
        final PlayerConfigurationConnection connection;
        volatile CompletableFuture<String> response;
        volatile CompletableFuture<PasswordCredential> checking;
        volatile PasswordCredential checkingCredential;
        volatile PasswordService.Session authority;
        Admission(PlayerConfigurationConnection connection) { this.connection = connection; }
    }
    private record Session(Player player, PasswordService.Session authority) { }
    private final Map<UUID, Admission> admissions = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final PasswordService passwords;
    private final java.util.function.Function<UUID, PlayerRole> roles;
    private final Prompt dialogs;
    private final LanguageService language;
    private final ExecutorService workers;
    private final PasswordAttempts attempts;
    private volatile boolean closed;
    private Component closeReason;

    PasswordLogin(PasswordService passwords, java.util.function.Function<UUID, PlayerRole> roles, Prompt dialogs,
                  LanguageService language, ExecutorService workers, PasswordAttempts attempts) {
        this.passwords = passwords; this.roles = roles; this.dialogs = dialogs;
        this.language = language; this.workers = workers;
        this.attempts = attempts;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preLogin(AsyncPlayerPreLoginEvent event) {
        if (closed || !passwords.available() || sessions.containsKey(event.getUniqueId()) || admissions.containsKey(event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, text(Language.DEFAULT,
                    closed || !passwords.available() ? Message.PASSWORD_STORAGE : Message.PASSWORD_ALREADY_CONNECTED));
        }
    }

    @EventHandler
    public void configure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID id = connection.getProfile().getId();
        if (id == null) { connection.disconnect(text(Language.DEFAULT, Message.PASSWORD_STORAGE)); return; }
        Language locale = Language.fromId(connection.getClientOption(com.destroystokyo.paper.ClientOption.LOCALE))
                .orElse(Language.DEFAULT);
        Admission admission = new Admission(connection);
        boolean admitted;
        synchronized (admissions) {
            admitted = !closed && admissions.size() < 128 && !sessions.containsKey(id)
                    && admissions.putIfAbsent(id, admission) == null;
        }
        if (!admitted) {
            connection.disconnect(text(locale, Message.PASSWORD_BUSY)); return;
        }
        boolean allowed = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (!closed && connection.isConnected() && System.nanoTime() < deadline) {
                PasswordCredential expected = passwords.credential(id);
                admission.checkingCredential = expected;
                if (!passwords.hasPassword(id)) {
                    if (PasswordPolicy.mandatory(roles.apply(id))) {
                        connection.disconnect(text(locale, Message.PASSWORD_REQUIRED_ROLE)); return;
                    }
                    admission.authority = passwords.login(id, expected);
                    allowed = true;
                    return;
                }
                CompletableFuture<String> response = dialogs.login(connection, locale);
                admission.response = response;
                if (closed || !connection.isConnected()) response.cancel(false);
                String submitted = response.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                String source = connection.getClientAddress().getAddress().getHostAddress();
                try {
                    CompletableFuture<PasswordCredential> checking = attempts.submit(workers, id, expected, source,
                            () -> passwords.verify(id, submitted));
                    admission.checking = checking;
                    if (closed || !connection.isConnected()) checking.cancel(false);
                    PasswordCredential verified = checking.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    passwords.coordinate(() -> {
                        if (passwords.currentCredential(id) != verified || verified != expected || closed || !connection.isConnected()) {
                            throw new PasswordException(PasswordException.Code.CONFLICT);
                        }
                        admission.authority = passwords.login(id, verified);
                        return null;
                    });
                    allowed = true;
                    return;
                } catch (Exception error) {
                    Throwable cause = unwrap(error);
                    if (!(cause instanceof PasswordException failure) || failure.code() != PasswordException.Code.INVALID) throw error;
                    connection.getAudience().sendMessage(text(locale, Message.PASSWORD_INVALID));
                }
            }
        } catch (Exception error) {
            Throwable cause = unwrap(error);
            Message message = cause instanceof PasswordException failure
                    ? PasswordFeedback.loginFailure(failure.code())
                    : Message.PASSWORD_TIMEOUT;
            if (connection.isConnected()) connection.disconnect(closed ? closeReason
                    : cause instanceof PasswordException failure && failure.code() == PasswordException.Code.COOLDOWN
                    ? text(locale, message, failure.retryAfterSeconds()) : text(locale, message));
        } finally {
            dialogs.cancel(connection);
            admission.response = null;
            admission.checking = null;
            if (!allowed || closed || !connection.isConnected() || admissions.get(id) != admission) {
                if (admission.authority != null) admission.authority.close();
                admissions.remove(id, admission);
                if (connection.isConnected()) connection.disconnect(closed ? closeReason : text(locale, Message.PASSWORD_TIMEOUT));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void joined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!passwords.available()) { player.kick(text(language.language(player), Message.PASSWORD_STORAGE)); return; }
        Admission admission = admissions.get(id);
        if (admission == null || !sameAddress(admission.connection.getAddress(), player.getConnection().getAddress())
                || !passwords.authenticated(admission.authority)) {
            player.kick(text(language.language(player), Message.PASSWORD_LOGIN_EXPIRED)); return;
        }
        boolean registered;
        synchronized (admissions) {
            registered = !closed && !sessions.containsKey(id) && admissions.remove(id, admission);
            if (registered) sessions.put(id, new Session(player, admission.authority));
        }
        if (!registered) { player.kick(text(language.language(player), Message.PASSWORD_LOGIN_EXPIRED)); return; }
        enforce(player);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessions.computeIfPresent(player.getUniqueId(), (id, session) -> {
            if (session.player() != player) return session;
            session.authority().close();
            return null;
        });
        dialogs.cancel(player.getConnection());
    }

    boolean currentSession(UUID id, Player player) {
        Session session = sessions.get(id);
        return !closed && session != null && session.player() == player && passwords.authenticated(session.authority());
    }

    boolean verified(Player player) {
        return currentSession(player.getUniqueId(), player)
                && (!PasswordPolicy.mandatory(PasswordRoles.role(player)) || passwords.hasPassword(player.getUniqueId()));
    }

    PasswordService.Session authority(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.player() == player && passwords.authenticated(session.authority()) ? session.authority() : null;
    }

    /** Logical revocation already happened in the credential transaction. This only cleans up obsolete connections. */
    void credentialsChanged(PasswordService.Change<?> change) {
        UUID id = change.account();
        Session session = sessions.get(id);
        if (session != null && !passwords.authenticated(session.authority())) {
            invalidateSession(id, session, text(language.language(session.player()), Message.PASSWORD_LOGIN_EXPIRED));
        }
        Admission admission = admissions.get(id);
        if (admission != null && (admission.authority != null ? !passwords.authenticated(admission.authority)
                : admission.checkingCredential != passwords.currentCredential(id))) {
            invalidateAdmission(id, admission, text(Language.DEFAULT, Message.PASSWORD_LOGIN_EXPIRED));
        }
    }

    void enforce(Player player) {
        if (!passwords.available()) { if (player.isOnline()) player.kick(text(language.language(player), Message.PASSWORD_STORAGE)); return; }
        if (player.isOnline() && !verified(player)) {
            player.kick(text(language.language(player), passwords.hasPassword(player.getUniqueId())
                    ? Message.PASSWORD_LOGIN_EXPIRED : Message.PASSWORD_REQUIRED_ROLE));
        }
    }

    private void invalidateSession(UUID id, Session session, Component reason) {
        if (session != null && sessions.remove(id, session)) {
            session.authority().close();
            if (session.player().isOnline()) session.player().kick(reason);
        }
    }

    private void invalidateAdmission(UUID id, Admission admission, Component reason) {
        if (admission != null && admissions.remove(id, admission)) {
            if (admission.authority != null) admission.authority.close();
            admission.connection.disconnect(reason);
            CompletableFuture<String> response = admission.response;
            if (response != null) response.cancel(false);
            CompletableFuture<PasswordCredential> checking = admission.checking;
            if (checking != null) checking.cancel(false);
        }
    }

    void sweep() {
        if (!passwords.available()) { close(); return; }
        admissions.forEach((id, admission) -> {
            if (!admission.connection.isConnected() && admissions.remove(id, admission)) {
                if (admission.authority != null) admission.authority.close();
                CompletableFuture<String> response = admission.response;
                if (response != null) response.cancel(false);
                CompletableFuture<PasswordCredential> checking = admission.checking;
                if (checking != null) checking.cancel(false);
            }
        });
        sessions.values().forEach(session -> {
            Player player = session.player();
            if (!passwords.authenticated(session.authority())
                    || PasswordPolicy.mandatory(PasswordRoles.role(player)) && !passwords.hasPassword(player.getUniqueId())) enforce(player);
        });
    }

    private Component text(Language locale, Message message, Object... args) {
        return Component.text(language.t(locale, message, args), NamedTextColor.YELLOW);
    }

    private static boolean sameAddress(SocketAddress first, SocketAddress second) { return first.equals(second); }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.util.concurrent.ExecutionException || error instanceof java.util.concurrent.CompletionException)
                && error.getCause() != null) error = error.getCause();
        return error;
    }

    @Override public void close() {
        close(text(Language.DEFAULT, Message.PASSWORD_STORAGE));
    }

    void close(Component reason) {
        synchronized (admissions) {
            if (closed) return;
            closeReason = java.util.Objects.requireNonNull(reason);
            closed = true;
        }
        admissions.forEach((id, admission) -> invalidateAdmission(id, admission, reason));
        sessions.forEach((id, session) -> invalidateSession(id, session, reason));
    }
}
