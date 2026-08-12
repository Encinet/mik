package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.identity.IdentityLinkResult;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.identity.IdentityPlatformRegistration;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.game.ServerSnapshot;
import org.encinet.mik.module.social.game.SocialGameService;
import org.encinet.mik.module.social.game.SocialPlayerAvatar;
import org.encinet.mik.module.social.game.SocialPlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileCommandTest {
    @Test
    void commandOwnsTheProfileTitleAndStructuredSkinResponse() {
        UUID playerId = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-08-12T06:00:00Z");
        IdentityBinding binding = new IdentityBinding(playerId, "PlayerOne",
                new ExternalIdentityKey("qq", "qq-official", "group", "member"),
                "QQ member", verifiedAt, verifiedAt);
        byte[] facePng = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        SocialPlayerProfile profile = new SocialPlayerProfile(playerId, "PlayerOne",
                SocialPlayerProfile.Presence.ONLINE,
                Duration.ofHours(49).plusMinutes(3),
                Optional.of(Instant.parse("2025-01-02T03:04:00Z")),
                Optional.of(Instant.parse("2026-08-12T06:00:00Z")),
                Optional.of(new SocialPlayerAvatar(facePng)));
        StubLanguageService languages = new StubLanguageService();
        PlayerProfileCommand command = command(languages);

        SocialDocument document = command.present(context(),
                new PlayerProfileCommand.Result.Found(binding, profile));

        assertEquals("👤 SOCIAL_TITLE_PLAYER_PROFILE · PlayerOne", document.title());
        SocialDocument.Image image = assertInstanceOf(
                SocialDocument.Image.class, document.blocks().getFirst());
        SocialDocument.EmbeddedImage embedded = assertInstanceOf(
                SocialDocument.EmbeddedImage.class, image.source());
        assertEquals("image/png", embedded.mediaType());
        assertEquals(256, image.width());
        assertEquals(256, image.height());
        SocialDocument.Fields fields = document.blocks().stream()
                .filter(SocialDocument.Fields.class::isInstance)
                .map(SocialDocument.Fields.class::cast).findFirst().orElseThrow();
        assertTrue(fields.fields().stream().anyMatch(field ->
                field.label().equals(Message.SOCIAL_PROFILE_UUID_LABEL.name())
                        && field.value().equals(playerId.toString())));
        assertTrue(document.plainText().contains(Message.SOCIAL_PROFILE_PLAY_TIME_LABEL.name()));
        assertFalse(document.plainText().contains("QQ member"));
        assertEquals("PlayerOne", document.untrustedPlainText());
    }

    @Test
    void commandKeepsPlatformIdentityOutOfOfflineProfiles() {
        UUID playerId = UUID.randomUUID();
        Instant boundAt = Instant.parse("2026-08-12T06:00:00Z");
        IdentityBinding binding = new IdentityBinding(playerId, "PlayerTwo",
                new ExternalIdentityKey(
                        "qq", "secret-app", "secret-group", "secret-member"),
                "Secret QQ nickname", boundAt, boundAt);
        SocialPlayerProfile profile = new SocialPlayerProfile(playerId, "PlayerTwo",
                SocialPlayerProfile.Presence.OFFLINE, Duration.ofHours(12),
                Optional.empty(), Optional.empty(), Optional.empty());
        StubLanguageService languages = new StubLanguageService();

        SocialDocument document = command(languages).present(context(),
                new PlayerProfileCommand.Result.Found(binding, profile));
        SocialDocument.Fields fields = assertInstanceOf(
                SocialDocument.Fields.class, document.blocks().getFirst());

        assertTrue(fields.fields().stream().anyMatch(field ->
                field.label().equals(Message.SOCIAL_PROFILE_PLAY_TIME_LABEL.name())));
        assertFalse(document.plainText().contains("Secret QQ nickname"));
        assertFalse(document.plainText().contains("secret-app"));
        assertFalse(document.plainText().contains("secret-group"));
        assertFalse(document.plainText().contains("secret-member"));
    }

    @Test
    void queriedProfileShowsTheTargetPlayersLanguageWhileReplyUsesTheRequesterLanguage() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant boundAt = Instant.parse("2026-08-12T06:00:00Z");
        IdentityBinding requester = new IdentityBinding(requesterId, "Requester",
                new ExternalIdentityKey("qq", "app", "group", "requester"),
                "Requester", boundAt, boundAt);
        SocialPlayerProfile target = new SocialPlayerProfile(targetId, "Target",
                SocialPlayerProfile.Presence.ONLINE, Duration.ofHours(2),
                Optional.empty(), Optional.empty(), Optional.empty());
        StubLanguageService languages = new StubLanguageService(Map.of(
                requesterId, Language.EN_US,
                targetId, Language.JA_JP));

        SocialDocument document = command(languages).present(context(),
                new PlayerProfileCommand.Result.Found(requester, target));
        SocialDocument.Fields fields = assertInstanceOf(
                SocialDocument.Fields.class, document.blocks().getFirst());
        SocialDocument.Field language = fields.fields().stream()
                .filter(field -> field.label().equals(
                        Message.SOCIAL_PROFILE_LANGUAGE_LABEL.name()))
                .findFirst().orElseThrow();

        assertEquals(Language.EN_US, document.language());
        assertEquals(Language.JA_JP.displayName(), language.value());
    }

    @Test
    void profileUsesThePlatformDefaultWhenTargetLanguageIsUnknown() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant boundAt = Instant.parse("2026-08-12T06:00:00Z");
        IdentityBinding requester = new IdentityBinding(requesterId, "Requester",
                new ExternalIdentityKey("qq", "app", "group", "requester"),
                "Requester", boundAt, boundAt);
        SocialPlayerProfile target = new SocialPlayerProfile(targetId, "Target",
                SocialPlayerProfile.Presence.OFFLINE, Duration.ZERO,
                Optional.empty(), Optional.empty(), Optional.empty());
        StubLanguageService languages = new StubLanguageService(
                Map.of(requesterId, Language.EN_US));

        SocialDocument document = command(languages).present(context(Language.FR_FR),
                new PlayerProfileCommand.Result.Found(requester, target));

        assertTrue(document.plainText().contains(Language.FR_FR.displayName()));
        assertFalse(document.plainText().contains(Language.EN_US.displayName()));
    }

    private static PlayerProfileCommand command(StubLanguageService languages) {
        EmptyBindingManager bindings = new EmptyBindingManager();
        SocialCommandLanguageResolver resolver = new SocialCommandLanguageResolver(
                bindings, languages);
        return new PlayerProfileCommand(
                bindings, new UnusedGameService(),
                resolver, languages, ZoneOffset.UTC);
    }

    private static SocialCommandContext context() {
        return context(Language.ZH_CN);
    }

    private static SocialCommandContext context(Language platformDefault) {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "/我的", false,
                ignored -> CompletableFuture.completedFuture(null));
        return new SocialCommandContext(new SocialPlatformDescriptor("qq", "QQ"), message,
                org.encinet.mik.module.social.command.SocialCommandInput.nativeCommand(
                        "binding.profile", "", Map.of()),
                SocialCommandSyntax.caseInsensitive(platformDefault, "/"));
    }

    private static final class StubLanguageService extends LanguageService {
        private final Map<UUID, Language> playerLanguages;

        private StubLanguageService() {
            this(new HashMap<>());
        }

        private StubLanguageService(Map<UUID, Language> playerLanguages) {
            super(null);
            this.playerLanguages = Map.copyOf(playerLanguages);
        }

        @Override
        public Optional<Language> preferredLanguage(UUID playerId) {
            return Optional.ofNullable(playerLanguages.getOrDefault(
                    playerId, playerLanguages.isEmpty() ? Language.ZH_CN : null));
        }

        @Override
        public String t(Language language, Message message, Object... args) {
            return message.name();
        }
    }

    private static final class EmptyBindingManager implements IdentityBindingManager {
        @Override
        public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdentityBinding> find(ExternalIdentityKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
            return Optional.empty();
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return List.of();
        }
    }

    private static final class UnusedGameService implements SocialGameService {
        @Override
        public ServerSnapshot serverSnapshot(boolean includePlayerNames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocialPlayerProfile playerProfile(IdentityBinding binding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SocialPlayerProfile> findPlayerProfile(String exactPlayerName) {
            return Optional.empty();
        }

        @Override
        public boolean isFullMember(UUID playerId) {
            return false;
        }
    }
}
