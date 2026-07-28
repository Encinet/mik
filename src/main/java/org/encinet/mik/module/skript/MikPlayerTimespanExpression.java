package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class MikPlayerTimespanExpression extends SimpleExpression<Timespan> {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private Property property;

    MikPlayerTimespanExpression(MikSkriptFacade facade) {
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
    protected Timespan[] get(Event event) {
        return Arrays.stream(players.getArray(event))
                .map(player -> property.resolve(facade, player))
                .filter(java.util.Objects::nonNull)
                .toArray(Timespan[]::new);
    }

    @Override
    public boolean isSingle() {
        return players.isSingle();
    }

    @Override
    public Class<? extends Timespan> getReturnType() {
        return Timespan.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String name = property == null ? "MIK player duration" : property.displayName;
        return name + " of " + (players == null ? "players" : players.toString(event, debug));
    }

    private enum Property {
        AFK_DURATION("MIK AFK duration"),
        PVP_OVERRIDE_REMAINING("MIK PVP override remaining time"),
        COMBAT_TAG_REMAINING("MIK combat tag remaining time");

        private final String displayName;

        Property(String displayName) {
            this.displayName = displayName;
        }

        Timespan resolve(MikSkriptFacade facade, Player player) {
            return switch (this) {
                case AFK_DURATION -> facade.afkDuration(player);
                case PVP_OVERRIDE_REMAINING -> facade.pvpOverrideRemaining(player);
                case COMBAT_TAG_REMAINING -> facade.combatTagRemaining(player);
            };
        }
    }
}
