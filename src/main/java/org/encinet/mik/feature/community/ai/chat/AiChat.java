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
    private static final int BOSS_BAR_SUMMARY_LIMIT = 48;
    private final LanguageService languages;

    public AiChat(LanguageService languages) {
        this.languages = languages;
    }

    public Component thinking(Player player) {
        return Component.text("◆ ", NamedTextColor.AQUA)
                .append(languages.text(languages.language(player), Message.AI_THINKING, NamedTextColor.WHITE));
    }

    public Component reasoningSummary(String summary) {
        String label = summary.codePointCount(0, summary.length()) > BOSS_BAR_SUMMARY_LIMIT
                ? summary.substring(0, summary.offsetByCodePoints(0, BOSS_BAR_SUMMARY_LIMIT - 1))
                        .stripTrailing() + "…"
                : summary;
        return Component.text("◆ ", NamedTextColor.AQUA).append(Component.text(label, NamedTextColor.WHITE));
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

    public RenderedReply reply(AssistantReply result, boolean nativeInteraction,
                               Function<String, ClickEvent<?>> followUpClick) {
        var document = new AiDocument(nativeInteraction, followUpClick);
        var content = document.blocks(result.getBlocksList(), "", "");
        if (content == null) return null;
        var marker = switch (result.getBlocks(0).getContentCase()) {
            case PARAGRAPH, HEADING -> "◆ ";
            default -> "◆\n";
        };
        var reply = Component.text().color(NamedTextColor.WHITE)
                .append(Component.text(marker, NamedTextColor.AQUA)).append(content);
        return new RenderedReply(reply.build(), document.hasCallbacks());
    }
}
