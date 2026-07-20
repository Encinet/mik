package org.encinet.mik.module.player;

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

class PlayerAssociationLanguageResourcesTest {

    private static final String PRIVATE_TEST_ADDRESS = "203.0.113.77";
    private static final List<Message> ASSOCIATION_MESSAGES = Arrays.stream(Message.values())
            .filter(message -> message.name().startsWith("PLAYER_ASSOCIATION_"))
            .toList();

    @Test
    void everyLanguageResolvesAllAssociationMessagesWithoutAddressData() throws IOException {
        var arguments = ArgumentListBuilder.builder()
                .add("arg0", "TestPlayer")
                .add("arg1", 2L)
                .add("arg2", 3L)
                .add("arg3", "4 days ago")
                .build();

        assertFalse(ASSOCIATION_MESSAGES.isEmpty());
        for (Language language : Language.values()) {
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource(language))
                    .addDefaultFunctions()
                    .build();
            for (Message message : ASSOCIATION_MESSAGES) {
                String rendered = bundle.resolveMessage(message.key(), arguments, StringResultFactory.construct())
                        .map(Object::toString)
                        .orElseThrow(() -> new AssertionError(language.id() + " is missing " + message.key()));
                assertFalse(rendered.isBlank(), () -> language.id() + " has a blank " + message.key());
                assertFalse(rendered.contains(PRIVATE_TEST_ADDRESS),
                        () -> language.id() + " leaks an address through " + message.key());
            }
        }
    }

    private FluentResource resource(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new AssertionError("Missing language resource " + resourcePath);
            }
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source));
        }
    }
}
