package org.encinet.mik.module.commands;

import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.impl.container.FluentResolverScope;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.ResultBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfKickLanguageResourcesTest {

    private static final List<Message> SELF_KICK_MESSAGES = Arrays.stream(Message.values())
            .filter(message -> message.name().startsWith("SELF_KICK_"))
            .toList();
    private static final List<Message> TEMPLATE_POOLS = List.of(
            Message.SELF_KICK_WITH_REASON,
            Message.SELF_KICK_WITHOUT_REASON);
    private static final Set<String> TEMPLATE_VARIANTS = Set.of(
            "item-1", "item-2", "item-3", "item-4");

    @Test
    void everyLanguageResolvesEverySelfKickMessage() throws IOException {
        assertFalse(SELF_KICK_MESSAGES.isEmpty());
        var arguments = ArgumentListBuilder.builder()
                .add("arg0", "Player")
                .add("arg1", "Reason")
                .add("player", "Player")
                .add("reason", "Reason")
                .build();

        for (Language language : Language.values()) {
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource(language))
                    .addDefaultFunctions()
                    .build();
            for (Message message : SELF_KICK_MESSAGES) {
                String rendered = bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                        .map(Object::toString)
                        .orElseThrow(() -> new AssertionError(
                                language.id() + " is missing " + message.key()));
                assertFalse(rendered.isBlank(),
                        () -> language.id() + " has a blank " + message.key());
            }
        }
    }

    @Test
    void everyLanguageProvidesTheSameSafeRandomVariants() throws IOException {
        var arguments = ArgumentListBuilder.builder()
                .add("player", "[[PLAYER]]")
                .add("reason", "[[REASON]]")
                .build();

        for (Language language : Language.values()) {
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource(language))
                    .addDefaultFunctions()
                    .build();
            for (Message pool : TEMPLATE_POOLS) {
                var message = bundle.getMessage(pool.key()).orElseThrow();
                String fallback = bundle.resolveMessage(
                                pool.key(), arguments, StringResultFactory.construct())
                        .map(Object::toString).orElseThrow();
                assertTemplateTokens(language, pool, "fallback", fallback);
                Set<String> names = Arrays.stream(message.getAttributes())
                        .map(attribute -> attribute.getIdentifier().getSimpleIdentifier())
                        .collect(java.util.stream.Collectors.toSet());
                assertTrue(TEMPLATE_VARIANTS.equals(names),
                        () -> language.id() + " has invalid variants for " + pool.key());

                for (var attribute : message.getAttributes()) {
                    ResultBuilder result = StringResultFactory.construct();
                    attribute.resolve(new FluentResolverScope(bundle, arguments, result), result);
                    assertTemplateTokens(language, pool,
                            attribute.getIdentifier().getSimpleIdentifier(), result.toString());
                }
            }
        }
    }

    private static void assertTemplateTokens(Language language, Message pool,
                                             String variant, String rendered) {
        assertTrue(rendered.contains("[[PLAYER]]"),
                () -> language.id() + " " + variant + " omits player in " + pool.key());
        if (pool == Message.SELF_KICK_WITH_REASON) {
            assertTrue(rendered.contains("[[REASON]]"),
                    () -> language.id() + " " + variant + " omits reason in " + pool.key());
        } else {
            assertFalse(rendered.contains("[[REASON]]"),
                    () -> language.id() + " " + variant + " leaks a reason field");
        }
    }

    @Test
    void allLanguageFilesExposeTheSameSelfKickKeySet() throws IOException {
        List<String> expected = SELF_KICK_MESSAGES.stream().map(Message::key).sorted().toList();
        for (Language language : Language.values()) {
            FluentResource resource = resource(language);
            List<String> actual = Arrays.stream(resource.entries())
                    .filter(net.quickwrite.fluent4j.ast.entry.FluentMessage.class::isInstance)
                    .map(net.quickwrite.fluent4j.ast.entry.FluentMessage.class::cast)
                    .map(message -> message.getIdentifier().getSimpleIdentifier())
                    .filter(key -> key.startsWith("self-kick-"))
                    .sorted()
                    .toList();
            assertTrue(expected.equals(actual),
                    () -> language.id() + " self-kick keys differ from Message");
        }
    }

    private FluentResource resource(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertTrue(input != null, () -> "Missing language resource " + resourcePath);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source));
        }
    }
}
