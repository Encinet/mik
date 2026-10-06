package org.encinet.mik.module.plot;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAtmosphereControllerTest {
    @Test
    void enteringAndLeavingRestoresOnlyTheOverriddenPlayerFields() {
        List<Call> calls = new ArrayList<>();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    calls.add(new Call(method.getName(), args == null ? List.of() : List.of(args)));
                    return switch (method.getName()) {
                        case "getPlayerTimeOffset" -> 90L;
                        case "isPlayerTimeRelative" -> true;
                        case "getPlayerWeather" -> WeatherType.CLEAR;
                        default -> null;
                    };
                });
        PlotAtmosphere eveningRain = new PlotAtmosphere(12_000, PlotAtmosphere.Weather.RAIN);
        PlotAtmosphere clearWorldTime = new PlotAtmosphere(null, PlotAtmosphere.Weather.CLEAR);

        PlotAtmosphereController.Applied applied = PlotAtmosphereController.apply(
                player, null, eveningRain, false);
        assertEquals(List.of("setPlayerTime", "setPlayerWeather"), writes(calls));
        assertEquals(List.of(12_000L, false), call(calls, "setPlayerTime").arguments());
        assertEquals(List.of(WeatherType.DOWNFALL),
                call(calls, "setPlayerWeather").arguments());

        calls.clear();
        applied = PlotAtmosphereController.apply(player, applied, clearWorldTime, false);
        assertEquals(List.of("setPlayerTime", "setPlayerWeather"), writes(calls));
        assertEquals(List.of(90L, true), call(calls, "setPlayerTime").arguments());
        assertEquals(List.of(WeatherType.CLEAR),
                call(calls, "setPlayerWeather").arguments());

        calls.clear();
        assertNull(PlotAtmosphereController.apply(player, applied, PlotAtmosphere.WORLD, false));
        assertEquals(List.of("setPlayerWeather"), writes(calls));
        assertEquals(List.of(WeatherType.CLEAR),
                call(calls, "setPlayerWeather").arguments());
    }

    @Test
    void unchangedAtmosphereReusesTheAppliedStateWithoutCallingPlayerApis() {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });
        var setting = new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR);
        var applied = new PlotAtmosphereController.Applied(setting,
                new PlotAtmosphereController.TimeBefore(90, true),
                new PlotAtmosphereController.WeatherBefore(WeatherType.DOWNFALL));
        assertSame(applied, PlotAtmosphereController.apply(player, applied,
                new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR), false));
        assertNull(PlotAtmosphereController.apply(player, null, PlotAtmosphere.WORLD, false));
    }

    @Test
    void observationsReuseACellButInvalidateForWorldCellOrEnvironmentVersionChanges() {
        UUID world = UUID.randomUUID();
        var observed = new PlotAtmosphereController.Observation(world, -1, 16, 0, 10);
        assertTrue(observed.matches(world, -1, 65, 0, 10));
        assertTrue(observed.matches(world, -4, 64, 3, 10));
        assertFalse(observed.matches(UUID.randomUUID(), -1, 65, 0, 10));
        assertFalse(observed.matches(world, 0, 65, 0, 10));
        assertFalse(observed.matches(world, -5, 65, 0, 10));
        assertFalse(observed.matches(world, -1, 68, 0, 10));
        assertFalse(observed.matches(world, -1, 65, 4, 10));
        assertFalse(observed.matches(world, -1, 65, 0, 11));
    }

    @Test
    void forceStillReappliesUnchangedOverridesAndPreservesRestorationState() {
        List<Call> calls = new ArrayList<>();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, arguments) -> {
                    calls.add(new Call(method.getName(), arguments == null ? List.of() : List.of(arguments)));
                    return null;
                });
        var setting = new PlotAtmosphere(6000, PlotAtmosphere.Weather.RAIN);
        var beforeTime = new PlotAtmosphereController.TimeBefore(90, true);
        var beforeWeather = new PlotAtmosphereController.WeatherBefore(WeatherType.CLEAR);
        var previous = new PlotAtmosphereController.Applied(setting, beforeTime, beforeWeather);
        var applied = PlotAtmosphereController.apply(player, previous, setting, true);
        assertEquals(List.of("setPlayerTime", "setPlayerWeather"), writes(calls));
        assertSame(beforeTime, applied.timeBefore());
        assertSame(beforeWeather, applied.weatherBefore());
    }

    @Test
    void worldDefaultsAreResetAfterLeaving() {
        List<Call> calls = new ArrayList<>();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    calls.add(new Call(method.getName(), args == null ? List.of() : List.of(args)));
                    return switch (method.getName()) {
                        case "getPlayerTimeOffset" -> 0L;
                        case "isPlayerTimeRelative" -> true;
                        default -> null;
                    };
                });
        PlotAtmosphereController.Applied applied = PlotAtmosphereController.apply(player,
                null, new PlotAtmosphere(6_000, PlotAtmosphere.Weather.RAIN), false);
        calls.clear();

        assertNull(PlotAtmosphereController.apply(player, applied, PlotAtmosphere.WORLD, false));
        assertEquals(List.of("resetPlayerTime", "resetPlayerWeather"), writes(calls));
    }

    private static List<String> writes(List<Call> calls) {
        return calls.stream().map(Call::name)
                .filter(name -> name.startsWith("setPlayer") || name.startsWith("resetPlayer"))
                .toList();
    }

    private static Call call(List<Call> calls, String name) {
        return calls.stream().filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
    }

    private record Call(String name, List<Object> arguments) { }
}
