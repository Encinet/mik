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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

/** Lists online players and owns its complete response layout. */
public final class OnlinePlayersCommand implements SocialCommand<NoArguments, ServerSnapshot> {
    private static final String TITLE_ICON = "👥";
    private static final Message TITLE = Message.SOCIAL_TITLE_ONLINE_PLAYERS;
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedNatural(
            "server.online",
            aliases(Language.EN_US, "online", "players", "who"),
            aliases(Language.ZH_CN, "在线", "在线人数", "玩家"),
            aliases(Language.ZH_TW, "在線", "在線人數"),
            aliases(Language.ZH_HK, "在線玩家"),
            aliases(Language.LZH, "在綫"),
            aliases(Language.DE_DE, "spieler"),
            aliases(Language.ES_ES, "enlínea", "jugadores"),
            aliases(Language.FR_FR, "enligne", "joueurs"),
            aliases(Language.IT_IT, "giocatori"),
            aliases(Language.JA_JP, "オンライン", "プレイヤー"),
            aliases(Language.KO_KR, "온라인", "플레이어"),
            aliases(Language.NL_NL, "spelers"),
            aliases(Language.PT_BR, "jogadores"),
            aliases(Language.RU_RU, "онлайн", "игроки"),
            aliases(Language.TH_TH, "ออนไลน์", "ผู้เล่น"),
            aliases(Language.UK_UA, "гравці"));

    private final SocialGameService game;
    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public OnlinePlayersCommand(
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
        return Optional.of(Message.SOCIAL_ONLINE_COMMAND_DESCRIPTION);
    }

    @Override
    public NoArguments decode(SocialCommandInput input) {
        return NoArguments.decode(input);
    }

    @Override
    public ServerSnapshot handle(SocialCommandContext context, NoArguments argument) {
        return game.serverSnapshot(true);
    }

    @Override
    public SocialDocument present(SocialCommandContext context, ServerSnapshot snapshot) {
        Language language = languageResolver.language(context);
        List<SocialDocument.Field> fields = new ArrayList<>();
        fields.add(new SocialDocument.Field(
                label(language, Message.SOCIAL_QUERY_ONLINE_LABEL),
                snapshot.onlinePlayers() + " / " + snapshot.maxPlayers()));
        fields.add(new SocialDocument.Field(
                label(language, Message.SOCIAL_QUERY_PLAYERS_LABEL),
                snapshot.playerNames().isEmpty()
                        ? languages.t(language, Message.SOCIAL_QUERY_NO_PLAYERS)
                        : String.join(", ", snapshot.playerNames())));
        SocialDocument document = new SocialDocument(
                TITLE_ICON + " " + languages.t(language, TITLE),
                SocialDocument.Tone.INFO, List.of(new SocialDocument.Fields(fields)))
                .localized(language);
        return snapshot.playerNames().isEmpty()
                ? document : document.withUntrustedText(snapshot.playerNames());
    }

    private String label(Language language, Message message) {
        return languages.t(language, message).replaceAll("[：:]\\s*$", "");
    }
}
