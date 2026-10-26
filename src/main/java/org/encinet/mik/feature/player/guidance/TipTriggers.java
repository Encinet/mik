package org.encinet.mik.feature.player.guidance;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.shared.util.LocationUtil;

import java.util.Objects;
import java.util.regex.Pattern;

/** Shared event recipes with semantics needed by more than one feature. */
public final class TipTriggers {
    private static final Pattern CHAT_LINK = Pattern.compile("(?:https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);

    private TipTriggers() {}

    /** Match feature-owned keywords in visible chat text, excluding links, on the chat event's thread. */
    public static TipTrigger<AsyncChatEvent> chatMentions(Pattern keywords) {
        Objects.requireNonNull(keywords, "keywords");
        return TipTrigger.event(AsyncChatEvent.class).adaptiveChance().filter(event -> {
            String text = PlainTextComponentSerializer.plainText().serialize(event.message());
            return keywords.matcher(CHAT_LINK.matcher(text).replaceAll(" ")).find();
        });
    }

    public static TipTrigger<PlayerTeleportEvent> teleport(double chance) {
        return TipTrigger.event(PlayerTeleportEvent.class)
                .filter(TipTriggers::changesBlock)
                .verify(event -> changesBlock(event)
                        && LocationUtil.sameBlock(event.getPlayer().getLocation(), event.getTo()))
                .chance(chance);
    }

    private static boolean changesBlock(PlayerTeleportEvent event) {
        return event.getTo() != null && !LocationUtil.sameBlock(event.getFrom(), event.getTo());
    }
}
