package org.encinet.mik.module.player;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class FlightSpeed {

    static final float MIN_USER_SPEED = 0.1f;
    static final float MAX_USER_SPEED = 10.0f;
    static final float DEFAULT_USER_SPEED = 1.0f;

    private static final float BUKKIT_SCALE = 0.1f;

    private FlightSpeed() {
    }

    static float toBukkit(float userSpeed) {
        return roundUserSpeed(userSpeed) * BUKKIT_SCALE;
    }

    static float fromBukkit(float bukkitSpeed) {
        return roundUserSpeed(bukkitSpeed / BUKKIT_SCALE);
    }

    static String format(float userSpeed) {
        return new BigDecimal(Float.toString(roundUserSpeed(userSpeed)))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static float roundUserSpeed(float userSpeed) {
        return BigDecimal.valueOf(userSpeed)
                .setScale(2, RoundingMode.HALF_UP)
                .floatValue();
    }
}
