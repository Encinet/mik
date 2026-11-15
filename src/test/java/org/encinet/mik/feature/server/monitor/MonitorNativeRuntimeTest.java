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
        var errors = System.err;
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            checkNativeSelectors();
            checkSpectatorCamera();
            checkCameraInterpolationTiming();
        } catch (Exception | Error failure) {
            // Bootstrap redirects stderr through logging; retain assertion details at process exit.
            failure.printStackTrace(errors);
            throw failure;
        }
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
        when(manager.getVersion()).thenReturn(com.github.retrooper.packetevents.manager.server.ServerVersion.V_26_3);
        com.github.retrooper.packetevents.PacketEvents.setAPI(api);
        var sent = new java.util.ArrayList<Object>();
        var ids = new java.util.concurrent.atomic.AtomicInteger(100);
        var viewer = mock(org.bukkit.entity.Player.class);
        when(viewer.getEntityId()).thenReturn(7);
        when(viewer.getEyeHeight()).thenReturn(0.9);
        var world = mock(org.bukkit.World.class);
        var view = new MonitorCameraView((_, packet) -> sent.add(packet), (_, packet) -> sent.add(packet),
                _ -> ids.incrementAndGet());
        var feet = new org.bukkit.Location(world, 4, 65, 8, 45, 10);
        view.move(viewer, feet, false);
        var spawn = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, sent.getFirst());
        assertSame(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, spawn.getType(),
                "a player camera can expose a default skin; the camera must have no player model");
        var metadata = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.class, sent.get(1));
        assertEquals(101, metadata.id());
        assertEquals(101, assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class,
                sent.get(2)).getCameraId(), "configure interpolation before attaching");

        var entity = clientDisplay();
        entity.recreateFromPacket(spawn);
        entity.getEntityData().assignValues(metadata.packedItems());
        entity.tick();
        assertEquals(0, entity.getEyeHeight(), "the display's position is the desired camera eye");
        assertEquals(65.9, entity.getEyePosition().y(), 1.0e-6);
        assertEquals(45, entity.getViewYRot(1), 1.0e-6, "display cameras use body yaw without a head-look packet");
        assertTrue(entity.getItemStack().isEmpty());
        assertTrue(entity.itemRenderState() == null || entity.itemRenderState().itemStack().isEmpty(),
                "the real display render state must stay empty even when spectator rendering includes the camera");
        assertEquals(0, entity.getShadowRadius(), "the invisible camera must not cast a shadow");

        view.move(viewer, feet.clone(), false);
        assertEquals(3, sent.size(), "a stationary camera must not restart interpolation or respawn");
        view.move(viewer, feet.clone().add(0.25, 0, 0), false);
        var movement = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, sent.get(3));
        assertEquals(101, movement.id());
        assertEquals(4.25, movement.position().endPosition().x(), 1.0e-6);
        assertEquals(65.9, movement.position().endPosition().y(), 1.0e-6);
        var rotated = feet.clone().add(0.25, 0, 0);
        rotated.setYaw(-135);
        view.move(viewer, rotated, false);
        var turn = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, sent.getLast());
        assertEquals(-135, turn.yRot(), 1.0e-6);
        view.move(viewer, rotated.clone().add(100, 80, 100), false);
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.class, sent.getLast());

        int beforeRelocation = sent.size();
        var nextWorld = mock(org.bukkit.World.class);
        view.move(viewer, new org.bukkit.Location(nextWorld, 0, 70, 0), false);
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, sent.get(beforeRelocation));
        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.class, sent.get(beforeRelocation + 1));
        assertEquals(102, assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class,
                sent.get(beforeRelocation + 2)).getCameraId());
        assertEquals(101, assertInstanceOf(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class,
                sent.get(beforeRelocation + 3)).entityIds().getInt(0), "attach the new world camera before removing the old one");
        int beforeCut = sent.size();
        view.move(viewer, new org.bukkit.Location(nextWorld, 4, 65, 8), true);
        assertEquals(103, assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class,
                sent.get(beforeCut + 2)).getCameraId());
        assertEquals(102, assertInstanceOf(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class,
                sent.get(beforeCut + 3)).entityIds().getInt(0), "cuts must not briefly return to the viewer's camera");

        int beforeClear = sent.size();
        view.clear(viewer);
        assertEquals(7, assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class,
                sent.get(beforeClear)).getCameraId());
        assertEquals(103, assertInstanceOf(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class,
                sent.get(beforeClear + 1)).entityIds().getInt(0));
        int count = sent.size();
        view.clear(viewer);
        assertEquals(count, sent.size(), "cleanup must be idempotent");
        view.move(viewer, feet, false);
        assertEquals(count + 3, sent.size(), "resuming after native first person must create a fresh configured camera");
        assertEquals(104, assertInstanceOf(com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera.class,
                sent.getLast()).getCameraId());
        assertFalse(sent.stream().anyMatch(packet ->
                packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                        || packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket),
                "camera lifecycle must not create player profiles or Tab entries");
        verify(viewer, never()).getAttribute(any());
    }

    private static void checkCameraInterpolationTiming() {
        var entity = clientDisplay();
        var viewer = mock(org.bukkit.entity.Player.class);
        when(viewer.getEyeHeight()).thenReturn(0.9);
        var world = mock(org.bukkit.World.class);
        var view = new MonitorCameraView((_, packet) -> {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket spawn) {
                entity.recreateFromPacket(spawn);
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket metadata) {
                entity.getEntityData().assignValues(metadata.packedItems());
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket movement) {
                var buffer = io.netty.buffer.Unpooled.buffer();
                try {
                    net.minecraft.world.entity.PositionPath.STREAM_CODEC.encode(buffer, movement.position());
                    var path = net.minecraft.world.entity.PositionPath.STREAM_CODEC.decode(buffer);
                    entity.moveOrInterpolateTo(path, movement.yRot(), movement.xRot());
                } finally {
                    buffer.release();
                }
            }
        }, (_, _) -> { }, _ -> 101);
        var feet = new org.bukkit.Location(world, 0, 0, 0);
        view.move(viewer, feet, true);
        for (int tick = 0; tick < 80; tick++) {
            entity.setOldPosAndRot();
            var before = entity.position();
            float beforeYaw = entity.getYRot();
            float beforePitch = entity.getXRot();
            // Fast travel, pause, resumption and reversal must retain the one-tick duration.
            if (tick < 30 || (tick >= 32 && tick < 55)) feet.add(4, 2, -3);
            else if (tick >= 55 && tick < 70) feet.add(-4, -2, 3);
            if (tick < 70) {
                feet.setYaw(net.minecraft.util.Mth.wrapDegrees(170 + tick * 5));
                feet.setPitch(tick % 30 - 15);
            }
            view.move(viewer, feet, false);
            assertEquals(before, entity.position(), "packets must enqueue interpolation instead of jumping immediately");
            entity.getInterpolation().interpolate();
            assertEquals(feet.getX(), entity.getX(), 1.0e-6, "one tick must reach its endpoint, tick " + tick);
            assertEquals(feet.getY() + 0.9, entity.getY(), 1.0e-6);
            assertEquals(feet.getZ(), entity.getZ(), 1.0e-6);
            var halfway = entity.getEyePosition(0.5f);
            assertEquals((before.x() + feet.getX()) / 2, halfway.x(), 1.0e-6);
            assertEquals((before.y() + feet.getY() + 0.9) / 2, halfway.y(), 1.0e-6);
            assertEquals((before.z() + feet.getZ()) / 2, halfway.z(), 1.0e-6);
            assertEquals(0, Math.IEEEremainder(entity.getViewYRot(0.5f)
                    - net.minecraft.util.Mth.rotLerp(0.5f, beforeYaw, feet.getYaw()), 360), 1.0e-5,
                    "render frames must interpolate turns across the 180-degree boundary");
            assertEquals((beforePitch + feet.getPitch()) / 2, entity.getViewXRot(0.5f), 1.0e-6);
        }
    }

    private static net.minecraft.world.entity.Display.ItemDisplay clientDisplay() {
        var level = mock(net.minecraft.world.level.Level.class);
        when(level.isClientSide()).thenReturn(true);
        when(level.getRelativeTickSpeed()).thenReturn(1f);
        var config = mock(io.papermc.paper.configuration.WorldConfiguration.class);
        config.entities = mock(io.papermc.paper.configuration.WorldConfiguration.Entities.class);
        config.entities.spawning = mock(io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.class);
        config.entities.spawning.despawnTime = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<>();
        when(level.paperConfig()).thenReturn(config);
        // Paper-only activation bookkeeping is irrelevant to the shared client interpolation code.
        try (var activation = mockStatic(io.papermc.paper.entity.activation.ActivationRange.class)) {
            activation.when(() -> io.papermc.paper.entity.activation.ActivationRange.initializeEntityActivationState(any(), any()))
                    .thenReturn(true);
            return new net.minecraft.world.entity.Display.ItemDisplay(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, level);
        }
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
