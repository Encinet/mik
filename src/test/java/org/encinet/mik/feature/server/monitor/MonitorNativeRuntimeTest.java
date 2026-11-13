package org.encinet.mik.feature.server.monitor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitorNativeRuntimeTest {
    @Test
    void nativeSelectorsAndCameraAreCheckedInAnIsolatedRegistryRuntime(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary) throws Exception {
        // Native bootstrap locks Bukkit registry holders; it must not share the suite's scoped test registries.
        var entries = new java.util.LinkedHashSet<String>();
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) {
                for (var url : urls.getURLs()) entries.add(java.nio.file.Path.of(url.toURI()).toString());
            }
        }
        String classpath = entries.isEmpty() ? System.getProperty("java.class.path")
                : String.join(java.io.File.pathSeparator, entries);
        String agent = java.nio.file.Path.of(org.mockito.Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var output = temporary.resolve("native-selectors.log");
        var process = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-javaagent:" + agent, "-Xshare:off", "--enable-native-access=ALL-UNNAMED",
                "-cp", classpath, getClass().getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "native selector checks timed out");
            assertEquals(0, process.exitValue(), () -> {
                try { return java.nio.file.Files.readString(output); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            });
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public static void main(String[] arguments) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        checkNativeSelectors();
        checkSpectatorCamera();
    }

    private static void checkNativeSelectors() throws Exception {
        ServerPlayer hidden = mock(ServerPlayer.class);
        ServerPlayer visible = mock(ServerPlayer.class);
        // Bukkit handles must be CraftPlayers for native entities.
        var hiddenPlayer = mock(org.bukkit.craftbukkit.entity.CraftPlayer.class);
        var visiblePlayer = mock(org.bukkit.craftbukkit.entity.CraftPlayer.class);
        when(hidden.getBukkitEntity()).thenReturn(hiddenPlayer);
        when(visible.getBukkitEntity()).thenReturn(visiblePlayer);
        CommandSourceStack source = mock(CommandSourceStack.class);
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList players = mock(PlayerList.class);
        when(source.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayerByName("Staff")).thenReturn(hidden);
        when(players.getPlayerByName("Member")).thenReturn(visible);
        UUID id = UUID.randomUUID();
        when(players.getPlayer(id)).thenReturn(hidden);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        when(server.getAllLevels()).thenReturn(java.util.List.of(level));
        when(level.getEntity(id)).thenReturn(hidden);
        doReturn(net.minecraft.world.entity.EntityTypes.PLAYER).when(hidden).getType();
        when(source.enabledFeatures()).thenReturn(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        when(source.getPosition()).thenReturn(net.minecraft.world.phys.Vec3.ZERO);
        when(source.hasPermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(players.getPlayers()).thenReturn(java.util.List.of(hidden, visible));

        assertTrue(MonitorCommandGuard.targetsMonitor(context(source, "Staff"), hiddenPlayer::equals));
        assertTrue(MonitorCommandGuard.targetsMonitor(context(source, id.toString()), hiddenPlayer::equals));
        assertFalse(MonitorCommandGuard.targetsMonitor(context(source, "Member"), hiddenPlayer::equals));
        assertFalse(MonitorCommandGuard.targetsMonitor(context(source, "Offline"), hiddenPlayer::equals));
        assertTrue(MonitorCommandGuard.targetsMonitor(context(source, "@a"), hiddenPlayer::equals));
        var redirected = context(source, "Member").withChild(context(source, "Staff"));
        assertTrue(MonitorCommandGuard.targetsMonitor(redirected, hiddenPlayer::equals));
    }

    private static void checkSpectatorCamera() {
        var api = mock(com.github.retrooper.packetevents.PacketEventsAPI.class);
        var manager = mock(com.github.retrooper.packetevents.manager.server.ServerManager.class);
        when(api.getServerManager()).thenReturn(manager);
        when(manager.getVersion()).thenReturn(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_21_4);
        com.github.retrooper.packetevents.PacketEvents.setAPI(api);
        var wrapped = new java.util.ArrayList<com.github.retrooper.packetevents.wrapper.PacketWrapper<?>>();
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var cameras = new java.util.ArrayList<Integer>();
        var ids = new java.util.concurrent.atomic.AtomicInteger(100);
        var viewer = mock(org.bukkit.entity.Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getEntityId()).thenReturn(7);
        when(viewer.getEyeHeight()).thenReturn(0.9);
        var world = mock(org.bukkit.World.class);
        var view = new MonitorCameraView((_, packet) -> packets.add(packet), (_, packet) -> {
            wrapped.add(packet);
            if (packet instanceof com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera camera) cameras.add(camera.getCameraId());
        }, _ -> ids.incrementAndGet());
        var feet = new org.bukkit.Location(world, 4, 65, 8, 45, 10);
        view.move(viewer, feet, false);
        var info = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class, packets.get(0));
        var profile = info.entries().getFirst();
        assertEquals(net.minecraft.world.level.GameType.SPECTATOR, profile.gameMode(),
                "the unlisted camera retains spectator behavior");
        assertFalse(profile.listed());
        assertEquals("", java.util.Objects.requireNonNull(profile.profile()).name());
        assertNotEquals(viewer.getUniqueId(), profile.profileId());
        var spawn = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, packets.get(1));
        assertSame(net.minecraft.world.entity.EntityTypes.PLAYER, spawn.getType());
        assertEquals(65.9, spawn.getY() + net.minecraft.world.entity.EntityTypes.PLAYER.getDimensions().eyeHeight(), 1.0e-6);
        assertEquals(java.util.List.of(101), cameras);
        var initialHead = assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook.class, wrapped.getFirst());
        assertEquals(101, initialHead.getEntityId());
        assertEquals(45, initialHead.getHeadYaw(), 1.0e-6);
        assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class, wrapped.get(1),
                "initialize head direction before attaching the viewer's camera");
        view.move(viewer, feet.clone(), false);
        assertEquals(2, packets.size(), "a stationary camera must not restart interpolation or respawn");
        assertEquals(2, wrapped.size(), "stationary views must not resend head rotation or reattach the camera");
        view.move(viewer, feet.clone().add(0.25, 0, 0), false);
        var movement = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, packets.get(2));
        assertEquals(4.25, movement.position().endPosition().x(), 1.0e-6);
        assertEquals(1, cameras.size(), "ordinary movement must keep the same interpolated camera entity");
        var rotated = feet.clone().add(0.25, 0, 0);
        rotated.setYaw(-135);
        view.move(viewer, rotated, false);
        var head = assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook.class, wrapped.getLast());
        assertEquals(101, head.getEntityId());
        assertEquals(-135, head.getHeadYaw(), 1.0e-6, "body rotation alone cannot steer a spectator player's camera");
        view.move(viewer, rotated.clone().add(100, 80, 100), false);
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, packets.getLast());
        assertEquals(java.util.List.of(101), cameras, "high speed alone must not destroy and recreate the camera every tick");
        int beforeRelocation = packets.size();
        var nextWorld = mock(org.bukkit.World.class);
        view.move(viewer, new org.bukkit.Location(nextWorld, 0, 70, 0), false);
        assertEquals(java.util.List.of(101, 102), cameras);
        var removed = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket.class, packets.get(beforeRelocation + 3));
        assertEquals(java.util.List.of(profile.profileId()), removed.profileIds());
        int beforeCut = packets.size();
        view.move(viewer, new org.bukkit.Location(nextWorld, 4, 65, 8), true);
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, packets.get(beforeCut + 1));
        assertEquals(java.util.List.of(101, 102, 103), cameras,
                "a cut must attach a fresh camera without briefly returning to the viewer's own camera");
        var cutRemoval = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class,
                packets.get(beforeCut + 2));
        assertEquals(102, cutRemoval.entityIds().getInt(0));
        view.clear(viewer);
        assertEquals(java.util.List.of(101, 102, 103, 7), cameras);
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket.class, packets.getLast());
        int count = packets.size();
        view.clear(viewer);
        assertEquals(count, packets.size(), "cleanup must be idempotent");
    }

    private static CommandContextBuilder<CommandSourceStack> context(CommandSourceStack source, String input)
            throws CommandSyntaxException {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("tp")
                .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("target", EntityArgument.entities())
                        .executes(_ -> 1)));
        var parsed = dispatcher.parse("tp " + input, source);
        assertFalse(parsed.getReader().canRead(), "the real entity argument must parse the whole input");
        return parsed.getContext();
    }
}
