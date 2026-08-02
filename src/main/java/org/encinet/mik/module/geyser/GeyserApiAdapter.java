package org.encinet.mik.module.geyser;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Type-safe access to Geyser itself. Only the Cumulus form call is delegated to
 * a class-loader compatibility boundary.
 */
final class GeyserApiAdapter {

    private final GeyserApi api;
    private final CumulusFormCompatBridge forms;

    private GeyserApiAdapter(GeyserApi api, CumulusFormCompatBridge forms) {
        this.api = api;
        this.forms = forms;
    }

    static GeyserApiAdapter create() throws ReflectiveOperationException {
        GeyserApi api = GeyserApi.api();
        if (api == null) {
            throw new IllegalStateException("GeyserApi.api() returned null");
        }
        return new GeyserApiAdapter(
                api,
                CumulusFormCompatBridge.create(api, GeyserApi.class));
    }

    boolean isBedrockPlayer(UUID playerId) {
        return api.isBedrockPlayer(playerId);
    }

    boolean sendForm(
            UUID playerId,
            BedrockSimpleForm form,
            Consumer<Throwable> callbackFailure
    ) throws ReflectiveOperationException {
        return forms.sendForm(playerId, form, callbackFailure);
    }

    void closeForm(UUID playerId) {
        GeyserConnection connection = api.connectionByUuid(playerId);
        if (connection != null && connection.hasFormOpen()) {
            connection.closeForm();
        }
    }
}
