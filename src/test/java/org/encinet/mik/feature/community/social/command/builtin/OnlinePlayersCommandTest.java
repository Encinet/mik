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

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlinePlayersCommandTest {
    @Test
    void explicitExtraArgumentsShowUsageBeforeReadingGameState() {
        var languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        var game = org.mockito.Mockito.mock(SocialGameService.class);
        var command = new OnlinePlayersCommand(game,
                new SocialCommandLanguageResolver(new EmptyBindingManager(), languages), languages);
        var original = context();
        var inbound = new SocialInboundMessage("extra", original.message().conversation(), Optional.empty(),
                "/在线 extra", false, org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        var context = new SocialCommandContext(original.platform(), inbound);
        var dispatcher = org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(command));
        var result = dispatcher.resolve(context.syntax(), context.message()).orElseThrow().execute(context);
        var reply = result.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context, languages));
        assertTrue(reply.plainText().contains("/在线"));
        org.mockito.Mockito.verifyNoInteractions(game);
    }

    @Test
    void anUnavailableNameListDoesNotMeanTheServerIsEmpty() {
        var languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        var command = new OnlinePlayersCommand(new UnusedGameService(),
                new SocialCommandLanguageResolver(new EmptyBindingManager(), languages), languages);
        var document = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(), languages), new ServerSnapshot(3, 100, List.of(), 0,
                20, 20, 20, 5, Duration.ofHours(1), "26.3"));
        assertTrue(document.plainText().contains("3"));
        assertTrue(!document.plainText().contains("没有玩家"));
    }

    @Test
    void marksAfkPlayersInTheDisplayedPlayerList() {
        LanguageService languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        OnlinePlayersCommand command = new OnlinePlayersCommand(new UnusedGameService(),
                new SocialCommandLanguageResolver(new EmptyBindingManager(), languages), languages);
        ServerSnapshot snapshot = new ServerSnapshot(
                3, 100, List.of(
                new ServerSnapshot.PlayerSummary("Alice", false),
                new ServerSnapshot.PlayerSummary("Bob", true),
                new ServerSnapshot.PlayerSummary("Carol", false)),
                1, 20.0, 20.0, 20.0, 5.0,
                Duration.ofHours(1), "1.21.8");

        SocialDocument document = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(), languages), snapshot);
        SocialDocument.ItemList players = (SocialDocument.ItemList) document.blocks().get(1);

        assertEquals(List.of("Alice", "Bob (挂机)", "Carol"), players.items());
        assertEquals("", document.title());
        assertEquals(REQUIRED, document.moderation());
    }

    private static SocialCommandContext context() {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "/online", false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
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
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key, java.util.UUID expectedBindingId) {
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
