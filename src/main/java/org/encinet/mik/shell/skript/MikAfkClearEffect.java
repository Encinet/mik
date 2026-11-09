package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikAfkClearEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;
    private boolean broadcast;

    MikAfkClearEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        broadcast = !parseResult.hasTag("silent");
        return true;
    }

    @Override
    protected void execute(Event event) {
        for (Player player : players.values(event)) {
            facade.clearAfk(player, broadcast);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "clear MIK AFK state of " + players.describe(event, debug)
                + (broadcast ? "" : " silently");
    }
}
