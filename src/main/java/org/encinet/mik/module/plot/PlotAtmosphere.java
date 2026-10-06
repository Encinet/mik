package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.util.Locale;

/** Client-only time and weather while a player is inside a plot. Null follows the world. */
record PlotAtmosphere(Integer timeTicks, Weather weather) {
    static final PlotAtmosphere WORLD = new PlotAtmosphere(null, null);

    enum Weather { CLEAR, RAIN }

    PlotAtmosphere {
        if (timeTicks != null && (timeTicks < 0 || timeTicks >= 24_000))
            throw new IllegalArgumentException("Plot time must be within one Minecraft day");
    }

    boolean followsWorld() {
        return timeTicks == null && weather == null;
    }

    PlotAtmosphere withTime(Integer ticks) {
        return new PlotAtmosphere(ticks, weather);
    }

    PlotAtmosphere withWeather(Weather next) {
        return new PlotAtmosphere(timeTicks, next);
    }

    static int parseClock(String input) {
        if (input == null || !input.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))
            throw new PlotProblem(Message.PLOT_ERROR_TIME_FORMAT);
        int hour = Integer.parseInt(input.substring(0, 2));
        int minute = Integer.parseInt(input.substring(3, 5));
        int sinceSunrise = Math.floorMod(hour * 60 + minute - 6 * 60, 24 * 60);
        return (int) Math.round(sinceSunrise * (1000.0 / 60.0)) % 24_000;
    }

    static String clock(int ticks) {
        int minuteOfDay = (int) Math.round(ticks * (60.0 / 1000.0)) + 6 * 60;
        int wrapped = Math.floorMod(minuteOfDay, 24 * 60);
        return String.format(Locale.ROOT, "%02d:%02d", wrapped / 60, wrapped % 60);
    }
}
