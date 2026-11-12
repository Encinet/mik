package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.shared.i18n.Message;

/** Only fixed, non-identifying receipts can bypass a failed detailed presentation. */
public enum SocialCompletionReceipt {
    BINDING_LINKED(Message.SOCIAL_BINDING_LINK_COMPLETED),
    BINDING_UNLINKED(Message.SOCIAL_BINDING_UNLINK_COMPLETED);

    private final Message message;

    SocialCompletionReceipt(Message message) { this.message = message; }

    public SocialDocument document(SocialPresentationContext context) {
        return SocialDocument.message(SocialDocument.Moderation.TRUSTED, SocialDocument.Tone.SUCCESS,
                SocialDocument.paragraph(context.t(message))).localized(context.language());
    }
}
