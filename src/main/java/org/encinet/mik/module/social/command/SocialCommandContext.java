package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;

import java.util.Objects;

/** Trusted platform and inbound-message context supplied to shared commands. */
public record SocialCommandContext(
        SocialPlatformDescriptor platform,
        SocialInboundMessage message,
        SocialCommandInput input,
        SocialCommandSyntax syntax
) {
    public SocialCommandContext {
        platform = Objects.requireNonNull(platform, "platform");
        message = Objects.requireNonNull(message, "message");
        input = Objects.requireNonNull(input, "input");
        syntax = Objects.requireNonNull(syntax, "syntax");
    }

    public SocialCommandContext(
            SocialPlatformDescriptor platform,
            SocialInboundMessage message,
            SocialCommandInput input
    ) {
        this(platform, message, input,
                SocialCommandSyntax.caseInsensitive(Language.DEFAULT, "/"));
    }

    public SocialCommandContext(
            SocialPlatformDescriptor platform,
            SocialInboundMessage message
    ) {
        this(platform, message,
                SocialCommandInput.nativeCommand("context", "", java.util.Map.of()),
                SocialCommandSyntax.caseInsensitive(Language.DEFAULT, "/"));
    }
}
