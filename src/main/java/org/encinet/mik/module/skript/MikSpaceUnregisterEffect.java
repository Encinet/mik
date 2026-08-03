package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/** Removes a runtime spatial link owned by the current script. */
final class MikSpaceUnregisterEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<String> id;
    private String owner;

    MikSpaceUnregisterEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        id = (Expression<String>) expressions[0];
        owner = MikSkriptOwner.current(getParser());
        return true;
    }

    @Override
    protected void execute(Event event) {
        String linkId = id.getSingle(event);
        if (linkId == null) {
            return;
        }
        try {
            facade.unregisterSpace(owner, linkId);
        } catch (IllegalArgumentException | IllegalStateException error) {
            error("Could not unregister MIK space: " + error.getMessage());
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "unregister MIK space " + (id == null ? "" : id.toString(event, debug));
    }
}
