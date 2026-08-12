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
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.game.ServerSnapshot;
import org.encinet.mik.module.social.game.SocialGameService;
import org.encinet.mik.module.social.game.SocialPlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerStatusCommandTest {

    @Test
    void usesFourCompactDynamicStatusRows() {
        StubLanguageService languages = new StubLanguageService();
        EmptyBindingManager bindings = new EmptyBindingManager();
        ServerStatusCommand command = new ServerStatusCommand(new UnusedGameService(),
                new SocialCommandLanguageResolver(bindings, languages), languages);
        ServerSnapshot snapshot = new ServerSnapshot(
                12, 100, List.of(), 2,
                20.0, 19.98, 19.95, 12.34,
                Duration.ofHours(3), "1.21.8");

        SocialDocument document = command.present(context(), snapshot);
        SocialDocument.Fields fields = (SocialDocument.Fields) document.blocks().getFirst();

        assertEquals(4, fields.fields().size());
        assertEquals(Message.SOCIAL_QUERY_PLAYERS_LABEL.name(),
                fields.fields().get(0).label());
        assertEquals("SOCIAL_QUERY_ONLINE_LABEL 12/100"
                        + " · SOCIAL_QUERY_ACTIVE_LABEL 10 · AFK 2",
                fields.fields().get(0).value());
        assertEquals("TPS", fields.fields().get(1).label());
        assertEquals("20.00", fields.fields().get(1).value());
        assertEquals("MSPT", fields.fields().get(2).label());
        assertEquals("12.34", fields.fields().get(2).value());
        assertEquals(Message.SOCIAL_QUERY_UPTIME_LABEL.name(),
                fields.fields().get(3).label());
        assertFalse(document.plainText().contains("1.21.8"));
        assertFalse(document.plainText().contains("19.98"));
        assertFalse(document.plainText().contains("19.95"));
        assertFalse(document.plainText().contains("12.34 ms"));
    }

    private static SocialCommandContext context() {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "/状态", false,
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
