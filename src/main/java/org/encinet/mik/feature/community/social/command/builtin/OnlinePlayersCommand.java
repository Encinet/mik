package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.social.command.NoArguments;
import org.encinet.mik.feature.community.social.command.SocialArguments;
import org.encinet.mik.feature.community.social.command.SocialCommandDefinition;
import org.encinet.mik.feature.community.social.command.SocialCommandExecution;
import org.encinet.mik.feature.community.social.command.SocialPresentationContext;
import org.encinet.mik.feature.community.social.command.SocialCommand;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSpec;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.game.ServerSnapshot;
import org.encinet.mik.feature.community.social.game.SocialGameService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.encinet.mik.feature.community.social.command.SocialCommandSpec.aliases;

/** Lists online players and owns its complete response layout. */
public final class OnlinePlayersCommand implements SocialCommand<NoArguments, ServerSnapshot> {
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
    public static final SocialCommandDefinition<NoArguments> DEFINITION =
            SocialCommandDefinition.visible(SPEC, Message.SOCIAL_ONLINE_COMMAND_DESCRIPTION, SocialArguments.none());

    @Override
    public SocialCommandDefinition<NoArguments> definition() { return DEFINITION; }


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
    public SocialCommandExecution<ServerSnapshot> execute(SocialCommandContext context, NoArguments argument) {
        return SocialCommandExecution.result(perform(context, argument), languageResolver.target(context));
    }

    private ServerSnapshot perform(SocialCommandContext context, NoArguments argument) {
        return game.serverSnapshot(true);
    }

    @Override
    public SocialDocument present(SocialPresentationContext context, ServerSnapshot snapshot) {
        Language language = context.language();
        List<String> displayedPlayerNames = snapshot.players().stream()
                .map(player -> languages.t(language, Message.SOCIAL_QUERY_ONLINE_PLAYER, player.name(), player.afk() ? "afk" : "active"))
                .toList();
        List<SocialDocument.Block> blocks = new ArrayList<>();
        blocks.add(SocialDocument.paragraph(snapshot.onlinePlayers() == 0
                ? languages.t(language, Message.SOCIAL_QUERY_NO_PLAYERS)
                : languages.t(language, Message.SOCIAL_QUERY_ONLINE_SUMMARY,
                        snapshot.onlinePlayers(), snapshot.afkPlayers())));
        if (!displayedPlayerNames.isEmpty()) blocks.add(
                SocialDocument.unorderedList(displayedPlayerNames.toArray(String[]::new)));
        return new SocialDocument(displayedPlayerNames.isEmpty() ? TRUSTED : REQUIRED,
                "", SocialDocument.Tone.INFO, blocks).localized(language);
    }

}
