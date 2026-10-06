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
    void ordinaryScenesFollowThePlayersPoseByDefault() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder().build();

        assertEquals(FloatingMenuViewpoint.POSE_AWARE, definition.viewpoint());
        assertEquals(FloatingMenuSpatialFrame.IN_FRONT, definition.spatialFrame());
        assertEquals(1.27, definition.viewpoint().eyeHeight(1.27, 1.62));
        assertEquals(1.62,
                FloatingMenuViewpoint.STANDING.eyeHeight(1.27, 1.62));
    }

    @Test
    void standingViewpointIsIndependentOfAnchorStability() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .build();

        assertEquals(FloatingMenuViewpoint.STANDING, definition.viewpoint());
        assertEquals(FloatingMenuAnchorMode.ADAPTIVE, definition.anchorMode());
    }

    @Test
    void surroundingSceneUsesAnIndependentSpatialFrame() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .aroundViewer()
                .stableAnchor()
                .build();

        assertEquals(FloatingMenuSpatialFrame.AROUND_VIEWER, definition.spatialFrame());
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION, definition.anchorMode());
    }

    @Test
    void decorationCanOptIntoExactTracking() {
        FloatingMenuDecoration decoration = FloatingMenuDecoration.text(
                "moving", FloatingMenuPoint.ORIGIN, Component.text("cue")).tracking();

        assertEquals(FloatingMenuDecoration.Transition.TRACKING,
                decoration.transition());
    }
}
