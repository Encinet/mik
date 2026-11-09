package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.encinet.mik.feature.player.pvp.PvpOverrideRules;
import org.jetbrains.annotations.Nullable;

final class MikPvpOverrideSetEffect extends Effect {

    private final MikSkriptFacade facade;
    private MikSkriptInput<String> id;
    private MikSkriptInput<Player> players;
    private @Nullable MikSkriptInput<Boolean> enabled;
    private @Nullable MikSkriptInput<Number> priority;
    private @Nullable MikSkriptInput<Timespan> duration;
    private @Nullable Boolean literalEnabled;
    private String owner;

    MikPvpOverrideSetEffect(MikSkriptFacade facade) {
        this.facade = facade;
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern,
                        Kleenean delayed, SkriptParser.ParseResult parseResult) {
        owner = MikSkriptOwner.current(getParser());
        if (matchedPattern == 0) {
            id = MikSkriptInput.of(expressions[0], String.class);
            players = MikSkriptInput.of(expressions[1], Player.class);
            enabled = MikSkriptInput.of(expressions[2], Boolean.class);
            priority = MikSkriptInput.of(expressions[3], Number.class);
            duration = MikSkriptInput.of(expressions[4], Timespan.class);
        } else {
            players = MikSkriptInput.of(expressions[0], Player.class);
            id = MikSkriptInput.of(expressions[1], String.class);
            priority = MikSkriptInput.of(expressions[2], Number.class);
            duration = MikSkriptInput.of(expressions[3], Timespan.class);
            literalEnabled = parseResult.hasTag("on");
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        String overrideId = id.single(event);
        Boolean value = literalEnabled != null
                ? literalEnabled : enabled == null ? null : enabled.single(event);
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
            Number number = priority.single(event);
            if (number == null) {
                return;
            }
            overridePriority = Math.clamp(number.longValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        long durationMillis = PvpOverrideRules.PERMANENT;
        if (duration != null) {
            Timespan timespan = duration.single(event);
            if (timespan == null) {
                return;
            }
            durationMillis = timespan.getAs(Timespan.TimePeriod.MILLISECOND);
        }

        for (Player player : players.values(event)) {
            facade.setPvpOverride(
                    player, owner, overrideId, value, overridePriority, durationMillis);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set MIK PVP override " + id.describe(event, debug) + " of "
                + players.describe(event, debug);
    }
}
