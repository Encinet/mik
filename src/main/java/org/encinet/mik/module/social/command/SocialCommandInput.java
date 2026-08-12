package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A normalized text or native platform command invocation. */
public record SocialCommandInput(
        String alias,
        String rawArgument,
        InvocationType invocationType,
        Map<String, String> options,
        Optional<Language> languageHint
) {

    public SocialCommandInput {
        alias = Objects.requireNonNull(alias, "alias").strip();
        rawArgument = Objects.requireNonNullElse(rawArgument, "").strip();
        invocationType = Objects.requireNonNull(invocationType, "invocationType");
        options = Map.copyOf(options == null ? Map.of() : options);
        languageHint = languageHint == null ? Optional.empty() : languageHint;
    }

    public SocialCommandInput(
            String alias,
            String rawArgument,
            InvocationType invocationType,
            Map<String, String> options
    ) {
        this(alias, rawArgument, invocationType, options, Optional.empty());
    }

    /** Compatibility constructor for callers migrating from explicit/bare inputs. */
    public SocialCommandInput(String alias, String argument, boolean explicit) {
        this(alias, argument, explicit
                ? InvocationType.EXPLICIT_TEXT : InvocationType.NATURAL_TEXT,
                Map.of(), Optional.empty());
    }

    public static SocialCommandInput explicit(String alias, String argument) {
        return new SocialCommandInput(
                alias, argument, InvocationType.EXPLICIT_TEXT, Map.of(), Optional.empty());
    }

    public static SocialCommandInput natural(String alias, String argument) {
        return new SocialCommandInput(
                alias, argument, InvocationType.NATURAL_TEXT, Map.of(), Optional.empty());
    }

    public static SocialCommandInput bare(String alias, String argument) {
        return natural(alias, argument);
    }

    public static SocialCommandInput nativeCommand(
            String command,
            String rawArgument,
            Map<String, String> options
    ) {
        Map<String, String> checkedOptions = options == null ? Map.of() : options;
        Optional<Language> language = Optional.ofNullable(checkedOptions.get("locale"))
                .flatMap(Language::fromId);
        return new SocialCommandInput(
                command, rawArgument, InvocationType.NATIVE, checkedOptions, language);
    }

    SocialCommandInput withLanguageHint(Optional<Language> language) {
        Optional<Language> checked = Objects.requireNonNull(language, "language");
        return checked.isEmpty() || languageHint.isPresent()
                ? this
                : new SocialCommandInput(
                alias, rawArgument, invocationType, options, checked);
    }

    public String argument() {
        return rawArgument;
    }

    public boolean explicit() {
        return invocationType != InvocationType.NATURAL_TEXT;
    }

    public enum InvocationType {
        EXPLICIT_TEXT,
        NATURAL_TEXT,
        NATIVE
    }
}
