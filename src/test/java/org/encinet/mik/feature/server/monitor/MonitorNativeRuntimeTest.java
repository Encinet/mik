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
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var cameras = new java.util.ArrayList<Integer>();
        var ids = new java.util.concurrent.atomic.AtomicInteger(100);
        var viewer = mock(org.bukkit.entity.Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getEntityId()).thenReturn(7);
        when(viewer.getEyeHeight()).thenReturn(0.9);
        var world = mock(org.bukkit.World.class);
        var view = new MonitorCameraView((_, packet) -> packets.add(packet), (_, id) -> cameras.add(id),
                _ -> ids.incrementAndGet());
        var feet = new org.bukkit.Location(world, 4, 65, 8, 45, 10);
        view.move(viewer, feet);
        var info = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class, packets.get(0));
        var profile = info.entries().getFirst();
        assertEquals(net.minecraft.world.level.GameType.SPECTATOR, profile.gameMode(),
                "the client camera itself must be a spectator player to keep inside-wall visibility");
        assertFalse(profile.listed());
        assertEquals("", java.util.Objects.requireNonNull(profile.profile()).name());
        assertNotEquals(viewer.getUniqueId(), profile.profileId());
        var spawn = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, packets.get(1));
        assertSame(net.minecraft.world.entity.EntityTypes.PLAYER, spawn.getType());
        assertEquals(65.9, spawn.getY() + net.minecraft.world.entity.EntityTypes.PLAYER.getDimensions().eyeHeight(), 1.0e-6);
        assertEquals(java.util.List.of(101), cameras);
        view.move(viewer, feet.clone());
        assertEquals(2, packets.size(), "a stationary camera must not restart interpolation or respawn");
        view.move(viewer, feet.clone().add(0.25, 0, 0));
        var movement = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, packets.get(2));
        assertEquals(4.25, movement.position().endPosition().x(), 1.0e-6);
        assertEquals(1, cameras.size(), "ordinary movement must keep the same interpolated camera entity");
        view.move(viewer, new org.bukkit.Location(mock(org.bukkit.World.class), 0, 70, 0));
        assertEquals(java.util.List.of(101, 7, 102), cameras);
        var removed = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket.class, packets.get(4));
        assertEquals(java.util.List.of(profile.profileId()), removed.profileIds());
        view.clear(viewer);
        assertEquals(java.util.List.of(101, 7, 102, 7), cameras);
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
