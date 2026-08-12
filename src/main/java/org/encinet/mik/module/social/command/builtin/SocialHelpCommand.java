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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.encinet.mik.module.social.command.SocialCommandSpec.aliases;

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

    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;
    private final List<SocialCommand<?, ?>> commands;

    public SocialHelpCommand(
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages,
            List<? extends SocialCommand<?, ?>> commands
    ) {
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
    }

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
    }

    @Override
    public Optional<Message> description() {
        return Optional.of(Message.SOCIAL_HELP_COMMAND_DESCRIPTION);
    }

    @Override
    public NoArguments decode(SocialCommandInput input) {
        return NoArguments.decode(input);
    }

    @Override
    public NoArguments handle(SocialCommandContext context, NoArguments argument) {
        return argument;
    }

    @Override
    public SocialDocument present(SocialCommandContext context, NoArguments result) {
        List<SocialCommand<?, ?>> visible = new ArrayList<>(commands);
        visible.add(this);
        Language language = languageResolver.language(context);
        List<String> items = new ArrayList<>();
        for (SocialCommand<?, ?> command : visible) {
            command.description().ifPresent(description ->
                    command.spec().primaryAlias(language).ifPresent(alias -> items.add(
                            context.syntax().displayPrefix() + alias + " — "
                                    + languages.t(language, description))));
        }
        return SocialDocument.of(
                TITLE_ICON + " " + languages.t(language, TITLE),
                SocialDocument.Tone.INFO,
                SocialDocument.unorderedList(items.toArray(String[]::new)))
                .localized(language);
    }
}
