package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** A command owns its parser and displayed parameter syntax together. */
public record SocialArguments<A>(Optional<Message> usage, Function<SocialCommandInput, Parsed<A>> parser) {
    public SocialArguments {
        usage = Objects.requireNonNull(usage, "usage");
        parser = Objects.requireNonNull(parser, "parser");
    }

    public static SocialArguments<NoArguments> none() {
        return new SocialArguments<>(Optional.empty(), input -> input.rawArgument().isEmpty()
                && input.options().keySet().stream().allMatch("locale"::equals)
                ? new Valid<>(NoArguments.INSTANCE) : new Invalid<>(Message.SOCIAL_COMMAND_USAGE));
    }

    public static SocialArguments<NoArguments> ignored() {
        return new SocialArguments<>(Optional.empty(), _ -> new Valid<>(NoArguments.INSTANCE));
    }

    public static SocialArguments<String> text(Message usage) {
        return text(usage, Set.of(), SocialCommandInput::rawArgument);
    }

    public static SocialArguments<String> text(Message usage, Set<String> options,
                                               Function<SocialCommandInput, String> parser) {
        Set<String> allowed = Set.copyOf(options);
        return new SocialArguments<>(Optional.of(usage), input -> input.options().keySet().stream()
                .allMatch(key -> key.equals("locale") || allowed.contains(key))
                ? new Valid<>(parser.apply(input)) : new Invalid<>(Message.SOCIAL_COMMAND_INVALID_ARGUMENTS));
    }

    public sealed interface Parsed<A> permits Valid, Invalid { }

    public record Valid<A>(A value) implements Parsed<A> {
        public Valid { value = Objects.requireNonNull(value, "value"); }
    }

    public record Invalid<A>(Message explanation) implements Parsed<A> {
        public Invalid { explanation = Objects.requireNonNull(explanation, "explanation"); }
    }
}
