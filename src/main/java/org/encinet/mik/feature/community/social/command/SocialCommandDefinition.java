package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;
import java.util.Optional;

/** Immutable public command metadata, shared by dispatch, help and AI instructions. */
public record SocialCommandDefinition<A>(SocialCommandSpec spec, Optional<Message> description,
                                         SocialArguments<A> arguments) {
    public SocialCommandDefinition {
        spec = Objects.requireNonNull(spec, "spec");
        description = Objects.requireNonNull(description, "description");
        arguments = Objects.requireNonNull(arguments, "arguments");
    }

    public static <A> SocialCommandDefinition<A> visible(SocialCommandSpec spec, Message description,
                                                        SocialArguments<A> arguments) {
        return new SocialCommandDefinition<>(spec, Optional.of(description), arguments);
    }

    public String usage(SocialCommandSyntax syntax, Language language, LanguageService languages) {
        return spec.invocation(syntax, language,
                arguments.usage().map(message -> languages.t(language, message)).orElse(""));
    }
}
