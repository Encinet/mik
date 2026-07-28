package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.module.afk.AfkModule;
import org.jetbrains.annotations.Nullable;

final class MikAfkSetEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private @Nullable Expression<String> message;
    private boolean broadcast;

    MikAfkSetEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        message = (Expression<String>) expressions[1];
        broadcast = !parseResult.hasTag("silent");
        return true;
    }

    @Override
    protected void execute(Event event) {
        String value = message == null ? "" : message.getSingle(event);
        if (value == null) {
            return;
        }
        if (value.codePointCount(0, value.length()) > AfkModule.MAX_STATUS_LENGTH) {
            error("MIK AFK message cannot exceed " + AfkModule.MAX_STATUS_LENGTH + " characters");
            return;
        }
        for (Player player : players.getArray(event)) {
            facade.setAfk(player, value, broadcast);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + players.toString(event, debug) + " MIK AFK"
                + (message == null ? "" : " with message " + message.toString(event, debug))
                + (broadcast ? "" : " silently");
    }
}
