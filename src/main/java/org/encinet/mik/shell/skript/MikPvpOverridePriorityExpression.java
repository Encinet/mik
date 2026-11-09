package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;


final class MikPvpOverridePriorityExpression extends SimpleExpression<Number> {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;

    MikPvpOverridePriorityExpression(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        return true;
    }

    @Override
    protected Number[] get(Event event) {
        return players.values(event).stream()
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
                + (players == null ? "players" : players.describe(event, debug));
    }
}
