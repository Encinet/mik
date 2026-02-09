package org.encinet.mik.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.ServerBuildInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.ProtocolUtil;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * Module for server branding modification via PacketEvents
 */
public class BrandingModule {

    private static final String BRAND_CHANNEL = "minecraft:brand";
    private static final String CUSTOM_BRAND = "§r§6Mi§fk §aCasual§r";

    private final JavaPlugin plugin;
    private byte[] customBrandBytes;

    public BrandingModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load PacketEvents (called in onLoad)
     */
    public void load() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        PacketEvents.getAPI().load();
    }

    /**
     * Enable branding module (called in onEnable)
     */
    public void enable() {
        PacketEvents.getAPI().init();

        // Prepare custom brand bytes
        byte[] brandBytes = CUSTOM_BRAND.getBytes(StandardCharsets.UTF_8);
        byte[] varIntLength = ProtocolUtil.encodeVarInt(brandBytes.length);
        customBrandBytes = new byte[varIntLength.length + brandBytes.length];
        System.arraycopy(varIntLength, 0, customBrandBytes, 0, varIntLength.length);
        System.arraycopy(brandBytes, 0, customBrandBytes, varIntLength.length, brandBytes.length);

        // Register packet listener
        PacketEvents.getAPI().getEventManager().registerListener(
                new BrandPacketListener()
        );
    }

    /**
     * Disable branding module (called in onDisable)
     */
    public void disable() {
        PacketEvents.getAPI().terminate();
    }

    /**
     * Packet listener for brand modification
     */
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
