package org.encinet.mik.feature.community.ai.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.protocol.AssistantToolCall;
import org.encinet.mik.protocol.AssistantToolResult;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class GameToolCalls {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final int MAX_ARGUMENT_BYTES = 4096;

    @FunctionalInterface
    public interface Viewer {
        Player find(UUID id, String turn, String tool);
    }

    private record Prepared(Player viewer, Supplier<AssistantToolResult> read) { }

    private final Plugin plugin;
    private final GameToolCatalog tools;
    private final Viewer viewers;

    public GameToolCalls(Plugin plugin, GameToolCatalog tools, Viewer viewers) {
        this.plugin = plugin;
        this.tools = tools;
        this.viewers = viewers;
    }

    public AssistantToolResult execute(AssistantToolCall call, int remainingMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(remainingMs);
        var tool = tools.find(call.getName());
        if (tool == null) return GameTool.accessUnavailable();
        if (call.getArgumentsJsonBytes().size() > MAX_ARGUMENT_BYTES) {
            return GameTool.invalidArguments("tool arguments are too large");
        }
        UUID id;
        JsonElement arguments;
        try {
            id = UUID.fromString(call.getPlayerUuid());
            arguments = JSON.fromJson(call.getArgumentsJson(), JsonElement.class);
        } catch (IllegalArgumentException | JsonParseException invalid) {
            return GameTool.invalidArguments("invalid tool request");
        }
        if (arguments == null || !arguments.isJsonObject()) {
            return GameTool.invalidArguments("tool arguments must be an object");
        }
        try {
            var snapshot = onMain(deadline, () -> {
                var player = viewers.find(id, call.getTurnId(), call.getName());
                return player != null && tool.available(player)
                        ? new Prepared(player, tool.prepare(player, arguments.getAsJsonObject())) : null;
            });
            if (snapshot == null) return GameTool.accessUnavailable();
            if (deadline - System.nanoTime() <= 0) throw new TimeoutException();
            var result = snapshot.read().get();
            boolean stillAllowed = onMain(deadline, () ->
                    viewers.find(id, call.getTurnId(), call.getName()) == snapshot.viewer()
                            && tool.available(snapshot.viewer()));
            return stillAllowed ? result : GameTool.accessUnavailable();
        } catch (TimeoutException timeout) {
            return GameTool.unavailable("game tool timed out");
        }
    }

    private <Result> Result onMain(long deadline, Callable<Result> action) throws Exception {
        if (deadline - System.nanoTime() <= 0) throw new TimeoutException();
        var pending = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
            if (deadline - System.nanoTime() <= 0) throw new TimeoutException();
            return action.call();
        });
        try {
            return pending.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } finally {
            if (!pending.isDone()) pending.cancel(false);
        }
    }
}
