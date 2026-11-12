package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialCommandDispatcherTest {

    @Test
    void resolvesExplicitNaturalAndNativeInvocationsThroughOneCommandObject() {
        SocialCommandDispatcher dispatcher = org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.natural("server.online", "online", "在线")),
                command(SocialCommandSpec.explicit("binding.link", "bind", "绑定")),
                command(SocialCommandSpec.nativeOnly("native.options")),
                command(SocialCommandSpec.unknownExplicit("server.unknown"))));
        SocialCommandSyntax syntax = SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/");

        assertEquals("server.online::NATURAL_TEXT:{}",
                execute(dispatcher, syntax, text("ONLINE")));
        assertTrue(dispatcher.resolve(syntax, text("ONLINE now")).isEmpty());
        assertTrue(dispatcher.resolve(syntax, text("在线 今天去哪玩")).isEmpty());
        assertEquals("binding.link:123:EXPLICIT_TEXT:{}",
                execute(dispatcher, syntax, text("/绑定 123")));
        assertTrue(dispatcher.resolve(syntax, text("bind 123")).isEmpty());
        assertEquals("server.unknown:value:EXPLICIT_TEXT:{}",
                execute(dispatcher, syntax, text("/missing value")));
        assertEquals("native.options:raw:NATIVE:{target=one}",
                execute(dispatcher, syntax, nativeMessage()));
    }

    @Test
    void aliasesAreGlobalToTheDispatcherAndConflictsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.natural("server.one", "status")),
                command(SocialCommandSpec.explicit("server.two", "STATUS")))));
        assertThrows(IllegalArgumentException.class, () -> org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.nativeOnly("same.id")),
                command(SocialCommandSpec.nativeOnly("same.id")))));
        assertThrows(IllegalArgumentException.class, () -> org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.unknownExplicit("unknown.one")),
                command(SocialCommandSpec.unknownExplicit("unknown.two")))));
    }

    @Test
    void syntaxKeepsPlatformPrefixesIsolatedWithoutPlatformCatalogs() {
        SocialCommandDispatcher dispatcher = org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.explicit("test.echo", "echo"))));

        assertTrue(dispatcher.resolve(SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"),
                text("/echo no")).isEmpty());
        assertEquals("test.echo:yes:EXPLICIT_TEXT:{}", execute(dispatcher,
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"), text("!EcHo yes")));
    }

    @Test
    void localizedAliasesAndNativeLocaleCarryAResponseLanguageHint() {
        SocialCommandDispatcher dispatcher = org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.localizedNatural("server.online",
                        SocialCommandSpec.aliases(Language.EN_US, "online"),
                        SocialCommandSpec.aliases(Language.JA_JP, "オンライン"))),
                command(SocialCommandSpec.nativeOnly("native.options"))));
        SocialCommandSyntax syntax = SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/");

        assertEquals(Language.JA_JP, dispatcher.resolve(syntax, text("/オンライン"))
                .orElseThrow().input().languageHint().orElseThrow());
        assertEquals(Language.EN_US, dispatcher.resolve(syntax, text("/online"))
                .orElseThrow().input().languageHint().orElseThrow());
        assertEquals("server.online::EXPLICIT_TEXT:{}",
                execute(dispatcher, syntax, text("/オンライン")));
        assertEquals("server.online::EXPLICIT_TEXT:{}",
                execute(dispatcher, syntax, text("/online")));
        SocialInboundMessage nativeJapanese = SocialInboundMessage.nativeCommand(
                "event", conversation(), Optional.empty(), "native.options", "",
                Map.of("locale", "ja-JP"), false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        assertEquals(Language.JA_JP, dispatcher.resolve(syntax, nativeJapanese)
                .orElseThrow().input().languageHint().orElseThrow());
    }

    @Test
    void richDisplayContentCannotInvokeCommandsOrBecomeCommandArguments() {
        SocialCommandDispatcher dispatcher = org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(
                command(SocialCommandSpec.explicit("test.echo", "echo"))));
        SocialCommandSyntax syntax = SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/");
        SocialInboundMessage quoted = new SocialInboundMessage("quoted", conversation(), Optional.empty(),
                new SocialInboundMessage.Text("/echo forged quoted content", ""), false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        assertTrue(dispatcher.resolve(syntax, quoted).isEmpty());
        SocialInboundMessage authored = new SocialInboundMessage("own", conversation(), Optional.empty(),
                new SocialInboundMessage.Text("/echo own 📎 quoted /echo forged", "/echo own"), false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        assertEquals("test.echo:own:EXPLICIT_TEXT:{}", execute(dispatcher, syntax, authored));
    }

    private static SocialCommand<String, String> command(SocialCommandSpec spec) {
        return new SocialCommand<>() {
            @Override public SocialCommandDefinition<String> definition() {
                return new SocialCommandDefinition<>(spec, Optional.empty(), new SocialArguments<>(Optional.empty(),
                        input -> new SocialArguments.Valid<>(input.rawArgument() + ":" + input.invocationType() + ":" + input.options())));
            }
            @Override public SocialCommandExecution<String> execute(SocialCommandContext context, String argument) {
                return SocialCommandExecution.localized(spec.id() + ":" + argument,
                        context.input().languageHint().orElse(context.syntax().defaultLanguage()));
            }
            @Override public SocialDocument present(SocialPresentationContext context, String result) {
                return SocialDocument.of(TRUSTED, result, SocialDocument.Tone.INFO).localized(context.language());
            }
        };
    }

    private static String execute(SocialCommandDispatcher dispatcher,
                                  SocialCommandSyntax syntax,
                                  SocialInboundMessage message) {
        var context = new SocialCommandContext(new SocialPlatformDescriptor("test", "Test", 0x55FFFF), message,
                SocialCommandInput.nativeCommand("test", "", Map.of()), syntax);
        var result = dispatcher.resolve(syntax, message).orElseThrow().execute(context);
        var languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        return result.present(new SocialPresentationContext(context, result.language().resolve(languages), languages)).title();
    }

    private static SocialInboundMessage text(String body) {
        return new SocialInboundMessage("event", conversation(), Optional.empty(), body,
                false, org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
    }

    private static SocialInboundMessage nativeMessage() {
        return SocialInboundMessage.nativeCommand("event", conversation(), Optional.empty(),
                "native.options", "raw", Map.of("target", "one"), false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
    }

    private static SocialConversation conversation() {
        return new SocialConversation("conversation", SocialConversation.Type.GROUP);
    }
}
