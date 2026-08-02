package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloatingMenuPresentationTest {

    @Test
    void timeCriticalSceneCanRequireSpatialPresentationAndStableAnchor() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .requireSpatialPresentation()
                .stableAnchor()
                .build();

        assertEquals(FloatingMenuPresentation.SPATIAL_REQUIRED,
                definition.presentation());
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION,
                definition.anchorMode());
    }

    @Test
    void decorationCanOptIntoExactTracking() {
        FloatingMenuDecoration decoration = FloatingMenuDecoration.text(
                "moving", FloatingMenuPoint.ORIGIN, Component.text("cue")).tracking();

        assertEquals(FloatingMenuDecoration.Transition.TRACKING,
                decoration.transition());
    }
}
