package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.LanguageService;
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

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerStatusCommandTest {

    @Test
    void usesFourCompactDynamicStatusRows() {
        LanguageService languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        EmptyBindingManager bindings = new EmptyBindingManager();
        ServerStatusCommand command = new ServerStatusCommand(new UnusedGameService(),
                new SocialCommandLanguageResolver(bindings, languages), languages);
        ServerSnapshot snapshot = new ServerSnapshot(
                12, 100, List.of(), 2,
                20.0, 19.98, 19.95, 12.34,
                Duration.ofHours(3), "1.21.8");

        SocialDocument document = command.present(context(), snapshot);
        SocialDocument.Fields fields = (SocialDocument.Fields) document.blocks().getFirst();

        assertEquals(TRUSTED, document.moderation());
        assertEquals(4, fields.fields().size());
        assertEquals("玩家",
                fields.fields().get(0).label());
        assertEquals("在线 12/100 · 活跃 10 · 挂机 2",
                fields.fields().get(0).value());
        assertEquals("TPS", fields.fields().get(1).label());
        assertEquals("20", fields.fields().get(1).value());
        assertEquals("MSPT", fields.fields().get(2).label());
        assertEquals("12.34", fields.fields().get(2).value());
        assertEquals("运行时间",
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
                _ -> CompletableFuture.completedFuture(null));
        return new SocialCommandContext(new SocialPlatformDescriptor("qq", "QQ", 0xFFC0CB), message);
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
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, org.encinet.mik.feature.community.identity.IdentityMemberships memberships) {
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
