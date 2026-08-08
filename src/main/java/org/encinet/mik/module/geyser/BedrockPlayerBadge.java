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

    /** A viewer-localized badge for chat and other viewer-specific output. */
    public Component prefix(Player subject, Audience viewer) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(viewer, "viewer");
        Language language = viewer instanceof Player player
                ? languageService.language(player)
                : Language.DEFAULT;
        return prefix(subject, language);
    }

    /** A badge translated for a known viewer language. */
    public Component prefix(Player subject, Language language) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(language, "language");
        return render(
                detector.isBedrockPlayer(subject.getUniqueId()),
                languageService.t(language, Message.PLAYER_BEDROCK_LABEL));
    }

    static Component render(boolean bedrockPlayer, String label) {
        return bedrockPlayer && label != null && !label.isBlank()
                ? Component.text("[" + label + "] ", NamedTextColor.AQUA)
                : Component.empty();
    }
}
