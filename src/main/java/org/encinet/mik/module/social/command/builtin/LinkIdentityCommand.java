package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingException;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.identity.IdentityLinkResult;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.document.SocialDocument;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

/** Redeems an identity code and owns every response for the link command. */
public final class LinkIdentityCommand implements SocialCommand<String, LinkIdentityCommand.Result> {
    private static final String TITLE_ICON = "🔗";
    private static final Message TITLE = Message.SOCIAL_TITLE_IDENTITY_LINKING;
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedExplicit(
            "binding.link",
            aliases(Language.EN_US, "bind"),
            aliases(Language.ZH_CN, "绑定"),
            aliases(Language.ZH_TW, "綁定"),
            aliases(Language.ZH_HK, "連結帳號"),
            aliases(Language.LZH, "繫"),
            aliases(Language.DE_DE, "binden"),
            aliases(Language.ES_ES, "vincular"),
            aliases(Language.FR_FR, "lier"),
            aliases(Language.IT_IT, "collega"),
            aliases(Language.JA_JP, "バインド"),
            aliases(Language.KO_KR, "연결"),
            aliases(Language.NL_NL, "koppelen"),
            aliases(Language.PT_BR, "associar"),
            aliases(Language.RU_RU, "привязать"),
            aliases(Language.TH_TH, "ผูก"),
            aliases(Language.UK_UA, "прив’язати"));

    private final IdentityBindingManager bindings;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public LinkIdentityCommand(
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
        return Optional.of(Message.SOCIAL_LINK_COMMAND_DESCRIPTION);
    }

    @Override
    public String decode(SocialCommandInput input) {
        return input.rawArgument();
    }

    @Override
    public Result handle(SocialCommandContext context, String code) {
        if (code == null || code.isBlank()) {
            return help(context);
        }
        Optional<ExternalIdentity> identity = identity(context);
        if (identity.isEmpty()) {
            return new Result.IdentityMissing();
        }
        try {
            Optional<IdentityBinding> existing = bindings.find(identity.get().key());
            return new Result.Linked(bindings.redeem(code, identity.get()), existing);
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
            case Result.Linked linked -> link(
                    linked.result(), language, context.platform().id());
            case Result.Help ignored -> SocialDocument.of(
                            title(language), SocialDocument.Tone.INFO,
                            SocialDocument.orderedList(lines(languages.t(
                                    language, Message.SOCIAL_BINDING_HELP,
                                    context.platform().id()))))
                    .localized(language);
            case Result.Unavailable ignored -> document(
                    language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_UNAVAILABLE);
        };
    }

    private Result help(SocialCommandContext context) {
        Optional<ExternalIdentity> identity = identity(context);
        if (identity.isEmpty()) {
            return new Result.IdentityMissing();
        }
        try {
            return new Result.Help(bindings.find(identity.get().key()));
        } catch (IdentityBindingException error) {
            return new Result.Unavailable();
        }
    }

    private static Optional<ExternalIdentity> identity(SocialCommandContext context) {
        return Objects.requireNonNull(context, "context")
                .message().authenticatedIdentity();
    }

    private SocialDocument link(
            IdentityLinkResult result,
            Language language,
            String platformId
    ) {
        return switch (result.status()) {
            case LINKED -> document(language, SocialDocument.Tone.SUCCESS,
                    Message.SOCIAL_BINDING_LINKED, result.binding().playerName())
                    .withUntrustedText(result.binding().playerName());
            case ALREADY_LINKED -> document(language, SocialDocument.Tone.INFO,
                    Message.SOCIAL_BINDING_ALREADY_LINKED, result.binding().playerName())
                    .withUntrustedText(result.binding().playerName());
            case INVALID_OR_EXPIRED_CODE -> document(language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_CODE_INVALID, platformId);
            case PLATFORM_MISMATCH -> document(language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_PLATFORM_MISMATCH);
            case EXTERNAL_IDENTITY_IN_USE -> document(language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_EXTERNAL_IN_USE);
            case PLAYER_SCOPE_IN_USE -> document(language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_PLAYER_SCOPE_IN_USE);
            case RATE_LIMITED -> document(language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_BINDING_RATE_LIMITED, retryMinutes(result.retryAt()));
        };
    }

    private SocialDocument document(
            Language language,
            SocialDocument.Tone tone,
            Message message,
            Object... arguments
    ) {
        return SocialDocument.of(title(language), tone,
                        SocialDocument.paragraph(languages.t(
                                language, message, arguments)))
                .localized(language);
    }

    private String title(Language language) {
        return TITLE_ICON + " " + languages.t(language, TITLE);
    }

    private static String[] lines(String value) {
        return value.lines().map(String::strip).filter(line -> !line.isEmpty())
                .toArray(String[]::new);
    }

    private static long retryMinutes(Instant retryAt) {
        if (retryAt == null) {
            return 1;
        }
        long seconds = Math.max(0, Duration.between(Instant.now(), retryAt).toSeconds());
        return Math.max(1, (seconds + 59) / 60);
    }

    public sealed interface Result permits
            Result.IdentityMissing, Result.Linked, Result.Help, Result.Unavailable {

        Optional<IdentityBinding> languageBinding();

        record IdentityMissing() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }

        record Linked(IdentityLinkResult result, Optional<IdentityBinding> previous)
                implements Result {
            public Linked {
                result = Objects.requireNonNull(result, "result");
                previous = previous == null ? Optional.empty() : previous;
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return result.bindingOptional().or(() -> previous);
            }
        }

        record Help(Optional<IdentityBinding> binding) implements Result {
            public Help {
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
