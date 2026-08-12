package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.social.command.NoArguments;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.game.ServerSnapshot;
import org.encinet.mik.module.social.game.SocialGameService;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

/** Shows the complete server status and owns its complete response layout. */
public final class ServerStatusCommand implements SocialCommand<NoArguments, ServerSnapshot> {
    private static final String TITLE_ICON = "📊";
    private static final Message TITLE = Message.SOCIAL_TITLE_SERVER_STATUS;
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedNatural(
            "server.status",
            aliases(Language.EN_US, "status", "server"),
            aliases(Language.ZH_CN, "状态", "服务器"),
            aliases(Language.ZH_TW, "狀態", "伺服器"),
            aliases(Language.ZH_HK, "伺服器狀態"),
            aliases(Language.LZH, "狀況"),
            aliases(Language.DE_DE, "serverstatus"),
            aliases(Language.ES_ES, "estado"),
            aliases(Language.FR_FR, "statut"),
            aliases(Language.IT_IT, "stato"),
            aliases(Language.JA_JP, "状態"),
            aliases(Language.KO_KR, "상태"),
            aliases(Language.NL_NL, "serverstatus"),
            aliases(Language.PT_BR, "situação"),
            aliases(Language.RU_RU, "статус"),
            aliases(Language.TH_TH, "สถานะ"),
            aliases(Language.UK_UA, "стан"));

    private final SocialGameService game;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public ServerStatusCommand(
            SocialGameService game,
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages
    ) {
        this.game = Objects.requireNonNull(game, "game");
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
    }

    @Override
    public Optional<Message> description() {
        return Optional.of(Message.SOCIAL_STATUS_COMMAND_DESCRIPTION);
    }

    @Override
    public NoArguments decode(SocialCommandInput input) {
        return NoArguments.decode(input);
    }

    @Override
    public ServerSnapshot handle(SocialCommandContext context, NoArguments argument) {
        return game.serverSnapshot(false);
    }

    @Override
    public SocialDocument present(SocialCommandContext context, ServerSnapshot snapshot) {
        Language language = languageResolver.language(context);
        return SocialDocument.of(
                TITLE_ICON + " " + languages.t(language, TITLE),
                SocialDocument.Tone.INFO,
                SocialDocument.fields(
                        field(language, Message.SOCIAL_QUERY_PLAYERS_LABEL,
                                population(snapshot, language)),
                        new SocialDocument.Field("TPS",
                                number(snapshot.tpsOneMinute())),
                        new SocialDocument.Field("MSPT",
                                number(snapshot.mspt())),
                        field(language, Message.SOCIAL_QUERY_UPTIME_LABEL,
                                duration(snapshot.uptime(), language))))
                .localized(language);
    }

    private String population(ServerSnapshot snapshot, Language language) {
        return label(language, Message.SOCIAL_QUERY_ONLINE_LABEL) + ' '
                + snapshot.onlinePlayers() + '/' + snapshot.maxPlayers()
                + " · " + label(language, Message.SOCIAL_QUERY_ACTIVE_LABEL) + ' '
                + snapshot.activePlayers() + " · AFK " + snapshot.afkPlayers();
    }

    private SocialDocument.Field field(Language language, Message label, String value) {
        return new SocialDocument.Field(label(language, label), value);
    }

    private String label(Language language, Message label) {
        return languages.t(language, label).replaceAll("[：:]\\s*$", "");
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

    private static String number(double value) {
        double safe = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
        return String.format(Locale.ROOT, "%.2f", safe);
    }
}
