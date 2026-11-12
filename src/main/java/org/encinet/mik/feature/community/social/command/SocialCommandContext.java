package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;

import java.util.Objects;

/** Trusted platform and inbound-message context supplied to shared commands. */
public record SocialCommandContext(
        SocialPlatformDescriptor platform,
        SocialInboundMessage message,
        SocialCommandInput input,
        SocialCommandSyntax syntax,
        java.util.UUID generationId
) {
    public SocialCommandContext {
        platform = Objects.requireNonNull(platform, "platform");
        message = Objects.requireNonNull(message, "message");
        input = Objects.requireNonNull(input, "input");
        syntax = Objects.requireNonNull(syntax, "syntax");
        generationId = Objects.requireNonNull(generationId, "generationId");
    }

    public SocialCommandContext(SocialPlatformDescriptor platform, SocialInboundMessage message,
                                SocialCommandInput input, SocialCommandSyntax syntax) {
        this(platform, message, input, syntax, new java.util.UUID(0, 0));
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
