package org.encinet.mik.feature.community.chat.input;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.util.List;

/** Imports explicitly authorized styled input. Raw user text never calls this adapter implicitly. */
public final class AuthorizedChatMarkup {
    private static final MiniMessage FORMAT = MiniMessage.builder().tags(TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
            StandardTags.rainbow(), StandardTags.reset())).build();
    private final AdventureComponentImporter components = new AdventureComponentImporter();

    public List<ChatNode> parse(String source) {
        try {
            return components.importComponent(FORMAT.deserialize(source)).nodes();
        } catch (RuntimeException ignored) {
            return List.of(new ChatNode.Text(source, ChatStyle.EMPTY));
        }
    }
}
