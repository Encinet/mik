package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikAfkClearEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private boolean broadcast;

    MikAfkClearEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        broadcast = !parseResult.hasTag("silent");
        return true;
    }

    @Override
    protected void execute(Event event) {
        for (Player player : players.getArray(event)) {
            facade.clearAfk(player, broadcast);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "clear MIK AFK state of " + players.toString(event, debug)
                + (broadcast ? "" : " silently");
    }
}
