package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Converts raw social messages into command-shaped inputs. */
public record SocialCommandSyntax(
        List<String> explicitPrefixes,
        boolean caseSensitiveAliases,
        Language defaultLanguage
) {

    private static final int MAX_PREFIX_LENGTH = 16;

    public SocialCommandSyntax {
        Objects.requireNonNull(explicitPrefixes, "explicitPrefixes");
        Set<String> uniquePrefixes = new HashSet<>();
        List<String> validatedPrefixes = new ArrayList<>(explicitPrefixes.size());
        for (String value : explicitPrefixes) {
            String prefix = requirePrefix(value);
            if (!uniquePrefixes.add(prefix)) {
                throw new IllegalArgumentException(
                        "Duplicate social command prefix: " + prefix);
            }
            validatedPrefixes.add(prefix);
        }
        validatedPrefixes.sort(Comparator.comparingInt(String::length)
                .reversed()
                .thenComparing(Comparator.naturalOrder()));
        explicitPrefixes = List.copyOf(validatedPrefixes);
        defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
    }

    public static SocialCommandSyntax caseInsensitive(
            Language defaultLanguage,
            String... explicitPrefixes
    ) {
        return new SocialCommandSyntax(List.of(explicitPrefixes), false, defaultLanguage);
    }

    public static SocialCommandSyntax caseSensitive(
            Language defaultLanguage,
            String... explicitPrefixes
    ) {
        return new SocialCommandSyntax(List.of(explicitPrefixes), true, defaultLanguage);
    }

    public Optional<SocialCommandInput> parse(String rawContent) {
        if (rawContent == null) {
            return Optional.empty();
        }
        String content = rawContent.strip();
        if (content.isEmpty()) {
            return Optional.empty();
        }

        String prefix = matchingPrefix(content);
        boolean explicit = prefix != null;
        String commandLine = explicit
                ? content.substring(prefix.length()).strip()
                : content;
        int separator = firstWhitespace(commandLine);
        String alias = separator < 0
                ? commandLine
                : commandLine.substring(0, separator);
        String argument = separator < 0
                ? ""
                : commandLine.substring(separator).strip();
        return Optional.of(new SocialCommandInput(
                normalizeAlias(alias), argument,
                explicit
                        ? SocialCommandInput.InvocationType.EXPLICIT_TEXT
                        : SocialCommandInput.InvocationType.NATURAL_TEXT,
                java.util.Map.of()));
    }

    /** Conventional shortest prefix used when displaying invocations in generated help. */
    public String displayPrefix() {
        return explicitPrefixes.isEmpty()
                ? "" : explicitPrefixes.getLast();
    }

    String normalizeAlias(String value) {
        String alias = Objects.requireNonNull(value, "alias").strip();
        return caseSensitiveAliases ? alias : alias.toLowerCase(Locale.ROOT);
    }

    boolean startsWithExplicitPrefix(String value) {
        return explicitPrefixes.stream().anyMatch(value::startsWith);
    }

    private String matchingPrefix(String content) {
        return explicitPrefixes.stream()
                .filter(content::startsWith)
                .findFirst()
                .orElse(null);
    }

    private static String requirePrefix(String value) {
        String prefix = Objects.requireNonNull(value, "explicitPrefix");
        if (prefix.isEmpty() || prefix.length() > MAX_PREFIX_LENGTH
                || prefix.chars().anyMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException(
                    "Invalid social command prefix: " + prefix);
        }
        return prefix;
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
