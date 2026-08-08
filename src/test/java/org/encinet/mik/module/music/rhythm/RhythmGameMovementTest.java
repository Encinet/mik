package org.encinet.mik.module.music.rhythm;

import org.bukkit.Location;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmGameMovementTest {

    @Test
    void cancelsCapturedMovementWithoutRewritingItIntoAPluginTeleport() {
        Location anchor = new Location(null, 8.0, 64.0, -3.0, 15.0F, 4.0F);
        Location destination = new Location(null, 8.12, 64.0, -3.0, 16.0F, 5.0F);
        PlayerMoveEvent event = new PlayerMoveEvent(player(), anchor.clone(), destination);

        assertTrue(RhythmGameService.capturePositionChange(event, anchor));
        assertTrue(event.isCancelled());
        assertSame(destination, event.getTo());
    }

    @Test
    void permitsLookingAroundWhileThePlayerRemainsAtTheAnchor() {
        Location anchor = new Location(null, 8.0, 64.0, -3.0, 15.0F, 4.0F);
        Location rotated = new Location(null, 8.0, 64.0, -3.0, 90.0F, -12.0F);
        PlayerMoveEvent event = new PlayerMoveEvent(player(), anchor.clone(), rotated);

        assertFalse(RhythmGameService.capturePositionChange(event, anchor));
        assertFalse(event.isCancelled());
        assertSame(rotated, event.getTo());
    }

    @Test
    void crouchExitsOnlyOnThePressEdge() {
        RhythmGameService.InputState released = RhythmGameService.InputState.of(
                input(false, false));
        RhythmGameService.InputState crouched = RhythmGameService.InputState.of(
                input(false, true));

        assertTrue(released.exitPressed(crouched));
        assertFalse(crouched.exitPressed(crouched));
        assertFalse(crouched.exitPressed(released));
    }

    @Test
    void movementInputDoesNotTriggerTheCrouchExit() {
        RhythmGameService.InputState released = RhythmGameService.InputState.of(
                input(false, false));
        RhythmGameService.InputState moving = RhythmGameService.InputState.of(
                input(true, false));

        assertFalse(released.exitPressed(moving));
    }

    @Test
    void hotbarSlotsOneThroughFourMapToTheFourLanes() {
        assertEquals(java.util.Optional.of(RhythmInput.ONE),
                RhythmInput.fromHotbarSlot(0));
        assertEquals(java.util.Optional.of(RhythmInput.TWO),
                RhythmInput.fromHotbarSlot(1));
        assertEquals(java.util.Optional.of(RhythmInput.THREE),
                RhythmInput.fromHotbarSlot(2));
        assertEquals(java.util.Optional.of(RhythmInput.FOUR),
                RhythmInput.fromHotbarSlot(3));
        assertTrue(RhythmInput.fromHotbarSlot(4).isEmpty());
        assertTrue(RhythmInput.fromHotbarSlot(8).isEmpty());
    }

    @Test
    void notesFallVerticallyThroughAFixedLaneAndCrossTheHitLine() {
        org.encinet.mik.module.menu.FloatingMenuPoint target =
                new org.encinet.mik.module.menu.FloatingMenuPoint(0.29, -0.42, 0.30);

        var spawn = RhythmGameService.fallingPoint(target, 0.0);
        var hit = RhythmGameService.fallingPoint(target, 1.0);
        var late = RhythmGameService.fallingPoint(target, 1.1);

        assertEquals(target.right(), spawn.right(), 1.0E-9);
        assertEquals(target.forward(), spawn.forward(), 1.0E-9);
        assertTrue(spawn.up() > hit.up());
        assertEquals(target.up(), hit.up(), 1.0E-9);
        assertTrue(late.up() < hit.up());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "rhythm-test-player";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Input input(boolean forward, boolean sneak) {
        return new Input() {
            @Override
            public boolean isForward() {
                return forward;
            }

            @Override
            public boolean isBackward() {
                return false;
            }

            @Override
            public boolean isLeft() {
                return false;
            }

            @Override
            public boolean isRight() {
                return false;
            }

            @Override
            public boolean isJump() {
                return false;
            }

            @Override
            public boolean isSneak() {
                return sneak;
            }

            @Override
            public boolean isSprint() {
                return false;
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }
}
