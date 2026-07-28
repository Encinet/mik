package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

final class MikPlayerStateCondition extends Condition {

    private final MikSkriptFacade facade;
    private Expression<Player> players;
    private State state;

    MikPlayerStateCondition(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = (Expression<Player>) expressions[0];
        state = State.values()[matchedPattern / 2];
        setNegated(matchedPattern % 2 == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        return players.check(event, player -> state.test(facade, player), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return players.toString(event, debug) + (isNegated() ? " is not " : " is ") + state.displayName;
    }

    private enum State {
        AFK("MIK AFK"),
        PVP_ENABLED("MIK PVP enabled"),
        PVP_OVERRIDDEN("MIK PVP overridden"),
        COMBAT_TAGGED("MIK combat tagged");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        boolean test(MikSkriptFacade facade, Player player) {
            return switch (this) {
                case AFK -> facade.afk(player);
                case PVP_ENABLED -> facade.pvpEnabled(player);
                case PVP_OVERRIDDEN -> facade.pvpOverridden(player);
                case COMBAT_TAGGED -> facade.combatTagged(player);
            };
        }
    }
}
