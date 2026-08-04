package org.encinet.mik.module.music.rhythm;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

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

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "rhythm-test-player";
                    default -> defaultValue(method.getReturnType());
                });
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
