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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.encinet.mik.feature.community.social.command.SocialCommandSpec.aliases;

/** Presents the shared inbound command help. */
public final class SocialHelpCommand implements SocialCommand<NoArguments, NoArguments> {
    private static final String TITLE_ICON = "📖";
    private static final Message TITLE = Message.SOCIAL_TITLE_COMMAND_HELP;
    private static final SocialCommandSpec SPEC = SocialCommandSpec.localizedNatural(
            "server.help",
            aliases(Language.EN_US, "help", "?"),
            aliases(Language.ZH_CN, "帮助"),
            aliases(Language.ZH_TW, "幫助"),
            aliases(Language.ZH_HK, "指令幫助"),
            aliases(Language.LZH, "助"),
            aliases(Language.DE_DE, "hilfe"),
            aliases(Language.ES_ES, "ayuda"),
            aliases(Language.FR_FR, "aide"),
            aliases(Language.IT_IT, "aiuto"),
            aliases(Language.JA_JP, "ヘルプ"),
            aliases(Language.KO_KR, "도움말"),
            aliases(Language.NL_NL, "hulp"),
            aliases(Language.PT_BR, "ajuda"),
            aliases(Language.RU_RU, "помощь"),
            aliases(Language.TH_TH, "ช่วยเหลือ"),
            aliases(Language.UK_UA, "допомога"));
    public static final SocialCommandDefinition<NoArguments> DEFINITION =
            SocialCommandDefinition.visible(SPEC, Message.SOCIAL_HELP_COMMAND_DESCRIPTION, SocialArguments.none());

    @Override
    public SocialCommandDefinition<NoArguments> definition() { return DEFINITION; }


    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;
    private final List<SocialCommandDefinition<?>> commands;

    public SocialHelpCommand(
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages,
            List<SocialCommandDefinition<?>> commands
    ) {
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
    }

    public static String invocation(org.encinet.mik.feature.community.social.command.SocialCommandSyntax syntax, Language language) {
        return SPEC.invocation(syntax, language, "");
    }

    @Override
    public SocialCommandExecution<NoArguments> execute(SocialCommandContext context, NoArguments argument) {
        return SocialCommandExecution.result(perform(context, argument), languageResolver.target(context));
    }

    private NoArguments perform(SocialCommandContext context, NoArguments argument) {
        return argument;
    }

    @Override
    public SocialDocument present(SocialPresentationContext context, NoArguments result) {
        List<SocialCommandDefinition<?>> visible = new ArrayList<>(commands);

        Language language = context.language();
        List<String> items = new ArrayList<>();
        for (SocialCommandDefinition<?> command : visible) {
            command.description().ifPresent(description ->
                    command.spec().primaryAlias(language).ifPresent(_ -> items.add(
                            command.usage(context.syntax(), language, languages) + " — "
                                    + languages.t(language, description))));
        }
        return SocialDocument.of(TRUSTED,
                TITLE_ICON + " " + languages.t(language, TITLE),
                SocialDocument.Tone.INFO,
                SocialDocument.unorderedList(items.toArray(String[]::new)))
                .localized(language);
    }
}
