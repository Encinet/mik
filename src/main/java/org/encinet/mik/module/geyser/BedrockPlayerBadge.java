package org.encinet.mik.module.geyser;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Objects;

/** Shared visual identity for Bedrock players across every player-name surface. */
public final class BedrockPlayerBadge {

    private final BedrockPlayerDetector detector;
    private final LanguageService languageService;

    public BedrockPlayerBadge(
            BedrockPlayerDetector detector,
            LanguageService languageService
    ) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    /** A viewer-localized prefix for chat and other viewer-specific output. */
    public Component prefix(Player subject, Audience viewer) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(viewer, "viewer");
        if (!detector.isBedrockPlayer(subject.getUniqueId())) {
            return Component.empty();
        }
        String label = viewer instanceof Player player
                ? languageService.t(player, Message.CHAT_BEDROCK_LABEL)
                : languageService.t(Language.DEFAULT, Message.CHAT_BEDROCK_LABEL);
        return render(true, label);
    }

    /**
     * A stable prefix for globally shared components such as a Bukkit player-list name.
     * Those components cannot vary by viewer locale.
     */
    public Component globalPrefix(Player subject) {
        Objects.requireNonNull(subject, "subject");
        return render(
                detector.isBedrockPlayer(subject.getUniqueId()),
                languageService.t(Language.DEFAULT, Message.CHAT_BEDROCK_LABEL));
    }

    static Component render(boolean bedrockPlayer, String label) {
        return bedrockPlayer && label != null && !label.isBlank()
                ? Component.text("[" + label + "] ", NamedTextColor.AQUA)
                : Component.empty();
    }
}
