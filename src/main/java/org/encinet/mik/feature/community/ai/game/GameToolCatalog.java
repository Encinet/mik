package org.encinet.mik.feature.community.ai.game;

import org.bukkit.entity.Player;
import org.encinet.mik.protocol.AssistantToolDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameToolCatalog {
    private final Map<String, GameTool> tools;

    public GameToolCatalog(List<GameTool> tools) {
        if (tools.size() > 24) throw new IllegalArgumentException("Too many assistant game tools");
        var index = new LinkedHashMap<String, GameTool>();
        int bytes = 0;
        for (var tool : tools) {
            var definition = tool.definition();
            if (definition.getDescriptionBytes().size() > 1024
                    || definition.getInputSchemaJsonBytes().size() > 4096
                    || (bytes += definition.getSerializedSize()) > 24 * 1024) {
                throw new IllegalArgumentException("Assistant tool catalog is too large");
            }
            if (index.putIfAbsent(definition.getName(), tool) != null) {
                throw new IllegalArgumentException("Duplicate assistant tool: " + definition.getName());
            }
        }
        this.tools = Collections.unmodifiableMap(index);
    }

    public List<AssistantToolDefinition> describe(Player viewer) {
        return tools.values().stream().filter(tool -> tool.available(viewer)).map(GameTool::definition).toList();
    }

    GameTool find(String name) {
        return tools.get(name);
    }
}
