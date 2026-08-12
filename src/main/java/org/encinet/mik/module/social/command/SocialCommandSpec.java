package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stable identity and text exposure owned by one social command. */
public record SocialCommandSpec(
        String id,
        List<String> aliases,
        TextMode textMode,
        Map<String, Language> languageHints
) {

    public SocialCommandSpec {
        id = requireId(id);
        textMode = Objects.requireNonNull(textMode, "textMode");
        Objects.requireNonNull(aliases, "aliases");
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String value : aliases) {
            String alias = Objects.requireNonNull(value, "alias").strip();
            if (alias.isEmpty() || alias.chars().anyMatch(character ->
                    Character.isWhitespace(character) || Character.isISOControl(character))) {
                throw new IllegalArgumentException("Invalid social command alias: " + value);
            }
            clean.add(alias);
        }
        aliases = List.copyOf(clean);
        languageHints = Map.copyOf(Objects.requireNonNull(languageHints, "languageHints"));
        if (!clean.containsAll(languageHints.keySet())) {
            throw new IllegalArgumentException("Language hints must reference declared aliases");
        }
        boolean aliasesRequired = textMode == TextMode.EXPLICIT_ONLY
                || textMode == TextMode.EXPLICIT_OR_NATURAL;
        if (aliasesRequired && aliases.isEmpty()) {
            throw new IllegalArgumentException("A text command requires at least one alias");
        }
        if (!aliasesRequired && !aliases.isEmpty()) {
            throw new IllegalArgumentException(textMode + " cannot declare text aliases");
        }
    }

    public SocialCommandSpec(String id, List<String> aliases, TextMode textMode) {
        this(id, aliases, textMode, Map.of());
    }

    public static SocialCommandSpec natural(String id, String... aliases) {
        return text(id, TextMode.EXPLICIT_OR_NATURAL, aliases);
    }

    public static SocialCommandSpec explicit(String id, String... aliases) {
        return text(id, TextMode.EXPLICIT_ONLY, aliases);
    }

    public static SocialCommandSpec localizedNatural(String id, AliasGroup... aliases) {
        return localized(id, TextMode.EXPLICIT_OR_NATURAL, aliases);
    }

    public static SocialCommandSpec localizedExplicit(String id, AliasGroup... aliases) {
        return localized(id, TextMode.EXPLICIT_ONLY, aliases);
    }

    public static SocialCommandSpec nativeOnly(String id) {
        return new SocialCommandSpec(id, List.of(), TextMode.NATIVE_ONLY, Map.of());
    }

    public static SocialCommandSpec unknownExplicit(String id) {
        return new SocialCommandSpec(
                id, List.of(), TextMode.UNKNOWN_EXPLICIT_FALLBACK, Map.of());
    }

    private static SocialCommandSpec text(String id, TextMode mode, String... aliases) {
        Objects.requireNonNull(aliases, "aliases");
        return new SocialCommandSpec(id, Arrays.asList(aliases), mode);
    }

    private static SocialCommandSpec localized(
            String id,
            TextMode mode,
            AliasGroup... groups
    ) {
        Objects.requireNonNull(groups, "groups");
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        java.util.LinkedHashMap<String, Language> hints = new java.util.LinkedHashMap<>();
        for (AliasGroup group : groups) {
            AliasGroup checked = Objects.requireNonNull(group, "alias group");
            for (String alias : checked.aliases()) {
                aliases.add(alias);
                hints.putIfAbsent(alias, checked.language());
            }
        }
        return new SocialCommandSpec(id, List.copyOf(aliases), mode, hints);
    }

    public static AliasGroup aliases(Language language, String... aliases) {
        return new AliasGroup(language, Arrays.asList(aliases));
    }

    public Optional<Language> languageHint(String invokedAlias, boolean caseSensitive) {
        if (caseSensitive) {
            return Optional.ofNullable(languageHints.get(invokedAlias));
        }
        return languageHints.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(invokedAlias))
                .map(Map.Entry::getValue).findFirst();
    }

    /** Returns the first alias declared for a language, with the first alias as fallback. */
    public Optional<String> primaryAlias(Language language) {
        Objects.requireNonNull(language, "language");
        return aliases.stream()
                .filter(alias -> languageHints.get(alias) == language)
                .findFirst()
                .or(() -> aliases.stream().findFirst());
    }

    static String requireId(String value) {
        String id = Objects.requireNonNull(value, "id").strip()
                .toLowerCase(Locale.ROOT);
        if (id.isEmpty() || id.chars().anyMatch(character ->
                !(character >= 'a' && character <= 'z'
                        || character >= '0' && character <= '9'
                        || character == '.' || character == '-' || character == '_'))) {
            throw new IllegalArgumentException("Invalid social command id: " + value);
        }
        return id;
    }

    public enum TextMode {
        NATIVE_ONLY,
        EXPLICIT_ONLY,
        EXPLICIT_OR_NATURAL,
        UNKNOWN_EXPLICIT_FALLBACK
    }

    public record AliasGroup(Language language, List<String> aliases) {
        public AliasGroup {
            language = Objects.requireNonNull(language, "language");
            aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
            if (aliases.isEmpty()) {
                throw new IllegalArgumentException("Alias group must not be empty");
            }
        }
    }
}
