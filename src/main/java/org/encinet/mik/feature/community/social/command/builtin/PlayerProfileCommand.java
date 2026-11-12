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
import org.encinet.mik.feature.community.social.command.SocialPresentationContext;
import org.encinet.mik.feature.community.social.command.SocialCommand;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSpec;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialTimeText;
import org.encinet.mik.feature.community.social.game.SocialGameService;
import org.encinet.mik.feature.community.social.game.SocialPlayerProfile;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.encinet.mik.feature.community.social.command.SocialCommandSpec.aliases;

/** Looks up a player profile and owns its complete response layout. */
public final class PlayerProfileCommand
        implements SocialCommand<String, PlayerProfileCommand.Result> {
    private static final String TITLE_ICON = "👤";
    private static final Message TITLE = Message.SOCIAL_TITLE_PLAYER_PROFILE;
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedExplicit(
            "binding.profile",
            aliases(Language.EN_US, "profile", "me"),
            aliases(Language.ZH_CN, "我的", "我的账号"),
            aliases(Language.ZH_TW, "我的帳號"),
            aliases(Language.ZH_HK, "我的資料"),
            aliases(Language.LZH, "吾"),
            aliases(Language.DE_DE, "profil"),
            aliases(Language.ES_ES, "perfil"),
            aliases(Language.FR_FR, "monprofil"),
            aliases(Language.IT_IT, "profilo"),
            aliases(Language.JA_JP, "プロフィール"),
            aliases(Language.KO_KR, "프로필"),
            aliases(Language.NL_NL, "profiel"),
            aliases(Language.PT_BR, "meuperfil"),
            aliases(Language.RU_RU, "профиль"),
            aliases(Language.TH_TH, "โปรไฟล์"),
            aliases(Language.UK_UA, "профіль"));
    public static final SocialCommandDefinition<String> DEFINITION =
            SocialCommandDefinition.visible(SPEC, Message.SOCIAL_PROFILE_COMMAND_DESCRIPTION,
                    SocialArguments.text(Message.SOCIAL_PROFILE_COMMAND_PARAMETERS, java.util.Set.of("player"),
                            input -> input.options().getOrDefault("player", input.rawArgument()).strip()));

    @Override
    public SocialCommandDefinition<String> definition() { return DEFINITION; }


    private final IdentityBindingManager bindings;
    private final SocialGameService game;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;
    private final ZoneId zone;

    public PlayerProfileCommand(
            IdentityBindingManager bindings,
            SocialGameService game,
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages,
            ZoneId zone
    ) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.game = Objects.requireNonNull(game, "game");
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public static String invocation(org.encinet.mik.feature.community.social.command.SocialCommandSyntax syntax, Language language) {
        return SPEC.invocation(syntax, language, "");
    }

    @Override
    public SocialCommandExecution<Result> execute(SocialCommandContext context, String requestedPlayerName) {
        Result result = perform(context, requestedPlayerName);
        return SocialCommandExecution.result(result, languageResolver.target(result.languageBinding(), context));
    }

    private Result perform(SocialCommandContext context, String requestedPlayerName) {
        if (context.message().authenticatedIdentity().isEmpty()) {
            return new Result.IdentityMissing();
        }
        try {
            Optional<IdentityBinding> binding = bindings.find(
                    context.message().authenticatedIdentity().orElseThrow().key());
            if (binding.isEmpty()) {
                return new Result.Unbound();
            }
            IdentityBinding caller = binding.orElseThrow();
            String targetName = Objects.requireNonNullElse(
                    requestedPlayerName, "").strip();
            List<ExternalIdentity> referenced = context.message().references()
                    .targetIdentities();
            if (targetName.isEmpty() && referenced.isEmpty()) {
                return found(caller, game.playerProfile(caller), context.syntax().defaultLanguage());
            }
            boolean targetsCallerByName = targetName.equalsIgnoreCase(caller.playerName());
            boolean targetsCallerByReference = targetName.isEmpty()
                    && referenced.size() == 1
                    && referenced.getFirst().key().equals(caller.externalKey());
            if (targetsCallerByName || targetsCallerByReference) {
                return found(caller, game.playerProfile(caller), context.syntax().defaultLanguage());
            }
            if (!game.isFullMember(caller.playerId())) {
                return new Result.MemberRequired(caller);
            }
            if (!targetName.isEmpty()) {
                return lookupByPlayerName(caller, targetName, context.syntax().defaultLanguage());
            }
            if (referenced.size() != 1) {
                return new Result.TargetAmbiguous(caller);
            }
            return bindings.find(referenced.getFirst().key())
                    .<Result>map(target -> found(
                            caller, game.playerProfile(target), context.syntax().defaultLanguage()))
                    .orElseGet(() -> new Result.TargetUnbound(caller));
        } catch (IdentityBindingException error) {
            return new Result.Unavailable();
        }
    }

    @Override
    public SocialDocument present(SocialPresentationContext context, Result result) {
        Language language = context.language();
        return switch (result) {
            case Result.IdentityMissing _ -> message(
                    TRUSTED, language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_IDENTITY_MISSING);
            case Result.Unbound _ -> SocialDocument.message(TRUSTED, SocialDocument.Tone.WARNING,
                    SocialDocument.paragraph(languages.t(language, Message.SOCIAL_BINDING_NOT_BOUND)),
                    SocialDocument.paragraph(languages.t(language, Message.SOCIAL_BINDING_HELP,
                            "/bind " + context.platform().id()))).localized(language);
            case Result.MemberRequired _ -> message(
                    TRUSTED, language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_MEMBER_REQUIRED);
            case Result.PlayerNotFound _ -> message(
                    TRUSTED, language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_PLAYER_NOT_FOUND);
            case Result.TargetUnbound _ -> message(
                    TRUSTED, language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_TARGET_UNBOUND);
            case Result.TargetAmbiguous _ -> message(
                    TRUSTED, language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_TARGET_AMBIGUOUS);
            case Result.Unavailable _ -> message(
                    TRUSTED, language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_UNAVAILABLE);
            case Result.Found found -> profile(
                    found.profile(), language, found.playerLanguage());
        };
    }

    private Result lookupByPlayerName(IdentityBinding caller, String playerName, Language fallback) {
        if (playerName.length() > 64
                || playerName.chars().anyMatch(Character::isISOControl)) {
            return new Result.PlayerNotFound(caller);
        }
        return game.findPlayerProfile(playerName)
                .<Result>map(profile -> found(caller, profile, fallback))
                .orElseGet(() -> new Result.PlayerNotFound(caller));
    }

    private SocialDocument profile(
            SocialPlayerProfile profile,
            Language language,
            Language playerLanguage
    ) {
        Message presence = switch (profile.presence()) {
            case ONLINE -> Message.SOCIAL_PROFILE_ONLINE;
            case AFK -> Message.SOCIAL_PROFILE_AFK;
            case OFFLINE -> Message.SOCIAL_PROFILE_OFFLINE;
        };
        List<SocialDocument.Block> blocks = new ArrayList<>();
        profile.avatar().ifPresent(avatar -> blocks.add(
                SocialDocument.image(
                        languages.t(language, Message.SOCIAL_PROFILE_AVATAR_ALT), "image/png",
                        avatar.pngData(), 256, 256)));
        List<SocialDocument.Field> fields = new ArrayList<>();
        fields.add(field(language, Message.SOCIAL_PROFILE_PLAYER_LABEL,
                profile.playerName()));
        fields.add(field(language, Message.SOCIAL_PROFILE_PRESENCE_LABEL,
                languages.t(language, presence)));
        fields.add(field(language, Message.SOCIAL_PROFILE_PLAY_TIME_LABEL,
                SocialTimeText.duration(languages, language, profile.playTime())));
        profile.firstJoined().ifPresent(firstJoined -> fields.add(field(language,
                Message.SOCIAL_PROFILE_FIRST_JOINED_LABEL,
                SocialTimeText.dateTime(language, firstJoined, zone))));
        profile.lastSeen().ifPresent(lastSeen -> fields.add(field(language,
                Message.SOCIAL_PROFILE_LAST_SEEN_LABEL,
                SocialTimeText.dateTime(language, lastSeen, zone))));
        fields.add(field(language, Message.SOCIAL_PROFILE_UUID_LABEL,
                profile.playerId().toString()));
        fields.add(field(language, Message.SOCIAL_PROFILE_LANGUAGE_LABEL,
                playerLanguage.displayName()));
        blocks.add(new SocialDocument.Fields(fields));
        return new SocialDocument(REQUIRED, title(language) + " · " + profile.playerName(),
                SocialDocument.Tone.INFO, blocks)
                .localized(language);
    }

    private SocialDocument message(
            SocialDocument.Moderation moderation,
            Language language,
            SocialDocument.Tone tone,
            Message message
    ) {
        return SocialDocument.message(moderation, tone,
                        SocialDocument.paragraph(languages.t(language, message)))
                .localized(language);
    }

    private SocialDocument.Field field(Language language, Message label, String value) {
        return new SocialDocument.Field(languages.t(language, label), value);
    }

    private Result.Found found(IdentityBinding caller, SocialPlayerProfile profile, Language fallback) {
        return new Result.Found(caller, profile, languages.preferredLanguage(profile.playerId()).orElse(fallback));
    }

    private String title(Language language) {
        return TITLE_ICON + " " + languages.t(language, TITLE);
    }

    public sealed interface Result permits
            Result.IdentityMissing,
            Result.Unbound,
            Result.MemberRequired,
            Result.PlayerNotFound,
            Result.TargetUnbound,
            Result.TargetAmbiguous,
            Result.Found,
            Result.Unavailable {

        Optional<IdentityBinding> languageBinding();

        record IdentityMissing() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }

        record Unbound() implements Result {
            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.empty();
            }
        }

        record MemberRequired(IdentityBinding binding) implements Result {
            public MemberRequired {
                binding = Objects.requireNonNull(binding, "binding");
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.of(binding);
            }
        }

        record PlayerNotFound(IdentityBinding binding) implements Result {
            public PlayerNotFound {
                binding = Objects.requireNonNull(binding, "binding");
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.of(binding);
            }
        }

        record TargetUnbound(IdentityBinding binding) implements Result {
            public TargetUnbound {
                binding = Objects.requireNonNull(binding, "binding");
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.of(binding);
            }
        }

        record TargetAmbiguous(IdentityBinding binding) implements Result {
            public TargetAmbiguous {
                binding = Objects.requireNonNull(binding, "binding");
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.of(binding);
            }
        }

        record Found(IdentityBinding binding, SocialPlayerProfile profile, Language playerLanguage) implements Result {
            public Found {
                binding = Objects.requireNonNull(binding, "binding");
                profile = Objects.requireNonNull(profile, "profile");
                playerLanguage = Objects.requireNonNull(playerLanguage, "playerLanguage");
            }

            @Override
            public Optional<IdentityBinding> languageBinding() {
                return Optional.of(binding);
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
