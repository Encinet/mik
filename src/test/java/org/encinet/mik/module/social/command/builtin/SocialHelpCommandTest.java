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
import org.encinet.mik.module.social.command.NoArguments;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialHelpCommandTest {
    @Test
    void commandBuildsHelpDirectlyFromInstalledCommandMetadata() {
        StubLanguageService languages = new StubLanguageService();
        EmptyBindings bindings = new EmptyBindings();
        SocialCommand<?, ?> online = describedCommand(
                SocialCommandSpec.localizedNatural("server.online",
                        SocialCommandSpec.aliases(Language.EN_US, "online"),
                        SocialCommandSpec.aliases(Language.JA_JP, "オンライン")),
                Message.SOCIAL_ONLINE_COMMAND_DESCRIPTION);
        SocialHelpCommand help = new SocialHelpCommand(
                new SocialCommandLanguageResolver(bindings, languages),
                languages, List.of(online));

        SocialDocument document = help.present(context(), NoArguments.INSTANCE);
        SocialDocument.ItemList list = (SocialDocument.ItemList) document.blocks().getFirst();

        assertEquals("📖 SOCIAL_TITLE_COMMAND_HELP", document.title());
        assertTrue(list.items().contains(
                "!オンライン — SOCIAL_ONLINE_COMMAND_DESCRIPTION"));
        assertTrue(list.items().contains("!ヘルプ — SOCIAL_HELP_COMMAND_DESCRIPTION"));
    }

    private static SocialCommand<NoArguments, NoArguments> describedCommand(
            SocialCommandSpec spec,
            Message description
    ) {
        return new SocialCommand<>() {
            @Override
            public SocialCommandSpec spec() {
                return spec;
            }

            @Override
            public Optional<Message> description() {
                return Optional.of(description);
            }

            @Override
            public NoArguments decode(SocialCommandInput input) {
                return NoArguments.INSTANCE;
            }

            @Override
            public NoArguments handle(SocialCommandContext context, NoArguments argument) {
                return argument;
            }

            @Override
            public SocialDocument present(SocialCommandContext context, NoArguments result) {
                return SocialDocument.of("unused", SocialDocument.Tone.INFO);
            }
        };
    }

    private static SocialCommandContext context() {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "!ヘルプ", false,
                ignored -> CompletableFuture.completedFuture(null));
        SocialCommandInput input = new SocialCommandInput(
                "ヘルプ", "", SocialCommandInput.InvocationType.EXPLICIT_TEXT,
                Map.of(), Optional.of(Language.JA_JP));
        return new SocialCommandContext(
                new SocialPlatformDescriptor("test", "Test"), message, input,
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"));
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

    private static final class EmptyBindings implements IdentityBindingManager {
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
}
