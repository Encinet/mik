package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAtmosphereTest {
    @Test
    void clockInputUsesMinecraftSunriseAsSixInTheMorning() {
        for (String clock : new String[] {"00:00", "05:59", "06:00", "12:00", "18:00", "23:59"}) {
            int ticks = PlotAtmosphere.parseClock(clock);
            assertEquals(clock, PlotAtmosphere.clock(ticks));
        }
        assertEquals(0, PlotAtmosphere.parseClock("06:00"));
        assertEquals(6_000, PlotAtmosphere.parseClock("12:00"));
        assertEquals(12_000, PlotAtmosphere.parseClock("18:00"));
        assertEquals(18_000, PlotAtmosphere.parseClock("00:00"));
    }

    @Test
    void malformedTimeCannotBecomeAPlotSetting() {
        for (String invalid : new String[] {"6:00", "24:00", "12:60", "noon", ""}) {
            assertEquals(Message.PLOT_ERROR_TIME_FORMAT,
                    assertThrows(PlotProblem.class,
                            () -> PlotAtmosphere.parseClock(invalid)).message());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PlotAtmosphere(24_000, null));
        assertTrue(PlotAtmosphere.WORLD.followsWorld());
    }
}
