package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class MikPvpOverrideIdsExpression extends SimpleExpression<String> {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private String owner;

    MikPvpOverrideIdsExpression(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        owner = MikSkriptOwner.current(getParser());
        return true;
    }

    @Override
    protected String[] get(Event event) {
        return Arrays.stream(players.getArray(event))
                .flatMap(player -> facade.pvpOverrideIds(player, owner).stream())
                .sorted()
                .toArray(String[]::new);
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "MIK PVP override IDs of "
                + (players == null ? "players" : players.toString(event, debug));
    }
}
