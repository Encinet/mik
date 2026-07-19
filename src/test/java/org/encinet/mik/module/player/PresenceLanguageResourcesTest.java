package org.encinet.mik.module.player;

import net.quickwrite.fluent4j.ast.entry.FluentMessage;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceLanguageResourcesTest {

    private static final String[] MESSAGE_LISTS = {"presence-join-eggs", "presence-quit-eggs"};

    @Test
    void eachConfiguredPresenceListContainsAtLeastOneEntry() throws IOException {
        for (Language language : Language.values()) {
            FluentResource resource = resource(language);
            for (String messageId : MESSAGE_LISTS) {
                FluentMessage message = message(resource, messageId).orElseThrow(
                        () -> new AssertionError(language.id() + " is missing " + messageId));
                assertTrue(message.getAttributes().length > 0,
                        () -> language.id() + " has an empty " + messageId + " list");
            }
        }
    }

    @Test
    void listEntriesResolveNamedArguments() throws IOException {
        FluentBundle bundle = FluentBundleBuilder.builder(Language.ZH_CN.locale())
                .addResource(resource(Language.ZH_CN))
                .addDefaultFunctions()
                .build();
        var message = bundle.getMessage("presence-join-eggs").orElseThrow();
        var entries = message.getAttributes();
        assertFalse(entries.length == 0);

        var arguments = ArgumentListBuilder.builder()
                .add("player", "TestPlayer")
                .build();
        ResultBuilder result = StringResultFactory.construct();
        entries[0].resolve(new FluentResolverScope(bundle, arguments, result), result);

        assertTrue(result.toString().contains("TestPlayer"));
    }

    private FluentResource resource(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertTrue(input != null, () -> "Missing language resource " + resourcePath);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source));
        }
    }

    private Optional<FluentMessage> message(FluentResource resource, String messageId) {
        return Arrays.stream(resource.entries())
                .filter(FluentMessage.class::isInstance)
                .map(FluentMessage.class::cast)
                .filter(message -> messageId.equals(message.getIdentifier().getSimpleIdentifier()))
                .findFirst();
    }
}
