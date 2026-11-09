package org.encinet.mik.shared.menu.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** A full snapshot. No base theme, inherited values or preset identity remain after copying. */
public record UiCustomTheme(Map<UiColorRole, UiColor.Literal> colors) implements UiThemeColors {
    public UiCustomTheme {
        colors = Map.copyOf(colors);
        for (var role : UiColorRole.values()) Objects.requireNonNull(colors.get(role), "Missing color: " + role);
    }
    public static UiCustomTheme copyOf(UiThemeColors source) {
        var result = new EnumMap<UiColorRole, UiColor.Literal>(UiColorRole.class);
        for (var role : UiColorRole.values()) result.put(role, new UiColor.Literal(source.rgb(role), source.alpha(role)));
        return new UiCustomTheme(result);
    }
    public UiCustomTheme withColor(UiColorRole role, int rgb, double alpha) {
        var result = new EnumMap<UiColorRole, UiColor.Literal>(UiColorRole.class);
        result.putAll(colors);
        result.put(Objects.requireNonNull(role), new UiColor.Literal(rgb, alpha));
        return new UiCustomTheme(result);
    }
    @Override public String id() { return "custom"; }
    @Override public int rgb(UiColorRole role) { return colors.get(role).rgb(); }
    @Override public double alpha(UiColorRole role) { return colors.get(role).alpha(); }
}
