package org.encinet.mik.feature.player.client;

import java.util.Optional;
import java.util.regex.Pattern;

/** Positive network signals only; a silent client does not prove a mod is absent. */
enum RecommendedClientMod {
    AXIOM("Axiom", "https://modrinth.com/mod/axiom"),
    PLASMO_VOICE("Plasmo Voice", "https://modrinth.com/mod/plasmo-voice"),
    XAERO_MINIMAP("Xaero's Minimap", "https://modrinth.com/mod/xaeros-minimap"),
    XAERO_WORLD_MAP("Xaero's World Map", "https://modrinth.com/mod/xaeros-world-map");

    private static final Pattern CHANNEL_NAME = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final String displayName;
    private final String downloadUrl;

    RecommendedClientMod(String displayName, String downloadUrl) {
        this.displayName = displayName;
        this.downloadUrl = downloadUrl;
    }

    String displayName() {
        return displayName;
    }

    String downloadUrl() {
        return downloadUrl;
    }

    static Optional<RecommendedClientMod> fromChannel(String channel) {
        if (channel.length() > 256 || !CHANNEL_NAME.matcher(channel).matches()) return Optional.empty();
        int colon = channel.indexOf(':');
        if (colon <= 0 || colon == channel.length() - 1) return Optional.empty();
        return switch (channel.substring(0, colon)) {
            case "axiom" -> Optional.of(AXIOM);
            case "xaerominimap" -> Optional.of(XAERO_MINIMAP);
            case "xaeroworldmap" -> Optional.of(XAERO_WORLD_MAP);
            case "plasmo" -> channel.equals("plasmo:voice") || channel.startsWith("plasmo:voice/")
                    ? Optional.of(PLASMO_VOICE) : Optional.empty();
            default -> Optional.empty();
        };
    }
}
