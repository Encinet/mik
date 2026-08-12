package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingException;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

/** Unlinks the current identity and owns every response for the unlink command. */
public final class UnlinkIdentityCommand
        implements SocialCommand<UnlinkIdentityCommand.Request, UnlinkIdentityCommand.Result> {
    private static final String TITLE_ICON = "🔓";
    private static final Message TITLE = Message.SOCIAL_TITLE_IDENTITY_UNLINKING;
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

    private final IdentityBindingManager bindings;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public UnlinkIdentityCommand(
            IdentityBindingManager bindings,
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages
    ) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
    }

    @Override
    public Optional<Message> description() {
        return Optional.of(Message.SOCIAL_UNLINK_COMMAND_DESCRIPTION);
    }

    @Override
    public Request decode(SocialCommandInput input) {
        String confirmation = input.options().getOrDefault(
                "confirm", input.rawArgument());
        return new Request(isConfirmation(confirmation));
    }

    @Override
    public Result handle(SocialCommandContext context, Request request) {
        Optional<ExternalIdentity> identity = context.message().authenticatedIdentity();
        if (identity.isEmpty()) {
            return new Result.IdentityMissing();
        }
        try {
            Optional<IdentityBinding> existing = bindings.find(identity.get().key());
            if (!request.confirmed()) {
                return new Result.Prompt(existing);
            }
            return new Result.Unlinked(bindings.unlink(identity.get().key()));
        } catch (IdentityBindingException error) {
            return new Result.Unavailable();
        }
    }

    @Override
    public SocialDocument present(SocialCommandContext context, Result result) {
        Language language = languageResolver.language(result.languageBinding(), context);
        return switch (result) {
            case Result.IdentityMissing ignored -> document(
                    language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_IDENTITY_MISSING);
            case Result.Prompt ignored -> document(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_BINDING_UNLINK_PROMPT);
            case Result.Unlinked unlinked -> unlinked.binding()
                    .map(binding -> document(language, SocialDocument.Tone.SUCCESS,
                            Message.SOCIAL_BINDING_UNLINKED, binding.playerName())
                            .withUntrustedText(binding.playerName()))
                    .orElseGet(() -> document(language, SocialDocument.Tone.WARNING,
                            Message.SOCIAL_BINDING_NOT_BOUND));
            case Result.Unavailable ignored -> document(
                    language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_UNAVAILABLE);
        };
    }

    private SocialDocument document(
            Language language,
            SocialDocument.Tone tone,
            Message message,
            Object... arguments
    ) {
        return SocialDocument.of(
                        TITLE_ICON + " " + languages.t(language, TITLE), tone,
                        SocialDocument.paragraph(languages.t(
                                language, message, arguments)))
                .localized(language);
    }

    private static boolean isConfirmation(String value) {
        return value != null && CONFIRMATIONS.stream().anyMatch(value::equalsIgnoreCase);
    }

    public record Request(boolean confirmed) {
    }

    public sealed interface Result permits
            Result.IdentityMissing, Result.Prompt, Result.Unlinked, Result.Unavailable {

        Optional<IdentityBinding> languageBinding();

        record IdentityMissing() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }

        record Prompt(Optional<IdentityBinding> binding) implements Result {
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

        record Unavailable() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }
    }
}
