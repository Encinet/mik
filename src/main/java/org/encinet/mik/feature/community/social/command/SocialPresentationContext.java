package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;

/** One resolved language is shared by the main response and every recovery path. */
public record SocialPresentationContext(SocialCommandContext invocation, Language language,
                                         LanguageService languages) {
    public SocialPresentationContext {
        invocation = Objects.requireNonNull(invocation, "invocation");
        language = Objects.requireNonNull(language, "language");
        languages = Objects.requireNonNull(languages, "languages");
    }

    public SocialCommandSyntax syntax() { return invocation.syntax(); }
    public SocialPlatformDescriptor platform() { return invocation.platform(); }
    public String t(Message message, Object... arguments) { return languages.t(language, message, arguments); }
}
