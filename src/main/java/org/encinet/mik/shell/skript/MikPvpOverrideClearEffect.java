package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.feature.player.pvp.PvpOverrideRules;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideClearEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;
    private @Nullable MikSkriptInput<String> id;
    private boolean all;
    private String owner;

    MikPvpOverrideClearEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        owner = MikSkriptOwner.current(getParser());
        all = matchedPattern == 1;
        if (all) {
            players = MikSkriptInput.of(expressions[0], Player.class);
        } else {
            id = MikSkriptInput.of(expressions[0], String.class);
            players = MikSkriptInput.of(expressions[1], Player.class);
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        if (all) {
            for (Player player : players.values(event)) {
                facade.clearPvpOverrides(player, owner);
            }
            return;
        }

        String overrideId = id == null ? null : id.single(event);
        if (overrideId == null) {
            return;
        }
        try {
            overrideId = PvpOverrideRules.normalizeId(overrideId);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }
        for (Player player : players.values(event)) {
            facade.clearPvpOverride(player, owner, overrideId);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "clear " + (all ? "all MIK PVP overrides" : "MIK PVP override " + id)
                + " of " + players.describe(event, debug);
    }
}
