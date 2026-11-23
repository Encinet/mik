package org.encinet.mik.feature.world.plot;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.ai.game.GameTool;

import java.util.Locale;
import java.util.function.Function;

public final class PlotAssistantTool {
    private PlotAssistantTool() { }

    public static GameTool create(Function<Player, PlotPlayerContext> read) {
        return GameTool.read("game_plot_context",
                "Check the plot at the requester's position, their own place and break permissions under plot "
                        + "protection, and whether their role allows creating a plot. Does not check target blocks, "
                        + "other plugins, spawn protection, or world and game-mode restrictions.",
                viewer -> {
                    var plot = read.apply(viewer);
                    var data = new JsonObject();
                    data.addProperty("standing", plot.access().name().toLowerCase(Locale.ROOT));
                    data.addProperty("plot_name", plot.access() == PlotPlayerContext.Access.OUTSIDE ? null : plot.plotName());
                    data.addProperty("role_allows_plot_creation", plot.memberEligible());
                    data.addProperty("can_place", plot.canPlace());
                    data.addProperty("can_break", plot.canBreak());
                    return data;
                });
    }
}
