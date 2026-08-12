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

import java.util.Objects;

/** Handles unknown explicitly prefixed commands. */
public final class UnknownSocialCommand implements SocialCommand<NoArguments, NoArguments> {
    private static final String TITLE_ICON = "❓";
    private static final Message TITLE = Message.SOCIAL_TITLE_UNKNOWN_COMMAND;
    private static final SocialCommandSpec SPEC =
            SocialCommandSpec.unknownExplicit("server.unknown");

    private final SocialCommandLanguageResolver languageResolver;
    private final LanguageService languages;

    public UnknownSocialCommand(
            SocialCommandLanguageResolver languageResolver,
            LanguageService languages
    ) {
        this.languageResolver = Objects.requireNonNull(languageResolver, "languageResolver");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    @Override
    public SocialCommandSpec spec() {
        return SPEC;
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
        Language language = languageResolver.language(context);
        return SocialDocument.of(
                TITLE_ICON + " " + languages.t(language, TITLE),
                SocialDocument.Tone.WARNING,
                SocialDocument.paragraph(languages.t(
                        language, Message.SOCIAL_QUERY_UNKNOWN)))
                .localized(language);
    }
}
