package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class MikPlayerBooleanExpression extends SimpleExpression<Boolean> {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private Property property;

    MikPlayerBooleanExpression(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        property = Property.values()[matchedPattern / 2];
        return true;
    }

    @Override
    protected Boolean[] get(Event event) {
        return Arrays.stream(players.getArray(event))
                .map(player -> property.resolve(facade, player))
                .filter(java.util.Objects::nonNull)
                .toArray(Boolean[]::new);
    }

    @Override
    public boolean isSingle() {
        return players.isSingle();
    }

    @Override
    public Class<? extends Boolean> getReturnType() {
        return Boolean.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String name = property == null ? "MIK player state" : property.displayName;
        return name + " of " + (players == null ? "players" : players.toString(event, debug));
    }

    private enum Property {
        AFK("MIK AFK state"),
        PVP_ENABLED("MIK effective PVP state"),
        PVP_PREFERENCE("MIK PVP preference"),
        PVP_OVERRIDDEN("MIK PVP override state"),
        PVP_OVERRIDE_VALUE("MIK PVP override value"),
        COMBAT_TAGGED("MIK combat tag state");

        private final String displayName;

        Property(String displayName) {
            this.displayName = displayName;
        }

        Boolean resolve(MikSkriptFacade facade, Player player) {
            return switch (this) {
                case AFK -> facade.afk(player);
                case PVP_ENABLED -> facade.pvpEnabled(player);
                case PVP_PREFERENCE -> facade.pvpPreference(player);
                case PVP_OVERRIDDEN -> facade.pvpOverridden(player);
                case PVP_OVERRIDE_VALUE -> facade.pvpOverrideValue(player);
                case COMBAT_TAGGED -> facade.combatTagged(player);
            };
        }
    }
}
