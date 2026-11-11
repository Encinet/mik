package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

/** Shared styling and actions for Minecraft-side binding prompts. */
final class IdentityBindingGuidance {
    private static final String JOIN_URL = "https://mcmik.top/join";

    private IdentityBindingGuidance() { }

    static Component community(LanguageService languages, Language language) {
        Component link = Component.text(JOIN_URL, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(JOIN_URL));
        return languages.rich(language, Message.IDENTITY_COMMUNITY_JOIN_RICH, NamedTextColor.WHITE,
                RichArg.component("link", link));
    }

    static Component instruction(LanguageService languages, Language language, Message message,
                                 IdentityPlatform platform, IdentityLinkCode issue) {
        String template = platform.redemptionInstruction();
        Component code = Component.text(issue.code(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);
        Component command = Component.text(template, NamedTextColor.WHITE)
                .replaceText(replacement -> replacement.matchLiteral("{code}").replacement(code))
                .clickEvent(ClickEvent.copyToClipboard(template.replace("{code}", issue.code())))
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language,
                        Message.IDENTITY_CODE_COPY), NamedTextColor.WHITE)));
        return languages.rich(language, message, NamedTextColor.WHITE,
                RichArg.component("platform", Component.text(platform.displayName(), NamedTextColor.AQUA)),
                RichArg.component("command", command));
    }

    static Component expiry(LanguageService languages, Language language) {
        return Component.text(languages.t(language, Message.IDENTITY_CODE_EXPIRY,
                IdentityBindingService.CODE_TTL.toMinutes()), NamedTextColor.WHITE);
    }
}
