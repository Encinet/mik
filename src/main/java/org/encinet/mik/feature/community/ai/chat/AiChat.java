package org.encinet.mik.feature.community.ai.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.protocol.AssistantFailure;
import org.encinet.mik.protocol.AssistantReply;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.function.Function;

/** Renders a daemon result for Java or basic chat; the turn owner supplies any follow-up grants. */
public final class AiChat {
    private final LanguageService languages;

    public AiChat(LanguageService languages) {
        this.languages = languages;
    }

    public Component failure(Player player, AssistantFailure failure) {
        var language = languages.language(player);
        if (failure.hasMaxPromptCodePoints()) {
            return languages.text(language, Message.AI_PROMPT_TOO_LONG,
                    NamedTextColor.RED, failure.getMaxPromptCodePoints());
        }
        return switch (failure.getReason()) {
            case BUSY -> languages.text(language, Message.AI_BUSY, NamedTextColor.YELLOW);
            case DISABLED -> languages.text(language, Message.AI_DISABLED, NamedTextColor.RED);
            default -> languages.text(language, Message.AI_REQUEST_FAILED, NamedTextColor.RED);
        };
    }

    public record RenderedReply(Component component, boolean hasCallbacks) {}

    public RenderedReply reply(Player player, AssistantReply result, boolean nativeInteraction,
                               Function<String, ClickEvent<?>> followUpClick) {
        var language = languages.language(player);
        var reply = Component.text().color(NamedTextColor.WHITE)
                .append(languages.text(language, Message.AI_ANSWER, NamedTextColor.AQUA));
        var document = new AiDocument(nativeInteraction, followUpClick);
        var content = document.blocks(result.getBlocksList(), "", "");
        if (content == null) return null;
        reply.append(Component.newline()).append(content);
        return new RenderedReply(reply.build(), document.hasCallbacks());
    }
}
