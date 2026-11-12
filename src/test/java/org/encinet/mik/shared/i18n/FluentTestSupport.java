package org.encinet.mik.shared.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.quickwrite.fluent4j.container.FluentBundle;
import net.quickwrite.fluent4j.container.FluentBundleBuilder;
import net.quickwrite.fluent4j.container.FluentResource;
import net.quickwrite.fluent4j.iterator.FluentIteratorFactory;
import net.quickwrite.fluent4j.parser.ResourceParserBuilder;
import net.quickwrite.fluent4j.result.StringResultFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Real resources and the production argument encoding for grammar and localized layout tests. */
public final class FluentTestSupport {
    private FluentTestSupport() { }

    public static LanguageService languages() {
        java.util.Map<Language, FluentBundle> bundles = new java.util.concurrent.ConcurrentHashMap<>();
        return new LanguageService(null) {
            @Override
            public java.util.Optional<Language> preferredLanguage(java.util.UUID playerId) {
                return java.util.Optional.empty();
            }

            @Override
            public String t(Language language, Message message, Object... args) {
                return FluentTestSupport.text(bundleFor(language), message, args);
            }

            @Override
            public Component rich(Language language, Message message, NamedTextColor baseColor,
                                  TextArg[] selectors, RichArg... values) {
                var arguments = FluentArguments.rich(selectors, values);
                String rendered = bundleFor(language).resolveMessage(message.key(), arguments.arguments(), StringResultFactory.construct())
                        .map(Object::toString).orElseThrow(() -> new AssertionError("Unable to resolve " + message.key()));
                return arguments.render(rendered, baseColor);
            }

            private FluentBundle bundleFor(Language language) {
                return bundles.computeIfAbsent(language, value -> {
                    try { return bundle(value); }
                    catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
            }
        };
    }

    public static String source(Language language) throws IOException {
        String path = "lang/" + language.id() + ".ftl";
        try (var input = FluentTestSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new AssertionError("Missing language resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static FluentResource resource(Language language) throws IOException {
        return ResourceParserBuilder.defaultParser().parse(FluentIteratorFactory.fromString(source(language)));
    }

    public static FluentBundle bundle(Language language) throws IOException {
        return FluentBundleBuilder.builder(language.locale()).addResource(resource(language)).addDefaultFunctions().build();
    }

    public static Component rich(FluentBundle bundle, Message message, RichArg... values) {
        var arguments = FluentArguments.rich(new TextArg[0], values);
        String rendered = bundle.resolveMessage(message.key(), arguments.arguments(), StringResultFactory.construct())
                .map(Object::toString).orElseThrow(() -> new AssertionError("Unable to resolve " + message.key()));
        return arguments.render(rendered, NamedTextColor.WHITE);
    }

    public static String text(FluentBundle bundle, Message message, Object... args) {
        return bundle.resolveMessage(message.key(), FluentArguments.positional(args), StringResultFactory.construct())
                .map(Object::toString).orElseThrow(() -> new AssertionError("Unable to resolve " + message.key()));
    }
}
