package org.encinet.mik.feature.community.social.command.builtin;

import java.util.Optional;

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

import java.util.Objects;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;

/** Handles unknown explicitly prefixed commands. */
public final class UnknownSocialCommand implements SocialCommand<NoArguments, NoArguments> {
    private static final SocialCommandSpec SPEC =
            SocialCommandSpec.unknownExplicit("server.unknown");
    public static final SocialCommandDefinition<NoArguments> DEFINITION =
            new SocialCommandDefinition<>(SPEC, Optional.empty(), SocialArguments.ignored());

    @Override
    public SocialCommandDefinition<NoArguments> definition() { return DEFINITION; }


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
    public SocialCommandExecution<NoArguments> execute(SocialCommandContext context, NoArguments argument) {
        return SocialCommandExecution.result(perform(context, argument), languageResolver.target(context));
    }

    private NoArguments perform(SocialCommandContext context, NoArguments argument) {
        return argument;
    }

    @Override
    public SocialDocument present(SocialPresentationContext context, NoArguments result) {
        Language language = context.language();
        return SocialDocument.message(TRUSTED, SocialDocument.Tone.WARNING,
                SocialDocument.paragraph(languages.t(
                        language, Message.SOCIAL_QUERY_UNKNOWN, SocialHelpCommand.invocation(context.syntax(), language))))
                .localized(language);
    }
}
