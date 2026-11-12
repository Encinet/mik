package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.LanguageService;
import java.util.Collection;

public final class SocialCommandTestSupport {
    private SocialCommandTestSupport() { }

    public static SocialPresentationContext presentation(SocialCommandContext context, LanguageService languages) {
        return new SocialPresentationContext(context, context.input().languageHint().orElse(context.syntax().defaultLanguage()), languages);
    }

    public static SocialCommandDispatcher dispatcher(Collection<? extends SocialCommand<?, ?>> commands) {
        return new SocialCommandDispatcher(commands, context -> SocialCommandExecution.LanguageTarget.fixed(
                context.input().languageHint().orElse(context.syntax().defaultLanguage())));
    }
}
