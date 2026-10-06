package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.ai.api.AiGateway;
import org.encinet.mik.module.ai.api.AiRequest;
import org.encinet.mik.module.ai.api.AiRequestException;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.document.SocialDocument;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

/** Exposes the shared AI gateway on every installed social platform. */
public final class AiSocialCommand implements SocialCommand<String, AiSocialCommand.Result> {
    private static final long REQUEST_TIMEOUT_SECONDS = 180;
    private static final Set<String> CLEAR_ARGUMENTS = Set.of(
            "clear", "reset", "清除", "清空", "重置", "löschen", "borrar", "effacer",
            "cancella", "消去", "초기화", "wissen", "limpar", "очистить", "ล้าง",
            "очистити");
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedExplicit(
            "ai.ask",
            aliases(Language.EN_US, "ai", "askai"),
            aliases(Language.ZH_CN, "问ai", "问智能"),
            aliases(Language.ZH_TW, "問ai"),
            aliases(Language.ZH_HK, "問智能"),
            aliases(Language.LZH, "問智"),
            aliases(Language.DE_DE, "ki"),
            aliases(Language.ES_ES, "ia"),
            aliases(Language.FR_FR, "demandeia"),
            aliases(Language.IT_IT, "chiediai"),
            aliases(Language.JA_JP, "ai質問"),
            aliases(Language.KO_KR, "ai질문"),
            aliases(Language.NL_NL, "vraagai"),
            aliases(Language.PT_BR, "pergunteia"),
            aliases(Language.RU_RU, "ии"),
            aliases(Language.TH_TH, "ถามai"),
            aliases(Language.UK_UA, "ші"));

    private final AiGateway ai;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;
    private final Logger logger;

    public AiSocialCommand(
            AiGateway ai,
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages,
            Logger logger
    ) {
        this.ai = Objects.requireNonNull(ai, "ai");
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
    }

    @Override
    public Optional<Message> description() {
        return Optional.of(Message.AI_SOCIAL_COMMAND_DESCRIPTION);
    }

    @Override
    public String decode(SocialCommandInput input) {
        return input.rawArgument();
    }

    @Override
    public Result handle(SocialCommandContext context, String argument) {
        Optional<IdentityBinding> binding = languageResolver.binding(context);
        Language language = languageResolver.language(binding, context);
        String prompt = Objects.requireNonNullElse(argument, "").strip();
        if (prompt.isEmpty()) {
            return new Result.QuestionRequired(language);
        }

        String conversationId = conversationId(context);
        if (CLEAR_ARGUMENTS.contains(prompt.toLowerCase(Locale.ROOT))) {
            ai.clear(conversationId);
            return new Result.Cleared(language);
        }
        if (!ai.available()) {
            return new Result.Disabled(language);
        }

        Optional<UUID> playerId = binding.map(IdentityBinding::playerId);
        String requesterName = binding.map(IdentityBinding::playerName)
                .orElse(context.message().senderDisplayName());
        CompletableFuture<String> request;
        try {
            request = ai.ask(new AiRequest(
                    conversationId, requesterName, language.id(), playerId, prompt));
            String answer = request.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            return new Result.Answer(language, answer);
        } catch (RuntimeException error) {
            Throwable cause = unwrap(error);
            if (cause instanceof AiRequestException rejection) {
                return switch (rejection.reason()) {
                    case DISABLED -> new Result.Disabled(language);
                    case PROMPT_EMPTY -> new Result.QuestionRequired(language);
                    case PROMPT_TOO_LONG -> new Result.PromptTooLong(language);
                    case CONVERSATION_BUSY, SERVER_BUSY -> new Result.Busy(language);
                    case CLOSED, EMPTY_RESPONSE -> new Result.Failed(language);
                };
            }
            logger.log(Level.WARNING, "Social AI request failed on "
                    + context.platform().id(), cause);
            return new Result.Failed(language);
        }
    }

    @Override
    public SocialDocument present(SocialCommandContext context, Result result) {
        Language language = result.language();
        return switch (result) {
            case Result.Answer answer -> document(language, SocialDocument.Tone.INFO,
                    answer.text()).withUntrustedText(answer.text());
            case Result.QuestionRequired ignored -> document(
                    language, SocialDocument.Tone.WARNING,
                    languages.t(language, Message.AI_SOCIAL_QUESTION_REQUIRED));
            case Result.Disabled ignored -> document(language, SocialDocument.Tone.WARNING,
                    languages.t(language, Message.AI_DISABLED));
            case Result.Busy ignored -> document(language, SocialDocument.Tone.WARNING,
                    languages.t(language, Message.AI_BUSY));
            case Result.PromptTooLong ignored -> document(language, SocialDocument.Tone.WARNING,
                    languages.t(language, Message.AI_PROMPT_TOO_LONG,
                            ai.maximumPromptCharacters()));
            case Result.Cleared ignored -> document(language, SocialDocument.Tone.SUCCESS,
                    languages.t(language, Message.AI_CLEARED));
            case Result.Failed ignored -> document(language, SocialDocument.Tone.ERROR,
                    languages.t(language, Message.AI_REQUEST_FAILED));
        };
    }

    private static SocialDocument document(
            Language language,
            SocialDocument.Tone tone,
            String text
    ) {
        return SocialDocument.of("✦ AI", tone, SocialDocument.paragraph(text))
                .localized(language);
    }

    private static String conversationId(SocialCommandContext context) {
        String principal = context.message().authenticatedIdentity()
                .map(identity -> identityKey(identity.key()))
                .orElse("event\u0000" + context.message().eventId());
        String scope = context.platform().id() + '\u0000'
                + context.message().conversation().type().name() + '\u0000'
                + context.message().conversation().id() + '\u0000' + principal;
        return "social:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256(scope));
    }

    private static String identityKey(ExternalIdentityKey key) {
        return key.platform() + '\u0000' + key.issuer() + '\u0000'
                + key.scope() + '\u0000' + key.subject();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("The Java runtime does not provide SHA-256", impossible);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public sealed interface Result permits Result.Answer, Result.QuestionRequired,
            Result.Disabled, Result.Busy, Result.PromptTooLong, Result.Cleared, Result.Failed {
        Language language();

        record Answer(Language language, String text) implements Result {
            public Answer {
                Objects.requireNonNull(language, "language");
                text = Objects.requireNonNull(text, "text").strip();
            }
        }

        record QuestionRequired(Language language) implements Result {
            public QuestionRequired {
                Objects.requireNonNull(language, "language");
            }
        }

        record Disabled(Language language) implements Result {
            public Disabled {
                Objects.requireNonNull(language, "language");
            }
        }

        record Busy(Language language) implements Result {
            public Busy {
                Objects.requireNonNull(language, "language");
            }
        }

        record PromptTooLong(Language language) implements Result {
            public PromptTooLong {
                Objects.requireNonNull(language, "language");
            }
        }

        record Cleared(Language language) implements Result {
            public Cleared {
                Objects.requireNonNull(language, "language");
            }
        }

        record Failed(Language language) implements Result {
            public Failed {
                Objects.requireNonNull(language, "language");
            }
        }
    }
}
