package org.encinet.mik.shared.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuScenePolicyTest {

    @Test void applicationScreensKeepTheirReadingFrameByDefault() {
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION, MenuScene.screen("plain").build().anchorMode());
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION,
                MenuScene.screen("titled", Component.text("Title")).build().anchorMode());
    }

    @Test
    void timeCriticalSceneCanUseAStableAnchor() {
        MenuScene definition = MenuScene.builder()
                .stableAnchor()
                .build();

        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION,
                definition.anchorMode());
    }

    @Test
    void ordinaryScenesFollowThePlayersPoseByDefault() {
        MenuScene definition = MenuScene.builder().build();

        assertEquals(FloatingMenuViewpoint.POSE_AWARE, definition.viewpoint());
        assertEquals(FloatingMenuSpatialFrame.IN_FRONT, definition.spatialFrame());
        assertEquals(1.27, definition.viewpoint().eyeHeight(1.27, 1.62));
        assertEquals(1.62,
                FloatingMenuViewpoint.STANDING.eyeHeight(1.27, 1.62));
    }

    @Test
    void standingViewpointIsIndependentOfAnchorStability() {
        MenuScene definition = MenuScene.builder()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .build();

        assertEquals(FloatingMenuViewpoint.STANDING, definition.viewpoint());
        assertEquals(FloatingMenuAnchorMode.ADAPTIVE, definition.anchorMode());
    }

    @Test
    void surroundingSceneUsesAnIndependentSpatialFrame() {
        MenuScene definition = MenuScene.builder()
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
