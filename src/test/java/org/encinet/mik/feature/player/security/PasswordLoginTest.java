package org.encinet.mik.feature.player.security;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class PasswordLoginTest {
    @TempDir Path directory;
    private static final String PASSWORD = "silver river lantern 8642";

    @Test void optionalAccountsEnterWithoutAFormButStaffWithoutPasswordsAreRefused() {
        try (var fixture = new Fixture()) {
            var optional = fixture.connection(10001);
            fixture.configure(optional);
            assertTrue(optional.connected.get());
            Player player = fixture.player(optional.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            assertTrue(fixture.login.verified(player));
            assertEquals(0, fixture.forms.get());
        }
        try (var fixture = new Fixture()) {
            fixture.role.set(PlayerRole.MODERATOR);
            var staff = fixture.connection(10002);
            fixture.configure(staff);
            assertFalse(staff.connected.get());
            assertEquals("password-required-role", plain(staff.disconnected.get()));
            assertEquals(0, fixture.forms.get());
        }
    }

    @Test void aConsoleResetAllowsAnUninitializedModeratorToUseTheSameLoginFlow() {
        try (var fixture = new Fixture()) {
            fixture.role.set(PlayerRole.MODERATOR);
            String generated = fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR).value();
            fixture.answer.set(() -> CompletableFuture.completedFuture(generated));
            var connection = fixture.connection(10003);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            assertTrue(fixture.login.verified(player));
            assertEquals(1, fixture.forms.get());
        }
    }

    @Test void wrongPasswordRetriesAndTheGrantIsBoundToTheActualSocket() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            AtomicInteger attempts = new AtomicInteger();
            fixture.answer.set(() -> CompletableFuture.completedFuture(attempts.incrementAndGet() == 1 ? "wrong password phrase" : PASSWORD));
            var connection = fixture.connection(10004);
            fixture.configure(connection);
            assertTrue(connection.connected.get());
            assertEquals(2, fixture.forms.get());
            Player otherSocket = fixture.player(new InetSocketAddress("127.0.0.1", 20004));
            fixture.login.joined(new PlayerJoinEvent(otherSocket, (net.kyori.adventure.text.Component) null));
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(otherSocket));
            assertEquals("password-login-expired", plain(fixture.kicked.get()));
        }
    }

    @Test void managementFailuresCarryIntoLoginAndResetImmediatelyAllowsTheNewPassword() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            for (int i = 0; i < 4; i++) {
                var verification = fixture.attempts.submit(Runnable::run, fixture.id, fixture.service.credential(fixture.id), "management-source",
                        () -> fixture.service.verify(fixture.id, "incorrect password"));
                assertThrows(java.util.concurrent.CompletionException.class, verification::join);
            }
            fixture.answer.set(() -> CompletableFuture.completedFuture("incorrect password"));
            var refused = fixture.connection(10009);
            fixture.configure(refused);
            assertFalse(refused.connected.get());
            assertEquals(1, fixture.forms.get());
            assertTrue(fixture.cooldownSeconds.get() > 0 && fixture.cooldownSeconds.get() <= 30);

            String fresh = fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER).value();
            fixture.answer.set(() -> CompletableFuture.completedFuture(fresh));
            var accepted = fixture.connection(10010);
            fixture.configure(accepted);
            assertTrue(accepted.connected.get());
            Player player = fixture.player(accepted.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            assertTrue(fixture.login.verified(player));
        }
    }

    @Test void resetBetweenVerificationAndWorldJoinInvalidatesTheGrant() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            var connection = fixture.connection(10005);
            fixture.configure(connection);
            fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
        }
    }

    @Test void delayedSelfChangeCannotAuthorizeALaterAdministratorReset() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            var connection = fixture.connection(10011);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            var change = fixture.service.change(fixture.id, "Alex", fixture.service.credential(fixture.id), PASSWORD, "copper orchard comet 5739", "copper orchard comet 5739", fixture.login.authority(player));
            fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER);
            assertFalse(fixture.login.verified(player));
            assertTrue(fixture.online.get()); // The UI callback has not kicked this connection yet.
            fixture.login.credentialsChanged(change);
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
        }
    }

    @Test void selfChangeAtomicallyContinuesOnlyTheInitiatingSession() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            var connection = fixture.connection(10012);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            var change = fixture.service.change(fixture.id, "Alex", fixture.service.credential(fixture.id), PASSWORD, "copper orchard comet 5739", "copper orchard comet 5739", fixture.login.authority(player));
            assertTrue(fixture.login.verified(player));
            fixture.login.credentialsChanged(change);
            assertTrue(fixture.login.verified(player));
            var reset = fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER);
            assertFalse(fixture.login.verified(player));
            fixture.login.credentialsChanged(reset);
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
            assertEquals("password-login-expired", plain(fixture.kicked.get()));
        }
    }

    @Test void delayedResetCompletionDoesNotRevokeAConnectionThatVerifiedTheNewPassword() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            var reset = fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER);
            fixture.answer.set(() -> CompletableFuture.completedFuture(reset.value()));
            var connection = fixture.connection(10013);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            fixture.login.credentialsChanged(reset);
            assertTrue(fixture.login.verified(player));
            assertTrue(fixture.online.get());
        }
    }

    @Test void configurationRepeatsTheDuplicateCheckAfterPreLoginMayHavePassed() {
        try (var fixture = new Fixture()) {
            var first = fixture.connection(10014);
            fixture.configure(first);
            Player player = fixture.player(first.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            var late = fixture.connection(10015);
            fixture.configure(late);
            assertFalse(late.connected.get());
            assertTrue(fixture.login.verified(player));
        }
    }

    @Test void operatorPromotionRequiresAPasswordEvenWithoutAnyStaffGroupPermission() {
        try (var fixture = new Fixture()) {
            var connection = fixture.connection(10016);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            fixture.operator.set(true);
            assertEquals(PlayerRole.MEMBER, PlayerRole.resolve(player));
            fixture.login.sweep();
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
            assertEquals("password-required-role", plain(fixture.kicked.get()));
        }
    }

    @Test void unavailableCredentialsRevokeEvenAnOptionalAccountsExistingSession() {
        try (var fixture = new Fixture()) {
            var connection = fixture.connection(10017);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            fixture.service.close();
            fixture.login.sweep();
            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
            assertEquals("password-storage", plain(fixture.kicked.get()));
        }
    }

    @Test void replacingCredentialsWhileTheLoginFormIsOpenCannotReleaseThatOldAttempt() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            fixture.answer.set(() -> {
                String fresh = fixture.service.reset(fixture.id, fixture.service.credential(fixture.id), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER).value();
                return CompletableFuture.completedFuture(fresh);
            });
            var connection = fixture.connection(10006);
            fixture.configure(connection);
            assertFalse(connection.connected.get());
            assertEquals("password-login-expired", plain(connection.disconnected.get()));
        }
    }

    @Test void promotionOfAnOptionalAccountWithoutCredentialsClosesItsExistingSession() {
        try (var fixture = new Fixture()) {
            var connection = fixture.connection(10007);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            fixture.role.set(PlayerRole.MODERATOR);
            fixture.login.enforce(player);
            assertFalse(fixture.online.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownRevokesOnlineSessionsWithTheServersShutdownMessage(boolean protectedAccount) {
        try (var fixture = new Fixture()) {
            if (protectedAccount) fixture.establish();
            var connection = fixture.connection(10018);
            fixture.configure(connection);
            Player player = fixture.player(connection.address);
            fixture.login.joined(new PlayerJoinEvent(player, (Component) null));
            var authority = fixture.login.authority(player);
            assertTrue(fixture.login.verified(player));
            Component reason = Component.text("Server restarting for maintenance");

            fixture.login.close(reason);
            fixture.login.close();

            assertFalse(fixture.online.get());
            assertFalse(fixture.login.verified(player));
            assertFalse(fixture.service.authenticated(authority));
            assertEquals(reason, fixture.kicked.get());
        }
    }

    @Test void shutdownDisconnectsAVerifiedConnectionBeforeWorldJoinWithTheShutdownMessage() {
        try (var fixture = new Fixture()) {
            fixture.establish();
            var connection = fixture.connection(10019);
            fixture.configure(connection);
            assertTrue(connection.connected.get());
            Component reason = Component.translatable("multiplayer.disconnect.server_shutdown");

            fixture.login.close(reason);

            assertFalse(connection.connected.get());
            assertEquals(reason, connection.disconnected.get());
        }
    }

    @Test void shutdownCancelsAConnectingPlayersPendingForm() throws Exception {
        try (var fixture = new Fixture(); var connections = Executors.newSingleThreadExecutor()) {
            fixture.establish();
            var response = new CompletableFuture<String>();
            var shown = new java.util.concurrent.CountDownLatch(1);
            fixture.answer.set(() -> { shown.countDown(); return response; });
            var connection = fixture.connection(10008);
            var configuring = connections.submit(() -> fixture.configure(connection));
            assertTrue(shown.await(5, TimeUnit.SECONDS));
            Component reason = Component.text("Server restarting for maintenance");
            fixture.login.close(reason);
            configuring.get(5, TimeUnit.SECONDS);
            assertTrue(response.isCancelled());
            assertFalse(connection.connected.get());
            assertEquals(reason, connection.disconnected.get());
        }
    }

    private final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final AtomicReference<PlayerRole> role = new AtomicReference<>(PlayerRole.MEMBER);
        final AtomicBoolean online = new AtomicBoolean(true);
        final AtomicBoolean operator = new AtomicBoolean();
        final AtomicReference<Component> kicked = new AtomicReference<>();
        final AtomicInteger forms = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicLong cooldownSeconds = new java.util.concurrent.atomic.AtomicLong();
        final AtomicReference<Supplier<CompletableFuture<String>>> answer = new AtomicReference<>(() -> CompletableFuture.completedFuture(PASSWORD));
        final ExecutorService workers = Executors.newFixedThreadPool(2);
        final PasswordService service = new PasswordService(directory.resolve(UUID.randomUUID() + ".db"));
        final LanguageService language = new LanguageService(null) {
            @Override public Language language(Player player) { return Language.EN_US; }
            @Override public String t(Language locale, Message message, Object... args) {
                if (message == Message.PASSWORD_COOLDOWN) cooldownSeconds.set(((Number) args[0]).longValue());
                return message.key();
            }
        };
        final PasswordAttempts attempts = new PasswordAttempts(() -> System.nanoTime() / 1_000_000, service::currentCredential);
        final PasswordLogin login;
        Fixture() {
            service.open();
            login = new PasswordLogin(service, ignored -> role.get(), new PasswordLogin.Prompt() {
                @Override public CompletableFuture<String> login(PlayerConfigurationConnection connection, Language locale) {
                    forms.incrementAndGet(); return answer.get().get();
                }
                @Override public void cancel(PlayerCommonConnection connection) { }
            }, language, workers, attempts);
        }
        void establish() { service.set(id, "Alex", service.credential(id), PASSWORD, PASSWORD, role::get, null); }
        Connection connection(int port) { return new Connection(id, port); }
        void configure(Connection connection) { login.configure(new AsyncPlayerConnectionConfigureEvent(connection.proxy)); }
        Player player(InetSocketAddress address) {
            PlayerGameConnection connection = (PlayerGameConnection) Proxy.newProxyInstance(PlayerGameConnection.class.getClassLoader(),
                    new Class<?>[] {PlayerGameConnection.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getAddress" -> address;
                        case "isConnected" -> online.get();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getConnection" -> connection;
                case "isOnline" -> online.get();
                case "isOp" -> operator.get();
                case "hasPermission" -> args[0].equals(role.get().permission());
                case "kick" -> { kicked.set((Component) args[0]); online.set(false); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
        @Override public void close() { login.close(); attempts.close(); workers.shutdownNow(); service.close(); }
    }

    private static final class Connection {
        final AtomicBoolean connected = new AtomicBoolean(true);
        final AtomicReference<Component> disconnected = new AtomicReference<>();
        final InetSocketAddress address;
        final PlayerConfigurationConnection proxy;
        Connection(UUID id, int port) {
            address = new InetSocketAddress("127.0.0.1", port);
            PlayerProfile profile = (PlayerProfile) Proxy.newProxyInstance(PlayerProfile.class.getClassLoader(), new Class<?>[] {PlayerProfile.class},
                    (object, method, args) -> method.getName().equals("getId") ? id : "Alex");
            proxy = (PlayerConfigurationConnection) Proxy.newProxyInstance(PlayerConfigurationConnection.class.getClassLoader(),
                    new Class<?>[] {PlayerConfigurationConnection.class, Audience.class}, (object, method, args) -> switch (method.getName()) {
                        case "getProfile" -> profile;
                        case "getClientOption" -> "en_us";
                        case "getAddress", "getClientAddress" -> address;
                        case "getAudience" -> object;
                        case "isConnected" -> connected.get();
                        case "disconnect" -> { disconnected.set((Component) args[0]); connected.set(false); yield null; }
                        case "sendMessage" -> null;
                        case "hashCode" -> System.identityHashCode(object);
                        case "equals" -> object == args[0];
                        case "toString" -> "Connection";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static String plain(Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }
}
