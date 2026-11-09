package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/** Removes a runtime spatial link owned by the current script. */
final class MikSpaceUnregisterEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<String> id;
    private String owner;

    MikSpaceUnregisterEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        id = MikSkriptInput.of(expressions[0], String.class);
        owner = MikSkriptOwner.current(getParser());
        return true;
    }

    @Override
    protected void execute(Event event) {
        String linkId = id.single(event);
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
        return "unregister MIK space " + (id == null ? "" : id.describe(event, debug));
    }
}
