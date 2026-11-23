package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.protocol.IdentityCode;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

import java.util.List;

final class IdentityBindingGuidance {
    private static final String JOIN_URL = "https://mcmik.top/join";

    private IdentityBindingGuidance() { }

    static String platformName(String platform) {
        return switch (platform) {
            case "qq" -> "QQ";
            case "matrix" -> "Matrix";
            default -> platform;
        };
    }

    static Component challenge(LanguageService languages, Language language, List<IdentityCode> codes) {
        var prompt = Component.text().append(
                community(languages, language), Component.newline(),
                Component.text(languages.t(language, Message.IDENTITY_ADMISSION_INSTRUCTIONS)));
        for (var code : codes) {
            prompt.append(Component.newline(), instruction(languages, language, Message.IDENTITY_ADMISSION_BIND_RICH, code));
        }
        long expiry = codes.stream().mapToLong(IdentityCode::getExpiresAtMs).min().orElseThrow();
        return prompt.append(Component.newline(), expiry(languages, language, expiry), Component.newline(),
                Component.text(languages.t(language, Message.IDENTITY_ADMISSION_RECONNECT))).build();
    }

    static Component community(LanguageService languages, Language language) {
        Component link = Component.text(JOIN_URL, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(JOIN_URL));
        return languages.rich(language, Message.IDENTITY_COMMUNITY_JOIN_RICH, NamedTextColor.WHITE,
                RichArg.component("link", link));
    }

    static Component instruction(LanguageService languages, Language language, Message message, IdentityCode issue) {
        String prefix = issue.getCommandPrefix().isEmpty() ? "/" : issue.getCommandPrefix();
        String commandText = prefix + "bind " + issue.getCode();
        Component command = Component.text(commandText, NamedTextColor.GOLD)
                .clickEvent(ClickEvent.copyToClipboard(commandText))
                .hoverEvent(HoverEvent.showText(Component.text(languages.t(language, Message.IDENTITY_CODE_COPY))));
        return languages.rich(language, message, NamedTextColor.WHITE,
                RichArg.component("platform", Component.text(platformName(issue.getPlatform()), NamedTextColor.AQUA)),
                RichArg.component("command", command));
    }

    static Component expiry(LanguageService languages, Language language, long expiresAt) {
        long minutes = Math.max(0, Math.ceilDiv(expiresAt - System.currentTimeMillis(), 60_000));
        return Component.text(languages.t(language, Message.IDENTITY_CODE_EXPIRY, minutes), NamedTextColor.WHITE);
    }
}
