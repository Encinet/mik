package org.encinet.mik.module.commands;

import net.quickwrite.fluent4j.container.ArgumentListBuilder;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfKickLanguageResourcesTest {

    private static final List<Message> SELF_KICK_MESSAGES = Arrays.stream(Message.values())
            .filter(message -> message.name().startsWith("SELF_KICK_"))
            .toList();

    @Test
    void everyLanguageResolvesEverySelfKickMessage() throws IOException {
        assertFalse(SELF_KICK_MESSAGES.isEmpty());
        var arguments = ArgumentListBuilder.builder()
                .add("arg0", "Player")
                .add("arg1", "Reason")
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
