package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.feature.player.pvp.PvpOverrideRules;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideCondition extends Condition {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;
    private MikSkriptInput<String> id;
    private String owner;

    MikPvpOverrideCondition(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        id = MikSkriptInput.of(expressions[1], String.class);
        owner = MikSkriptOwner.current(getParser());
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        String overrideId = id.single(event);
        if (overrideId == null) {
            return false;
        }
        try {
            overrideId = PvpOverrideRules.normalizeId(overrideId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        String normalized = overrideId;
        return players.check(
                event, player -> facade.hasPvpOverride(player, owner, normalized), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return players.describe(event, debug) + (isNegated() ? " does not have " : " has ")
                + "MIK PVP override " + id.describe(event, debug);
    }
}
