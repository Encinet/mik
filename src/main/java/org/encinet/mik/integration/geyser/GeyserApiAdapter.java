package org.encinet.mik.integration.geyser;

import org.geysermc.geyser.api.GeyserApi;
import java.util.UUID;

/** Optional, type-safe access to Bedrock player identity. */
final class GeyserApiAdapter {
    private final GeyserApi api;

    private GeyserApiAdapter(GeyserApi api) { this.api = api; }

    static GeyserApiAdapter create() {
        GeyserApi api = GeyserApi.api();
        if (api == null) throw new IllegalStateException("GeyserApi.api() returned null");
        return new GeyserApiAdapter(api);
    }

    boolean isBedrockPlayer(UUID playerId) { return api.isBedrockPlayer(playerId); }
}
