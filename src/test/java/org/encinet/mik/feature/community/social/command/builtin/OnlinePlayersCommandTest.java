package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.community.identity.IdentityLinkResult;
import org.encinet.mik.feature.community.identity.IdentityPlatform;
import org.encinet.mik.feature.community.identity.IdentityPlatformRegistration;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.game.ServerSnapshot;
import org.encinet.mik.feature.community.social.game.SocialGameService;
import org.encinet.mik.feature.community.social.game.SocialPlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlinePlayersCommandTest {

    @Test
    void marksAfkPlayersInTheDisplayedPlayerList() {
        StubLanguageService languages = new StubLanguageService();
        OnlinePlayersCommand command = new OnlinePlayersCommand(new UnusedGameService(),
                new SocialCommandLanguageResolver(new EmptyBindingManager(), languages), languages);
        ServerSnapshot snapshot = new ServerSnapshot(
                3, 100, List.of(
                new ServerSnapshot.PlayerSummary("Alice", false),
                new ServerSnapshot.PlayerSummary("Bob", true),
                new ServerSnapshot.PlayerSummary("Carol", false)),
                1, 20.0, 20.0, 20.0, 5.0,
                Duration.ofHours(1), "1.21.8");

        SocialDocument document = command.present(context(), snapshot);
        SocialDocument.Fields fields = (SocialDocument.Fields) document.blocks().getFirst();

        assertEquals("Alice, Bob (AFK), Carol", fields.fields().get(1).value());
        assertEquals(List.of("Alice", "Bob", "Carol"), document.untrustedText());
    }

    private static SocialCommandContext context() {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "/online", false,
                ignored -> CompletableFuture.completedFuture(null));
        return new SocialCommandContext(new SocialPlatformDescriptor("qq", "QQ"), message);
    }

    private static final class StubLanguageService extends LanguageService {
        private StubLanguageService() {
            super(null);
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
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFullMember(UUID playerId) {
            throw new UnsupportedOperationException();
        }
    }
}
