package org.encinet.mik.feature.world.elevator;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.ai.game.GameTool;

import java.util.function.Function;

public final class ElevatorAssistantTool {
    private ElevatorAssistantTool() { }

    public static GameTool create(Function<Player, ElevatorStatus> read) {
        return GameTool.read("game_elevator_status",
                "Check the iron platform under the requester and currently known safe directions. A null direction "
                        + "is unconfirmed; false means no safe destination was found. Does not travel or scan unobserved blocks.",
                viewer -> {
                    var status = read.apply(viewer);
                    var data = new JsonObject();
                    data.addProperty("on_iron", status.onIron());
                    data.addProperty("can_up", status.canUp());
                    data.addProperty("can_down", status.canDown());
                    return data;
                });
    }
}
