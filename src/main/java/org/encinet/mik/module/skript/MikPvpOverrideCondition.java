package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideCondition extends Condition {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private Expression<String> id;
    private String owner;

    MikPvpOverrideCondition(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        id = (Expression<String>) expressions[1];
        owner = MikSkriptOwner.current(getParser());
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        String overrideId = id.getSingle(event);
        if (overrideId == null) {
            return false;
        }
        try {
            overrideId = facade.normalizePvpOverrideId(overrideId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        String normalized = overrideId;
        return players.check(
                event, player -> facade.hasPvpOverride(player, owner, normalized), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return players.toString(event, debug) + (isNegated() ? " does not have " : " has ")
                + "MIK PVP override " + id.toString(event, debug);
    }
}
