package org.encinet.mik.module.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.module.pvp.PvpOverrideRules;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideSetEffect extends Effect {

    private final MikSkriptFacade facade;
    private Expression<String> id;
    private Expression<Player> players;
    private @Nullable Expression<Boolean> enabled;
    private @Nullable Expression<Number> priority;
    private @Nullable Expression<Timespan> duration;
    private @Nullable Boolean literalEnabled;
    private String owner;

    MikPvpOverrideSetEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        owner = MikSkriptOwner.current(getParser());
        if (matchedPattern == 0) {
            id = (Expression<String>) expressions[0];
            players = (Expression<Player>) expressions[1];
            enabled = (Expression<Boolean>) expressions[2];
            priority = (Expression<Number>) expressions[3];
            duration = (Expression<Timespan>) expressions[4];
        } else {
            players = (Expression<Player>) expressions[0];
            id = (Expression<String>) expressions[1];
            priority = (Expression<Number>) expressions[2];
            duration = (Expression<Timespan>) expressions[3];
            literalEnabled = parseResult.hasTag("on");
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        String overrideId = id.getSingle(event);
        Boolean value = literalEnabled != null
                ? literalEnabled : enabled == null ? null : enabled.getSingle(event);
        if (overrideId == null || value == null) {
            return;
        }
        try {
            overrideId = PvpOverrideRules.normalizeId(overrideId);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }

        int overridePriority = 0;
        if (priority != null) {
            Number number = priority.getSingle(event);
            if (number == null) {
                return;
            }
            overridePriority = (int) Math.clamp(number.longValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        long durationMillis = PvpOverrideRules.PERMANENT;
        if (duration != null) {
            Timespan timespan = duration.getSingle(event);
            if (timespan == null) {
                return;
            }
            durationMillis = timespan.getAs(Timespan.TimePeriod.MILLISECOND);
        }

        for (Player player : players.getArray(event)) {
            facade.setPvpOverride(
                    player, owner, overrideId, value, overridePriority, durationMillis);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set MIK PVP override " + id.toString(event, debug) + " of "
                + players.toString(event, debug);
    }
}
