package org.encinet.mik.feature.community.identity;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.social.runtime.SocialMembershipDirectory;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformAdapter;
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformAdapter;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityAdmissionTest {
    @TempDir Path directory;
    private IdentityBindingRuntime runtime;
    private IdentityAdmission admission;
    private SocialMembershipDirectory qq;
    private SocialMembershipDirectory matrix;
    private final UUID player = UUID.randomUUID();
    private final ExternalIdentity account = new ExternalIdentity(new ExternalIdentityKey("qq", "app", "", "alice"), "Alice");
    private final ExternalIdentity matrixAccount = new ExternalIdentity(new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org"), "Alice");

    @BeforeEach
    void open() {
        var service = new IdentityBindingService(new IdentityBindingRepository(directory.resolve("bindings.db").toFile()));
        runtime = new IdentityBindingRuntime(service, new IdentityPlatformRegistry(), mock(IdentityBindingNotifier.class), Runnable::run);
        runtime.open();
        qq = new SocialMembershipDirectory(key -> key.platform().equals("qq") && key.issuer().equals("app"), Set.of("one", "two"));
        matrix = new SocialMembershipDirectory(key -> key.platform().equals("matrix"), Set.of("room"));
        qq.replace(qq.revision(), Map.of("one", Set.of(account.key()), "two", Set.of(account.key())));
        matrix.replace(matrix.revision(), Map.of("room", Set.of(matrixAccount.key())));
        runtime.registerPlatform(QqPlatformAdapter.IDENTITY_PLATFORM, qq);
        runtime.registerPlatform(MatrixPlatformAdapter.IDENTITY_PLATFORM, matrix);
        admission = new IdentityAdmission(mock(JavaPlugin.class), runtime, FluentTestSupport.languages());
    }

    @AfterEach
    void close() {
        admission.close();
        runtime.close();
        qq.close();
        matrix.close();
    }

    @Test
    void newPlayersSeeTheCommunityEntryBeforeBotCommandsInTheirClientLanguage() {
        var english = login("en_us");
        admission.configure(english);
        Component englishPrompt = denialComponent(english);
        String text = PlainTextComponentSerializer.plainText().serialize(englishPrompt);
        IdentityPromptAssertions.assertCodeEmphasis(englishPrompt, 2);
        assertTrue(text.contains("Join a community:"), text);
        assertTrue(text.contains("https://mcmik.top/join"), text);
        assertTrue(text.indexOf("https://mcmik.top/join") < text.indexOf("/bind "), text);
        assertTrue(text.contains("!bind "), text);
        assertFalse(text.contains("/绑定"), text);
        assertTrue(text.contains("\n\nChoose a platform and send its command to the community bot:\n"), text);
        assertTrue(text.endsWith("\n\nCode validity: 5 minutes\nReconnect to the server after linking."), text);
        var chinese = login("zh_cn");
        admission.configure(chinese);
        Component chinesePrompt = denialComponent(chinese);
        String localized = PlainTextComponentSerializer.plainText().serialize(chinesePrompt);
        IdentityPromptAssertions.assertCodeEmphasis(chinesePrompt, 2);
        assertTrue(localized.contains("加入社区"), localized);
        assertTrue(localized.contains("\n\n选择一个平台，向社区机器人发送对应指令：\n"), localized);
        assertTrue(localized.endsWith("\n\n验证码有效期：5 分钟\n绑定后重新连接服务器"), localized);
        assertEquals(code(text), code(localized));
    }

    @Test
    void authenticatedFirstLoginProvidesReusableCodesAndConsumedChallengesAreReplaced() {
        var first = login();
        admission.configure(first);
        String text = denial(first);
        String code = code(text);
        var retry = login();
        admission.configure(retry);
        assertEquals(text, denial(retry));
        runtime.redeem(code, account);
        assertTrue(runtime.canJoin(player));
        runtime.unlink(account.key(), runtime.find(account.key()).orElseThrow().bindingId());
        var afterUnlink = login();
        admission.configure(afterUnlink);
        String renewed = code(denial(afterUnlink));
        assertNotEquals(code, renewed);
        runtime.redeem(renewed, account);
        var allowed = login();
        admission.configure(allowed);
        verify(allowed.getConnection(), never()).disconnect(any(Component.class));
    }

    @Test
    void leavingOneCommunityKeepsTheAccountAndOnlyTheLastPlatformLossRevokesAdmission() {
        bind(account);
        bind(matrixAccount);
        qq.member("one", account.key(), false);
        assertEquals(2, runtime.findByPlayer(player).size());
        qq.member("two", account.key(), false);
        assertTrue(runtime.find(account.key()).isEmpty());
        assertTrue(runtime.canJoin(player));
        assertFalse(runtime.mustDisconnect(player));
        matrix.member("room", matrixAccount.key(), false);
        assertTrue(runtime.findByPlayer(player).isEmpty());
        assertFalse(runtime.canJoin(player));
        assertTrue(runtime.mustDisconnect(player));
    }

    @Test
    void unknownMembershipPreservesBindingsAndOnlinePlayersButPausesNewAdmission() {
        bind(account);
        qq.close();
        runtime.reconcileMemberships();
        assertTrue(runtime.hasBindings(player));
        assertFalse(runtime.canJoin(player));
        assertFalse(runtime.mustDisconnect(player));
        var pending = login();
        admission.configure(pending);
        String instructions = denial(pending);
        assertTrue(instructions.contains("现有绑定暂时无法验证"));
        assertTrue(instructions.contains("!bind"));
        assertEquals(IdentityLinkResult.Status.MEMBERSHIP_UNAVAILABLE,
                runtime.redeem("123456", account).status());
    }

    @Test
    void membershipFailuresDistinguishAbsenceFromUnknownAndPreserveTheCodeForRetry() {
        var code = runtime.issueCode(player, "Alice", "matrix").orElseThrow();
        matrix.member("room", matrixAccount.key(), false);
        assertEquals(IdentityLinkResult.Status.MEMBERSHIP_REQUIRED,
                runtime.redeem(code.code(), matrixAccount).status());
        assertTrue(runtime.codeIsPending(player, code));
        assertTrue(runtime.findByPlayer(player).isEmpty());

        matrix.invalidate();
        assertEquals(IdentityLinkResult.Status.MEMBERSHIP_UNAVAILABLE,
                runtime.redeem(code.code(), matrixAccount).status());
        assertTrue(runtime.codeIsPending(player, code));
        assertTrue(runtime.findByPlayer(player).isEmpty());

        matrix.replace(matrix.revision(), Map.of("room", Set.of(matrixAccount.key())));
        assertEquals(IdentityLinkResult.Status.LINKED,
                runtime.redeem(code.code(), matrixAccount).status());
        assertTrue(runtime.canJoin(player));
    }

    @Test
    void genuinelyScopedIdentitiesAreRevokedIndependently() {
        var first = new ExternalIdentity(new ExternalIdentityKey("scoped", "app", "one", "same-id"), "Alice", "One");
        var second = new ExternalIdentity(new ExternalIdentityKey("scoped", "app", "two", "same-id"), "Alice", "Two");
        try (var source = new SocialMembershipDirectory(key -> key.platform().equals("scoped"), Set.of("one", "two"))) {
            source.replace(source.revision(), Map.of("one", Set.of(first.key()), "two", Set.of(second.key())));
            var registration = runtime.registerPlatform(new IdentityPlatform("scoped", "Scoped", "/bind {code}"), source);
            try {
                bind(first);
                bind(second);
                source.member("one", first.key(), false);
                assertTrue(runtime.find(first.key()).isEmpty());
                assertTrue(runtime.find(second.key()).isPresent());
                assertTrue(runtime.canJoin(player));
            } finally { registration.close(); }
        }
    }

    @Test
    void anIncompleteSecondProviderCannotTurnPartialAbsenceIntoRevocation() {
        bind(account);
        try (var source = new SocialMembershipDirectory(key -> key.platform().equals("qq"), Set.of("three"))) {
            var registration = runtime.registerPlatform(QqPlatformAdapter.IDENTITY_PLATFORM, source);
            try {
                qq.member("one", account.key(), false);
                qq.member("two", account.key(), false);
                assertTrue(runtime.hasBindings(player));
                assertFalse(runtime.mustDisconnect(player));
                source.replace(source.revision(), Map.of("three", Set.of(account.key())));
                assertTrue(runtime.canJoin(player));
                source.member("three", account.key(), false);
                assertFalse(runtime.hasBindings(player));
            } finally { registration.close(); }
        }
    }

    @Test
    void nativeWhitelistOnlyAllowsConfigurationAndEarlierAuthenticationFailuresCannotMintChallenges() {
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getId()).thenReturn(player);
        var whitelist = mock(ProfileWhitelistVerifyEvent.class);
        when(whitelist.getPlayerProfile()).thenReturn(profile);
        when(whitelist.isWhitelisted()).thenReturn(true);
        admission.whitelist(whitelist);
        verify(whitelist).setWhitelisted(true);
        var unbound = login();
        admission.configure(unbound);
        assertTrue(denial(unbound).contains("绑定社区账号后即可入服"));
        var rejected = login();
        when(rejected.getConnection().isConnected()).thenReturn(false);
        admission.configure(rejected);
        verify(rejected.getConnection(), never()).disconnect(any(Component.class));
    }

    private void bind(ExternalIdentity identity) {
        var code = runtime.issueCode(player, "Alice", identity.key().platform()).orElseThrow();
        assertEquals(IdentityLinkResult.Status.LINKED, runtime.redeem(code.code(), identity).status());
    }

    private AsyncPlayerConnectionConfigureEvent login() {
        return login("zh_cn");
    }

    private AsyncPlayerConnectionConfigureEvent login(String locale) {
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getId()).thenReturn(player);
        when(profile.getName()).thenReturn("Alice");
        var connection = mock(PlayerConfigurationConnection.class);
        when(connection.getProfile()).thenReturn(profile);
        when(connection.isConnected()).thenReturn(true);
        when(connection.getClientOption(com.destroystokyo.paper.ClientOption.LOCALE)).thenReturn(locale);
        var event = mock(AsyncPlayerConnectionConfigureEvent.class);
        when(event.getConnection()).thenReturn(connection);
        return event;
    }

    private static String denial(AsyncPlayerConnectionConfigureEvent event) {
        return PlainTextComponentSerializer.plainText().serialize(denialComponent(event));
    }

    private static Component denialComponent(AsyncPlayerConnectionConfigureEvent event) {
        var reason = ArgumentCaptor.forClass(Component.class);
        verify(event.getConnection()).disconnect(reason.capture());
        return reason.getValue();
    }

    private static String code(String text) {
        var matcher = Pattern.compile("/bind ([0-9]{6})").matcher(text);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
