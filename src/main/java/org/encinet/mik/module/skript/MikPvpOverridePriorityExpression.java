package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class MikPvpOverridePriorityExpression extends SimpleExpression<Number> {

    private final MikSkriptFacade facade;
    private Expression<Player> players;

    MikPvpOverridePriorityExpression(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        return true;
    }

    @Override
    protected Number[] get(Event event) {
        return Arrays.stream(players.getArray(event))
                .map(facade::pvpOverridePriority)
                .filter(java.util.Objects::nonNull)
                .toArray(Number[]::new);
    }

    @Override
    public boolean isSingle() {
        return players.isSingle();
    }

    @Override
    public Class<? extends Number> getReturnType() {
        return Number.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "MIK PVP override priority of "
                + (players == null ? "players" : players.toString(event, debug));
    }
}
