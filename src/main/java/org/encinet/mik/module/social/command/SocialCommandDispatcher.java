package org.encinet.mik.module.social.command;

import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable command index responsible only for resolution and typed invocation. */
public final class SocialCommandDispatcher {

    private final Map<String, RegisteredCommand> commands;
    private final Map<String, RegisteredCommand> exactExplicitAliases;
    private final Map<String, RegisteredCommand> exactNaturalAliases;
    private final Map<String, RegisteredCommand> foldedExplicitAliases;
    private final Map<String, RegisteredCommand> foldedNaturalAliases;
    private final RegisteredCommand unknownExplicit;

    public SocialCommandDispatcher(Collection<? extends SocialCommand<?, ?>> commands) {
        Objects.requireNonNull(commands, "commands");
        Map<String, RegisteredCommand> byId = new LinkedHashMap<>();
        Map<String, RegisteredCommand> exactExplicit = new LinkedHashMap<>();
        Map<String, RegisteredCommand> exactNatural = new LinkedHashMap<>();
        Map<String, RegisteredCommand> foldedExplicit = new LinkedHashMap<>();
        Map<String, RegisteredCommand> foldedNatural = new LinkedHashMap<>();
        RegisteredCommand fallback = null;
        for (SocialCommand<?, ?> command : commands) {
            RegisteredCommand registered = register(command);
            SocialCommandSpec spec = registered.spec();
            if (byId.putIfAbsent(spec.id(), registered) != null) {
                throw new IllegalArgumentException(
                        "Duplicate social command id: " + spec.id());
            }
            if (spec.textMode() == SocialCommandSpec.TextMode.UNKNOWN_EXPLICIT_FALLBACK) {
                if (fallback != null) {
                    throw new IllegalArgumentException(
                            "Multiple unknown explicit social commands: "
                                    + fallback.spec().id() + " and " + spec.id());
                }
                fallback = registered;
                continue;
            }
            if (spec.textMode() == SocialCommandSpec.TextMode.NATIVE_ONLY) {
                continue;
            }
            for (String alias : spec.aliases()) {
                bindAlias(exactExplicit, alias, registered);
                bindAlias(foldedExplicit, fold(alias), registered);
                if (spec.textMode() == SocialCommandSpec.TextMode.EXPLICIT_OR_NATURAL) {
                    bindAlias(exactNatural, alias, registered);
                    bindAlias(foldedNatural, fold(alias), registered);
                }
            }
        }
        this.commands = Map.copyOf(byId);
        exactExplicitAliases = Map.copyOf(exactExplicit);
        exactNaturalAliases = Map.copyOf(exactNatural);
        foldedExplicitAliases = Map.copyOf(foldedExplicit);
        foldedNaturalAliases = Map.copyOf(foldedNatural);
        unknownExplicit = fallback;
    }

    public Optional<PreparedInvocation> resolve(
            SocialCommandSyntax syntax,
            SocialInboundMessage message
    ) {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(message, "message");
        return switch (message.content()) {
            case SocialInboundMessage.NativeCommand nativeCommand -> {
                RegisteredCommand command = commands.get(
                        SocialCommandSpec.requireId(nativeCommand.commandId()));
                yield command == null ? Optional.empty()
                        : Optional.of(new PreparedInvocation(command,
                        SocialCommandInput.nativeCommand(command.spec().id(),
                                nativeCommand.rawArgument(), nativeCommand.options()), syntax));
            }
            case SocialInboundMessage.Text text -> syntax.parse(text.body())
                    .flatMap(input -> resolveText(syntax, input));
        };
    }

    public boolean contains(String commandId) {
        return commands.containsKey(SocialCommandSpec.requireId(commandId));
    }

    public java.util.List<SocialCommandSpec> specs() {
        return commands.values().stream().map(RegisteredCommand::spec).toList();
    }

    private Optional<PreparedInvocation> resolveText(
            SocialCommandSyntax syntax,
            SocialCommandInput input
    ) {
        Map<String, RegisteredCommand> aliases;
        if (syntax.caseSensitiveAliases()) {
            aliases = input.invocationType() == SocialCommandInput.InvocationType.EXPLICIT_TEXT
                    ? exactExplicitAliases : exactNaturalAliases;
        } else {
            aliases = input.invocationType() == SocialCommandInput.InvocationType.EXPLICIT_TEXT
                    ? foldedExplicitAliases : foldedNaturalAliases;
        }
        String alias = syntax.caseSensitiveAliases() ? input.alias() : fold(input.alias());
        RegisteredCommand command = aliases.get(alias);
        if (command == null
                && input.invocationType() == SocialCommandInput.InvocationType.EXPLICIT_TEXT) {
            command = unknownExplicit;
        }
        if (command != null) {
            input = input.withLanguageHint(command.spec().languageHint(
                    input.alias(), syntax.caseSensitiveAliases()));
        }
        return command == null
                ? Optional.empty()
                : Optional.of(new PreparedInvocation(command, input, syntax));
    }

    private static void bindAlias(
            Map<String, RegisteredCommand> aliases,
            String alias,
            RegisteredCommand command
    ) {
        RegisteredCommand previous = aliases.putIfAbsent(alias, command);
        if (previous != null && previous != command) {
            throw new IllegalArgumentException("Social command alias '" + alias
                    + "' is mapped to both " + previous.spec().id()
                    + " and " + command.spec().id());
        }
    }

    private static String fold(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static <A, R> RegisteredCommand register(SocialCommand<A, R> command) {
        SocialCommand<A, R> checked = Objects.requireNonNull(command, "command");
        SocialCommandSpec spec = Objects.requireNonNull(checked.spec(), "command spec");
        return new RegisteredCommand(spec, (context, input) -> {
            A argument = Objects.requireNonNull(checked.decode(input),
                    "Social command decoder returned null for " + spec.id());
            R result = Objects.requireNonNull(checked.handle(context, argument),
                    "Social command handler returned null for " + spec.id());
            return Objects.requireNonNull(checked.present(context, result),
                    "Social command presenter returned null for " + spec.id());
        });
    }

    public static final class PreparedInvocation {
        private final RegisteredCommand command;
        private final SocialCommandInput input;
        private final SocialCommandSyntax syntax;

        private PreparedInvocation(
                RegisteredCommand command,
                SocialCommandInput input,
                SocialCommandSyntax syntax
        ) {
            this.command = command;
            this.input = input;
            this.syntax = syntax;
        }

        public String commandId() {
            return command.spec().id();
        }

        public SocialCommandInput input() {
            return input;
        }

        public SocialCommandSyntax syntax() {
            return syntax;
        }

        public SocialDocument execute(SocialCommandContext context) {
            SocialCommandContext checked = Objects.requireNonNull(context, "context");
            return command.invoker().invoke(new SocialCommandContext(
                    checked.platform(), checked.message(), input, syntax), input);
        }
    }

    private record RegisteredCommand(SocialCommandSpec spec, Invoker invoker) {
    }

    @FunctionalInterface
    private interface Invoker {
        SocialDocument invoke(SocialCommandContext context, SocialCommandInput input);
    }
}
