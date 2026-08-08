package org.encinet.mik.module.music.rhythm.calibration;

import java.util.Objects;

/**
 * Canonical state contract for the complete four-part latency test.
 *
 * <p>Keeping input modality and legal transitions beside the states prevents
 * the renderer, input listeners and audio supervisor from quietly disagreeing
 * about whether a preview frame is already scoreable.</p>
 */
public enum CalibrationStage {
    INTRO,
    MINECRAFT_LISTEN,
    MINECRAFT_AUDIO,
    TRANSITION_TO_PLASMO,
    PLASMO_LISTEN,
    PLASMO_AUDIO,
    TRANSITION_TO_VISUAL,
    VISUAL_LISTEN,
    VISUAL,
    TRANSITION_TO_POINTER,
    POINTER_LISTEN,
    POINTER_VISUAL,
    RESULT;

    /** True only while raw timing evidence may be recorded. */
    public boolean samplesInput() {
        return this == MINECRAFT_AUDIO || this == PLASMO_AUDIO
                || this == VISUAL || this == POINTER_VISUAL;
    }

    /** Enforces keyboard-only audio/visual stages and the pointer-only baseline. */
    public boolean acceptsInput(boolean pointerInput) {
        if (pointerInput) return this == POINTER_VISUAL;
        return this == MINECRAFT_AUDIO || this == PLASMO_AUDIO
                || this == VISUAL;
    }

    /** Stages that cannot continue if the player's private voice stream is lost. */
    public boolean requiresPlasmoVoice() {
        return this == TRANSITION_TO_PLASMO || this == PLASMO_LISTEN
                || this == PLASMO_AUDIO;
    }

    /** Stages that render the silent falling-cue scene. */
    public boolean presentsVisualCues() {
        return this == TRANSITION_TO_VISUAL || this == VISUAL_LISTEN
                || this == VISUAL || this == TRANSITION_TO_POINTER
                || this == POINTER_LISTEN || this == POINTER_VISUAL;
    }

    /**
     * Validates the forward test sequence. The sole backward edge is the
     * Plasmo audio recovery path, which returns to a fresh listen cycle.
     */
    public CalibrationStage transitionTo(CalibrationStage next) {
        Objects.requireNonNull(next, "next");
        if (next == this) return this;
        boolean allowed = switch (this) {
            case INTRO -> next == MINECRAFT_LISTEN;
            case MINECRAFT_LISTEN -> next == MINECRAFT_AUDIO;
            case MINECRAFT_AUDIO -> next == TRANSITION_TO_PLASMO;
            case TRANSITION_TO_PLASMO -> next == PLASMO_LISTEN;
            case PLASMO_LISTEN -> next == PLASMO_AUDIO;
            case PLASMO_AUDIO -> next == PLASMO_LISTEN
                    || next == TRANSITION_TO_VISUAL;
            case TRANSITION_TO_VISUAL -> next == VISUAL_LISTEN;
            case VISUAL_LISTEN -> next == VISUAL;
            case VISUAL -> next == TRANSITION_TO_POINTER;
            case TRANSITION_TO_POINTER -> next == POINTER_LISTEN;
            case POINTER_LISTEN -> next == POINTER_VISUAL;
            case POINTER_VISUAL -> next == RESULT;
            case RESULT -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "illegal latency-test transition: " + this + " -> " + next);
        }
        return next;
    }
}
