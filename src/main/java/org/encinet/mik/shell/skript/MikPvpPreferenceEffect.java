package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikPvpPreferenceEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;
    private MikSkriptInput<Boolean> enabled;

    MikPvpPreferenceEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        enabled = MikSkriptInput.of(expressions[1], Boolean.class);
        return true;
    }

    @Override
    protected void execute(Event event) {
        Boolean value = enabled.single(event);
        if (value == null) {
            return;
        }
        for (Player player : players.values(event)) {
            facade.setPvpPreference(player, value);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set MIK PVP preference of " + players.describe(event, debug)
                + " to " + enabled.describe(event, debug);
    }
}
