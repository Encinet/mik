package org.encinet.mik.module.presentation;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import io.papermc.paper.ServerBuildInfo;
import org.encinet.mik.util.ProtocolUtil;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;

public class BrandingModule {

    private static final String BRAND_CHANNEL = "minecraft:brand";
    private static final String CUSTOM_BRAND = "§r§6Mi§fk §aCasual§r";

    private final BrandPacketListener listener = new BrandPacketListener();
    private byte[] customBrandBytes;
    private EventManager eventManager;

    public void enable() {
        if (eventManager != null) {
            return;
        }

        byte[] brandBytes = CUSTOM_BRAND.getBytes(StandardCharsets.UTF_8);
        byte[] varIntLength = ProtocolUtil.encodeVarInt(brandBytes.length);
        customBrandBytes = new byte[varIntLength.length + brandBytes.length];
        System.arraycopy(varIntLength, 0, customBrandBytes, 0, varIntLength.length);
        System.arraycopy(brandBytes, 0, customBrandBytes, varIntLength.length, brandBytes.length);

        eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(listener);
    }

    public void disable() {
        if (eventManager == null) {
            return;
        }
        eventManager.unregisterListener(listener);
        eventManager = null;
    }

    private class BrandPacketListener extends PacketListenerAbstract {

        public BrandPacketListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketSend(@NonNull PacketSendEvent event) {
            ConnectionState state = event.getConnectionState();

            // Only handle PLAY, CONFIGURATION and STATUS states
            if (!state.equals(ConnectionState.PLAY)
                    && !state.equals(ConnectionState.CONFIGURATION)
                    && !state.equals(ConnectionState.STATUS)) {
                return;
            }

            switch (event.getPacketType()) {
                case PacketType.Configuration.Server.PLUGIN_MESSAGE -> {
                    WrapperConfigServerPluginMessage packet =
                            new WrapperConfigServerPluginMessage(event);
                    if (!packet.getChannelName().equals(BRAND_CHANNEL)) {
                        return;
                    }
                    packet.setData(customBrandBytes);
                    event.markForReEncode(true);
                }
                case PacketType.Play.Server.PLUGIN_MESSAGE -> {
                    WrapperPlayServerPluginMessage packet =
                            new WrapperPlayServerPluginMessage(event);
                    if (!packet.getChannelName().equals(BRAND_CHANNEL)) {
                        return;
                    }
                    packet.setData(customBrandBytes);
                    event.markForReEncode(true);
                }
                case PacketType.Status.Server.RESPONSE -> {
                    WrapperStatusServerResponse packet =
                            new WrapperStatusServerResponse(event);
                    var component = packet.getComponent();
                    var versionJson = component.get("version");

                    if (versionJson != null) {
                        versionJson.getAsJsonObject().addProperty(
                                "name",
                                CUSTOM_BRAND + " " + ServerBuildInfo.buildInfo()
                                        .minecraftVersionName()
                        );
                        packet.setComponent(component);
                        event.markForReEncode(true);
                    }
                }
                default -> {
                }
            }
        }
    }
}
