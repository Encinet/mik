package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;


final class MikPlayerPropertyExpression extends SimpleExpression<String> {

    private final MikSkriptFacade facade;

    private MikSkriptInput<Player> players;
    private Property property;

    MikPlayerPropertyExpression(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        players = MikSkriptInput.of(expressions[0], Player.class);
        property = Property.values()[matchedPattern / 2];
        return true;
    }

    @Override
    protected String[] get(Event event) {
        return players.values(event).stream()
                .map(player -> property.resolve(facade, player))
                .filter(value -> value != null && !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public boolean isSingle() {
        return players.isSingle();
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String propertyName = property == null
                ? "MIK player property" : property.displayName;
        return propertyName + " of " + (players == null ? "players" : players.describe(event, debug));
    }

    private enum Property {
        LANGUAGE("MIK language"),
        CLIENT_VERSION("MIK client version"),
        ROLE("MIK role"),
        AFK_MESSAGE("MIK AFK message"),
        AFK_SOURCE("MIK AFK source"),
        PVP_OVERRIDE_ID("MIK PVP override ID"),
        PVP_OVERRIDE_OWNER("MIK PVP override owner");

        private final String displayName;

        Property(String displayName) {
            this.displayName = displayName;
        }

        String resolve(MikSkriptFacade facade, Player player) {
            return switch (this) {
                case LANGUAGE -> facade.language(player);
                case CLIENT_VERSION -> facade.clientVersion(player);
                case ROLE -> facade.role(player);
                case AFK_MESSAGE -> facade.afkMessage(player);
                case AFK_SOURCE -> facade.afkSource(player);
                case PVP_OVERRIDE_ID -> facade.pvpOverrideId(player);
                case PVP_OVERRIDE_OWNER -> facade.pvpOverrideOwner(player);
            };
        }
    }
}
