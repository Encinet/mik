package org.encinet.mik.feature.player.security;

import org.encinet.mik.shared.role.PlayerRole;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    @TempDir Path directory;
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String ORIGINAL = "silver river lantern 8642";
    private static final String REPLACEMENT = "copper orchard comet 5739";

    private PasswordService open() {
        var service = new PasswordService(directory.resolve("passwords.db"));
        service.open();
        service.retention(id -> false);
        return service;
    }

    @Test void saltsDifferAndVerificationRejectsWrongPasswordsAndUnboundedParameters() {
        var hasher = new PasswordHasher();
        String first = hasher.hash(ORIGINAL);
        String second = hasher.hash(ORIGINAL);
        assertNotEquals(first, second);
        assertTrue(hasher.verify(ORIGINAL, first));
        assertTrue(hasher.verify(ORIGINAL, second));
        assertFalse(hasher.verify(REPLACEMENT, first));
        assertThrows(IllegalArgumentException.class, () -> hasher.verify(ORIGINAL, first.replace("19456", "999999999")));
        assertFalse(first.contains(ORIGINAL));
    }

    @Test void credentialsSurviveRestartAndRemovalCannotReuseAnOldCredential() throws Exception {
        try (var service = open()) {
            assertFalse(service.hasPassword(ACCOUNT));
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, ORIGINAL));
            failCode(PasswordException.Code.INVALID, () -> service.verify(ACCOUNT, REPLACEMENT));
        }
        try (var service = open()) {
            PasswordCredential original = service.verify(ACCOUNT, ORIGINAL);
            service.remove(ACCOUNT, original, ORIGINAL, () -> PlayerRole.MEMBER, null);
            assertFalse(service.hasPassword(ACCOUNT));
            PasswordCredential removed = service.credential(ACCOUNT);
            assertNotSame(original, removed);
            service.set(ACCOUNT, "Alex", removed, REPLACEMENT, REPLACEMENT, () -> PlayerRole.DEFAULT, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, REPLACEMENT));
            failCode(PasswordException.Code.CONFLICT,
                    () -> service.change(ACCOUNT, "Alex", original, REPLACEMENT, ORIGINAL, ORIGINAL, null));
        }
        String bytes = new String(Files.readAllBytes(directory.resolve("passwords.db")), StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(ORIGINAL));
        assertFalse(bytes.contains(REPLACEMENT));
    }

    @Test void changeIsAtomicAndUnicodeNormalizationPreservesSpaces() {
        try (var service = open()) {
            String decomposed = "cafe\u0301 river lantern 8642 ";
            String composed = "café river lantern 8642 ";
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), decomposed, composed, () -> PlayerRole.MEMBER, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, composed));
            failCode(PasswordException.Code.INVALID, () -> service.verify(ACCOUNT, composed.strip()));
            failCode(PasswordException.Code.MISMATCH, () -> service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), composed, REPLACEMENT, ORIGINAL, null));
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, composed));
            service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), composed, REPLACEMENT, REPLACEMENT, null);
            failCode(PasswordException.Code.INVALID, () -> service.verify(ACCOUNT, composed));
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, REPLACEMENT));
        }
    }

    @Test void staffCannotSelfInitializeOrRemoveAndApplicantsCannotRemoveButCanChange() {
        try (var service = open()) {
            failCode(PasswordException.Code.REQUIRED_ROLE, () -> service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MODERATOR, null));
            String password = service.reset(ACCOUNT, service.credential(ACCOUNT), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR).value();
            assertEquals(24, password.length());
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, password));
            failCode(PasswordException.Code.KEEP_ROLE, () -> service.remove(ACCOUNT, service.credential(ACCOUNT), password, () -> PlayerRole.CUSTODIAN, null));
            service.retention(id -> true);
            failCode(PasswordException.Code.REQUIRED_APPLICATION, () -> service.remove(ACCOUNT, service.credential(ACCOUNT), password, () -> PlayerRole.MEMBER, null));
            service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), password, REPLACEMENT, REPLACEMENT, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, REPLACEMENT));
            service.retention(id -> false);
            service.remove(ACCOUNT, service.credential(ACCOUNT), REPLACEMENT, () -> PlayerRole.MEMBER, null);
            assertFalse(service.hasPassword(ACCOUNT));
        }
    }

    @Test void resetPermissionsAreRecheckedAtCommitAndResetNeverClearsCredential() {
        UUID moderator = UUID.randomUUID();
        try (var service = open()) {
            service.reset(moderator, service.credential(moderator), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR);
            String generated = service.reset(ACCOUNT, service.credential(ACCOUNT), moderator, service.credential(moderator), () -> PlayerRole.MODERATOR, () -> PlayerRole.MODERATOR).value();
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, generated));
            failCode(PasswordException.Code.DENIED, () -> service.reset(ACCOUNT, service.credential(ACCOUNT), moderator, service.credential(moderator), () -> PlayerRole.MODERATOR, () -> PlayerRole.MODERATOR));
            failCode(PasswordException.Code.DENIED, () -> service.reset(ACCOUNT, service.credential(ACCOUNT), moderator, service.credential(moderator), () -> PlayerRole.CUSTODIAN, () -> PlayerRole.CUSTODIAN));
            failCode(PasswordException.Code.DENIED, () -> service.reset(moderator, service.credential(moderator), moderator, service.credential(moderator), () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR));
            String replacement = service.reset(ACCOUNT, service.credential(ACCOUNT), moderator, service.credential(moderator), () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR).value();
            assertNotEquals(generated, replacement);
            failCode(PasswordException.Code.INVALID, () -> service.verify(ACCOUNT, generated));
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, replacement));
            PasswordCredential oldActor = service.credential(moderator);
            service.reset(moderator, oldActor, null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR);
            failCode(PasswordException.Code.CONFLICT, () -> service.reset(ACCOUNT, service.credential(ACCOUNT), moderator, oldActor, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MODERATOR));
        }
    }

    @Test void anAuditFailureRollsBackTheCredentialAndStopsTrustingTheCache() throws Exception {
        try (var service = open()) {
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("passwords.db"));
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'audit unavailable'); END");
            }
            failCode(PasswordException.Code.STORAGE, () -> service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, REPLACEMENT, REPLACEMENT, null));
            assertFalse(service.available());
            failCode(PasswordException.Code.STORAGE, () -> service.verify(ACCOUNT, ORIGINAL));
        }
        try (var service = open()) { assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, ORIGINAL)); }
    }

    @Test void anErrorAfterDurableCommitCannotLeaveTheOldCachedPasswordUsable() throws Exception {
        AtomicBoolean stopped = new AtomicBoolean();
        try (var service = open()) {
            service.onStorageFailure(() -> stopped.set(true));
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null);
            var storeField = PasswordService.class.getDeclaredField("store");
            storeField.setAccessible(true);
            var store = (PasswordStore) storeField.get(service);
            var connectionField = PasswordStore.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            var real = (java.sql.Connection) connectionField.get(store);
            AtomicBoolean failed = new AtomicBoolean();
            // Keep real SQLite transactions, but fail the connection reset after COMMIT has succeeded.
            var unreliable = java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),
                    new Class<?>[] {java.sql.Connection.class}, (proxy, method, args) -> {
                        Object value;
                        try { value = method.invoke(real, args); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                        if (method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(args[0])
                                && failed.compareAndSet(false, true)) throw new java.sql.SQLException("Connection reset failed after commit");
                        return value;
                    });
            connectionField.set(store, unreliable);
            failCode(PasswordException.Code.STORAGE, () -> service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, REPLACEMENT, REPLACEMENT, null));
            assertTrue(stopped.get());
            assertFalse(service.available());
            failCode(PasswordException.Code.STORAGE, () -> service.verify(ACCOUNT, ORIGINAL));
            failCode(PasswordException.Code.STORAGE, () -> service.verify(ACCOUNT, REPLACEMENT));
        }
        try (var service = open()) {
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, REPLACEMENT));
            failCode(PasswordException.Code.INVALID, () -> service.verify(ACCOUNT, ORIGINAL));
        }
    }

    @Test void committedChangesIdentifyTheCredentialWithoutPrintingResetSecrets() {
        try (var service = open()) {
            var first = service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null);
            var next = service.reset(ACCOUNT, first.credential(), null, null, () -> PlayerRole.CUSTODIAN, () -> PlayerRole.MEMBER);
            assertEquals(ACCOUNT, first.account());
            assertNotSame(first.credential(), next.credential());
            assertSame(next.credential(), service.credential(ACCOUNT));
            assertFalse(next.toString().contains(next.value()));
        }
    }

    @Test void aClosedConnectionCannotBeResurrectedByItsQueuedMutation() {
        try (var service = open()) {
            PasswordCredential empty = service.credential(ACCOUNT);
            var departed = service.login(ACCOUNT, empty);
            departed.close();
            var replacement = service.login(ACCOUNT, empty);
            var failure = assertThrows(PasswordException.class,
                    () -> service.set(ACCOUNT, "Alex", empty, ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, departed));
            assertEquals(PasswordException.Code.SESSION_EXPIRED, failure.code());
            assertEquals(Message.PASSWORD_LOGIN_EXPIRED, PasswordFeedback.failure(failure.code()));
            assertFalse(service.hasPassword(ACCOUNT));
            assertTrue(service.authenticated(replacement));
            assertFalse(service.authenticated(departed));
        }
    }

    @Test void malformedStorageNeverTurnsIntoAnEmptyAccountList() throws Exception {
        try (var service = open()) { service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null); }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("passwords.db"));
             var statement = connection.createStatement()) {
            statement.execute("UPDATE credentials SET hash='not-a-password-hash'");
        }
        failCode(PasswordException.Code.STORAGE, this::open);
        String data = new String(Files.readAllBytes(directory.resolve("passwords.db")), StandardCharsets.ISO_8859_1);
        assertTrue(data.contains("not-a-password-hash"));
    }

    @Test void nominationAndRemovalShareTheSameCoordinationBoundary() throws Exception {
        try (var service = open(); var executor = Executors.newFixedThreadPool(2)) {
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), ORIGINAL, ORIGINAL, () -> PlayerRole.MEMBER, null);
            AtomicBoolean application = new AtomicBoolean();
            service.retention(id -> application.get());
            CountDownLatch checked = new CountDownLatch(1);
            CountDownLatch create = new CountDownLatch(1);
            var nomination = executor.submit(() -> service.coordinate(() -> {
                assertTrue(service.hasPassword(ACCOUNT));
                checked.countDown();
                assertTrue(create.await(5, TimeUnit.SECONDS));
                application.set(true);
                return true;
            }));
            assertTrue(checked.await(5, TimeUnit.SECONDS));
            var removal = executor.submit(() -> assertThrows(PasswordException.class,
                    () -> service.remove(ACCOUNT, service.credential(ACCOUNT), ORIGINAL, () -> PlayerRole.MEMBER, null)));
            create.countDown();
            assertTrue(nomination.get(5, TimeUnit.SECONDS));
            assertEquals(PasswordException.Code.REQUIRED_APPLICATION, removal.get(5, TimeUnit.SECONDS).code());
            assertTrue(service.hasPassword(ACCOUNT));
        }
    }

    @Test void tenCharacterPasswordsCanBeSetChangedAndVerified() {
        String first = "river8642!";
        String second = "comet5739!";
        try (var service = open()) {
            failCode(PasswordException.Code.WEAK,
                    () -> service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), "river8642", "river8642", () -> PlayerRole.MEMBER, null));
            service.set(ACCOUNT, "Alex", service.credential(ACCOUNT), first, first, () -> PlayerRole.MEMBER, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, first));
            service.change(ACCOUNT, "Alex", service.credential(ACCOUNT), first, second, second, null);
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, second));
        }
        try (var service = open()) {
            assertSame(service.credential(ACCOUNT), service.verify(ACCOUNT, second));
        }
    }

    @Test void policyRejectsWeakAndOversizedInputsWithoutTruncatingUnicode() {
        failCode(PasswordException.Code.WEAK, () -> PasswordPolicy.validate("short", "Alex"));
        failCode(PasswordException.Code.WEAK, () -> PasswordPolicy.normalize("😀".repeat(9)));
        assertEquals("😀".repeat(10), PasswordPolicy.normalize("😀".repeat(10)));
        failCode(PasswordException.Code.WEAK, () -> PasswordPolicy.validate("long password password", "Alex"));
        failCode(PasswordException.Code.WEAK, () -> PasswordPolicy.validate("silver Alex river lantern", "Alex"));
        failCode(PasswordException.Code.WEAK, () -> PasswordPolicy.normalize("a".repeat(129)));
        assertEquals("😀".repeat(128), PasswordPolicy.normalize("😀".repeat(128)));
    }

    private static void failCode(PasswordException.Code code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(PasswordException.class, action).code());
    }
}
