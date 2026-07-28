package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideClearEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private @Nullable Expression<String> id;
    private boolean all;
    private String owner;

    MikPvpOverrideClearEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        owner = MikSkriptOwner.current(getParser());
        all = matchedPattern == 1;
        if (all) {
            players = (Expression<Player>) expressions[0];
        } else {
            id = (Expression<String>) expressions[0];
            players = (Expression<Player>) expressions[1];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        if (all) {
            for (Player player : players.getArray(event)) {
                facade.clearPvpOverrides(player, owner);
            }
            return;
        }

        String overrideId = id == null ? null : id.getSingle(event);
        if (overrideId == null) {
            return;
        }
        try {
            overrideId = facade.normalizePvpOverrideId(overrideId);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }
        for (Player player : players.getArray(event)) {
            facade.clearPvpOverride(player, owner, overrideId);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "clear " + (all ? "all MIK PVP overrides" : "MIK PVP override " + id)
                + " of " + players.toString(event, debug);
    }
}
