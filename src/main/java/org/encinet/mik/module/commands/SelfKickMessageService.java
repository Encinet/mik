package org.encinet.mik.module.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.util.PlayerDisplay;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Selects and renders localized self-kick templates without inventing a fake reason. */
final class SelfKickMessageService {

    private final LanguageService languageService;

    SelfKickMessageService(LanguageService languageService) {
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    Request createRequest(String reason) {
        Message pool = messagePool(reason);
        return new Request(reason, randomAttribute(pool));
    }

    Component message(Player viewer, Player kickedPlayer, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        return message(languageService.language(viewer), kickedPlayer, request);
    }

    Component message(Language language, Player player, Request request) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        RichArg playerArg = RichArg.component("player",
                PlayerDisplay.name(player, NamedTextColor.YELLOW), player.getName());
        RichArg[] arguments = request.hasReason()
                ? new RichArg[]{playerArg, reasonArg(request.reason())}
                : new RichArg[]{playerArg};
        return render(language, messagePool(request.reason()),
                request.templateAttribute(), arguments);
    }

    private Component render(Language language, Message pool, String attribute,
                             RichArg... arguments) {
        if (attribute != null) {
            var localized = languageService.richAttribute(
                    language, pool.key(), attribute, NamedTextColor.YELLOW, arguments);
            if (localized.isPresent()) return localized.get();
        }
        return languageService.rich(language, pool, NamedTextColor.YELLOW, arguments);
    }

    private String randomAttribute(Message pool) {
        List<String> attributes = languageService.attributeNames(Language.DEFAULT, pool.key());
        if (attributes.isEmpty()) return null;
        return attributes.get(ThreadLocalRandom.current().nextInt(attributes.size()));
    }

    private static RichArg reasonArg(String reason) {
        return RichArg.component("reason", Component.text(reason, NamedTextColor.WHITE), reason);
    }

    private static Message messagePool(String reason) {
        return reason == null ? Message.SELF_KICK_WITHOUT_REASON : Message.SELF_KICK_WITH_REASON;
    }

    record Request(String reason, String templateAttribute) {
        boolean hasReason() {
            return reason != null;
        }
    }
}
