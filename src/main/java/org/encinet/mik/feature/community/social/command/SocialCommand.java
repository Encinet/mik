package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.feature.community.social.document.SocialDocument;

/** A complete shared command from input decoding through semantic presentation. */
public interface SocialCommand<A, R> {
    SocialCommandDefinition<A> definition();

    SocialCommandExecution<R> execute(SocialCommandContext context, A argument);

    SocialDocument present(SocialPresentationContext context, R result);
}
