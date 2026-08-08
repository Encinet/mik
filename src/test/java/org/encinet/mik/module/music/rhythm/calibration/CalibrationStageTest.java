package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationStageTest {

    @Test
    void completeTestHasOneExplicitLegalHappyPath() {
        List<CalibrationStage> path = List.of(
                CalibrationStage.INTRO,
                CalibrationStage.MINECRAFT_LISTEN,
                CalibrationStage.MINECRAFT_AUDIO,
                CalibrationStage.TRANSITION_TO_PLASMO,
                CalibrationStage.PLASMO_LISTEN,
                CalibrationStage.PLASMO_AUDIO,
                CalibrationStage.TRANSITION_TO_VISUAL,
                CalibrationStage.VISUAL_LISTEN,
                CalibrationStage.VISUAL,
                CalibrationStage.TRANSITION_TO_POINTER,
                CalibrationStage.POINTER_LISTEN,
                CalibrationStage.POINTER_VISUAL,
                CalibrationStage.RESULT);

        CalibrationStage current = path.getFirst();
        for (CalibrationStage next : path.subList(1, path.size())) {
            current = current.transitionTo(next);
        }

        assertEquals(CalibrationStage.RESULT, current);
        assertThrows(IllegalStateException.class,
                () -> CalibrationStage.RESULT.transitionTo(
                        CalibrationStage.INTRO));
    }

    @Test
    void previewStagesNeverAcceptEvidenceAndInputModalitiesCannotLeak() {
        for (CalibrationStage stage : CalibrationStage.values()) {
            assertEquals(stage.samplesInput(),
                    stage.acceptsInput(false) || stage.acceptsInput(true),
                    stage.name());
        }

        assertTrue(CalibrationStage.MINECRAFT_AUDIO.acceptsInput(false));
        assertTrue(CalibrationStage.PLASMO_AUDIO.acceptsInput(false));
        assertTrue(CalibrationStage.VISUAL.acceptsInput(false));
        assertFalse(CalibrationStage.VISUAL.acceptsInput(true));
        assertTrue(CalibrationStage.POINTER_VISUAL.acceptsInput(true));
        assertFalse(CalibrationStage.POINTER_VISUAL.acceptsInput(false));
        assertFalse(CalibrationStage.VISUAL_LISTEN.samplesInput());
        assertFalse(CalibrationStage.POINTER_LISTEN.samplesInput());
    }

    @Test
    void plasmoMayOnlyRecoverBackToItsOwnListenStage() {
        assertEquals(CalibrationStage.PLASMO_LISTEN,
                CalibrationStage.PLASMO_AUDIO.transitionTo(
                        CalibrationStage.PLASMO_LISTEN));
        assertThrows(IllegalStateException.class,
                () -> CalibrationStage.VISUAL.transitionTo(
                        CalibrationStage.PLASMO_LISTEN));
        assertTrue(CalibrationStage.TRANSITION_TO_PLASMO.requiresPlasmoVoice());
        assertTrue(CalibrationStage.PLASMO_LISTEN.requiresPlasmoVoice());
        assertTrue(CalibrationStage.PLASMO_AUDIO.requiresPlasmoVoice());
        assertFalse(CalibrationStage.MINECRAFT_AUDIO.requiresPlasmoVoice());
    }

    @Test
    void silentSceneIncludesBothPreviewAndSamplingPhases() {
        assertTrue(CalibrationStage.TRANSITION_TO_VISUAL.presentsVisualCues());
        assertTrue(CalibrationStage.VISUAL_LISTEN.presentsVisualCues());
        assertTrue(CalibrationStage.VISUAL.presentsVisualCues());
        assertTrue(CalibrationStage.TRANSITION_TO_POINTER.presentsVisualCues());
        assertTrue(CalibrationStage.POINTER_LISTEN.presentsVisualCues());
        assertTrue(CalibrationStage.POINTER_VISUAL.presentsVisualCues());
        assertFalse(CalibrationStage.PLASMO_AUDIO.presentsVisualCues());
    }
}
