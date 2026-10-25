package org.encinet.mik.shared.i18n;

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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LanguageResourcesTest {

    private static final Set<String> ATTRIBUTE_POOL_KEYS = Set.of(
            Message.AFK_DEFAULT_STATUSES.key(),
            Message.AFK_ENTER_CUSTOM_TEMPLATES.key(),
            Message.AFK_ENTER_DEFAULT_TEMPLATES.key(),
            Message.AFK_EXIT_TEMPLATES.key(),
            Message.PRESENCE_JOIN_TEMPLATES.key(),
            Message.PRESENCE_QUIT_TEMPLATES.key()
    );
    private static final Set<String> MESSAGE_KEYS = Arrays.stream(Message.values())
            .map(Message::key)
            .filter(key -> !ATTRIBUTE_POOL_KEYS.contains(key))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> EXPECTED_KEYS = Arrays.stream(Message.values())
            .map(Message::key)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ARGUMENT_MARKERS = Set.of(
            "__MIK_ARG0__", "__MIK_ARG1__", "__MIK_ARG2__", "__MIK_ARG3__", "__MIK_ARG4__",
            "__MIK_ARG5__", "__MIK_ARG6__", "__MIK_ARG7__",
            "__MIK_CLIENT_VERSION__", "__MIK_COMMAND__", "__MIK_COUNT__", "__MIK_DAYS__", "__MIK_DISTANCE__", "__MIK_DURATION__",
            "__MIK_HOME__", "__MIK_HOMES__", "__MIK_KEYWORD__", "__MIK_LABEL__",
            "__MIK_LOCATION__", "__MIK_MAX__", "__MIK_MSPT__", "__MIK_MUSIC__",
            "__MIK_MODS__", "__MIK_ONLINE__", "__MIK_PING__", "__MIK_PLAYER__", "__MIK_RADIUS__", "__MIK_REMAINING__",
            "__MIK_SENDER__", "__MIK_SERVER_VERSION__", "__MIK_SITE__", "__MIK_STATE__", "__MIK_STEPS__", "__MIK_SUPPORTED__",
            "__MIK_TPS__", "__MIK_UNSUPPORTED__", "__MIK_WORLD__"
    );

    @Test
    void everyLanguageContainsExactlyTheSupportedMessageKeys() throws IOException {
        for (Language language : Language.values()) {
            Map<String, FluentMessage> messages = messages(language);
            assertEquals(EXPECTED_KEYS, messages.keySet(),
                    () -> language.id() + " keys differ from the supported language resources");
        }
    }

    @Test
    void everyLanguageMessageAndListEntryResolvesToText() throws IOException {
        var arguments = ArgumentListBuilder.builder()
                .add("arg0", "__MIK_ARG0__")
                .add("arg1", "__MIK_ARG1__")
                .add("arg2", "__MIK_ARG2__")
                .add("arg3", "__MIK_ARG3__")
                .add("arg4", "__MIK_ARG4__")
                .add("arg5", "__MIK_ARG5__")
                .add("arg6", "__MIK_ARG6__")
                .add("arg7", "__MIK_ARG7__")
                .add("clientVersion", "__MIK_CLIENT_VERSION__")
                .add("command", "__MIK_COMMAND__")
                .add("count", "__MIK_COUNT__")
                .add("days", "__MIK_DAYS__")
                .add("distance", "__MIK_DISTANCE__")
                .add("duration", "__MIK_DURATION__")
                .add("home", "__MIK_HOME__")
                .add("homes", "__MIK_HOMES__")
                .add("keyword", "__MIK_KEYWORD__")
                .add("label", "__MIK_LABEL__")
                .add("location", "__MIK_LOCATION__")
                .add("max", "__MIK_MAX__")
                .add("mspt", "__MIK_MSPT__")
                .add("music", "__MIK_MUSIC__")
                .add("mods", "__MIK_MODS__")
                .add("online", "__MIK_ONLINE__")
                .add("ping", "__MIK_PING__")
                .add("player", "__MIK_PLAYER__")
                .add("radius", "__MIK_RADIUS__")
                .add("remaining", "__MIK_REMAINING__")
                .add("sender", "__MIK_SENDER__")
                .add("serverVersion", "__MIK_SERVER_VERSION__")
                .add("site", "__MIK_SITE__")
                .add("state", "__MIK_STATE__")
                .add("steps", "__MIK_STEPS__")
                .add("supported", "__MIK_SUPPORTED__")
                .add("tps", "__MIK_TPS__")
                .add("unsupported", "__MIK_UNSUPPORTED__")
                .add("world", "__MIK_WORLD__")
                .build();
        FluentBundle referenceBundle = bundle(Language.EN_US);
        Map<String, Set<String>> expectedArguments = MESSAGE_KEYS.stream()
                .collect(Collectors.toUnmodifiableMap(
                        key -> key,
                        key -> usedArgumentMarkers(resolve(referenceBundle, key, arguments))
                ));

        for (Language language : Language.values()) {
            FluentResource resource = resource(language);
            FluentBundle bundle = FluentBundleBuilder.builder(language.locale())
                    .addResource(resource)
                    .addDefaultFunctions()
                    .build();

            for (String key : MESSAGE_KEYS) {
                String rendered = resolve(bundle, key, arguments);
                assertFalse(rendered.isBlank(), () -> language.id() + " has a blank " + key);
                assertEquals(expectedArguments.get(key), usedArgumentMarkers(rendered),
                        () -> language.id() + " uses different arguments in " + key);
            }

            for (String key : EXPECTED_KEYS) {
                FluentMessage message = bundle.getMessage(key).orElseThrow(
                        () -> new AssertionError(language.id() + " is missing " + key));
                Set<String> expectedAttributes = Arrays.stream(referenceBundle.getMessage(key)
                                .orElseThrow().getAttributes())
                        .map(attribute -> attribute.getIdentifier().getSimpleIdentifier())
                        .collect(Collectors.toUnmodifiableSet());
                Set<String> actualAttributes = Arrays.stream(message.getAttributes())
                        .map(attribute -> attribute.getIdentifier().getSimpleIdentifier())
                        .collect(Collectors.toUnmodifiableSet());
                assertEquals(expectedAttributes, actualAttributes,
                        () -> language.id() + " has different attributes in " + key);
                if (ATTRIBUTE_POOL_KEYS.contains(key)) {
                    assertFalse(actualAttributes.isEmpty(),
                            () -> language.id() + " has an empty " + key);
                }
                for (FluentAttributeEntry.Attribute entry : message.getAttributes()) {
                    ResultBuilder result = StringResultFactory.construct();
                    entry.resolve(new FluentResolverScope(bundle, arguments, result), result);
                    assertFalse(result.toString().isBlank(),
                            () -> language.id() + " has a blank entry in " + key);
                    var referenceAttribute = referenceBundle.getMessage(key).orElseThrow()
                            .getAttribute(entry.getIdentifier().getSimpleIdentifier()).orElseThrow();
                    ResultBuilder expected = StringResultFactory.construct();
                    referenceAttribute.resolve(new FluentResolverScope(
                            referenceBundle, arguments, expected), expected);
                    assertEquals(usedArgumentMarkers(expected.toString()), usedArgumentMarkers(result.toString()),
                            () -> language.id() + " uses different attribute arguments in " + key);
                }
            }
        }
    }

    private Map<String, FluentMessage> messages(Language language) throws IOException {
        Map<String, FluentMessage> messages = new LinkedHashMap<>();
        Arrays.stream(resource(language).entries())
                .filter(FluentMessage.class::isInstance)
                .map(FluentMessage.class::cast)
                .forEach(message -> {
                    String key = message.getIdentifier().getSimpleIdentifier();
                    assertNull(messages.putIfAbsent(key, message),
                            () -> language.id() + " contains duplicate key " + key);
                });
        return messages;
    }

    private FluentResource resource(Language language) throws IOException {
        String resourcePath = "lang/" + language.id() + ".ftl";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, () -> "Missing language resource " + resourcePath);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source));
        }
    }

    private FluentBundle bundle(Language language) throws IOException {
        return FluentBundleBuilder.builder(language.locale())
                .addResource(resource(language))
                .addDefaultFunctions()
                .build();
    }

    private String resolve(
            FluentBundle bundle,
            String key,
            net.quickwrite.fluent4j.ast.pattern.ArgumentList arguments
    ) {
        return bundle.resolveMessage(key, arguments, StringResultFactory.construct())
                .map(Object::toString)
                .orElseThrow(() -> new AssertionError("Unable to resolve " + key));
    }

    private Set<String> usedArgumentMarkers(String rendered) {
        return ARGUMENT_MARKERS.stream()
                .filter(rendered::contains)
                .collect(Collectors.toUnmodifiableSet());
    }
}
