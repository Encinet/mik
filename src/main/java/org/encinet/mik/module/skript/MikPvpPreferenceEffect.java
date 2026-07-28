package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikPvpPreferenceEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private Expression<Boolean> enabled;

    MikPvpPreferenceEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        enabled = (Expression<Boolean>) expressions[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Boolean value = enabled.getSingle(event);
        if (value == null) {
            return;
        }
        for (Player player : players.getArray(event)) {
            facade.setPvpPreference(player, value);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set MIK PVP preference of " + players.toString(event, debug)
                + " to " + enabled.toString(event, debug);
    }
}
