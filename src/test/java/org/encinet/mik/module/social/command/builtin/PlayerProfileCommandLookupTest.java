package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.identity.IdentityLinkResult;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.identity.IdentityPlatformRegistration;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialMessageReferences;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.game.ServerSnapshot;
import org.encinet.mik.module.social.game.SocialGameService;
import org.encinet.mik.module.social.game.SocialPlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlayerProfileCommandLookupTest {
    private static final ExternalIdentityKey CALLER_KEY =
            new ExternalIdentityKey("qq", "app", "group", "caller");
    private static final ExternalIdentityKey TARGET_KEY =
            new ExternalIdentityKey("qq", "app", "group", "target");

    @Test
    void anOrdinaryBoundPlayerCanStillQueryTheirOwnProfile() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        StubGameService game = new StubGameService(caller);
        PlayerProfileCommand command = command(caller, List.of(), game);

        PlayerProfileCommand.Result.Found found = assertInstanceOf(
                PlayerProfileCommand.Result.Found.class, command.handle(context(), ""));

        assertEquals(caller.playerId(), found.profile().playerId());
        assertEquals(0, game.otherQueries);
    }

    @Test
    void queryingAnotherPlayerRequiresAFullMemberCaller() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        IdentityBinding target = binding("Target", TARGET_KEY);
        StubGameService game = new StubGameService(caller, target);
        PlayerProfileCommand command = command(caller, List.of(target), game);

        assertInstanceOf(PlayerProfileCommand.Result.MemberRequired.class,
                command.handle(context(), "Target"));
        assertEquals(0, game.otherQueries);
    }

    @Test
    void queryingAnotherMentionedPlayerAlsoRequiresAFullMemberCaller() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        IdentityBinding target = binding("Target", TARGET_KEY);
        StubGameService game = new StubGameService(caller, target);
        PlayerProfileCommand command = command(caller, List.of(target), game);
        ExternalIdentity referenced = new ExternalIdentity(TARGET_KEY, "QQ Target");

        assertInstanceOf(PlayerProfileCommand.Result.MemberRequired.class,
                command.handle(context(new SocialMessageReferences(
                        List.of(referenced), Optional.empty())), ""));
        assertEquals(0, game.otherQueries);
    }

    @Test
    void aFullMemberCanQueryAnExistingPlayerByExactNameWithoutATargetBinding() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        IdentityBinding target = binding("Target", TARGET_KEY);
        StubGameService game = new StubGameService(caller, target);
        game.fullMember = true;
        PlayerProfileCommand command = command(caller, List.of(), game);

        PlayerProfileCommand.Result.Found found = assertInstanceOf(
                PlayerProfileCommand.Result.Found.class,
                command.handle(context(), "target"));

        assertEquals(target.playerId(), found.profile().playerId());
        assertEquals(Optional.of(caller), found.languageBinding());
        assertEquals(1, game.otherQueries);
    }

    @Test
    void aFullMemberCanResolveAMentionOrReplyThroughItsAuthenticatedIdentity() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        IdentityBinding target = binding("Target", TARGET_KEY);
        StubGameService game = new StubGameService(caller, target);
        game.fullMember = true;
        PlayerProfileCommand command = command(caller, List.of(target), game);
        ExternalIdentity referenced = new ExternalIdentity(TARGET_KEY, "QQ Target");

        PlayerProfileCommand.Result.Found mentioned = assertInstanceOf(
                PlayerProfileCommand.Result.Found.class,
                command.handle(context(new SocialMessageReferences(
                        List.of(referenced), Optional.empty())), ""));
        PlayerProfileCommand.Result.Found replied = assertInstanceOf(
                PlayerProfileCommand.Result.Found.class,
                command.handle(context(new SocialMessageReferences(
                        List.of(), Optional.of(referenced))), ""));

        assertEquals(target.playerId(), mentioned.profile().playerId());
        assertEquals(target.playerId(), replied.profile().playerId());
        assertEquals(2, game.otherQueries);
    }

    @Test
    void referencedTargetsMustBeBoundAndUnambiguous() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        StubGameService game = new StubGameService(caller);
        game.fullMember = true;
        PlayerProfileCommand command = command(caller, List.of(), game);
        ExternalIdentity first = new ExternalIdentity(TARGET_KEY, "First");
        ExternalIdentity second = new ExternalIdentity(
                new ExternalIdentityKey("qq", "app", "group", "second"), "Second");

        assertInstanceOf(PlayerProfileCommand.Result.TargetUnbound.class,
                command.handle(context(new SocialMessageReferences(
                        List.of(first), Optional.empty())), ""));
        assertInstanceOf(PlayerProfileCommand.Result.TargetAmbiguous.class,
                command.handle(context(new SocialMessageReferences(
                        List.of(first, second), Optional.empty())), ""));
        assertEquals(0, game.otherQueries);
    }

    @Test
    void anUnknownPlayerNameIsNotQueryable() {
        IdentityBinding caller = binding("Caller", CALLER_KEY);
        StubGameService game = new StubGameService(caller);
        game.fullMember = true;
        PlayerProfileCommand command = command(caller, List.of(), game);

        assertInstanceOf(PlayerProfileCommand.Result.PlayerNotFound.class,
                command.handle(context(), "UnboundPlayer"));
        assertEquals(1, game.otherQueries);
    }

    private static PlayerProfileCommand command(
            IdentityBinding caller,
            List<IdentityBinding> targets,
            StubGameService game
    ) {
        List<IdentityBinding> bindings = new ArrayList<>();
        bindings.add(caller);
        bindings.addAll(targets);
        StubBindings bindingManager = new StubBindings(bindings);
        LanguageService languages = new LanguageService(null);
        return new PlayerProfileCommand(bindingManager, game,
                new SocialCommandLanguageResolver(bindingManager, languages),
                languages, ZoneOffset.UTC);
    }

    private static SocialCommandContext context() {
        return context(SocialMessageReferences.empty());
    }

    private static SocialCommandContext context(SocialMessageReferences references) {
        ExternalIdentity identity = new ExternalIdentity(CALLER_KEY, "QQ caller");
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.of(identity), new SocialInboundMessage.Text("/profile"), false,
                references, ignored -> CompletableFuture.completedFuture(null));
        return new SocialCommandContext(
                new SocialPlatformDescriptor("qq", "QQ"), message);
    }

    private static IdentityBinding binding(String name, ExternalIdentityKey key) {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        return new IdentityBinding(UUID.randomUUID(), name, key,
                "QQ " + name, now, now);
    }

    private static SocialPlayerProfile profile(IdentityBinding binding) {
        return new SocialPlayerProfile(binding.playerId(), binding.playerName(),
                SocialPlayerProfile.Presence.OFFLINE, Duration.ofHours(10),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static final class StubGameService implements SocialGameService {
        private final UUID callerId;
        private final java.util.Map<UUID, SocialPlayerProfile> profiles =
                new java.util.HashMap<>();
        private boolean fullMember;
        private int otherQueries;

        private StubGameService(IdentityBinding... bindings) {
            callerId = bindings[0].playerId();
            for (IdentityBinding binding : bindings) {
                profiles.put(binding.playerId(), profile(binding));
            }
        }

        @Override
        public ServerSnapshot serverSnapshot(boolean includePlayerNames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocialPlayerProfile playerProfile(IdentityBinding binding) {
            if (!binding.playerId().equals(callerId)) {
                otherQueries++;
            }
            return profiles.get(binding.playerId());
        }

        @Override
        public Optional<SocialPlayerProfile> findPlayerProfile(String exactPlayerName) {
            otherQueries++;
            return profiles.values().stream().filter(profile ->
                    profile.playerName().equalsIgnoreCase(exactPlayerName)).findFirst();
        }

        @Override
        public boolean isFullMember(UUID playerId) {
            return fullMember;
        }
    }

    private record StubBindings(List<IdentityBinding> bindings)
            implements IdentityBindingManager {
        private StubBindings {
            bindings = List.copyOf(bindings);
        }

        @Override
        public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdentityBinding> find(ExternalIdentityKey key) {
            return bindings.stream().filter(value -> value.externalKey().equals(key))
                    .findFirst();
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
            return bindings.stream().filter(value -> value.playerId().equals(playerId))
                    .toList();
        }

    }
}
