package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;

import java.net.URI;

/** The single visible-label and style policy for semantic chat links. */
final class ChatLinkPresentation {
    private ChatLinkPresentation() {
    }

    static ChatNode.Link link(
            String label,
            URI target,
            ChatLinkPalette palette
    ) {
        return new ChatNode.Link(displayLabel(label), target, palette.style());
    }

    static ChatNode.Link urlLink(
            ChatUrlSupport.CanonicalHttpLink link,
            ChatLinkPalette palette
    ) {
        return link(link.displayText(), link.target(), palette);
    }

    static ChatNode.Link serviceLink(
            String service,
            String detail,
            URI target,
            ChatLinkPalette palette
    ) {
        String label = detail == null || detail.isBlank()
                ? service : service + ": " + detail;
        return link(label, target, palette);
    }

    private static String displayLabel(String label) {
        String visible = java.util.Objects.requireNonNull(label, "label").strip();
        if (visible.isEmpty()) {
            throw new IllegalArgumentException("chat link label is empty");
        }
        return "[" + visible + "]";
    }
}
