package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialCommandDispatcherTest {

    @Test
    void resolvesExplicitNaturalAndNativeInvocationsThroughOneCommandObject() {
        SocialCommandDispatcher dispatcher = new SocialCommandDispatcher(List.of(
                command(SocialCommandSpec.natural("server.online", "online", "在线")),
                command(SocialCommandSpec.explicit("binding.link", "bind", "绑定")),
                command(SocialCommandSpec.nativeOnly("native.options")),
                command(SocialCommandSpec.unknownExplicit("server.unknown"))));
        SocialCommandSyntax syntax = SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/");

        assertEquals("server.online:now:NATURAL_TEXT:{}",
                execute(dispatcher, syntax, text("ONLINE now")));
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
        assertThrows(IllegalArgumentException.class, () -> new SocialCommandDispatcher(List.of(
                command(SocialCommandSpec.natural("server.one", "status")),
                command(SocialCommandSpec.explicit("server.two", "STATUS")))));
        assertThrows(IllegalArgumentException.class, () -> new SocialCommandDispatcher(List.of(
                command(SocialCommandSpec.nativeOnly("same.id")),
                command(SocialCommandSpec.nativeOnly("same.id")))));
        assertThrows(IllegalArgumentException.class, () -> new SocialCommandDispatcher(List.of(
                command(SocialCommandSpec.unknownExplicit("unknown.one")),
                command(SocialCommandSpec.unknownExplicit("unknown.two")))));
    }

    @Test
    void syntaxKeepsPlatformPrefixesIsolatedWithoutPlatformCatalogs() {
        SocialCommandDispatcher dispatcher = new SocialCommandDispatcher(List.of(
                command(SocialCommandSpec.explicit("test.echo", "echo"))));

        assertTrue(dispatcher.resolve(SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"),
                text("/echo no")).isEmpty());
        assertEquals("test.echo:yes:EXPLICIT_TEXT:{}", execute(dispatcher,
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"), text("!EcHo yes")));
    }

    @Test
    void localizedAliasesAndNativeLocaleCarryAResponseLanguageHint() {
        SocialCommandDispatcher dispatcher = new SocialCommandDispatcher(List.of(
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
                document -> java.util.concurrent.CompletableFuture.completedFuture(null));
        assertEquals(Language.JA_JP, dispatcher.resolve(syntax, nativeJapanese)
                .orElseThrow().input().languageHint().orElseThrow());
    }

    @Test
    void syntaxCarriesTheOwningPlatformsDefaultLanguage() {
        SocialCommandSyntax syntax = SocialCommandSyntax.caseInsensitive(Language.FR_FR, "/");

        assertEquals(Language.FR_FR, syntax.defaultLanguage());
    }

    private static SocialCommand<String, String> command(SocialCommandSpec spec) {
        return new SocialCommand<>() {
            @Override
            public SocialCommandSpec spec() {
                return spec;
            }

            @Override
            public String decode(SocialCommandInput input) {
                return input.rawArgument() + ":" + input.invocationType() + ":" + input.options();
            }

            @Override
            public String handle(SocialCommandContext context, String argument) {
                return spec.id() + ":" + argument;
            }

            @Override
            public SocialDocument present(SocialCommandContext context, String result) {
                return SocialDocument.of(result, SocialDocument.Tone.INFO);
            }
        };
    }

    private static String execute(SocialCommandDispatcher dispatcher,
                                  SocialCommandSyntax syntax,
                                  SocialInboundMessage message) {
        return dispatcher.resolve(syntax, message).orElseThrow()
                .execute(new SocialCommandContext(
                        new SocialPlatformDescriptor("test", "Test"), message))
                .title();
    }

    private static SocialInboundMessage text(String body) {
        return new SocialInboundMessage("event", conversation(), Optional.empty(), body,
                false, document -> java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    private static SocialInboundMessage nativeMessage() {
        return SocialInboundMessage.nativeCommand("event", conversation(), Optional.empty(),
                "native.options", "raw", Map.of("target", "one"), false,
                document -> java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    private static SocialConversation conversation() {
        return new SocialConversation("conversation", SocialConversation.Type.GROUP);
    }
}
