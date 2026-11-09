package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuDisplayObstaclesTest {
    @Test void transformedTextBeyondItsEntityOriginStillBlocksTheReadingSurface() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var display = text(eye.clone().add(40, 0, 1), Component.text("A long hologram"), 200);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(-40, 0, 0), new Vector3f(1)));
        fixture.entity(display);
        Player viewer = mock(Player.class);
        when(viewer.canSee(display)).thenReturn(true);
        var snapshot = MenuEntityObstacles.capture(viewer, eye, 5);
        var space = new FloatingMenuWorldSpace(eye, snapshot);
        assertEquals(1, snapshot.size());
        assertTrue(space.limitSurface(eye, eye.clone().add(0, 0, 2).toVector(), new Vector(1, 0, 0),
                new Vector(0, 1, 0), 1, 1, .1) < .5);
    }

    @Test void textWrappingAndMultilineHeightArePartOfTheActualModelEnvelope() {
        var eye = new Location(null, 0, 0, 0);
        var display = text(eye.clone().add(0, 0, 1), Component.text("one two three four five"), 200);
        var wide = MenuDisplayObstacles.box(eye, display);
        when(display.getLineWidth()).thenReturn(30);
        var wrapped = MenuDisplayObstacles.box(eye, display);
        assertNotNull(wide);
        assertNotNull(wrapped);
        assertTrue(wrapped.envelope().getHeight() > wide.envelope().getHeight());
        assertTrue(wrapped.envelope().getWidthX() < wide.envelope().getWidthX());
    }

    @Test void textAndBackgroundOpacityAreIndependentAndSignedBytesRemainVisible() {
        var eye = new Location(null, 0, 0, 0);
        var display = text(eye.clone().add(0, 0, 1), Component.text("hologram"), 200);
        when(display.getTextOpacity()).thenReturn((byte) 0);
        assertNull(MenuDisplayObstacles.box(eye, display));
        when(display.getBackgroundColor()).thenReturn(Color.fromARGB(160, 255, 255, 255));
        assertNotNull(MenuDisplayObstacles.box(eye, display));
        when(display.getBackgroundColor()).thenReturn(Color.fromARGB(0, 0, 0, 0));
        when(display.getTextOpacity()).thenReturn((byte) 128);
        assertNotNull(MenuDisplayObstacles.box(eye, display));
        when(display.getTextOpacity()).thenReturn((byte) 0);
        when(display.isDefaultBackground()).thenReturn(true);
        assertNotNull(MenuDisplayObstacles.box(eye, display));
    }

    @Test void emptyAndWhitespaceOnlyHologramsNeedAVisibleBackgroundToOcclude() {
        var eye = new Location(null, 0, 0, 0);
        var display = text(eye.clone().add(0, 0, 1), Component.empty(), 200);
        assertNull(MenuDisplayObstacles.box(eye, display));
        when(display.text()).thenReturn(Component.text(" ").append(Component.text("\n   ")));
        assertNull(MenuDisplayObstacles.box(eye, display));
        when(display.isDefaultBackground()).thenReturn(true);
        assertNotNull(MenuDisplayObstacles.box(eye, display));
    }

    @Test void billboardRotationPrecedesTheLocalTranslationWithoutAnEyeContainingSphere() {
        var eye = new Location(null, 0, 0, 0);
        BlockDisplay display = mock(BlockDisplay.class);
        when(display.getLocation()).thenReturn(eye.clone().add(0, 0, 81));
        when(display.getBillboard()).thenReturn(Display.Billboard.CENTER);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(0, 0, 80), new Vector3f(.2f)));
        var box = MenuDisplayObstacles.box(eye, display, 3);
        assertNotNull(box);
        assertFalse(box.containsEye());
        assertEquals(.8, box.envelope().getMinZ(), 1e-6);
        eye.setYaw(90);
        assertNull(MenuDisplayObstacles.box(eye, display, 3));
    }

    @Test void aFlattenedButVisibleFaceStillOccludesWhileZeroSizeAndNonfiniteModelsDoNot() {
        var eye = new Location(null, 0, 0, 0);
        var display = text(eye.clone().add(0, 0, 1), Component.text("panel"), 200);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(), new Vector3f(1, 1, 0)));
        var box = MenuDisplayObstacles.box(eye, display);
        assertNotNull(box);
        assertTrue(MenuOcclusionGeometry.firstHit(new Vector(), new Vector(0, 0, 1), 2, List.of(box)) < 1.01);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(), new Vector3f(0)));
        assertNull(MenuDisplayObstacles.box(eye, display));
        when(display.getTransformation()).thenReturn(transform(new Vector3f(Float.NaN, 0, 0), new Vector3f(1)));
        assertNull(MenuDisplayObstacles.box(eye, display));
    }

    @Test void billboardsUseTheCurrentCameraRatherThanThePreservedReadingYaw() {
        var fixture = new FloatingMenuTestWorld();
        var reference = new Location(fixture.world(), 0, 64, 0, 90, 0);
        var camera = new Location(fixture.world(), 0, 64, 0);
        var display = text(reference.clone().add(0, 0, 81), Component.text("billboard"), 200);
        when(display.getBillboard()).thenReturn(Display.Billboard.CENTER);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(0, 0, 80), new Vector3f(1)));
        Player viewer = mock(Player.class);
        when(viewer.canSee(display)).thenReturn(true);
        when(viewer.getEyeLocation()).thenAnswer(_ -> camera.clone());
        fixture.entity(display);
        assertFalse(MenuEntityObstacles.capture(viewer, reference, 3).isEmpty());
        camera.setYaw(90);
        assertTrue(MenuEntityObstacles.capture(viewer, reference, 3).isEmpty());
    }

    private static TextDisplay text(Location location, Component content, int width) {
        TextDisplay display = mock(TextDisplay.class);
        when(display.isValid()).thenReturn(true);
        when(display.getLocation()).thenReturn(location);
        when(display.getBillboard()).thenReturn(Display.Billboard.FIXED);
        when(display.getTransformation()).thenReturn(transform(new Vector3f(), new Vector3f(1)));
        when(display.text()).thenReturn(content);
        when(display.getLineWidth()).thenReturn(width);
        when(display.getTextOpacity()).thenReturn((byte) -1);
        return display;
    }

    private static Transformation transform(Vector3f translation, Vector3f scale) {
        return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
    }
}
