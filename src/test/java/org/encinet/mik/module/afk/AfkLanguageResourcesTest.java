package org.encinet.mik.module.afk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.quickwrite.fluent4j.ast.entry.FluentAttributeEntry;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkLanguageResourcesTest {

    private static final String DEFAULT_STATUSES = "afk-default-statuses";
    private static final String DEFAULT_ENTER_TEMPLATES = "afk-enter-default-templates";
    private static final String CUSTOM_ENTER_TEMPLATES = "afk-enter-custom-templates";
    private static final String EXIT_TEMPLATES = "afk-exit-templates";
    private static final String[] MESSAGE_LISTS = {
            DEFAULT_STATUSES,
            DEFAULT_ENTER_TEMPLATES,
            CUSTOM_ENTER_TEMPLATES,
            EXIT_TEMPLATES
    };
    private static final Pattern ITEM_NAME = Pattern.compile("item-[1-9][0-9]*");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Test
    void everyLanguageProvidesNonEmptyAnonymousAfkLists() throws IOException {
        for (Language language : Language.values()) {
            FluentResource resource = resource(language);
            for (String messageId : MESSAGE_LISTS) {
                FluentMessage message = message(resource, messageId).orElseThrow(
                        () -> new AssertionError(language.id() + " is missing " + messageId));
                FluentAttributeEntry.Attribute[] entries = message.getAttributes();
                assertTrue(entries.length > 0,
                        () -> language.id() + " has an empty " + messageId + " list");
                for (FluentAttributeEntry.Attribute entry : entries) {
                    String attributeName = entry.getIdentifier().getSimpleIdentifier();
                    assertTrue(ITEM_NAME.matcher(attributeName).matches(),
                            () -> language.id() + " uses a semantic AFK list key: " + attributeName);
                }
            }
        }
    }

    @Test
    void everyAfkEntryResolvesAndTemplatesAcceptTheirPlaceholders() throws IOException {
        for (Language language : Language.values()) {
            FluentResource resource = resource(language);
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource)
                    .addDefaultFunctions()
                    .build();

            assertResolvedEntriesAreNotBlank(language, bundle, DEFAULT_STATUSES);
            assertTemplates(language, bundle, DEFAULT_ENTER_TEMPLATES, true);
            assertTemplates(language, bundle, CUSTOM_ENTER_TEMPLATES, true);
            assertTemplates(language, bundle, EXIT_TEMPLATES, false);
        }
    }

    private void assertResolvedEntriesAreNotBlank(Language language, FluentBundle bundle, String messageId) {
        FluentMessage message = bundle.getMessage(messageId).orElseThrow();
        for (FluentAttributeEntry.Attribute entry : message.getAttributes()) {
            String rendered = resolve(bundle, entry);
            assertFalse(rendered.isBlank(),
                    () -> language.id() + " has a blank entry in " + messageId);
        }
    }

    private void assertTemplates(Language language, FluentBundle bundle, String messageId, boolean needsStatus) {
        FluentMessage message = bundle.getMessage(messageId).orElseThrow();
        for (FluentAttributeEntry.Attribute entry : message.getAttributes()) {
            String rendered = resolve(bundle, entry);
            assertTrue(rendered.contains("<player>"),
                    () -> language.id() + " is missing <player> in " + messageId);
            if (needsStatus) {
                assertTrue(rendered.contains("<status>"),
                        () -> language.id() + " is missing <status> in " + messageId);
            }
            MINI_MESSAGE.deserialize(rendered,
                    Placeholder.component("player", Component.text("TestPlayer")),
                    Placeholder.component("status", Component.text("Test status")));
        }
    }

    private String resolve(FluentBundle bundle, FluentAttributeEntry.Attribute entry) {
        ResultBuilder result = StringResultFactory.construct();
        entry.resolve(new FluentResolverScope(bundle, ArgumentListBuilder.builder().build(), result), result);
        return result.toString();
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
