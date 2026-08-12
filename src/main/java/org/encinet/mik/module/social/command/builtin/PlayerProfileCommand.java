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
import org.encinet.mik.module.social.game.SocialGameService;
import org.encinet.mik.module.social.game.SocialPlayerProfile;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

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

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
    }

    @Override
    public Optional<Message> description() {
        return Optional.of(Message.SOCIAL_PROFILE_COMMAND_DESCRIPTION);
    }

    @Override
    public String decode(SocialCommandInput input) {
        return input.options().getOrDefault("player", input.rawArgument()).strip();
    }

    @Override
    public Result handle(SocialCommandContext context, String requestedPlayerName) {
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
                return new Result.Found(caller, game.playerProfile(caller));
            }
            boolean targetsCallerByName = targetName.equalsIgnoreCase(caller.playerName());
            boolean targetsCallerByReference = targetName.isEmpty()
                    && referenced.size() == 1
                    && referenced.getFirst().key().equals(caller.externalKey());
            if (targetsCallerByName || targetsCallerByReference) {
                return new Result.Found(caller, game.playerProfile(caller));
            }
            if (!game.isFullMember(caller.playerId())) {
                return new Result.MemberRequired(caller);
            }
            if (!targetName.isEmpty()) {
                return lookupByPlayerName(caller, targetName);
            }
            if (referenced.size() != 1) {
                return new Result.TargetAmbiguous(caller);
            }
            return bindings.find(referenced.getFirst().key())
                    .<Result>map(target -> new Result.Found(
                            caller, game.playerProfile(target)))
                    .orElseGet(() -> new Result.TargetUnbound(caller));
        } catch (IdentityBindingException error) {
            return new Result.Unavailable();
        }
    }

    @Override
    public SocialDocument present(SocialCommandContext context, Result result) {
        Language language = languageResolver.language(result.languageBinding(), context);
        return switch (result) {
            case Result.IdentityMissing ignored -> message(
                    language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_IDENTITY_MISSING);
            case Result.Unbound ignored -> message(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_BINDING_STATUS_UNBOUND);
            case Result.MemberRequired ignored -> message(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_MEMBER_REQUIRED);
            case Result.PlayerNotFound ignored -> message(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_PLAYER_NOT_FOUND);
            case Result.TargetUnbound ignored -> message(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_TARGET_UNBOUND);
            case Result.TargetAmbiguous ignored -> message(
                    language, SocialDocument.Tone.WARNING,
                    Message.SOCIAL_PROFILE_TARGET_AMBIGUOUS);
            case Result.Unavailable ignored -> message(
                    language, SocialDocument.Tone.ERROR,
                    Message.SOCIAL_BINDING_UNAVAILABLE);
            case Result.Found found -> profile(
                    found.profile(), language, context.syntax().defaultLanguage());
        };
    }

    private Result lookupByPlayerName(IdentityBinding caller, String playerName) {
        if (playerName.length() > 64
                || playerName.chars().anyMatch(Character::isISOControl)) {
            return new Result.PlayerNotFound(caller);
        }
        return game.findPlayerProfile(playerName)
                .<Result>map(profile -> new Result.Found(caller, profile))
                .orElseGet(() -> new Result.PlayerNotFound(caller));
    }

    private SocialDocument profile(
            SocialPlayerProfile profile,
            Language language,
            Language platformDefault
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
        fields.add(field(language, Message.SOCIAL_PROFILE_UUID_LABEL,
                profile.playerId().toString()));
        fields.add(field(language, Message.SOCIAL_PROFILE_PRESENCE_LABEL,
                languages.t(language, presence)));
        fields.add(field(language, Message.SOCIAL_PROFILE_PLAY_TIME_LABEL,
                duration(profile.playTime(), language)));
        profile.firstJoined().ifPresent(firstJoined -> fields.add(field(language,
                Message.SOCIAL_PROFILE_FIRST_JOINED_LABEL,
                dateTime(firstJoined, language))));
        profile.lastSeen().ifPresent(lastSeen -> fields.add(field(language,
                Message.SOCIAL_PROFILE_LAST_SEEN_LABEL,
                dateTime(lastSeen, language))));
        fields.add(field(language, Message.SOCIAL_PROFILE_LANGUAGE_LABEL,
                playerLanguage(profile.playerId(), platformDefault)));
        blocks.add(new SocialDocument.Fields(fields));
        return new SocialDocument(title(language) + " · " + profile.playerName(),
                SocialDocument.Tone.INFO, blocks)
                .localized(language)
                .withUntrustedText(profile.playerName());
    }

    private SocialDocument message(
            Language language,
            SocialDocument.Tone tone,
            Message message
    ) {
        return SocialDocument.of(title(language), tone,
                        SocialDocument.paragraph(languages.t(language, message)))
                .localized(language);
    }

    private SocialDocument.Field field(Language language, Message label, String value) {
        return new SocialDocument.Field(languages.t(language, label), value);
    }

    /** The document follows the requester, while this profile field belongs to its player. */
    private String playerLanguage(java.util.UUID playerId, Language platformDefault) {
        return languages.preferredLanguage(playerId)
                .orElse(platformDefault)
                .displayName();
    }

    private String dateTime(Instant instant, Language language) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(language.locale()).withZone(zone).format(instant);
    }

    private String duration(Duration duration, Language language) {
        long seconds = Math.max(0, duration.toSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) {
            return languages.t(language, Message.SOCIAL_DURATION_DAYS,
                    days, hours, minutes);
        }
        if (hours > 0) {
            return languages.t(language, Message.SOCIAL_DURATION_HOURS, hours, minutes);
        }
        return languages.t(language, Message.SOCIAL_DURATION_MINUTES, minutes);
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

        record Found(IdentityBinding binding, SocialPlayerProfile profile) implements Result {
            public Found {
                binding = Objects.requireNonNull(binding, "binding");
                profile = Objects.requireNonNull(profile, "profile");
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
