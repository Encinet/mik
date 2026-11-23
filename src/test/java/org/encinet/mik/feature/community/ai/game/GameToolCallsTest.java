package org.encinet.mik.feature.community.ai.game;

import com.google.gson.JsonParser;
import org.bukkit.Difficulty;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.feature.world.buildings.BuildingAssistantTool;
import org.encinet.mik.feature.world.buildings.NearbyBuildings;
import org.encinet.mik.feature.world.elevator.ElevatorAssistantTool;
import org.encinet.mik.feature.world.elevator.ElevatorStatus;
import org.encinet.mik.feature.world.plot.PlotAssistantTool;
import org.encinet.mik.feature.world.plot.PlotPlayerContext;
import org.encinet.mik.protocol.AssistantToolCall;
import org.encinet.mik.protocol.AssistantToolFailure.Reason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameToolCallsTest {
    private final Player player = mock(Player.class);
    private final UUID id = UUID.randomUUID();

    @Test
    void rejectsResultsIfAccessIsRevokedDuringABackgroundLookup() throws Exception {
        var authorized = new AtomicBoolean(true);
        var finished = new AtomicBoolean();
        var buildings = BuildingAssistantTool.create((_, _) -> () -> {
            authorized.set(false);
            finished.set(true);
            return Optional.of(new NearbyBuildings("world", "", 0, 0, List.of(), null));
        });
        var calls = new GameToolCalls(plugin(), new GameToolCatalog(List.of(buildings)),
                (viewer, turn, _) -> id.equals(viewer) && turn.equals("current") && authorized.get() ? player : null);
        var result = calls.execute(call("game_nearby_buildings", "{}"), 1000);
        assertTrue(finished.get());
        assertEquals(Reason.ACCESS_UNAVAILABLE, result.getFailure().getReason());
        assertFalse(result.hasObservation());
    }

    @Test
    void paperPreservesUnknownDirectionsAndOwnsBuildingValidationAndLinks() throws Exception {
        var search = new AtomicReference<NearbyBuildings.Search>();
        var world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getDifficulty()).thenReturn(Difficulty.HARD);
        when(world.getTime()).thenReturn(13_000L);

        var tools = new ArrayList<>(VanillaGameTools.tools());
        tools.add(PlotAssistantTool.create(_ ->
                new PlotPlayerContext("Here", PlotPlayerContext.Access.COLLABORATOR, true, false, true)));
        tools.add(ElevatorAssistantTool.create(_ -> new ElevatorStatus(true, null, false)));
        tools.add(BuildingAssistantTool.create((_, query) -> {
            search.set(query);
            return () -> Optional.of(new NearbyBuildings("world", query.filter(), query.offset(), 3,
                    List.of(new NearbyBuildings.Building("49d193bf24ed404196a9c2eb4a884ea7", "Hall",
                            "replica", -10, 50, 20, 24)), 2));
        }));
        var calls = new GameToolCalls(plugin(), new GameToolCatalog(tools), (_, _, _) -> player);
        var plot = JsonParser.parseString(calls.execute(call("game_plot_context", "{}"), 1000)
                .getObservation().getDataJson()).getAsJsonObject();
        assertEquals("Here", plot.get("plot_name").getAsString());
        assertEquals("collaborator", plot.get("standing").getAsString());
        assertTrue(plot.get("role_allows_plot_creation").getAsBoolean());
        assertFalse(plot.get("can_place").getAsBoolean());
        assertTrue(plot.get("can_break").getAsBoolean());

        var elevator = JsonParser.parseString(calls.execute(call("game_elevator_status", "{}"), 1000)
                .getObservation().getDataJson()).getAsJsonObject();
        assertTrue(elevator.get("on_iron").getAsBoolean());
        assertTrue(elevator.get("can_up").isJsonNull());
        assertFalse(elevator.get("can_down").getAsBoolean());

        var worldInfo = JsonParser.parseString(calls.execute(call("game_world_info", "{}"), 1000)
                .getObservation().getDataJson()).getAsJsonObject();
        assertEquals(13_000, worldInfo.get("day_time_ticks").getAsInt());

        var nearby = calls.execute(call("game_nearby_buildings", "{\"query\":\" Hall \",\"offset\":1,\"limit\":1}"), 1000)
                .getObservation();
        var page = JsonParser.parseString(nearby.getDataJson()).getAsJsonObject();
        assertEquals(new NearbyBuildings.Search("Hall", 1, 1), search.get());
        assertEquals("world", page.get("world").getAsString());
        assertEquals(3, page.get("total_matches").getAsInt());
        assertEquals(2, page.get("next_offset").getAsInt());
        var item = page.getAsJsonArray("buildings").get(0).getAsJsonObject();
        assertEquals(-10, item.get("x").getAsInt());
        assertEquals(24, item.get("distance_blocks").getAsInt());
        assertEquals("replica", item.get("type").getAsString());
        assertEquals("https://mcmik.top/buildings/49d193bf24ed404196a9c2eb4a884ea7", nearby.getLinks(0).getUrl());

        for (var args : List.of("[]", "{offset:1}", "{\"offset\":-1}", "{\"limit\":17}", "{\"limit\":1.5}",
                "{\"limit\":\"1\"}", "{\"player_uuid\":\"someone else\"}", "{\"query\":\"" + "x".repeat(257) + "\"}")) {
            var result = calls.execute(call("game_nearby_buildings", args), 1000);
            assertEquals(Reason.INVALID_ARGUMENTS, result.getFailure().getReason(), args);
        }
        assertEquals(new NearbyBuildings.Search("Hall", 1, 1), search.get());
    }

    private AssistantToolCall call(String name, String arguments) {
        return AssistantToolCall.newBuilder().setPlayerUuid(id.toString()).setTurnId("current")
                .setName(name).setArgumentsJson(arguments).build();
    }

    private static Plugin plugin() {
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.callSyncMethod(eq(plugin), any())).thenAnswer(invocation -> {
            Callable<?> action = invocation.getArgument(1);
            return CompletableFuture.completedFuture(action.call());
        });
        return plugin;
    }
}
