package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingException;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.community.social.command.SocialArguments;
import org.encinet.mik.feature.community.social.command.SocialCommandDefinition;
import org.encinet.mik.feature.community.social.command.SocialCommandExecution;
import org.encinet.mik.feature.community.social.command.SocialCompletionReceipt;
import org.encinet.mik.feature.community.social.command.SocialPresentationContext;
import org.encinet.mik.feature.community.social.command.SocialCommand;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandInput;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSpec;
import org.encinet.mik.feature.community.social.document.SocialDocument;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.encinet.mik.feature.community.social.command.SocialCommandSpec.aliases;

/** Unlinks the current identity and owns every response for the unlink command. */
public final class UnlinkIdentityCommand
        implements SocialCommand<UnlinkIdentityCommand.Request, UnlinkIdentityCommand.Result> {
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedExplicit(
            "binding.unlink",
            aliases(Language.EN_US, "unbind"),
            aliases(Language.ZH_CN, "解绑"),
            aliases(Language.ZH_TW, "解綁"),
            aliases(Language.ZH_HK, "解除連結"),
            aliases(Language.LZH, "解繫"),
            aliases(Language.DE_DE, "trennen"),
            aliases(Language.ES_ES, "desvincular"),
            aliases(Language.FR_FR, "délier"),
            aliases(Language.IT_IT, "scollega"),
            aliases(Language.JA_JP, "解除"),
            aliases(Language.KO_KR, "연결해제"),
            aliases(Language.NL_NL, "ontkoppelen"),
            aliases(Language.PT_BR, "desassociar"),
            aliases(Language.RU_RU, "отвязать"),
            aliases(Language.TH_TH, "ยกเลิกผูก"),
            aliases(Language.UK_UA, "відв’язати"));
    private static final Set<String> CONFIRMATIONS = Set.of(
            "confirm", "true", "yes",
            "确认", "確認", "确定", "確定", "是",
            "bestätigen", "ja", "confirmar", "sí", "si",
            "confirmer", "oui", "conferma", "sì",
            "확인", "예", "bevestigen", "sim",
            "подтвердить", "да", "ยืนยัน", "ใช่", "підтвердити", "так");

    public static final SocialCommandDefinition<Request> DEFINITION = SocialCommandDefinition.visible(
            SPEC, Message.SOCIAL_UNLINK_COMMAND_DESCRIPTION,
            new SocialArguments<>(Optional.of(Message.SOCIAL_UNLINK_COMMAND_PARAMETERS), UnlinkIdentityCommand::parse));

    @Override
    public SocialCommandDefinition<Request> definition() { return DEFINITION; }

    private final UnlinkConfirmations confirmations;
    private final IdentityBindingManager bindings;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public UnlinkIdentityCommand(
            IdentityBindingManager bindings,
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages,
            UnlinkConfirmations confirmations
    ) {
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    private static SocialArguments.Parsed<Request> parse(SocialCommandInput input) {
        if (!input.options().keySet().stream().allMatch(key -> key.equals("locale") || key.equals("confirmation"))) {
            return new SocialArguments.Invalid<>(Message.SOCIAL_COMMAND_INVALID_ARGUMENTS);
        }
        String raw = input.rawArgument().strip();
        if (input.options().containsKey("confirmation")) {
            return new SocialArguments.Valid<>(new Request(Optional.of(input.options().get("confirmation"))));
        }
        if (raw.isEmpty()) return new SocialArguments.Valid<>(new Request(Optional.empty()));
        String[] parts = raw.split("\\s+", 3);
        if (!isConfirmation(parts[0]) || parts.length > 2) {
            return new SocialArguments.Invalid<>(Message.SOCIAL_COMMAND_INVALID_ARGUMENTS);
        }
        return new SocialArguments.Valid<>(new Request(Optional.of(parts.length == 2 ? parts[1] : "")));
    }

    @Override
    public SocialCommandExecution<Result> execute(SocialCommandContext context, Request request) {
        Result result = perform(context, request);
        var receipt = result instanceof Result.Unlinked unlinked && unlinked.binding().isPresent()
                ? Optional.of(SocialCompletionReceipt.BINDING_UNLINKED) : Optional.<SocialCompletionReceipt>empty();
        return new SocialCommandExecution<>(result, languageResolver.target(result.languageBinding(), context), receipt);
    }

    private Result perform(SocialCommandContext context, Request request) {
        Optional<ExternalIdentity> identity = context.message().authenticatedIdentity();
        if (identity.isEmpty()) return new Result.IdentityMissing();
        try {
            var key = identity.orElseThrow().key();
            Optional<IdentityBinding> existing = bindings.find(key);
            if (request.token().isEmpty()) {
                return new Result.Prompt(existing, existing.map(binding -> confirmations.issue(context, key, binding.bindingId())).orElse(""));
            }
            var expected = confirmations.consume(context, key, request.token().orElseThrow());
            if (expected.isEmpty()) return new Result.Expired(existing);
            var removed = bindings.unlink(key, expected.orElseThrow());
            return removed.isEmpty() && bindings.find(key).isPresent()
                    ? new Result.Expired(existing) : new Result.Unlinked(removed);
        } catch (IdentityBindingException error) {
            return new Result.Unavailable();
        }
    }

    @Override
    public SocialDocument present(SocialPresentationContext context, Result result) {
        Language language = context.language();
        return switch (result) {
            case Result.IdentityMissing _ -> document(
                    TRUSTED, language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_IDENTITY_MISSING);
            case Result.Prompt prompt -> prompt.binding().isEmpty()
                    ? document(TRUSTED, language, SocialDocument.Tone.INFO, Message.SOCIAL_BINDING_NOT_BOUND)
                    : SocialDocument.message(REQUIRED, SocialDocument.Tone.WARNING,
                            SocialDocument.instruction(languages.t(language, Message.SOCIAL_BINDING_UNLINK_PROMPT,
                                    prompt.binding().orElseThrow().playerName()),
                                    SPEC.invocation(context.syntax(), language,
                                    confirmationWord(language) + " " + prompt.token())))
                            .localized(language);
            case Result.Unlinked unlinked -> unlinked.binding()
                    .map(binding -> document(REQUIRED, language, SocialDocument.Tone.SUCCESS,
                            Message.SOCIAL_BINDING_UNLINKED, binding.playerName()))
                    .orElseGet(() -> document(TRUSTED, language, SocialDocument.Tone.WARNING,
                            Message.SOCIAL_BINDING_NOT_BOUND));
            case Result.Expired _ -> SocialDocument.message(TRUSTED, SocialDocument.Tone.WARNING,
                    SocialDocument.instruction(languages.t(language, Message.SOCIAL_BINDING_CONFIRMATION_EXPIRED),
                            SPEC.invocation(context.syntax(), language, ""))).localized(language);
            case Result.Unavailable _ -> document(
                    TRUSTED, language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_UNAVAILABLE);
        };
    }

    private SocialDocument document(
            SocialDocument.Moderation moderation,
            Language language,
            SocialDocument.Tone tone,
            Message message,
            Object... arguments
    ) {
        return SocialDocument.message(moderation, tone,
                        SocialDocument.paragraph(languages.t(
                                language, message, arguments)))
                .localized(language);
    }

    private String confirmationWord(Language language) {
        return languages.t(language, Message.SOCIAL_UNLINK_CONFIRMATION);
    }

    private static boolean isConfirmation(String value) {
        return value != null && CONFIRMATIONS.stream().anyMatch(value::equalsIgnoreCase);
    }

    public record Request(Optional<String> token) {
        public Request { token = Objects.requireNonNull(token, "token"); }
    }

    public sealed interface Result permits
            Result.IdentityMissing, Result.Prompt, Result.Unlinked, Result.Expired, Result.Unavailable {

        Optional<IdentityBinding> languageBinding();

        record IdentityMissing() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }

        record Prompt(Optional<IdentityBinding> binding, String token) implements Result {
            public Prompt {
                binding = binding == null ? Optional.empty() : binding;
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return binding;
            }
        }

        record Unlinked(Optional<IdentityBinding> binding) implements Result {
            public Unlinked {
                binding = binding == null ? Optional.empty() : binding;
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return binding;
            }
        }

        record Expired(Optional<IdentityBinding> binding) implements Result {
            @Override public Optional<IdentityBinding> languageBinding() { return binding; }
        }

        record Unavailable() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }
    }
}
