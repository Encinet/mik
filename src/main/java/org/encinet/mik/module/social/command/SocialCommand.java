package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Optional;

/** A complete shared command from input decoding through semantic presentation. */
public interface SocialCommand<A, R> {
    SocialCommandSpec spec();

    /** Localized through {@link Message}; empty commands are internal and hidden from help. */
    default Optional<Message> description() {
        return Optional.empty();
    }

    A decode(SocialCommandInput input);

    R handle(SocialCommandContext context, A argument);

    SocialDocument present(SocialCommandContext context, R result);
}
