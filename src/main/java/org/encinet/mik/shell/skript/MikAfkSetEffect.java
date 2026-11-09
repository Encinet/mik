package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.feature.player.afk.AfkState;
import org.jetbrains.annotations.Nullable;

final class MikAfkSetEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<Player> players;
    private @Nullable MikSkriptInput<String> message;
    private boolean broadcast;

    MikAfkSetEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        message = MikSkriptInput.of(expressions[1], String.class);
        broadcast = !parseResult.hasTag("silent");
        return true;
    }

    @Override
    protected void execute(Event event) {
        String value = message == null ? "" : message.single(event);
        if (value == null) {
            return;
        }
        if (value.codePointCount(0, value.length()) > AfkState.MAX_MESSAGE_CODE_POINTS) {
            error("MIK AFK message cannot exceed " + AfkState.MAX_MESSAGE_CODE_POINTS + " characters");
            return;
        }
        for (Player player : players.values(event)) {
            facade.setAfk(player, value, broadcast);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + players.describe(event, debug) + " MIK AFK"
                + (message == null ? "" : " with message " + message.describe(event, debug))
                + (broadcast ? "" : " silently");
    }
}
