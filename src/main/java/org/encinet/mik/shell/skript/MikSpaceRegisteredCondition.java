package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/** Tests whether a configured or runtime MIK spatial link is currently active. */
final class MikSpaceRegisteredCondition extends Condition {

    private final MikSkriptFacade facade;
    private MikSkriptInput<String> id;

    MikSpaceRegisteredCondition(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        id = MikSkriptInput.of(expressions[0], String.class);
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        String linkId = id.single(event);
        boolean registered = linkId != null && facade.spaceRegistered(linkId);
        return isNegated() ? !registered : registered;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "MIK space " + (id == null ? "" : id.describe(event, debug))
                + (isNegated() ? " is not registered" : " is registered");
    }
}
