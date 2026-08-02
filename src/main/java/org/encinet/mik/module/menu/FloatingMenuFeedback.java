package org.encinet.mik.module.menu;

/** Centralized, rate-limited feedback policy for all menu screens. */
public record FloatingMenuFeedback(
        float hoverVolume,
        float hoverPitch,
        float primaryPitch,
        float secondaryPitch,
        float hotkeyPitch,
        float scrollUpPitch,
        float scrollDownPitch
) {
    public static final FloatingMenuFeedback DEFAULT =
            new FloatingMenuFeedback(0.12F, 1.75F, 1.15F, 0.85F, 0.65F, 1.45F, 1.05F);

    public FloatingMenuFeedback {
        if (!Float.isFinite(hoverVolume) || hoverVolume < 0 || hoverVolume > 1) {
            throw new IllegalArgumentException("Invalid hover volume");
        }
        if (!validPitch(hoverPitch) || !validPitch(primaryPitch)
                || !validPitch(secondaryPitch) || !validPitch(hotkeyPitch)
                || !validPitch(scrollUpPitch) || !validPitch(scrollDownPitch)) {
            throw new IllegalArgumentException("Feedback pitches must be finite and in (0, 2]");
        }
    }

    public float pitch(FloatingMenuInteraction interaction) {
        return switch (interaction) {
            case PRIMARY -> primaryPitch;
            case SECONDARY -> secondaryPitch;
            case HOTKEY -> hotkeyPitch;
            case SCROLL_UP -> scrollUpPitch;
            case SCROLL_DOWN -> scrollDownPitch;
        };
    }

    private static boolean validPitch(float pitch) {
        return Float.isFinite(pitch) && pitch > 0.0F && pitch <= 2.0F;
    }
}
